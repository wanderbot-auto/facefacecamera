package com.facefacecamera.feature.capture

import android.Manifest
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.facefacecamera.R
import com.facefacecamera.camera.FrontCameraCoordinator
import com.facefacecamera.facefx.FaceEffectRenderer
import com.facefacecamera.facefx.FaceEffectKind
import com.facefacecamera.facefx.FaceFilterPreset
import com.facefacecamera.facefx.FaceTrackerResult
import com.facefacecamera.facefx.MlKitFaceTracker
import com.facefacecamera.facefx.SimpleFaceEffectRenderer
import com.facefacecamera.media.PhotoSaver
import com.facefacecamera.media.decodeBitmap
import com.facefacecamera.ui.theme.Cream
import com.facefacecamera.ui.theme.GlowBlue
import com.facefacecamera.ui.theme.GlowMint
import com.facefacecamera.ui.theme.GlowRose
import com.facefacecamera.ui.theme.Ink
import com.facefacecamera.ui.theme.InkSoft
import com.facefacecamera.ui.theme.Peach
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CaptureRoute(
    viewModel: CaptureViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val inspectionMode = LocalInspectionMode.current
    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val renderer = remember { SimpleFaceEffectRenderer() }
    val faceTracker = remember { MlKitFaceTracker() }
    val coordinator = remember { FrontCameraCoordinator(faceTracker) }
    val photoSaver = remember(context.applicationContext) { PhotoSaver.from(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    val flashAlpha = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val tone = remember { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> viewModel.setPermission(granted) },
    )
    val activeFilter = remember(uiState.activeFilterId, uiState.filters) {
        uiState.filters.firstOrNull { it.id == uiState.activeFilterId } ?: uiState.filters.first()
    }

    LaunchedEffect(Unit) {
        viewModel.setPermission(context.hasCameraPermission())
    }

    LaunchedEffect(uiState.permissionState, inspectionMode) {
        if (inspectionMode) return@LaunchedEffect
        if (uiState.permissionState == CameraPermissionState.Granted) {
            try {
                coordinator.bind(
                    context = context,
                    lifecycleOwner = lifecycleOwner,
                    previewView = previewView,
                    onFaceTracked = viewModel::onFaceTracked,
                )
                viewModel.setCameraReady(true)
            } catch (_: Throwable) {
                viewModel.setCameraReady(false)
                viewModel.onCaptureFailed(context.getString(R.string.camera_unavailable))
            }
        } else {
            coordinator.unbind()
            viewModel.setCameraReady(false)
        }
    }

    LaunchedEffect(uiState.transientMessage) {
        uiState.transientMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeTransientMessage()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            tone.release()
            coordinator.close()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CaptureScreen(
            uiState = uiState,
            activeFilter = activeFilter,
            flashAlpha = flashAlpha.value,
            renderer = renderer,
            effectControls = uiState.effectControls,
            preview = {
                if (inspectionMode) {
                    PreviewPlaceholder()
                } else {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { previewView },
                    )
                }
            },
            onGrantPermission = {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            },
            onSelectFilter = viewModel::selectFilter,
            onSquareGridChange = viewModel::setSquareGridSize,
            onCapture = {
                if (uiState.isCapturing || uiState.permissionState != CameraPermissionState.Granted) return@CaptureScreen
                scope.launch {
                    viewModel.onCaptureStarted()
                    tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 120)
                    launch {
                        flashAlpha.animateTo(0.92f, animationSpec = tween(70))
                        flashAlpha.animateTo(0f, animationSpec = tween(220, easing = FastOutSlowInEasing))
                    }
                    try {
                        val tempFile = coordinator.captureToTempFile(context)
                        val bitmap = decodeBitmap(tempFile)
                        val rendered = renderer.renderStill(
                            bitmap,
                            activeFilter,
                            uiState.latestFace,
                            uiState.effectControls,
                        )
                        val savedUri = try {
                            photoSaver.save(rendered, activeFilter)
                        } finally {
                            if (rendered !== bitmap) {
                                rendered.recycle()
                            }
                            bitmap.recycle()
                            tempFile.delete()
                        }
                        if (savedUri != null) {
                            viewModel.onCaptureSaved(savedUri, context.getString(R.string.save_success))
                        } else {
                            viewModel.onCaptureFailed(context.getString(R.string.save_failed))
                        }
                    } catch (error: Throwable) {
                        error.printStackTrace()
                        viewModel.onCaptureFailed(error.message ?: context.getString(R.string.save_failed))
                    }
                }
            },
            onShare = {
                uiState.lastSavedUri?.let { uri ->
                    val chooser = android.content.Intent.createChooser(
                        photoSaver.shareIntent(uri),
                        context.getString(R.string.share_photo),
                    )
                    context.startActivity(chooser)
                }
            },
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 12.dp),
        )
    }
}

