package com.scrub.app.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.tasks.await
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** One face found in a picture. [embedding] is an L2-normalised 128-number "face fingerprint". */
class DetectedFace(
    val box: Rect,
    val embedding: FloatArray,
    val thumbnail: Bitmap?,
)

/**
 * Turns a cropped face into a 128-dimensional embedding using FaceNet (assets/facenet.tflite).
 * Two photos of the same person give embeddings that point in nearly the same direction,
 * so we compare them with cosine similarity.
 */
class FaceEmbedder(context: Context) : Closeable {

    private val interpreter: Interpreter
    private val input: ByteBuffer =
        ByteBuffer.allocateDirect(4 * SIZE * SIZE * 3).order(ByteOrder.nativeOrder())
    private val output = Array(1) { FloatArray(EMBEDDING_SIZE) }
    private val pixels = IntArray(SIZE * SIZE)
    private val values = FloatArray(SIZE * SIZE * 3)

    init {
        val bytes = context.assets.open("facenet.tflite").use { it.readBytes() }
        val model = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
        model.put(bytes)
        model.rewind()
        interpreter = Interpreter(model, Interpreter.Options().setNumThreads(4))
    }

    @Synchronized
    fun embed(face: Bitmap): FloatArray {
        val scaled = Bitmap.createScaledBitmap(face, SIZE, SIZE, true)
        scaled.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        if (scaled !== face) scaled.recycle()

        // Per-image standardisation (what this FaceNet model was trained with)
        var sum = 0.0
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = ((p shr 16) and 0xFF).toFloat()
            val g = ((p shr 8) and 0xFF).toFloat()
            val b = (p and 0xFF).toFloat()
            values[i * 3] = r
            values[i * 3 + 1] = g
            values[i * 3 + 2] = b
            sum += r + g + b
        }
        val n = values.size
        val mean = (sum / n).toFloat()
        var sq = 0.0
        for (v in values) {
            val d = v - mean
            sq += d * d
        }
        val std = max(sqrt(sq / n).toFloat(), 1f / sqrt(n.toFloat()))

        input.rewind()
        for (v in values) input.putFloat((v - mean) / std)
        input.rewind()

        interpreter.run(input, output)
        return normalize(output[0].copyOf())
    }

    override fun close() = interpreter.close()

    companion object {
        const val SIZE = 160
        const val EMBEDDING_SIZE = 128

        fun normalize(v: FloatArray): FloatArray {
            var s = 0f
            for (x in v) s += x * x
            val len = sqrt(s)
            if (len > 0f) for (i in v.indices) v[i] /= len
            return v
        }

        /** Cosine similarity of two normalised vectors: 1 = identical, 0 = unrelated. */
        fun similarity(a: FloatArray, b: FloatArray): Float {
            var s = 0f
            for (i in a.indices) s += a[i] * b[i]
            return s
        }
    }
}

/** Finds faces in a bitmap (ML Kit) and fingerprints each one (FaceNet). Runs fully on-device. */
class FaceFinder(context: Context) : Closeable {

    private val detector: FaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setMinFaceSize(0.06f)
            .build()
    )
    private val embedder = FaceEmbedder(context)

    suspend fun findFaces(bitmap: Bitmap, withThumbnails: Boolean = false): List<DetectedFace> {
        val faces = detector.process(InputImage.fromBitmap(bitmap, 0)).await()
        return faces.mapNotNull { face ->
            val crop = cropSquare(bitmap, face.boundingBox) ?: return@mapNotNull null
            val embedding = embedder.embed(crop)
            val thumb = if (withThumbnails) crop else {
                crop.recycle(); null
            }
            DetectedFace(face.boundingBox, embedding, thumb)
        }
    }

    /** Square crop around the face with a little margin, clamped to the image. */
    private fun cropSquare(src: Bitmap, box: Rect): Bitmap? {
        val side = (max(box.width(), box.height()) * 1.15f).toInt()
        if (side < MIN_FACE_PX) return null
        val cx = box.centerX()
        val cy = box.centerY()
        val left = max(0, cx - side / 2)
        val top = max(0, cy - side / 2)
        val right = min(src.width, cx + side / 2)
        val bottom = min(src.height, cy + side / 2)
        val w = right - left
        val h = bottom - top
        if (w < MIN_FACE_PX || h < MIN_FACE_PX) return null
        val crop = Bitmap.createBitmap(src, left, top, w, h)
        // createBitmap can hand back the source itself; never let callers recycle that
        return if (crop === src) src.copy(Bitmap.Config.ARGB_8888, false) else crop
    }

    override fun close() {
        detector.close()
        embedder.close()
    }

    companion object {
        private const val MIN_FACE_PX = 40
    }
}
