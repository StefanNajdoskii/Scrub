package com.scrub.app.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
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
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** One face found in a picture: where it is, its fingerprint and quality, and optionally its crop. */
class DetectedFace(
    val box: Rect,
    val record: FaceRecord,
    val thumbnail: Bitmap?,
) {
    val embedding: FloatArray get() = record.embedding
    val quality: FaceQuality get() = record.quality
}

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

    /** Raw model output for [face], resized to 160x160 and L2-normalised. */
    @Synchronized
    fun embed(face: Bitmap): FloatArray {
        loadPixels(face)
        return run()
    }

    /**
     * Embedding of [face] averaged with the embedding of its mirror image, then L2-normalised.
     * Faces are roughly symmetric, so this cancels some of the noise from lighting and pose
     * at the cost of a second model run. This is what scanning and reference faces use.
     */
    @Synchronized
    fun embedWithFlip(face: Bitmap): FloatArray {
        loadPixels(face)
        val a = run()
        mirrorPixels()
        val b = run()
        for (i in a.indices) a[i] += b[i]
        return FaceMath.normalize(a)
    }

    private fun loadPixels(face: Bitmap) {
        val scaled = Bitmap.createScaledBitmap(face, SIZE, SIZE, true)
        scaled.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        if (scaled !== face) scaled.recycle()
    }

    private fun mirrorPixels() {
        for (y in 0 until SIZE) {
            var l = y * SIZE
            var r = l + SIZE - 1
            while (l < r) {
                val t = pixels[l]; pixels[l] = pixels[r]; pixels[r] = t
                l++; r--
            }
        }
    }

    /** Runs FaceNet on [pixels] and returns a new normalised vector. */
    private fun run(): FloatArray {
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
        return FaceMath.normalize(output[0].copyOf())
    }

    override fun close() = interpreter.close()

    companion object {
        const val SIZE = 160
        const val EMBEDDING_SIZE = 128

        fun normalize(v: FloatArray): FloatArray = FaceMath.normalize(v)

        /** Cosine similarity of two normalised vectors: 1 = identical, 0 = unrelated. */
        fun similarity(a: FloatArray, b: FloatArray): Float = FaceMath.similarity(a, b)
    }
}

/** Cuts faces out of a photo, straightened so the eyes are level. */
object FaceAligner {

    /** Crops are rendered at most this big; FaceNet only sees 160x160 anyway. */
    private const val MAX_CROP = 480

    /** Square margin around ML Kit's face box. */
    const val MARGIN = 1.15f

    /**
     * Square crop of side [side] centred on ([cx], [cy]) in [src], rotated by [rollDegrees]
     * clockwise so a face that ML Kit reports as tilted by that much (headEulerAngleZ,
     * positive = counter-clockwise) comes out upright. Pixels outside [src] repeat its edge,
     * so faces near a border don't get hard black corners.
     */
    fun straightenedCrop(src: Bitmap, cx: Float, cy: Float, side: Float, rollDegrees: Float): Bitmap {
        val out = min(side.roundToInt(), MAX_CROP).coerceAtLeast(1)
        val scale = out / side
        val m = Matrix().apply {
            setTranslate(-cx, -cy)
            // Android rotates clockwise for positive degrees (y points down).
            postRotate(rollDegrees)
            postScale(scale, scale)
            postTranslate(out / 2f, out / 2f)
        }
        val shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(m) }
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { this.shader = shader }
        val dst = Bitmap.createBitmap(out, out, Bitmap.Config.ARGB_8888)
        Canvas(dst).drawRect(0f, 0f, out.toFloat(), out.toFloat(), paint)
        return dst
    }

    /** The crop the scanner uses for an ML Kit face box. */
    fun cropFor(src: Bitmap, box: Rect, rollDegrees: Float): Bitmap =
        straightenedCrop(
            src,
            box.exactCenterX(),
            box.exactCenterY(),
            max(box.width(), box.height()) * MARGIN,
            rollDegrees,
        )
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

    /**
     * Every face in [bitmap], each with an embedding and quality numbers. Low-quality faces are
     * kept (and cached) too; matching skips them via [FaceQuality.usable], so the rules can
     * change later without rescanning.
     */
    suspend fun findFaces(bitmap: Bitmap, withThumbnails: Boolean = false): List<DetectedFace> {
        val faces = detector.process(InputImage.fromBitmap(bitmap, 0)).await()
        return faces.mapNotNull { face ->
            val box = face.boundingBox
            if (max(box.width(), box.height()) < MIN_FACE_PX) return@mapNotNull null
            val roll = face.headEulerAngleZ
            val crop = FaceAligner.cropFor(bitmap, box, roll)
            val quality = FaceQuality(
                sizePx = box.width(),
                yaw = face.headEulerAngleY,
                roll = roll,
                sharpness = FaceQuality.sharpnessOf(crop),
            )
            val embedding = embedder.embedWithFlip(crop)
            val thumb = if (withThumbnails) crop else {
                crop.recycle(); null
            }
            DetectedFace(box, FaceRecord(embedding, quality), thumb)
        }
    }

    override fun close() {
        detector.close()
        embedder.close()
    }

    companion object {
        /** Below this there's nothing worth fingerprinting, even for the cache. */
        private const val MIN_FACE_PX = 24
    }
}