@Composable
private fun CaptureScreen(
    uiState: CaptureUiState,
    activeFilter: FaceFilterPreset,
    flashAlpha: Float,
    renderer: FaceEffectRenderer,
    effectControls: com.facefacecamera.facefx.FaceEffectControls,
    preview: @Composable () -> Unit,
    onGrantPermission: () -> Unit,
    onSelectFilter: (String) -> Unit,
    onSquareGridChange: (Int) -> Unit,
    onCapture: () -> Unit,
    onShare: () -> Unit,
) {
    val previewHint = renderer.renderPreview(activeFilter, uiState.latestFace, effectControls)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink),
    ) {
        BackgroundOrbs()
        if (uiState.permissionState == CameraPermissionState.Granted) {
            preview()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xAA090C13), Color.Transparent, Color(0xE6131A26)),
                        ),
                    ),
            )
            FacePreviewOverlay(
                face = uiState.latestFace,
                previewHint = previewHint,
            )
        } else {
            PermissionGate(onGrantPermission = onGrantPermission)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            HeaderBlock()
            BottomControls(
                uiState = uiState,
                activeFilter = activeFilter,
                effectControls = effectControls,
                onSelectFilter = onSelectFilter,
                onSquareGridChange = onSquareGridChange,
                onCapture = onCapture,
                onShare = onShare,
            )
        }

        AnimatedVisibility(
            visible = uiState.permissionState == CameraPermissionState.Granted && uiState.latestFace == null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 118.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color.Black.copy(alpha = 0.36f),
                tonalElevation = 0.dp,
            ) {
                Text(
                    text = stringResource(R.string.no_face_detected),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = Cream,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = flashAlpha)),
        )
    }
}

@Composable
private fun HeaderBlock() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TinyBadge(label = stringResource(R.string.badge_live), glow = GlowMint)
        TinyBadge(label = stringResource(R.string.badge_front), glow = GlowBlue)
    }
}

@Composable
private fun BottomControls(
    uiState: CaptureUiState,
    activeFilter: FaceFilterPreset,
    effectControls: com.facefacecamera.facefx.FaceEffectControls,
    onSelectFilter: (String) -> Unit,
    onSquareGridChange: (Int) -> Unit,
    onCapture: () -> Unit,
    onShare: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        FilterCarousel(
            filters = uiState.filters,
            activeFilterId = activeFilter.id,
            onSelectFilter = onSelectFilter,
        )
        if (activeFilter.deformProfile.kind == FaceEffectKind.SquareGrid) {
            SquareGridControl(
                gridSize = effectControls.squareGridSize,
                onGridSizeChange = onSquareGridChange,
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0x26161D2A),
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = activeFilter.description,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Cream.copy(alpha = 0.72f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = stringResource(R.string.active_filter_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = Cream.copy(alpha = 0.58f),
                            )
                            Text(
                                text = activeFilter.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = colorFromHex(activeFilter.accentColorHex),
                            )
                        }
                    }
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        CaptureButton(
                            accentColor = colorFromHex(activeFilter.accentColorHex),
                            enabled = uiState.permissionState == CameraPermissionState.Granted && uiState.isCameraReady && !uiState.isCapturing,
                            onClick = onCapture,
                        )
                    }
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        ShareButton(
                            enabled = uiState.lastSavedUri != null && !uiState.isCapturing,
                            onClick = onShare,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SquareGridControl(
    gridSize: Int,
    onGridSizeChange: (Int) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0x1F121826),
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.square_grid_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = Cream.copy(alpha = 0.78f),
                )
                Text(
                    text = "${gridSize} x ${gridSize}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Cream,
                )
            }
            Slider(
                value = gridSize.toFloat(),
                onValueChange = { onGridSizeChange(it.toInt()) },
                valueRange = 3f..9f,
                steps = 5,
            )
        }
    }
}

