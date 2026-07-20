package cc.ptoe.messenger.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import cc.ptoe.messenger.domain.model.MessageImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Reads user-picked images into the chat app and writes them to private
 * storage so the rest of the pipeline (Coil, the API, cloud sync) can
 * reference a stable path/URI.
 *
 * Two outputs are produced for every picked image:
 *
 *  - a base64 data: URI used to ship the bitmap to OpenAI-compatible
 *    chat APIs as a vision input
 *  - a cached PNG copy inside `filesDir/chat_images/` that Coil can
 *    decode without re-reading the source
 *
 * Cached copies are reused on subsequent app launches because the
 * [MessageImage.localPath] is persisted in the message row.
 */
class ChatImageStore(private val context: Context) {

    private val cacheDir: File
        get() = File(context.filesDir, CACHE_SUBDIR).apply { mkdirs() }

    /**
     * Reads [source] (a content:// or file:// URI returned by the
     * photo picker / camera), downscales it, and persists a cached
     * copy. The returned [MessageImage] is ready to attach to a
     * [cc.ptoe.messenger.domain.model.Message].
     */
    suspend fun importImage(source: Uri): MessageImage = withContext(Dispatchers.IO) {
        val bounds = readBounds(source)
        val sampleSize = computeInSampleSize(bounds.first, bounds.second, MAX_DIMENSION_PX, MAX_DIMENSION_PX)
        val rawBitmap = decodeBitmap(source, sampleSize) ?: error("Unable to decode picked image")
        val oriented = applyExifRotation(source, rawBitmap)

        val targetFile = File(cacheDir, "${UUID.randomUUID()}.png")
        FileOutputStream(targetFile).use { out ->
            oriented.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        if (oriented !== rawBitmap) rawBitmap.recycle()
        oriented.recycle()

        val dataUri = withContext(Dispatchers.IO) {
            val bytes = targetFile.readBytes()
            val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            "data:image/png;base64,$b64"
        }

        MessageImage(
            dataUri = dataUri,
            localPath = targetFile.absolutePath
        )
    }

    /**
     * Allocates a destination file in the cache dir and returns a
     * `content://` URI for it. Used by the camera capture flow where
     * the system camera writes the captured photo to the URI we
     * supply.
     */
    suspend fun newCameraOutput(): CameraTarget = withContext(Dispatchers.IO) {
        val file = File(cacheDir, "capture_${UUID.randomUUID()}.jpg")
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        CameraTarget(file = file, uri = uri)
    }

    /**
     * Reads a previously-allocated camera output back as a
     * [MessageImage]. The caller is responsible for confirming the
     * file exists and is non-empty (camera may have been cancelled).
     */
    suspend fun importCameraTarget(target: CameraTarget): MessageImage? = withContext(Dispatchers.IO) {
        if (!target.file.exists() || target.file.length() == 0L) return@withContext null
        importImage(Uri.fromFile(target.file))
    }

    data class CameraTarget(val file: File, val uri: Uri)

    private fun readBounds(uri: Uri): Pair<Int, Int> {
        val resolver = context.contentResolver
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
        return opts.outWidth to opts.outHeight
    }

    private fun decodeBitmap(uri: Uri, sampleSize: Int): Bitmap? {
        val resolver = context.contentResolver
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
    }

    private fun applyExifRotation(uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun computeInSampleSize(
        srcWidth: Int,
        srcHeight: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        if (srcWidth <= 0 || srcHeight <= 0) return 1
        var sample = 1
        var halfW = srcWidth / 2
        var halfH = srcHeight / 2
        while (halfW / sample >= reqWidth && halfH / sample >= reqHeight) {
            sample *= 2
        }
        return sample
    }

    /**
     * Drops cached bitmaps for message ids that no longer appear in
     * the conversation. Called by
     * [cc.ptoe.messenger.data.repository.MessageRepositoryImpl] after
     * a delete; the chat screen doesn't need to invoke it directly.
     */
    fun deleteIfExists(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).takeIf { it.exists() }?.delete() }
    }

    companion object {
        private const val CACHE_SUBDIR = "chat_images"
        /**
         * Cap sent bitmaps at 1568px on the longest side. This matches
         * the OpenAI "high" detail suggestion and keeps the base64
         * payload under ~3 MB for typical JPEGs, well inside the
         * request body limits of every common OpenAI-compatible
         * provider.
         */
        private const val MAX_DIMENSION_PX = 1568
    }
}
