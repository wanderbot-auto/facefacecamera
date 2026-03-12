package com.facefacecamera.facefx

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

class SimpleFaceEffectRenderer : FaceEffectRenderer {
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val atmospherePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun renderPreview(
        filter: FaceFilterPreset,
        face: FaceTrackerResult?,
        controls: FaceEffectControls,
    ): PreviewEffectHint {
        val profile = filter.deformProfile
        return PreviewEffectHint(
            kind = profile.kind,
            topScale = profile.previewHeightScale,
            widthScale = profile.previewWidthScale,
            jawScale = profile.previewJawScale,
            crownLift = profile.previewCrownLift,
            accentColorHex = filter.accentColorHex,
            squareGridSize = controls.squareGridSize,
        )
    }

    override suspend fun renderStill(
        source: Bitmap,
        filter: FaceFilterPreset,
        face: FaceTrackerResult?,
        controls: FaceEffectControls,
    ): Bitmap = withContext(Dispatchers.Default) {
        val working = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(working)
        val profile = filter.deformProfile

        face?.let { trackedFace ->
            val faceRect = trackedFace.bounds.toPixelRect(width = working.width, height = working.height)
                .expand(
                    horizontalRatio = 0.16f,
                    topRatio = 0.22f + profile.lift,
                    bottomRatio = 0.16f,
                    maxWidth = working.width,
                    maxHeight = working.height,
                )

            when (profile.kind) {
                FaceEffectKind.SquareGrid -> {
                    renderMeshWarp(
                        canvas = canvas,
                        source = source,
                        targetRect = faceRect,
                        meshWidth = 28,
                        meshHeight = 28
                    ) { u, v, rect ->
                        squareGridTransform(
                            u = u,
                            v = v,
                            rect = rect,
                            gridSize = controls.squareGridSize.coerceIn(3, 9),
                            profile = profile
                        )
                    }
                }
                FaceEffectKind.PeakPrism -> {
                    renderMeshWarp(
                        canvas = canvas,
                        source = source,
                        targetRect = faceRect,
                        meshWidth = 26,
                        meshHeight = 30
                    ) { u, v, rect ->
                        peakPrismTransform(u = u, v = v, rect = rect, profile = profile)
                    }
                }
                FaceEffectKind.BubbleOrb -> {
                    renderMeshWarp(
                        canvas = canvas,
                        source = source,
                        targetRect = faceRect,
                        meshWidth = 30,
                        meshHeight = 30
                    ) { u, v, rect ->
                        bubbleOrbTransform(u = u, v = v, rect = rect, profile = profile)
                    }
                }
                FaceEffectKind.BladeSlice -> {
                    renderMeshWarp(
                        canvas = canvas,
                        source = source,
                        targetRect = faceRect,
                        meshWidth = 24,
                        meshHeight = 30
                    ) { u, v, rect ->
                        bladeSliceTransform(u = u, v = v, rect = rect, profile = profile)
                    }
                }
            }

            drawAura(canvas = canvas, faceRect = faceRect, accentColorHex = filter.accentColorHex)
        }

        addAtmosphere(working = working, accentColorHex = filter.accentColorHex)
        if (profile.mirrorOutput) {
            val mirrored = working.mirrored()
            working.recycle()
            mirrored
        } else {
            working
        }
    }

    private fun renderMeshWarp(
        canvas: Canvas,
        source: Bitmap,
        targetRect: RectF,
        meshWidth: Int,
        meshHeight: Int,
        transform: (u: Float, v: Float, rect: RectF) -> VertexTransform,
    ) {
        val safeRect = RectF(
            targetRect.left.coerceIn(0f, source.width - 2f),
            targetRect.top.coerceIn(0f, source.height - 2f),
            targetRect.right.coerceIn(1f, source.width.toFloat()),
            targetRect.bottom.coerceIn(1f, source.height.toFloat()),
        )
        val cropRect = safeRect.toRect().normalizeWithin(source.width, source.height)
        if (cropRect.width() <= 1 || cropRect.height() <= 1) return

        val faceBitmap = Bitmap.createBitmap(source, cropRect.left, cropRect.top, cropRect.width(), cropRect.height())
        try {
            val vertexCount = (meshWidth + 1) * (meshHeight + 1) * 2
            val verts = FloatArray(vertexCount)
            var index = 0
            for (row in 0..meshHeight) {
                val v = row / meshHeight.toFloat()
                for (column in 0..meshWidth) {
                    val u = column / meshWidth.toFloat()
                    val mapped = transform(u, v, safeRect)
                    verts[index++] = mapped.x
                    verts[index++] = mapped.y
                }
            }
            canvas.drawBitmapMesh(faceBitmap, meshWidth, meshHeight, verts, 0, null, 0, bitmapPaint)
        } finally {
            faceBitmap.recycle()
        }
    }

