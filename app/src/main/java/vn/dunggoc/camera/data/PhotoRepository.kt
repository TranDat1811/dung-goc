package vn.dunggoc.camera.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class PhotoRepository(private val context: Context) {
    private val directory = File(context.cacheDir, "shots").apply { mkdirs() }

    fun newCapture(): File = File.createTempFile("shot_", ".jpg", directory)

    fun resolve(path: String?): File? = path?.let(::File)?.takeIf {
        it.isFile && it.canonicalFile.parentFile == directory.canonicalFile
    }

    fun delete(path: String?) { resolve(path)?.delete() }

    fun cleanOldFiles(keep: String?) {
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000
        directory.listFiles()?.filter { it.path != keep && it.lastModified() < cutoff }?.forEach { it.delete() }
    }

    fun preview(file: File): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IOException("Cannot decode captured image")
        val matrix = Matrix()
        when (ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    fun suggestedName(): String = "DungGoc_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}_${UUID.randomUUID().toString().take(6)}.jpg"

    /** API 26–28 uses a system document picker; API 29+ publishes atomically to Pictures. */
    fun save(file: File, destination: Uri? = null): Uri {
        val resolver = context.contentResolver
        if (destination != null) {
            try {
                resolver.openOutputStream(destination, "w")?.use { output -> file.inputStream().use { it.copyTo(output) } }
                    ?: throw IOException("Cannot open selected destination")
                return destination
            } catch (error: Exception) {
                // CreateDocument always creates a new file. Remove an incomplete copy.
                runCatching { android.provider.DocumentsContract.deleteDocument(resolver, destination) }
                throw error
            }
        }
        if (Build.VERSION.SDK_INT < 29) throw IllegalStateException("Use the document picker on Android 8 and 9")
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, suggestedName())
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/DungGoc")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Cannot create gallery entry")
        try {
            resolver.openOutputStream(uri)?.use { output -> file.inputStream().use { it.copyTo(output) } }
                ?: throw IOException("Cannot write image")
            val published = resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            if (published == 0) throw IOException("Cannot publish image")
            return uri
        } catch (error: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
    }
}

