package com.facefacecamera

import android.net.Uri
import com.facefacecamera.feature.capture.CameraPermissionState
import com.facefacecamera.feature.capture.CaptureViewModel
import com.facefacecamera.feature.capture.SaveState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CaptureViewModelTest {
    @Test
    fun captureLifecycleUpdatesUiState() {
        val viewModel = CaptureViewModel()

        viewModel.setPermission(true)
        viewModel.onCaptureStarted()
        viewModel.onCaptureSaved(Uri.parse("content://photos/1"), "已保存到相册")

        val state = viewModel.uiState.value
        assertEquals(CameraPermissionState.Granted, state.permissionState)
        assertFalse(state.isCapturing)
        assertEquals(SaveState.Success, state.saveState)
        assertTrue(state.lastSavedUri.toString().contains("content://photos/1"))
    }
}
