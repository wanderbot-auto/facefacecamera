package com.facefacecamera.facefx

import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import java.util.concurrent.atomic.AtomicBoolean

class MlKitFaceTracker : FaceTracker {
    private val isProcessing = AtomicBoolean(false)
    private var isClosed = false
    private var lastProcessedAtMs = 0L

    private val detector: FaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setMinFaceSize(0.15f)
            .enableTracking()
            .build(),
    )

    override fun process(imageProxy: ImageProxy, onResult: (FaceTrackerResult?) -> Unit) {
        if (isClosed) {
            imageProxy.close()
            return
        }

        val now = System.currentTimeMillis()
        if ((now - lastProcessedAtMs) < MIN_ANALYSIS_INTERVAL_MS || !isProcessing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            isProcessing.set(false)
            imageProxy.close()
            onResult(null)
            return
        }

        lastProcessedAtMs = now
        val rotation = imageProxy.imageInfo.rotationDegrees
        val width = if (rotation % 180 == 0) imageProxy.width else imageProxy.height
        val height = if (rotation % 180 == 0) imageProxy.height else imageProxy.width
        val image = InputImage.fromMediaImage(mediaImage, rotation)
        detector.process(image)
            .addOnSuccessListener { faces ->
                val tracked = faces
                    .maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                    ?.toTrackerResult(width = width, height = height)
                onResult(tracked)
            }
            .addOnFailureListener {
                onResult(null)
            }
            .addOnCompleteListener {
                isProcessing.set(false)
                imageProxy.close()
            }
    }

    override fun close() {
        isClosed = true
        detector.close()
    }

    private companion object {
        const val MIN_ANALYSIS_INTERVAL_MS = 120L
    }
}

private fun Face.toTrackerResult(width: Int, height: Int): FaceTrackerResult {
    val normalizedBounds = boundingBox.normalized(width = width, height = height)
    return FaceTrackerResult(
        bounds = normalizedBounds,
        leftEye = getLandmark(FaceLandmark.LEFT_EYE)?.position?.normalized(width, height),
        rightEye = getLandmark(FaceLandmark.RIGHT_EYE)?.position?.normalized(width, height),
        noseBase = getLandmark(FaceLandmark.NOSE_BASE)?.position?.normalized(width, height),
        mouthBase = getLandmark(FaceLandmark.MOUTH_BOTTOM)?.position?.normalized(width, height),
        rotationY = headEulerAngleY,
        rotationZ = headEulerAngleZ,
        trackingConfidence = trackingId?.toFloat()?.let { 1f } ?: 0.85f,
    )
}

private fun Rect.normalized(width: Int, height: Int): RectF = RectF(
    (left / width.toFloat()).coerceIn(0f, 1f),
    (top / height.toFloat()).coerceIn(0f, 1f),
    (right / width.toFloat()).coerceIn(0f, 1f),
    (bottom / height.toFloat()).coerceIn(0f, 1f),
)

private fun PointF.normalized(width: Int, height: Int): PointF = PointF(
    (x / width.toFloat()).coerceIn(0f, 1f),
    (y / height.toFloat()).coerceIn(0f, 1f),
)
