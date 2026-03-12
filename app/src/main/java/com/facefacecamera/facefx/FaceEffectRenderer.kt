package com.facefacecamera.facefx

import android.graphics.Bitmap

data class FaceEffectControls(
    val squareGridSize: Int = 5,
)

data class PreviewEffectHint(
    val kind: FaceEffectKind,
    val topScale: Float,
    val widthScale: Float,
    val jawScale: Float,
    val crownLift: Float,
    val accentColorHex: Long,
    val squareGridSize: Int,
)

interface FaceEffectRenderer {
    fun renderPreview(
        filter: FaceFilterPreset,
        face: FaceTrackerResult?,
        controls: FaceEffectControls,
    ): PreviewEffectHint

    suspend fun renderStill(
        source: Bitmap,
        filter: FaceFilterPreset,
        face: FaceTrackerResult?,
        controls: FaceEffectControls,
    ): Bitmap
}
