package com.facefacecamera.feature.capture

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.facefacecamera.facefx.FaceFilterPreset
import com.facefacecamera.facefx.FaceTrackerResult
import com.facefacecamera.facefx.isVisuallyEquivalentTo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class CaptureViewModel : ViewModel() {
    private val defaults = FaceFilterPreset.defaults()
    private val _uiState = MutableStateFlow(
        CaptureUiState(
            filters = defaults,
            activeFilterId = defaults.first().id,
        ),
    )
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    fun setPermission(granted: Boolean) {
        _uiState.update {
            it.copy(
                permissionState = if (granted) CameraPermissionState.Granted else CameraPermissionState.Denied,
            )
        }
    }

    fun selectFilter(filterId: String) {
        _uiState.update {
            it.copy(activeFilterId = filterId)
        }
    }

    fun setSquareGridSize(gridSize: Int) {
        _uiState.update { state ->
            state.copy(
                effectControls = state.effectControls.copy(
                    squareGridSize = gridSize.coerceIn(3, 9),
                ),
            )
        }
    }

    fun onFaceTracked(face: FaceTrackerResult?) {
        _uiState.update { state ->
            if (state.latestFace.isVisuallyEquivalentTo(face)) {
                state
            } else {
                state.copy(latestFace = face)
            }
        }
    }

    fun setCameraReady(ready: Boolean) {
        _uiState.update {
            it.copy(isCameraReady = ready)
        }
    }

    fun onCaptureStarted() {
        _uiState.update {
            it.copy(
                isCapturing = true,
                saveState = SaveState.Saving,
                transientMessage = null,
            )
        }
    }

    fun onCaptureSaved(uri: Uri, successMessage: String) {
        _uiState.update {
            it.copy(
                isCapturing = false,
                lastSavedUri = uri,
                saveState = SaveState.Success,
                transientMessage = successMessage,
            )
        }
    }

    fun onCaptureFailed(message: String) {
        _uiState.update {
            it.copy(
                isCapturing = false,
                saveState = SaveState.Error,
                transientMessage = message,
            )
        }
    }

    fun consumeTransientMessage() {
        _uiState.update {
            it.copy(transientMessage = null)
        }
    }
}
