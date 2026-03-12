package com.facefacecamera.facefx

import androidx.camera.core.ImageProxy
import java.io.Closeable

interface FaceTracker : Closeable {
    fun process(imageProxy: ImageProxy, onResult: (FaceTrackerResult?) -> Unit)
}

