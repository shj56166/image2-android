package com.shj56166androidimage2.app.data.repo

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import androidx.core.content.FileProvider
import com.shj56166androidimage2.app.R
import com.shj56166androidimage2.app.data.db.ImageAssetDao
import com.shj56166androidimage2.app.data.model.ImageSource
import com.shj56166androidimage2.app.data.model.StoredImageAsset
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val MASK_WORKING_MAX_EDGE = 1920
private const val MASK_WORKING_DIMENSION_MULTIPLE = 16

data class MaskWorkingSize(
    val width: Int,
    val height: Int,
    val scale: Float,
    val wasResized: Boolean,
)

data class PreparedMaskTarget(
    val asset: StoredImageAsset,
    val originalWidth: Int,
    val originalHeight: Int,
    val wasConvertedToPng: Boolean,
    val wasResized: Boolean,
)

data class ShareableImageAsset(
    val uri: Uri,
    val mimeType: String,
)

class ImageStorageRepository(
    private val context: Context,
    private val dao: ImageAssetDao,
) {
    private val imageDir = File(context.filesDir, "images").apply { mkdirs() }
    private val thumbDir = File(context.filesDir, "thumbnails").apply { mkdirs() }

    fun observeAll(): Flow<List<StoredImageAsset>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    suspend fun getById(id: String): StoredImageAsset? = dao.getById(id)?.toModel()

    suspend fun getAll(): List<StoredImageAsset> = withContext(Dispatchers.IO) {
        dao.getAll().map { it.toModel() }
    }

    suspend fun getDataUrl(id: String): String? = withContext(Dispatchers.IO) {
        val asset = dao.getById(id) ?: return@withContext null
        val file = File(asset.filePath)
        if (!file.exists()) return@withContext null
        val bytes = file.readBytes()
        "data:${asset.mimeType};base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
    }

    suspend fun importUri(uri: Uri, source: ImageSource): StoredImageAsset = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error(context.getString(R.string.image_uri_read_failed))
        val mime = context.contentResolver.getType(uri) ?: "image/png"
        storeBytes(bytes = bytes, mimeType = mime, source = source)
    }

    suspend fun storeBytes(bytes: ByteArray, mimeType: String, source: ImageSource): StoredImageAsset =
        withContext(Dispatchers.IO) {
            val sha = sha256(bytes)
            dao.getById(sha)?.toModel()?.let { return@withContext it }
            val extension = when {
                mimeType.contains("jpeg") -> "jpg"
                mimeType.contains("webp") -> "webp"
                else -> "png"
            }
            val file = File(imageDir, "$sha.$extension")
            file.writeBytes(bytes)
            val thumb = createThumbnail(bytes, extension, sha)
            val size = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, size)
            val asset = StoredImageAsset(
                id = sha,
                filePath = file.absolutePath,
                thumbnailPath = thumb?.absolutePath,
                mimeType = mimeType,
                source = source,
                width = size.outWidth.takeIf { it > 0 },
                height = size.outHeight.takeIf { it > 0 },
                createdAt = System.currentTimeMillis(),
                sha256 = sha,
            )
            dao.upsert(asset.toEntity())
            asset
        }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        imageDir.listFiles()?.forEach { it.delete() }
        thumbDir.listFiles()?.forEach { it.delete() }
        dao.deleteAll()
    }

    suspend fun storeBase64DataUrl(dataUrl: String, source: ImageSource): StoredImageAsset {
        val (mimeType, bytes) = decodeDataUrl(dataUrl)
        return storeBytes(bytes, mimeType, source)
    }

    suspend fun prepareMaskWorkingImage(id: String): PreparedMaskTarget = withContext(Dispatchers.IO) {
        val original = dao.getById(id)?.toModel() ?: error(context.getString(R.string.image_not_found_for_mask))
        val file = File(original.filePath)
        if (!file.exists()) error(context.getString(R.string.image_file_missing_for_mask))
        val bytes = file.readBytes()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val originalWidth = bounds.outWidth.takeIf { it > 0 } ?: original.width ?: error(context.getString(R.string.image_width_unreadable))
        val originalHeight = bounds.outHeight.takeIf { it > 0 } ?: original.height ?: error(context.getString(R.string.image_height_unreadable))
        val workingSize = calculateMaskWorkingSize(originalWidth, originalHeight)
        val isPng = original.mimeType.equals("image/png", ignoreCase = true) || file.extension.equals("png", ignoreCase = true)

        if (!workingSize.wasResized && isPng) {
            return@withContext PreparedMaskTarget(
                asset = original,
                originalWidth = originalWidth,
                originalHeight = originalHeight,
                wasConvertedToPng = false,
                wasResized = false,
            )
        }

        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: error(context.getString(R.string.image_content_unreadable))
        val workingBitmap =
            if (bitmap.width == workingSize.width && bitmap.height == workingSize.height) {
                bitmap
            } else {
                Bitmap.createScaledBitmap(bitmap, workingSize.width, workingSize.height, true).also {
                    if (it !== bitmap) bitmap.recycle()
                }
            }
        val output = ByteArrayOutputStream()
        workingBitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        if (workingBitmap !== bitmap) workingBitmap.recycle()
        val asset = storeBytes(output.toByteArray(), "image/png", ImageSource.UPLOAD)
        PreparedMaskTarget(
            asset = asset,
            originalWidth = originalWidth,
            originalHeight = originalHeight,
            wasConvertedToPng = !isPng,
            wasResized = workingSize.wasResized,
        )
    }

    suspend fun saveToPictures(id: String): Uri = withContext(Dispatchers.IO) {
        val asset = dao.getById(id)?.toModel() ?: error(context.getString(R.string.image_not_found_for_save))
        val file = File(asset.filePath)
        if (!file.exists()) error(context.getString(R.string.image_file_missing_for_save))
        val extension = when {
            asset.mimeType.contains("jpeg", ignoreCase = true) -> "jpg"
            asset.mimeType.contains("webp", ignoreCase = true) -> "webp"
            else -> "png"
        }
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "image-playground-${System.currentTimeMillis()}.$extension")
            put(MediaStore.Images.Media.MIME_TYPE, asset.mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Image Playground")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error(context.getString(R.string.picture_file_create_failed))
        runCatching {
            resolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            } ?: error(context.getString(R.string.picture_file_write_failed))
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        }.getOrElse { throwable ->
            resolver.delete(uri, null, null)
            throw throwable
        }
    }

    suspend fun getShareableImage(id: String): ShareableImageAsset = withContext(Dispatchers.IO) {
        val asset = dao.getById(id)?.toModel() ?: error(context.getString(R.string.image_not_found_for_share))
        val file = File(asset.filePath)
        if (!file.exists()) error(context.getString(R.string.image_file_missing_for_share))
        ShareableImageAsset(
            uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file),
            mimeType = asset.mimeType,
        )
    }

    private fun createThumbnail(bytes: ByteArray, extension: String, sha: String): File? {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val scaled = Bitmap.createScaledBitmap(bitmap, 512, ((bitmap.height / bitmap.width.toFloat()) * 512f).toInt().coerceAtLeast(1), true)
        val file = File(thumbDir, "$sha.webp")
        FileOutputStream(file).use { out ->
            scaled.compress(Bitmap.CompressFormat.WEBP_LOSSY, 85, out)
        }
        return file
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun decodeDataUrl(dataUrl: String): Pair<String, ByteArray> {
        val header = dataUrl.substringBefore(",")
        val payload = dataUrl.substringAfter(",")
        val mime = header.substringAfter("data:").substringBefore(";")
        return mime to android.util.Base64.decode(payload, android.util.Base64.DEFAULT)
    }
}

internal fun calculateMaskWorkingSize(
    width: Int,
    height: Int,
    maxEdge: Int = MASK_WORKING_MAX_EDGE,
    multiple: Int = MASK_WORKING_DIMENSION_MULTIPLE,
): MaskWorkingSize {
    require(width > 0 && height > 0) { "Image size must be positive." }
    val longestEdge = maxOf(width, height)
    if (longestEdge <= maxEdge) {
        return MaskWorkingSize(width = width, height = height, scale = 1f, wasResized = false)
    }
    val scale = maxEdge.toFloat() / longestEdge.toFloat()
    return MaskWorkingSize(
        width = floorToMultiple(width * scale, multiple),
        height = floorToMultiple(height * scale, multiple),
        scale = scale,
        wasResized = true,
    )
}

private fun floorToMultiple(value: Float, multiple: Int): Int =
    maxOf(multiple, (value.toInt() / multiple) * multiple)
