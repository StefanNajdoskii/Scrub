package com.scrub.app.face

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.scrub.app.data.Gallery
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Developer tool, not a pass/fail test: fingerprints every face in the device gallery the same
 * way a scan does and writes the results to the app's private cache (cache/eval/<tag>.txt),
 * plus each face crop as a PNG so faces can be labelled by eye:
 * - FACE: quality numbers per face
 * - SIM: pairwise similarity with the new pipeline (straighten + flip) and the old one
 *   (axis-aligned crop, single embedding)
 * - BLUR: sharpness and drift from the sharp embedding as the photo is blurred
 * - ROT: the photo rotated by ±20°: reported roll and similarity to the upright embedding
 * Only runs when the instrumentation argument `eval=<tag>` is given, so a normal
 * connectedAndroidTest run never reads the gallery.
 * Pull results with `adb shell run-as com.scrub.app cat cache/eval/<tag>.txt`.
 */
@RunWith(AndroidJUnit4::class)
class GalleryEvalTest {

    private class Face(val id: String, val face: DetectedFace, val legacy: FloatArray)

    @Test
    fun dumpGallerySimilarities(): Unit = runBlocking {
        val tag = InstrumentationRegistry.getArguments().getString("eval")
        assumeTrue("pass -e eval <tag> to run", tag != null)
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(ctx.cacheDir, "eval").apply { mkdirs() }
        val images = Gallery.queryAll(ctx.contentResolver)
        val faces = ArrayList<Face>()
        val sb = StringBuilder()
        FaceEmbedder(ctx).use { legacyEmbedder ->
            FaceFinder(ctx).use { finder ->
                for (img in images) {
                    val bmp = Gallery.decode(ctx.contentResolver, img.uri, 1024) ?: continue
                    val found = finder.findFaces(bmp, withThumbnails = true)
                    found.forEachIndexed { i, f ->
                        val id = "${img.id}_$i"
                        f.thumbnail?.let { t ->
                            File(dir, "$tag-$id.png").outputStream().use { t.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        }
                        val legacy = legacyCrop(bmp, f.box)?.let { legacyEmbedder.embed(it) } ?: FloatArray(128)
                        faces += Face(id, f, legacy)
                    }
                    // Blur and rotation probes on the largest face of this photo
                    val main = found.maxByOrNull { it.box.width() }
                    if (main != null) {
                        for (r in listOf(1, 2, 3, 5, 8)) {
                            val blurred = boxBlur3(bmp, r)
                            val bf = finder.findFaces(blurred).maxByOrNull { it.box.width() }
                            sb.append("BLUR ${img.id} r=$r ")
                            if (bf == null) sb.append("no face\n")
                            else sb.append("sharp=${f0(bf.quality.sharpness)} sim=${f4(FaceMath.similarity(bf.embedding, main.embedding))}\n")
                            blurred.recycle()
                        }
                        for (deg in listOf(20f, -20f)) {
                            val rotated = rotate(bmp, deg)
                            val rf = finder.findFaces(rotated).maxByOrNull { it.box.width() }
                            sb.append("ROT ${img.id} deg=$deg ")
                            if (rf == null) sb.append("no face\n")
                            else {
                                val leg = legacyCrop(rotated, rf.box)?.let { legacyEmbedder.embed(it) }
                                sb.append(
                                    "roll=${f0(rf.quality.roll)} (upright ${f0(main.quality.roll)}) " +
                                        "new_sim=${f4(FaceMath.similarity(rf.embedding, main.embedding))} " +
                                        "old_sim=${leg?.let { f4(FaceMath.similarity(it, legacyCrop(bmp, main.box)!!.let(legacyEmbedder::embed))) }}\n"
                                )
                            }
                            rotated.recycle()
                        }
                    }
                    bmp.recycle()
                }
            }
        }
        for (f in faces) {
            val q = f.face.quality
            sb.append("FACE ${f.id} size=${q.sizePx} yaw=${f0(q.yaw)} roll=${f0(q.roll)} sharp=${f0(q.sharpness)} usable=${q.usable} goodRef=${q.goodReference}\n")
        }
        for (a in faces.indices) for (b in a + 1 until faces.size) {
            val fa = faces[a]; val fb = faces[b]
            sb.append(
                "SIM ${fa.id} ${fb.id} new=${f4(FaceMath.similarity(fa.face.embedding, fb.face.embedding))} " +
                    "old=${f4(FaceMath.similarity(fa.legacy, fb.legacy))}\n"
            )
        }
        File(dir, "$tag.txt").writeText(sb.toString())
        Log.i("EVAL", "wrote ${faces.size} faces")
    }

    /** The pre-straightening crop: axis-aligned square, clamped to the image. */
    private fun legacyCrop(src: Bitmap, box: Rect): Bitmap? {
        val side = (max(box.width(), box.height()) * 1.15f).toInt()
        val left = max(0, box.centerX() - side / 2)
        val top = max(0, box.centerY() - side / 2)
        val right = min(src.width, box.centerX() + side / 2)
        val bottom = min(src.height, box.centerY() + side / 2)
        if (right - left < 8 || bottom - top < 8) return null
        return Bitmap.createBitmap(src, left, top, right - left, bottom - top)
    }

    private fun rotate(src: Bitmap, deg: Float): Bitmap =
        Bitmap.createBitmap(src, 0, 0, src.width, src.height, Matrix().apply { postRotate(deg) }, true)

    /** Three box blurs of radius [r] ≈ a Gaussian with sigma ≈ r. */
    private fun boxBlur3(src: Bitmap, r: Int): Bitmap {
        val w = src.width; val h = src.height
        var px = IntArray(w * h).also { src.getPixels(it, 0, w, 0, 0, w, h) }
        repeat(3) { px = blurPass(blurPass(px, w, h, r, true), w, h, r, false) }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun blurPass(src: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean): IntArray {
        val out = IntArray(src.size)
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        for (line in 0 until lines) {
            for (i in 0 until len) {
                var rs = 0; var gs = 0; var bs = 0; var n = 0
                for (k in max(0, i - r)..min(len - 1, i + r)) {
                    val p = if (horizontal) src[line * w + k] else src[k * w + line]
                    rs += (p shr 16) and 0xFF; gs += (p shr 8) and 0xFF; bs += p and 0xFF; n++
                }
                val idx = if (horizontal) line * w + i else i * w + line
                out[idx] = (0xFF shl 24) or ((rs / n) shl 16) or ((gs / n) shl 8) or (bs / n)
            }
        }
        return out
    }

    private fun f4(v: Float) = String.format(Locale.US, "%.4f", v)
    private fun f0(v: Float) = String.format(Locale.US, "%.0f", v)
}
