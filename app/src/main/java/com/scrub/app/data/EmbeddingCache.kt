package com.scrub.app.data

import com.scrub.app.face.FaceEmbedder
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/**
 * Remembers the face fingerprints found in each photo, so a second scan (for example after
 * adding another reference photo) only has to look at new or edited pictures.
 * Stored privately in the app's own storage.
 */
class EmbeddingCache(private val file: File) {

    private class Entry(val dateModified: Long, val faces: List<FloatArray>)

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
                        FloatArray(FaceEmbedder.EMBEDDING_SIZE) { input.readFloat() }
                    }
                    map[id] = Entry(modified, faces)
                }
            }
        } catch (t: Throwable) {
            map.clear() // corrupt cache: just rescan
        }
    }

    @Synchronized
    fun get(id: Long, dateModified: Long): List<FloatArray>? =
        map[id]?.takeIf { it.dateModified == dateModified }?.faces

    @Synchronized
    fun put(id: Long, dateModified: Long, faces: List<FloatArray>) {
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
                for (f in e.faces) for (v in f) out.writeFloat(v)
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
        const val VERSION = 1
    }
}
