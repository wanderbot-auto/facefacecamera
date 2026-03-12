package com.facefacecamera.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File

fun decodeBitmap(file: File, maxDimension: Int = 2160): Bitmap {
    val bounds = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeFile(file.absolutePath, bounds)

    val decodeOptions = BitmapFactory.Options().apply {
        inSampleSize = calculateInSampleSize(
            width = bounds.outWidth,
            height = bounds.outHeight,
            maxDimension = maxDimension,
        )
    }

    val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
        ?: error("Unable to decode image file")
    val exif = ExifInterface(file.absolutePath)
    val rotation = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    if (rotation == 0f) return bitmap
    val matrix = Matrix().apply { postRotate(rotation) }
    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (rotated !== bitmap) {
        bitmap.recycle()
    }
    return rotated
}

private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
    var sampleSize = 1
    var longestEdge = maxOf(width, height)
    while (longestEdge > maxDimension) {
        sampleSize *= 2
        longestEdge /= 2
    }
    return sampleSize
}