    private fun drawAura(canvas: Canvas, faceRect: RectF, accentColorHex: Long) {
        val centerX = faceRect.centerX()
        val centerY = faceRect.centerY()
        val radius = faceRect.width().coerceAtLeast(faceRect.height()) * 0.72f
        glowPaint.shader = RadialGradient(
            centerX,
            centerY,
            radius,
            intArrayOf(withAlpha(accentColorHex, 72), Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(centerX, centerY, radius, glowPaint)
    }

    private fun addAtmosphere(working: Bitmap, accentColorHex: Long) {
        val canvas = Canvas(working)
        atmospherePaint.shader = LinearGradient(
            0f,
            0f,
            working.width.toFloat(),
            working.height.toFloat(),
            intArrayOf(withAlpha(accentColorHex, 28), withAlpha(0xFF090C13, 0), withAlpha(0xFF090C13, 72)),
            floatArrayOf(0f, 0.42f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, working.width.toFloat(), working.height.toFloat(), atmospherePaint)
    }
}

private data class VertexTransform(
    val x: Float,
    val y: Float,
)

private fun RectF.toPixelRect(width: Int, height: Int): RectF = RectF(
    left * width,
    top * height,
    right * width,
    bottom * height,
)

private fun RectF.toRect(): Rect = Rect(
    left.roundToInt(),
    top.roundToInt(),
    right.roundToInt(),
    bottom.roundToInt(),
)

private fun Rect.normalizeWithin(maxWidth: Int, maxHeight: Int): Rect {
    val normalizedLeft = left.coerceIn(0, maxWidth - 1)
    val normalizedTop = top.coerceIn(0, maxHeight - 1)
    val normalizedRight = right.coerceIn(normalizedLeft + 1, maxWidth)
    val normalizedBottom = bottom.coerceIn(normalizedTop + 1, maxHeight)
    return Rect(normalizedLeft, normalizedTop, normalizedRight, normalizedBottom)
}

private fun RectF.expand(
    horizontalRatio: Float,
    topRatio: Float,
    bottomRatio: Float,
    maxWidth: Int,
    maxHeight: Int,
): RectF {
    val horizontalPadding = width() * horizontalRatio
    val topPadding = height() * topRatio
    val bottomPadding = height() * bottomRatio
    return RectF(
        (left - horizontalPadding).coerceIn(0f, maxWidth.toFloat()),
        (top - topPadding).coerceIn(0f, maxHeight.toFloat()),
        (right + horizontalPadding).coerceIn(0f, maxWidth.toFloat()),
        (bottom + bottomPadding).coerceIn(0f, maxHeight.toFloat()),
    )
}

private fun Bitmap.mirrored(): Bitmap {
    val matrix = Matrix().apply { preScale(-1f, 1f) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

private fun withAlpha(colorHex: Long, alpha: Int): Int {
    val base = colorHex.toInt() and 0x00FFFFFF
    return (alpha.coerceIn(0, 255) shl 24) or base
}

private fun squareGridTransform(
    u: Float,
    v: Float,
    rect: RectF,
    gridSize: Int,
    profile: DeformProfile,
): VertexTransform {
    val dx = u - 0.5f
    val dy = v - 0.5f
    val blockX = ((u * gridSize).toInt().coerceIn(0, gridSize - 1) + 0.5f) / gridSize.toFloat()
    val blockY = ((v * gridSize).toInt().coerceIn(0, gridSize - 1) + 0.5f) / gridSize.toFloat()
    val snapBlend = (0.08f + profile.amount * 0.04f).coerceAtMost(0.16f)
    val snappedDx = lerp(dx, blockX - 0.5f, snapBlend)
    val snappedDy = lerp(dy, blockY - 0.5f, snapBlend)
    val horizontalPlate = (1f - abs(snappedDy) * 1.72f).coerceIn(0f, 1f)
    val verticalPlate = (1f - abs(snappedDx) * 1.72f).coerceIn(0f, 1f)
    val widthScale = 1.02f + horizontalPlate * (0.2f + profile.amount * 0.08f)
    val heightScale = 1.02f + verticalPlate * (0.2f + profile.tension * 0.08f)
    val microStepX = sin((blockY * gridSize * 1.2f + u * 0.55f) * Math.PI).toFloat() * rect.width() * 0.01f * profile.tension
    val microStepY = sin((blockX * gridSize * 1.2f + v * 0.55f) * Math.PI).toFloat() * rect.height() * 0.01f * profile.tension
    return VertexTransform(
        x = rect.centerX() + snappedDx * rect.width() * widthScale + microStepX,
        y = rect.centerY() + snappedDy * rect.height() * heightScale + microStepY,
    )
}

private fun peakPrismTransform(
    u: Float,
    v: Float,
    rect: RectF,
    profile: DeformProfile,
): VertexTransform {
    val dx = u - 0.5f
    val dy = v - 0.5f
    val topBias = (1f - v).pow(1.18f)
    val widthFactor = (0.16f + v.pow(0.85f) * 1.08f).coerceAtMost(1.08f)
    val peakPull = rect.height() * (0.22f + profile.amount * 0.12f) * topBias
    val cheekPinch = 1f - (1f - abs(dx) * 2f).coerceIn(0f, 1f) * (0.28f + profile.tension * 0.12f) * (1f - v)
    val x = rect.centerX() + dx * rect.width() * widthFactor * cheekPinch
    val y = rect.centerY() + dy * rect.height() * (1.1f + topBias * 0.44f) - peakPull
    return VertexTransform(x = x, y = y)
}

private fun bubbleOrbTransform(
    u: Float,
    v: Float,
    rect: RectF,
    profile: DeformProfile,
): VertexTransform {
    val dx = u - 0.5f
    val dy = v - 0.5f
    val radial = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtMost(0.78f) / 0.78f
    val centerWeight = (1f - radial).coerceIn(0f, 1f)
    val inflate = 1.56f - radial.pow(1.9f) * (0.98f - profile.amount * 0.1f)
    val roundBias = 1.12f + centerWeight * (0.24f + profile.tension * 0.12f)
    val wobble = sin((u + v) * Math.PI * 2).toFloat() * rect.width() * 0.012f * centerWeight
    val lift = centerWeight * rect.height() * (0.1f + profile.lift * 0.18f)
    val x = rect.centerX() + dx * rect.width() * inflate * roundBias + wobble
    val y = rect.centerY() + dy * rect.height() * (inflate + 0.16f) * (1.04f + centerWeight * 0.08f) - lift
    return VertexTransform(x = x, y = y)
}

private fun bladeSliceTransform(
    u: Float,
    v: Float,
    rect: RectF,
    profile: DeformProfile,
): VertexTransform {
    val dx = u - 0.5f
    val dy = v - 0.5f
    val centerFalloff = (1f - abs(dx) * 2f).coerceIn(0f, 1f)
    val widthFactor = 0.22f + abs(dy).pow(0.86f) * (1.08f + profile.amount * 0.08f)
    val verticalStretch = 1.18f + centerFalloff * (0.34f + profile.tension * 0.12f)
    val bladeLift = rect.height() * (0.08f + profile.lift * 0.16f) * (1f - v)
    val x = rect.centerX() + dx * rect.width() * widthFactor
    val y = rect.centerY() + dy * rect.height() * verticalStretch - bladeLift
    return VertexTransform(x = x, y = y)
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float = start + (stop - start) * fraction
