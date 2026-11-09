package com.scrub.app.data

import android.content.ContentResolver
import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.MediaStore
import kotlin.math.max
import kotlin.math.roundToInt

data class GalleryImage(
    val id: Long,
    val uri: Uri,
    val dateModified: Long,
    val displayName: String,
    val relativePath: String,
    val mimeType: String,
    val dateTaken: Long,
)

object Gallery {

    /** Every image on the phone that MediaStore knows about, newest first. */
    fun queryAll(resolver: ContentResolver): List<GalleryImage> {
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_TAKEN,
        )
        val out = ArrayList<GalleryImage>()
        resolver.query(
            collection, projection, null, null,
            "${MediaStore.Images.Media.DATE_TAKEN} DESC"
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val modCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val pathCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val takenCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                out += GalleryImage(
                    id = id,
                    uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id),
                    dateModified = c.getLong(modCol),
                    displayName = c.getString(nameCol) ?: "photo_$id.jpg",
                    relativePath = c.getString(pathCol) ?: "Pictures/",
                    mimeType = c.getString(mimeCol) ?: "image/jpeg",
                    dateTaken = c.getLong(takenCol),
                )
            }
        }
        return out
    }

    /** Decode an image, already rotated upright, shrunk so its longest side is at most [maxSide]. */
    fun decode(resolver: ContentResolver, uri: Uri, maxSide: Int): Bitmap? = try {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
            val w = info.size.width
            val h = info.size.height
            val longest = max(w, h)
            if (longest > maxSide) {
                val scale = maxSide.toFloat() / longest
                decoder.setTargetSize(
                    max(1, (w * scale).roundToInt()),
                    max(1, (h * scale).roundToInt())
                )
            }
        }.let { bmp ->
            if (bmp.config == Bitmap.Config.ARGB_8888) bmp
            else bmp.copy(Bitmap.Config.ARGB_8888, false).also { bmp.recycle() }
        }
    } catch (t: Throwable) {
        null
    }
}
