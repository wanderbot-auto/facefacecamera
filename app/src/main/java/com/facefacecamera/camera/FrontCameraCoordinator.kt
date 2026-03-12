package com.facefacecamera.camera

import android.content.Context
import android.util.Size
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.facefacecamera.facefx.FaceTracker
import com.facefacecamera.facefx.FaceTrackerResult
import java.io.Closeable
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class FrontCameraCoordinator(
    private val faceTracker: FaceTracker,
) : Closeable {
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var analysisExecutor: ExecutorService? = null

    suspend fun bind(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onFaceTracked: (FaceTrackerResult?) -> Unit,
    ) {
        val provider = context.cameraProvider()
        val preview = Preview.Builder().build().apply {
            setSurfaceProvider(previewView.surfaceProvider)
        }
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetRotation(previewView.display?.rotation ?: Surface.ROTATION_0)
            .build()
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .setTargetResolution(Size(480, 640))
            .build()

        imageAnalysis?.clearAnalyzer()
        analysisExecutor?.shutdownNow()
        analysisExecutor = Executors.newSingleThreadExecutor()
        analysis.setAnalyzer(analysisExecutor!!) { imageProxy ->
            faceTracker.process(imageProxy, onFaceTracked)
        }

        provider.unbindAll()
        provider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_FRONT_CAMERA,
            preview,
            capture,
            analysis,
        )

        cameraProvider = provider
        imageCapture = capture
        imageAnalysis = analysis
    }

    fun unbind() {
        imageAnalysis?.clearAnalyzer()
        cameraProvider?.unbindAll()
    }

    suspend fun captureToTempFile(context: Context): File = suspendCancellableCoroutine { continuation ->
        val capture = imageCapture
        if (capture == null) {
            continuation.resumeWithException(IllegalStateException("ImageCapture is not ready"))
            return@suspendCancellableCoroutine
        }

        val tempFile = File(
            context.cacheDir,
            "ffc_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg",
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()
        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exception: ImageCaptureException) {
                    continuation.resumeWithException(exception)
                }

                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    continuation.resume(tempFile)
                }
            },
        )
    }

    override fun close() {
        unbind()
        analysisExecutor?.shutdownNow()
        faceTracker.close()
    }
}

private suspend fun Context.cameraProvider(): ProcessCameraProvider =
    suspendCancellableCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener(
            {
                try {
                    continuation.resume(future.get())
                } catch (error: Throwable) {
                    continuation.resumeWithException(error)
                }
            },
            ContextCompat.getMainExecutor(this),
        )
    }
