package com.facefacecamera

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.facefacecamera.feature.capture.CaptureRoute
import com.facefacecamera.ui.theme.FaceFaceCameraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            FaceFaceCameraTheme {
                CaptureRoute()
            }
        }
    }
}

