package com.scrub.app.face

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.sqrt

/** A face fingerprint plus the quality numbers measured when it was made. Cached in faces.bin. */
class FaceRecord(val embedding: FloatArray, val quality: FaceQuality)

/**
 * How trustworthy a face crop is for matching.
 *
 * @param sizePx width of ML Kit's face box, in pixels of the decoded image (scans decode at
 *   up to 1024 px, reference photos at up to 1600 px).
 * @param yaw left/right head turn in degrees (ML Kit headEulerAngleY), 0 = facing the camera.
 * @param roll in-plane tilt in degrees (headEulerAngleZ). The crop is straightened by this.
 * @param sharpness variance of the Laplacian of the 64x64 grayscale crop. Higher = sharper.
 */
data class FaceQuality(
    val sizePx: Int,
    val yaw: Float,
    val roll: Float,
    val sharpness: Float,
) {
    val tooSmall: Boolean get() = sizePx < MIN_SIZE_PX
    val tooTurned: Boolean get() = abs(yaw) > MAX_YAW
    val tooBlurry: Boolean get() = sharpness < MIN_SHARPNESS

    /** Whether this face takes part in matching at all. */
    val usable: Boolean get() = !tooSmall && !tooTurned && !tooBlurry

    /**
     * Stricter bar for a reference face, since every photo is compared against it. Below it the
     * Pick face screen warns but still lets the user continue.
     */
    val goodReference: Boolean
        get() = sizePx >= REF_MIN_SIZE_PX && abs(yaw) <= REF_MAX_YAW && sharpness >= REF_MIN_SHARPNESS

    companion object {
        const val MIN_SIZE_PX = 56
        const val MAX_YAW = 45f

        /**
         * Measured with GalleryEvalTest on the emulator's test faces (decoded at 1024 px):
         * sharp photos score 1800-5300. Blurred with a Gaussian of sigma ~5 px they score
         * 80-170 and the embedding still holds (0.88-0.92 similar to the sharp one); at
         * sigma ~8 px they score 19-51 and the embedding drifts (0.67-0.77). 60 drops only
         * that last, very blurry band.
         */
        const val MIN_SHARPNESS = 60f

        /** References are compared with every photo, so they get a stricter bar. */
        const val REF_MIN_SIZE_PX = 80
        const val REF_MAX_YAW = 30f
        const val REF_MIN_SHARPNESS = 150f

        /** Side of the grayscale image the sharpness is measured on. */
        const val SHARPNESS_SIDE = 64

        /** Variance of the Laplacian on a 64x64 grayscale copy of [crop]. */
        fun sharpnessOf(crop: Bitmap): Float {
            val n = SHARPNESS_SIDE
            val small = Bitmap.createScaledBitmap(crop, n, n, true)
            val px = IntArray(n * n)
            small.getPixels(px, 0, n, 0, 0, n, n)
            if (small !== crop) small.recycle()
            val gray = FloatArray(n * n) { i ->
                val p = px[i]
                0.299f * ((p shr 16) and 0xFF) + 0.587f * ((p shr 8) and 0xFF) + 0.114f * (p and 0xFF)
            }
            return laplacianVariance(gray, n, n)
        }

        /** 4-neighbour Laplacian over the interior pixels, then its variance. */
        fun laplacianVariance(gray: FloatArray, w: Int, h: Int): Float {
            if (w < 3 || h < 3) return 0f
            var sum = 0.0
            var sq = 0.0
            var count = 0
            for (y in 1 until h - 1) for (x in 1 until w - 1) {
                val i = y * w + x
                val l = (gray[i - 1] + gray[i + 1] + gray[i - w] + gray[i + w] - 4f * gray[i]).toDouble()
                sum += l
                sq += l * l
                count++
            }
            val mean = sum / count
            return (sq / count - mean * mean).toFloat()
        }
    }
}

/** Vector maths for embeddings. */
object FaceMath {
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
