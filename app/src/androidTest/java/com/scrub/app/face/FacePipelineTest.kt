package com.scrub.app.face

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The full fingerprint pipeline after detection: straighten (FaceAligner) + flip-averaged,
 * normalised embedding (FaceEmbedder.embedWithFlip). EmbeddingRegressionTest covers the raw
 * model on its own.
 *
 * Uses a drawn, deliberately lopsided face so the test needs no photo of a real person.
 */
@RunWith(AndroidJUnit4::class)
class FacePipelineTest {

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val box = Rect(140, 110, 340, 350)

    private fun fingerprint(embedder: FaceEmbedder, src: Bitmap, box: Rect, roll: Float): FloatArray =
        embedder.embedWithFlip(FaceAligner.cropFor(src, box, roll))

    @Test
    fun faceAndItsMirrorGiveTheSameEmbedding() {
        FaceEmbedder(ctx).use { embedder ->
            val face = drawFace()
            val mirror = mirror(face)
            val mirroredBox = Rect(face.width - box.right, box.top, face.width - box.left, box.bottom)
            for (roll in listOf(0f, 12f, -25f)) {
                val a = fingerprint(embedder, face, box, roll)
                // A mirrored face is tilted the other way
                val b = fingerprint(embedder, mirror, mirroredBox, -roll)
                assertEquals(1f, norm(a), 1e-4f)
                assertEquals(1f, norm(b), 1e-4f)
                val cos = FaceMath.similarity(a, b)
                assertTrue("roll $roll: cosine $cos", cos > 0.98f)
            }
        }
    }

    @Test
    fun straighteningUndoesRoll() {
        FaceEmbedder(ctx).use { embedder ->
            val face = drawFace()
            val upright = fingerprint(embedder, face, box, 0f)
            for (roll in listOf(15f, -30f)) {
                // Tilt the face counter-clockwise by `roll` (ML Kit's sign for headEulerAngleZ);
                // Android rotates clockwise for positive degrees, hence the minus.
                val tilted = Bitmap.createBitmap(face.width, face.height, Bitmap.Config.ARGB_8888)
                Canvas(tilted).apply {
                    drawColor(Color.rgb(90, 110, 130))
                    drawBitmap(face, Matrix().apply { setRotate(-roll, box.exactCenterX(), box.exactCenterY()) },
                        Paint(Paint.FILTER_BITMAP_FLAG))
                }
                val straightened = FaceMath.similarity(upright, fingerprint(embedder, tilted, box, roll))
                val wrongWay = FaceMath.similarity(upright, fingerprint(embedder, tilted, box, -roll))
                assertTrue("roll $roll: straightened $straightened", straightened > 0.98f)
                assertTrue("roll $roll: straightened $straightened vs wrong way $wrongWay", straightened > wrongWay)
            }
        }
    }

    @Test
    fun sharpnessDropsWithBlur() {
        val face = drawFace()
        val crop = FaceAligner.cropFor(face, box, 0f)
        val blurry = Bitmap.createScaledBitmap(Bitmap.createScaledBitmap(crop, 12, 12, true), crop.width, crop.height, true)
        val sharp = FaceQuality.sharpnessOf(crop)
        val soft = FaceQuality.sharpnessOf(blurry)
        assertTrue("sharp $sharp vs blurry $soft", sharp > soft * 4)
    }

    private fun norm(v: FloatArray): Float = kotlin.math.sqrt(FaceMath.similarity(v, v))

    private fun mirror(src: Bitmap): Bitmap =
        Bitmap.createBitmap(src, 0, 0, src.width, src.height, Matrix().apply { setScale(-1f, 1f) }, false)

    /** 480x480 cartoon face, asymmetric on purpose (hair parting, one bigger eye, a mole). */
    private fun drawFace(): Bitmap {
        val bmp = Bitmap.createBitmap(480, 480, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        c.drawColor(Color.rgb(90, 110, 130))
        p.color = Color.rgb(60, 40, 30)
        c.drawOval(RectF(120f, 80f, 360f, 300f), p)                // hair
        p.color = Color.rgb(224, 182, 150)
        c.drawOval(RectF(150f, 120f, 330f, 350f), p)                // face
        p.color = Color.rgb(60, 40, 30)
        c.drawOval(RectF(150f, 100f, 300f, 170f), p)                // fringe, swept to one side
        p.color = Color.WHITE
        c.drawOval(RectF(178f, 200f, 222f, 226f), p)
        c.drawOval(RectF(260f, 202f, 296f, 222f), p)
        p.color = Color.rgb(50, 70, 40)
        c.drawCircle(200f, 213f, 10f, p)
        c.drawCircle(278f, 212f, 8f, p)
        p.color = Color.rgb(190, 140, 115)
        c.drawOval(RectF(228f, 230f, 256f, 280f), p)                // nose
        p.color = Color.rgb(170, 70, 80)
        c.drawOval(RectF(205f, 295f, 285f, 315f), p)                // mouth
        p.color = Color.rgb(90, 60, 50)
        c.drawCircle(292f, 270f, 4f, p)                             // mole
        return bmp
    }
}
