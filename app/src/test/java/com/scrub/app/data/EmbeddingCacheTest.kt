package com.scrub.app.data

import com.scrub.app.face.FaceQuality
import com.scrub.app.face.FaceRecord
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.DataOutputStream

class EmbeddingCacheTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun versionOneCacheIsIgnoredSoPhotosGetRescanned() {
        val file = tmp.newFile("faces.bin")
        DataOutputStream(file.outputStream()).use { out ->
            out.writeInt(1)          // old version
            out.writeInt(1)          // one photo
            out.writeLong(7L); out.writeLong(100L); out.writeInt(1)
            repeat(128) { out.writeFloat(0.1f) }
        }
        val cache = EmbeddingCache(file).apply { load() }
        assertNull(cache.get(7L, 100L))
    }

    @Test
    fun embeddingsAndQualityRoundTrip() {
        // Not pre-created: on Windows File.renameTo won't replace an existing file (Android does)
        val file = java.io.File(tmp.root, "faces.bin")
        val embedding = FloatArray(128) { it / 128f }
        val quality = FaceQuality(sizePx = 143, yaw = -12.5f, roll = 7.25f, sharpness = 812f)
        EmbeddingCache(file).apply {
            load()
            put(7L, 100L, listOf(FaceRecord(embedding, quality)))
            put(8L, 200L, emptyList())
            save()
        }
        val reloaded = EmbeddingCache(file).apply { load() }
        val faces = reloaded.get(7L, 100L)!!
        assertEquals(1, faces.size)
        assertArrayEquals(embedding, faces[0].embedding, 0f)
        assertEquals(quality, faces[0].quality)
        assertEquals(0, reloaded.get(8L, 200L)!!.size)
        assertNull(reloaded.get(7L, 101L))   // edited photo: rescan
    }
}
