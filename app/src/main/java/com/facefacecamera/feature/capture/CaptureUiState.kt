package com.facefacecamera.feature.capture

import android.net.Uri
import com.facefacecamera.facefx.FaceEffectControls
import com.facefacecamera.facefx.FaceFilterPreset
import com.facefacecamera.facefx.FaceTrackerResult

enum class CameraPermissionState {
    Unknown,
    Granted,
    Denied,
}

enum class SaveState {
    Idle,
    Saving,
    Success,
    Error,
}

data class CaptureUiState(
    val permissionState: CameraPermissionState = CameraPermissionState.Unknown,
    val filters: List<FaceFilterPreset> = FaceFilterPreset.defaults(),
    val activeFilterId: String = FaceFilterPreset.defaults().first().id,
    val effectControls: FaceEffectControls = FaceEffectControls(
        squareGridSize = FaceFilterPreset.defaults().first().deformProfile.defaultGridSize,
    ),
    val latestFace: FaceTrackerResult? = null,
    val isCameraReady: Boolean = false,
    val isCapturing: Boolean = false,
    val lastSavedUri: Uri? = null,
    val saveState: SaveState = SaveState.Idle,
    val transientMessage: String? = null,
)
