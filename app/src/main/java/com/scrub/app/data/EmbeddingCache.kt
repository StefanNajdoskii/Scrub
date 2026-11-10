package com.scrub.app.data

import com.scrub.app.face.FaceEmbedder
import com.scrub.app.face.FaceQuality
import com.scrub.app.face.FaceRecord
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/**
 * Remembers the face fingerprints found in each photo, so a second scan (for example after
 * adding another reference photo) only has to look at new or edited pictures.
 * Each face is stored with its quality numbers, so the matching filters can change without a
 * rescan. Stored privately in the app's own storage.
 */
class EmbeddingCache(private val file: File) {

    private class Entry(val dateModified: Long, val faces: List<FaceRecord>)

    private val map = HashMap<Long, Entry>()
    private var loaded = false
    private var dirty = false

    @Synchronized
    fun load() {
        if (loaded) return
        loaded = true
        if (!file.exists()) return
        try {
            DataInputStream(BufferedInputStream(file.inputStream())).use { input ->
                if (input.readInt() != VERSION) return
                val count = input.readInt()
                repeat(count) {
                    val id = input.readLong()
                    val modified = input.readLong()
                    val faceCount = input.readInt()
                    val faces = List(faceCount) {
                        val embedding = FloatArray(FaceEmbedder.EMBEDDING_SIZE) { input.readFloat() }
                        val quality = FaceQuality(
                            sizePx = input.readInt(),
                            yaw = input.readFloat(),
                            roll = input.readFloat(),
                            sharpness = input.readFloat(),
                        )
                        FaceRecord(embedding, quality)
                    }
                    map[id] = Entry(modified, faces)
                }
            }
        } catch (t: Throwable) {
            map.clear() // corrupt cache: just rescan
        }
    }

    @Synchronized
    fun get(id: Long, dateModified: Long): List<FaceRecord>? =
        map[id]?.takeIf { it.dateModified == dateModified }?.faces

    @Synchronized
    fun put(id: Long, dateModified: Long, faces: List<FaceRecord>) {
        map[id] = Entry(dateModified, faces)
        dirty = true
    }

    @Synchronized
    fun remove(ids: Collection<Long>) {
        ids.forEach { map.remove(it) }
        dirty = true
    }

    @Synchronized
    fun save() {
        if (!dirty) return
        val tmp = File(file.parentFile, file.name + ".tmp")
        DataOutputStream(BufferedOutputStream(tmp.outputStream())).use { out ->
            out.writeInt(VERSION)
            out.writeInt(map.size)
            for ((id, e) in map) {
                out.writeLong(id)
                out.writeLong(e.dateModified)
                out.writeInt(e.faces.size)
                for (f in e.faces) {
                    for (v in f.embedding) out.writeFloat(v)
                    out.writeInt(f.quality.sizePx)
                    out.writeFloat(f.quality.yaw)
                    out.writeFloat(f.quality.roll)
                    out.writeFloat(f.quality.sharpness)
                }
            }
        }
        tmp.renameTo(file)
        dirty = false
    }

    @Synchronized
    fun clear() {
        map.clear()
        file.delete()
        dirty = false
    }

    private companion object {
        /**
         * 1: embedding only. 2: straightened + flip-averaged embeddings, plus quality numbers.
         * A cache with another version is ignored and rebuilt by the next scan.
         */
        const val VERSION = 2
    }
}
