package com.facefacecamera.facefx

import android.graphics.PointF
import android.graphics.RectF

data class FaceTrackerResult(
    val bounds: RectF,
    val leftEye: PointF? = null,
    val rightEye: PointF? = null,
    val noseBase: PointF? = null,
    val mouthBase: PointF? = null,
    val rotationY: Float = 0f,
    val rotationZ: Float = 0f,
    val trackingConfidence: Float = 1f,
)

fun FaceTrackerResult?.isVisuallyEquivalentTo(other: FaceTrackerResult?): Boolean {
    if (this === other) return true
    if (this == null || other == null) return false

    return bounds.deltaFrom(other.bounds) < 0.012f &&
        kotlin.math.abs(rotationY - other.rotationY) < 2f &&
        kotlin.math.abs(rotationZ - other.rotationZ) < 2f
}

private fun RectF.deltaFrom(other: RectF): Float {
    val leftDelta = kotlin.math.abs(left - other.left)
    val topDelta = kotlin.math.abs(top - other.top)
    val rightDelta = kotlin.math.abs(right - other.right)
    val bottomDelta = kotlin.math.abs(bottom - other.bottom)
    return maxOf(leftDelta, topDelta, rightDelta, bottomDelta)
}
