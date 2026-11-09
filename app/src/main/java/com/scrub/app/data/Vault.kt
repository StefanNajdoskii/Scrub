package com.scrub.app.data

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class VaultItem(
    val fileName: String,
    val displayName: String,
    val mimeType: String,
    val relativePath: String,
    val dateTaken: Long,
)

/**
 * The Vault: photos moved here are copied into Scrub's private storage and removed from the
 * gallery. Other apps can't see them. They can be restored back to the gallery at any time.
 * Note: uninstalling Scrub deletes the vault.
 */
class Vault(context: Context) {

    private val dir = File(context.filesDir, "vault").apply { mkdirs() }
    private val indexFile = File(dir, "index.json")

    fun fileFor(item: VaultItem) = File(dir, item.fileName)

    @Synchronized
    fun items(): List<VaultItem> {
        if (!indexFile.exists()) return emptyList()
        return try {
            val arr = JSONArray(indexFile.readText())
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                VaultItem(
                    fileName = o.getString("file"),
                    displayName = o.getString("name"),
                    mimeType = o.getString("mime"),
                    relativePath = o.getString("path"),
                    dateTaken = o.getLong("taken"),
                )
            }.filter { fileFor(it).exists() }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    @Synchronized
    private fun writeIndex(items: List<VaultItem>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(
                JSONObject()
                    .put("file", it.fileName)
                    .put("name", it.displayName)
                    .put("mime", it.mimeType)
                    .put("path", it.relativePath)
                    .put("taken", it.dateTaken)
            )
        }
        val tmp = File(dir, "index.json.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(indexFile)
    }

    /** Copy a gallery photo into the vault. The original is removed separately (needs user OK). */
    @Synchronized
    fun copyIn(resolver: ContentResolver, image: GalleryImage): VaultItem? {
        val ext = image.displayName.substringAfterLast('.', "jpg")
        val name = "${System.currentTimeMillis()}_${image.id}.$ext"
        val target = File(dir, name)
        return try {
            resolver.openInputStream(image.uri)?.use { input ->
                target.outputStream().use { input.copyTo(it) }
            } ?: return null
            val item = VaultItem(name, image.displayName, image.mimeType, image.relativePath, image.dateTaken)
            writeIndex(items() + item)
            item
        } catch (t: Throwable) {
            target.delete()
            null
        }
    }

    /** Permanently delete vault items. */
    @Synchronized
    fun delete(toDelete: Collection<VaultItem>) {
        val names = toDelete.map { it.fileName }.toSet()
        toDelete.forEach { fileFor(it).delete() }
        writeIndex(items().filterNot { it.fileName in names })
    }

    /** Put a vault item back into the phone's gallery, in its original folder if possible. */
    @Synchronized
    fun restore(resolver: ContentResolver, item: VaultItem): Boolean {
        val src = fileFor(item)
        if (!src.exists()) return false
        val path = item.relativePath.takeIf {
            it.startsWith("DCIM/") || it.startsWith("Pictures/")
        } ?: "Pictures/Restored/"

        for (attemptPath in listOf(path, "Pictures/Restored/").distinct()) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, item.displayName)
                put(MediaStore.Images.Media.MIME_TYPE, item.mimeType)
                put(MediaStore.Images.Media.RELATIVE_PATH, attemptPath)
                if (item.dateTaken > 0) put(MediaStore.Images.Media.DATE_TAKEN, item.dateTaken)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = try {
                resolver.insert(
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values
                )
            } catch (t: Throwable) {
                null
            } ?: continue
            try {
                resolver.openOutputStream(uri)?.use { out -> src.inputStream().use { it.copyTo(out) } }
                    ?: throw IllegalStateException("no stream")
                resolver.update(uri, ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }, null, null)
                src.delete()
                writeIndex(items().filterNot { it.fileName == item.fileName })
                return true
            } catch (t: Throwable) {
                resolver.delete(uri, null, null)
            }
        }
        return false
    }
}
