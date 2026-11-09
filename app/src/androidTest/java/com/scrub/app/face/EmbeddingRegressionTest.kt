package com.scrub.app.face

import android.graphics.Bitmap
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards the FaceNet runtime: the same input must give the same 128-number embedding as the
 * reference run, so faces.bin caches and the strictness default stay valid after upgrading the
 * TFLite / LiteRT runtime.
 *
 * GOLDEN_* were recorded with org.tensorflow:tensorflow-lite:2.16.1, before the move to LiteRT.
 * To re-record, run with the instrumentation argument `record=true`
 * (`adb shell am instrument -w -e record true ...`) and copy the "EMBED" logcat lines.
 */
@RunWith(AndroidJUnit4::class)
class EmbeddingRegressionTest {

    @Test
    fun embeddingsMatchReferenceRuntime() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val record = InstrumentationRegistry.getArguments().getString("record") == "true"
        FaceEmbedder(ctx).use { embedder ->
            val inputs = listOf("gradient" to gradient(), "noise" to noise())
            for ((name, bitmap) in inputs) {
                val e = embedder.embed(bitmap)
                Log.i("EMBED", "$name=" + e.joinToString(",") { "%.7ef".format(it) })
                if (record) continue
                val golden = if (name == "gradient") GOLDEN_GRADIENT else GOLDEN_NOISE
                assertEquals(FaceEmbedder.EMBEDDING_SIZE, e.size)
                // LiteRT 1.4's CPU kernels differ from TFLite 2.16.1's in the last float digits
                // (measured: max |diff| 3.4e-3, cosine 0.99988). Matching uses a cosine cut-off
                // of ~0.55, so this is far below anything that changes a match.
                for (i in e.indices) assertEquals("$name[$i]", golden[i], e[i], 5e-3f)
                val cosine = FaceEmbedder.similarity(golden, e)
                assertTrue("$name cosine $cosine", cosine > 0.9995f)
            }
        }
    }

    /** Smooth color gradient, 200x180 (not 160x160, so the resize step is exercised too). */
    private fun gradient(): Bitmap {
        val w = 200
        val h = 180
        val px = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            val r = x * 255 / (w - 1)
            val g = y * 255 / (h - 1)
            val b = (x + y) * 255 / (w + h - 2)
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Fixed pseudo-random pixels (LCG with a constant seed). */
    private fun noise(): Bitmap {
        val w = 160
        val h = 160
        var s = 12345L
        val px = IntArray(w * h) {
            s = (s * 6364136223846793005L + 1442695040888963407L)
            (0xFF shl 24) or ((s ushr 40).toInt() and 0xFFFFFF)
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private companion object {
        val GOLDEN_GRADIENT = floatArrayOf(
            1.2811373e-03f, -9.6170865e-02f, -3.8914811e-02f, -7.7164628e-02f,
            1.0364127e-01f, 2.8800959e-02f, -8.9998515e-03f, -4.5712296e-02f,
            1.0458517e-02f, 2.1436376e-02f, 6.8963423e-02f, -8.5114457e-02f,
            2.0764641e-02f, -1.0793234e-01f, -1.1865135e-03f, 6.5441258e-02f,
            1.4919731e-01f, -1.4158481e-01f, 6.2268116e-02f, -1.3268191e-01f,
            -3.1072434e-02f, 2.0522237e-02f, -5.5565067e-02f, 2.0582367e-02f,
            5.5067480e-02f, 8.5016992e-03f, 1.9728911e-01f, 1.1570344e-01f,
            -9.8884366e-02f, -1.0216274e-02f, -4.4969633e-02f, 9.1988064e-02f,
            -2.9268643e-02f, -8.1362948e-03f, 8.3035551e-02f, -7.1034213e-03f,
            3.1721041e-02f, 8.2927845e-02f, 1.3156536e-01f, 8.0062181e-02f,
            4.4897411e-02f, 2.2374732e-02f, -2.3345424e-02f, -1.0799460e-01f,
            -1.3322216e-01f, -8.7830216e-02f, -3.8625754e-03f, -7.0904180e-02f,
            9.4563574e-02f, 3.9617967e-02f, -8.2727812e-02f, 7.6505020e-02f,
            9.9420846e-02f, -9.4000898e-02f, 1.4741516e-02f, 1.2055667e-01f,
            6.9913305e-02f, -2.6403347e-02f, -4.1570328e-02f, -2.4387762e-01f,
            -1.1587760e-01f, -1.1601852e-01f, 4.5170806e-02f, 1.8314441e-01f,
            -4.6230577e-02f, 6.2146064e-02f, 4.8981141e-02f, -2.2321621e-02f,
            3.2735948e-02f, -5.4917213e-02f, 2.4530876e-02f, -9.0978272e-02f,
            8.2459562e-02f, -1.3159807e-01f, 9.3141204e-04f, -8.1420951e-03f,
            -1.0249273e-02f, 1.2720720e-01f, -1.1463519e-01f, -9.0115778e-02f,
            -1.3075587e-01f, -5.1068403e-02f, 3.6157640e-03f, 1.2735005e-01f,
            7.9785071e-02f, 6.5429837e-02f, 1.8308517e-01f, -1.3379361e-01f,
            -1.8434343e-01f, 1.9722542e-01f, 9.3969405e-02f, -4.0022854e-02f,
            -1.0454326e-02f, -4.4875117e-03f, 1.6384120e-01f, 3.4720954e-02f,
            2.2570176e-02f, -8.5137568e-02f, -1.5098593e-01f, 4.6576649e-02f,
            -7.6151624e-02f, 1.7324610e-01f, 1.0308912e-01f, -6.9749415e-02f,
            4.5616653e-02f, 3.1743988e-02f, -1.0205714e-01f, 3.0512050e-02f,
            -9.8734036e-02f, -1.5054786e-01f, 1.2274876e-01f, 3.4151964e-02f,
            6.9251239e-02f, 1.7377397e-01f, 2.6556600e-02f, 6.6914506e-02f,
            -5.1705346e-02f, 1.4099328e-01f, 5.5459198e-02f, 4.4199703e-03f,
            -3.5651643e-02f, -6.3044213e-02f, 4.1323684e-02f, 1.4955146e-02f,
            6.3993786e-03f, -9.1996200e-02f, 4.7865801e-02f, 3.8712606e-02f,
        )
        val GOLDEN_NOISE = floatArrayOf(
            -4.3003660e-02f, -1.2398519e-01f, -2.7010225e-02f, -1.8694846e-01f,
            -5.0133385e-04f, 6.2242396e-02f, -5.4236557e-02f, -2.2261595e-02f,
            4.4372533e-02f, 2.6397020e-02f, -1.8145844e-02f, 8.1398256e-02f,
            1.7807703e-01f, -8.4939823e-02f, 2.4813185e-03f, 2.6451068e-02f,
            5.2273761e-02f, -8.9159431e-03f, 1.1903553e-01f, -7.0147477e-02f,
            -3.2802410e-02f, 1.0174694e-02f, -6.6374660e-02f, 1.2085405e-01f,
            1.2331557e-01f, -4.7233328e-02f, 1.2170714e-01f, 1.1328126e-01f,
            1.5133528e-02f, -4.3780658e-02f, -5.3242888e-02f, 1.2946489e-01f,
            3.5102032e-02f, -6.0078382e-02f, 1.1688895e-02f, 4.8228521e-02f,
            -1.0439498e-01f, 3.7816711e-02f, 3.8388971e-02f, 8.8656597e-02f,
            4.1092262e-02f, 5.8024105e-02f, 8.3544411e-02f, 1.2494735e-01f,
            4.6656847e-02f, -1.3187565e-01f, -8.8427126e-02f, -1.2482858e-01f,
            3.5371847e-02f, 1.6710270e-02f, -2.5620857e-01f, 5.1057119e-02f,
            -8.2629092e-02f, -2.9077103e-02f, -1.0819464e-02f, 6.8435065e-02f,
            3.2702096e-02f, -4.3062918e-02f, -1.3596296e-01f, -1.7265540e-01f,
            -5.6808021e-02f, -2.4056610e-02f, -8.1063680e-02f, -5.1435135e-02f,
            -1.0100441e-01f, 1.9151270e-01f, 2.3482041e-02f, 2.0753842e-02f,
            -1.5189740e-02f, -1.4948204e-01f, -2.0714609e-02f, 6.1337825e-02f,
            4.2633958e-02f, -4.7067206e-02f, 8.8450141e-02f, -1.2497707e-01f,
            -1.6161101e-01f, -1.1856757e-01f, -6.0689874e-02f, 8.5075229e-02f,
            5.2927341e-02f, 4.7303762e-02f, -4.6582576e-02f, 7.0305683e-02f,
            1.4566217e-01f, 8.0064550e-02f, 5.8121298e-02f, 1.0085328e-02f,
            -1.0566761e-01f, 2.0780826e-01f, 6.0419228e-02f, -3.2492004e-02f,
            -6.2577605e-02f, -2.6014697e-02f, 6.0748212e-02f, 1.7617087e-01f,
            -1.7963980e-01f, -3.4424473e-02f, -1.6926424e-01f, -4.5790657e-02f,
            2.0425492e-03f, -1.6075841e-03f, 5.5835944e-02f, -1.9667311e-01f,
            -1.0665853e-01f, -8.0753222e-02f, -6.9541372e-02f, -1.3383240e-02f,
            6.0313750e-02f, -1.1804686e-01f, 1.2278835e-01f, -3.3571973e-02f,
            7.1749456e-02f, 7.3303781e-02f, 1.7009510e-02f, 5.6717068e-02f,
            5.0093446e-02f, 3.5367094e-02f, 1.0428958e-01f, 4.7212318e-02f,
            8.4409609e-02f, 4.8400670e-02f, 1.1233749e-01f, -5.5383582e-02f,
            6.7669287e-02f, -6.4573660e-02f, 1.1473020e-01f, 6.8117785e-03f,
        )
    }
}
