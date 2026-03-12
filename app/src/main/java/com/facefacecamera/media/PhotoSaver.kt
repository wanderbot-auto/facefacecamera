package com.facefacecamera.media

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.facefacecamera.facefx.FaceFilterPreset
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PhotoSaver(
    private val context: Context,
) {
    suspend fun save(bitmap: Bitmap, preset: FaceFilterPreset): Uri? = withContext(Dispatchers.IO) {
        val fileName = buildString {
            append("FFC_")
            append(SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()))
            append("_")
            append(preset.id)
            append(".jpg")
        }

        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/FaceFaceCamera")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext null
        try {
            resolver.openOutputStream(uri)?.use { stream ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)) {
                    throw IOException("Failed to compress bitmap")
                }
            } ?: throw IOException("Unable to open output stream")

            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            null
        }
    }

    fun shareIntent(uri: Uri): Intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    companion object {
        fun from(context: Context): PhotoSaver = PhotoSaver(context.applicationContext)
    }
}