@Composable
private fun FilterCarousel(
    filters: List<FaceFilterPreset>,
    activeFilterId: String,
    onSelectFilter: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.filter_label),
            style = MaterialTheme.typography.labelLarge,
            color = Cream.copy(alpha = 0.7f),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(filters, key = { it.id }) { filter ->
                val selected = filter.id == activeFilterId
                Surface(
                    modifier = Modifier
                        .width(126.dp)
                        .height(108.dp)
                        .clickable { onSelectFilter(filter.id) },
                    shape = RoundedCornerShape(22.dp),
                    color = if (selected) Color(0x331A2436) else Color(0x1F121826),
                    border = androidx.compose.foundation.BorderStroke(
                        width = if (selected) 1.4.dp else 1.dp,
                        color = colorFromHex(filter.accentColorHex).copy(alpha = if (selected) 0.9f else 0.2f),
                    ),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        FilterCardArtwork(
                            kind = filter.deformProfile.kind,
                            accent = colorFromHex(filter.accentColorHex),
                            selected = selected,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            TinyBadge(
                                label = if (selected) {
                                    stringResource(R.string.filter_state_active)
                                } else {
                                    stringResource(R.string.filter_state_inactive)
                                },
                                glow = colorFromHex(filter.accentColorHex),
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = filter.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Cream,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterCardArtwork(
    kind: FaceEffectKind,
    accent: Color,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val alphaBoost = if (selected) 1f else 0.74f
        when (kind) {
            FaceEffectKind.SquareGrid -> {
                val edge = size.minDimension * 0.58f
                val topLeft = Offset(size.width - edge * 0.62f, -edge * 0.16f)
                rotate(
                    degrees = 30f,
                    pivot = Offset(topLeft.x + edge / 2f, topLeft.y + edge / 2f),
                ) {
                    drawRoundRect(
                        color = accent.copy(alpha = 0.18f * alphaBoost),
                        topLeft = topLeft,
                        size = Size(edge, edge),
                        cornerRadius = CornerRadius(edge * 0.1f, edge * 0.1f),
                    )
                    drawRoundRect(
                        color = accent.copy(alpha = 0.44f * alphaBoost),
                        topLeft = topLeft,
                        size = Size(edge, edge),
                        cornerRadius = CornerRadius(edge * 0.1f, edge * 0.1f),
                        style = Stroke(width = 1.3.dp.toPx()),
                    )
                    val cell = edge / 4f
                    repeat(3) { index ->
                        val offset = cell * (index + 1)
                        drawLine(
                            color = accent.copy(alpha = 0.3f * alphaBoost),
                            start = Offset(topLeft.x + offset, topLeft.y),
                            end = Offset(topLeft.x + offset, topLeft.y + edge),
                            strokeWidth = 1.dp.toPx(),
                        )
                        drawLine(
                            color = accent.copy(alpha = 0.3f * alphaBoost),
                            start = Offset(topLeft.x, topLeft.y + offset),
                            end = Offset(topLeft.x + edge, topLeft.y + offset),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                }
            }

            FaceEffectKind.PeakPrism -> {
                val pivot = Offset(size.width * 0.8f, size.height * 0.33f)
                rotate(degrees = 30f, pivot = pivot) {
                    val prism = Path().apply {
                        moveTo(size.width * 0.8f, -size.height * 0.06f)
                        lineTo(size.width * 0.97f, size.height * 0.18f)
                        lineTo(size.width * 0.92f, size.height * 0.52f)
                        lineTo(size.width * 0.8f, size.height * 0.72f)
                        lineTo(size.width * 0.68f, size.height * 0.52f)
                        lineTo(size.width * 0.63f, size.height * 0.18f)
                        close()
                    }
                    drawPath(prism, color = accent.copy(alpha = 0.16f * alphaBoost))
                    drawPath(
                        prism,
                        color = accent.copy(alpha = 0.46f * alphaBoost),
                        style = Stroke(width = 1.4.dp.toPx()),
                    )
                    drawLine(
                        color = accent.copy(alpha = 0.28f * alphaBoost),
                        start = Offset(size.width * 0.8f, -size.height * 0.06f),
                        end = Offset(size.width * 0.8f, size.height * 0.72f),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
            }

            FaceEffectKind.BubbleOrb -> {
                rotate(
                    degrees = 30f,
                    pivot = Offset(size.width * 0.84f, size.height * 0.14f),
                ) {
                    drawCircle(
                        color = accent.copy(alpha = 0.16f * alphaBoost),
                        radius = size.minDimension * 0.28f,
                        center = Offset(size.width * 0.86f, size.height * 0.1f),
                    )
                    drawCircle(
                        color = accent.copy(alpha = 0.44f * alphaBoost),
                        radius = size.minDimension * 0.235f,
                        center = Offset(size.width * 0.86f, size.height * 0.1f),
                        style = Stroke(width = 1.4.dp.toPx()),
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.18f * alphaBoost),
                        radius = size.minDimension * 0.058f,
                        center = Offset(size.width * 0.72f, size.height * 0.07f),
                    )
                    drawCircle(
                        color = accent.copy(alpha = 0.24f * alphaBoost),
                        radius = size.minDimension * 0.082f,
                        center = Offset(size.width * 1.01f, size.height * 0.18f),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
            }

            FaceEffectKind.BladeSlice -> {
                val pivot = Offset(size.width * 0.82f, size.height * 0.35f)
                rotate(degrees = 30f, pivot = pivot) {
                    val blade = Path().apply {
                        moveTo(size.width * 0.82f, -size.height * 0.04f)
                        lineTo(size.width * 0.93f, size.height * 0.28f)
                        lineTo(size.width * 0.82f, size.height * 0.74f)
                        lineTo(size.width * 0.71f, size.height * 0.28f)
                        close()
                    }
                    drawPath(blade, color = accent.copy(alpha = 0.16f * alphaBoost))
                    drawPath(
                        blade,
                        color = accent.copy(alpha = 0.48f * alphaBoost),
                        style = Stroke(width = 1.4.dp.toPx()),
                    )
                    drawLine(
                        color = accent.copy(alpha = 0.3f * alphaBoost),
                        start = Offset(size.width * 0.82f, -size.height * 0.04f),
                        end = Offset(size.width * 0.82f, size.height * 0.74f),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
            }
        }
    }
}

@Composable
private fun CaptureButton(
    accentColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val animatedAccent = animateColorAsState(
        targetValue = if (enabled) accentColor else accentColor.copy(alpha = 0.38f),
        animationSpec = tween(durationMillis = 320),
        label = "captureAccent",
    )
    val ringColor = lerp(animatedAccent.value, Cream, 0.58f)
    val innerTint = lerp(animatedAccent.value, Color.White, 0.72f)
    val innerShadow = lerp(animatedAccent.value, Ink, 0.78f)
    val infiniteTransition = rememberInfiniteTransition(label = "captureWave")
    val wavePhase = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wavePhase",
    )
    val dashCycle = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dashCycle",
    )

    Box(
        modifier = Modifier
            .size(96.dp)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Canvas(
            modifier = Modifier
                .align(Alignment.Center)
                .size(88.dp),
        ) {
            val strokeWidth = 1.dp.toPx()
            val baseRadius = size.minDimension / 2f - strokeWidth * 1.4f
            val amplitude = 2.6.dp.toPx()
            val center = center
            val solidPath = Path()
            val dashedPath = Path()
            val stepCount = 180
            val dashVisibility = dashCycle.value
            val solidAlpha = (1f - (dashVisibility * 1.12f)).coerceIn(0.1f, 1f)
            val dashedAlpha = ((dashVisibility - 0.06f) * 1.22f).coerceIn(0f, 1f)

            repeat(stepCount + 1) { index ->
                val angle = ((index % stepCount).toFloat() / stepCount.toFloat()) * (PI * 2).toFloat()
                val radius = baseRadius + sin(angle * 6f + wavePhase.value) * amplitude
                val x = center.x + cos(angle) * radius
                val y = center.y + sin(angle) * radius
                if (index == 0) {
                    solidPath.moveTo(x, y)
                    dashedPath.moveTo(x, y)
                } else {
                    solidPath.lineTo(x, y)
                    dashedPath.lineTo(x, y)
                }
            }
            solidPath.close()
            dashedPath.close()

            drawPath(
                path = solidPath,
                color = animatedAccent.value.copy(alpha = if (enabled) solidAlpha else solidAlpha * 0.42f),
                style = Stroke(width = strokeWidth),
            )
            drawPath(
                path = dashedPath,
                color = animatedAccent.value.copy(alpha = if (enabled) dashedAlpha else dashedAlpha * 0.42f),
                style = Stroke(
                    width = strokeWidth,
                    pathEffect = PathEffect.dashPathEffect(
                        intervals = floatArrayOf(8.dp.toPx(), 6.dp.toPx()),
                        phase = dashCycle.value * 18.dp.toPx(),
                    ),
                ),
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(60.dp)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.74f),
                            innerTint.copy(alpha = 0.7f),
                            innerShadow.copy(alpha = 0.68f),
                        ),
                    ),
                )
                .border(1.dp, ringColor.copy(alpha = 0.6f), CircleShape),
        )
    }
}

@Composable
private fun ShareButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0x33FFFFFF),
            contentColor = Cream,
            disabledContainerColor = Color(0x1AFFFFFF),
            disabledContentColor = Cream.copy(alpha = 0.35f),
        ),
    ) {
        Text(text = stringResource(R.string.share_button), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun PermissionGate(
    onGrantPermission: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(30.dp),
            color = InkSoft.copy(alpha = 0.9f),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TinyBadge(label = stringResource(R.string.badge_camera), glow = GlowRose)
                Text(
                    text = stringResource(R.string.capture_permission_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = Cream,
                )
                Text(
                    text = stringResource(R.string.capture_permission_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Cream.copy(alpha = 0.76f),
                )
                Button(
                    onClick = onGrantPermission,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Peach,
                        contentColor = Ink,
                    ),
                ) {
                    Text(stringResource(R.string.capture_permission_button))
                }
            }
        }
    }
}

@Composable
private fun FacePreviewOverlay(
    face: FaceTrackerResult?,
    previewHint: com.facefacecamera.facefx.PreviewEffectHint,
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val trackedFace = face ?: return@Canvas
        val accent = colorFromHex(previewHint.accentColorHex)
        val width = trackedFace.bounds.width() * size.width * previewHint.widthScale
        val height = trackedFace.bounds.height() * size.height * previewHint.topScale
        val centerX = trackedFace.bounds.centerX() * size.width
        val centerY = trackedFace.bounds.centerY() * size.height - (trackedFace.bounds.height() * size.height * previewHint.crownLift * 0.2f)
        val topLeft = Offset(centerX - width / 2f, centerY - height / 2f)
        when (previewHint.kind) {
            FaceEffectKind.SquareGrid -> {
                val edge = maxOf(width, height)
                val squareTopLeft = Offset(centerX - edge / 2f, centerY - edge / 2f)
                drawRoundRect(
                    color = accent.copy(alpha = 0.22f),
                    topLeft = squareTopLeft,
                    size = Size(edge, edge),
                    cornerRadius = CornerRadius(edge * 0.08f, edge * 0.08f),
                    style = Stroke(width = 4.dp.toPx()),
                )
                val cell = edge / previewHint.squareGridSize
                repeat(previewHint.squareGridSize - 1) { index ->
                    val offset = cell * (index + 1)
                    drawLine(
                        color = accent.copy(alpha = 0.28f),
                        start = Offset(squareTopLeft.x + offset, squareTopLeft.y),
                        end = Offset(squareTopLeft.x + offset, squareTopLeft.y + edge),
                        strokeWidth = 1.5.dp.toPx(),
                    )
                    drawLine(
                        color = accent.copy(alpha = 0.28f),
                        start = Offset(squareTopLeft.x, squareTopLeft.y + offset),
                        end = Offset(squareTopLeft.x + edge, squareTopLeft.y + offset),
                        strokeWidth = 1.5.dp.toPx(),
                    )
                }
            }
            FaceEffectKind.PeakPrism -> {
                val prism = Path().apply {
                    moveTo(centerX, topLeft.y - height * 0.2f)
                    lineTo(centerX + width * 0.28f, centerY - height * 0.18f)
                    lineTo(centerX + width * 0.4f, centerY + height * 0.34f)
                    lineTo(centerX, centerY + height * 0.5f)
                    lineTo(centerX - width * 0.4f, centerY + height * 0.34f)
                    lineTo(centerX - width * 0.28f, centerY - height * 0.18f)
                    close()
                }
                drawPath(prism, color = accent.copy(alpha = 0.14f))
                drawPath(prism, color = accent.copy(alpha = 0.36f), style = Stroke(width = 4.dp.toPx()))
            }
            FaceEffectKind.BubbleOrb -> {
                drawOval(
                    color = accent.copy(alpha = 0.16f),
                    topLeft = Offset(centerX - width * 0.56f, centerY - height * 0.46f),
                    size = Size(width * 1.12f, height * 0.96f),
                )
                drawOval(
                    color = accent.copy(alpha = 0.32f),
                    topLeft = Offset(centerX - width * 0.5f, centerY - height * 0.4f),
                    size = Size(width, height * 0.88f),
                    style = Stroke(width = 4.dp.toPx()),
                )
            }
            FaceEffectKind.BladeSlice -> {
                val blade = Path().apply {
                    moveTo(centerX, topLeft.y - height * 0.12f)
                    lineTo(centerX + width * 0.16f, centerY)
                    lineTo(centerX, centerY + height * 0.54f)
                    lineTo(centerX - width * 0.16f, centerY)
                    close()
                }
                drawPath(blade, color = accent.copy(alpha = 0.14f))
                drawPath(blade, color = accent.copy(alpha = 0.38f), style = Stroke(width = 4.dp.toPx()))
            }
        }
    }
}

@Composable
private fun TinyBadge(
    label: String,
    glow: Color,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color.Black.copy(alpha = 0.22f),
        border = androidx.compose.foundation.BorderStroke(1.dp, glow.copy(alpha = 0.45f)),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            color = glow,
        )
    }
}

@Composable
private fun BackgroundOrbs() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(GlowRose.copy(alpha = 0.22f), Color.Transparent),
                center = Offset(width * 0.84f, height * 0.12f),
                radius = width * 0.28f,
            ),
            radius = width * 0.28f,
            center = Offset(width * 0.84f, height * 0.12f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(GlowBlue.copy(alpha = 0.2f), Color.Transparent),
                center = Offset(width * 0.12f, height * 0.72f),
                radius = width * 0.34f,
            ),
            radius = width * 0.34f,
            center = Offset(width * 0.12f, height * 0.72f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(GlowMint.copy(alpha = 0.18f), Color.Transparent),
                center = Offset(width * 0.18f, height * 0.22f),
                radius = width * 0.22f,
            ),
            radius = width * 0.22f,
            center = Offset(width * 0.18f, height * 0.22f),
        )
    }
}

@Composable
private fun PreviewPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF131A24), Color(0xFF22202A), Color(0xFF0B0F17)),
                ),
            ),
    )
}

private fun colorFromHex(value: Long): Color = Color(value)

private fun Context.hasCameraPermission(): Boolean {
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        android.Manifest.permission.CAMERA
    } else {
        return true
    }
    return ContextCompat.checkSelfPermission(this, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
}
