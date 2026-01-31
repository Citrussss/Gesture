package top.arakawa.gesture

import android.content.Context
import android.graphics.PointF
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class MultiFingerSwipeDetector(
    context: Context,
    private val onResult: (Result) -> Unit,
) {
    enum class Direction {
        Up,
        Down,
        Left,
        Right,
    }

    data class Gesture(
        val fingers: Int,
        val direction: Direction,
    )

    enum class InvalidReason {
        TooShort,
        OffAxis,
        Reversed,
        EndMismatch,
        Wiggly,
        Incomplete,
    }

    data class Metrics(
        val primaryPx: Float,
        val offAxisPx: Float,
        val oppositePx: Float,
        val endPrimaryPx: Float,
        val linearity: Float,
        val durationMs: Long,
    )

    data class Result(
        val fingers: Int,
        val candidateDirection: Direction?,
        val gesture: Gesture?,
        val invalidReason: InvalidReason?,
        val metrics: Metrics?,
    )

    private val touchSlopPx = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val minDistancePx = touchSlopPx * 7f
    private val maxOffAxisRatio = 0.25f
    private val reverseMinDistancePx = touchSlopPx * 1.5f
    private val reverseMaxRatio = 0.15f
    private val endKeepRatio = 0.8f
    private val minLinearityRatio = 0.88f

    private var trackingFingers: Int? = null
    private var trackingPointerIds: IntArray? = null
    private var startCentroid: PointF? = null
    private var lastCentroid: PointF? = null
    private var lastMoveCentroid: PointF? = null
    private var maxDx: Float = 0f
    private var minDx: Float = 0f
    private var maxDy: Float = 0f
    private var minDy: Float = 0f
    private var totalPathPx: Float = 0f
    private var startTimeMs: Long = 0L
    private var lastTimeMs: Long = 0L
    private var tracking = false

    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                reset()
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val count = event.pointerCount
                if (count in 3..5) {
                    // Allow 3 -> 4 -> 5 transitions: re-baseline on each additional finger.
                    startTracking(event, count)
                    return true
                }

                if (tracking) {
                    reset()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (!tracking) return false
                val current = centroidForTrackingPointers(event) ?: run {
                    reset()
                    return false
                }
                lastTimeMs = event.eventTime

                val prev = lastMoveCentroid
                if (prev != null) {
                    totalPathPx += hypot(current.x - prev.x, current.y - prev.y)
                }
                lastMoveCentroid = current
                lastCentroid = current

                val start = startCentroid ?: run {
                    reset()
                    return false
                }

                val dx = current.x - start.x
                val dy = current.y - start.y
                if (dx > maxDx) maxDx = dx
                if (dx < minDx) minDx = dx
                if (dy > maxDy) maxDy = dy
                if (dy < minDy) minDy = dy
                return true
            }
            MotionEvent.ACTION_POINTER_UP,
            -> {
                if (!tracking) return false
                lastTimeMs = event.eventTime
                lastCentroid = centroidForTrackingPointers(event) ?: lastCentroid

                val fingers = trackingFingers
                val remainingAfterUp = event.pointerCount - 1
                if (fingers == null || remainingAfterUp < fingers) {
                    return finishGestureAndReset()
                }

                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!tracking) return false
                lastTimeMs = event.eventTime
                lastCentroid = centroidForTrackingPointers(event) ?: lastCentroid
                return finishGestureAndReset()
            }
            MotionEvent.ACTION_CANCEL -> {
                val wasTracking = tracking
                reset()
                return wasTracking
            }
        }

        return tracking
    }

    private fun startTracking(event: MotionEvent, fingers: Int) {
        val ids = IntArray(event.pointerCount) { idx -> event.getPointerId(idx) }
        trackingFingers = fingers
        trackingPointerIds = ids
        startCentroid = centroid(event, ids)
        lastCentroid = startCentroid
        lastMoveCentroid = startCentroid
        maxDx = 0f
        minDx = 0f
        maxDy = 0f
        minDy = 0f
        totalPathPx = 0f
        startTimeMs = event.eventTime
        lastTimeMs = event.eventTime
        tracking = true
    }

    private fun centroidForTrackingPointers(event: MotionEvent): PointF? {
        val ids = trackingPointerIds ?: return null
        return centroid(event, ids)
    }

    private fun centroid(event: MotionEvent, pointerIds: IntArray): PointF? {
        var sumX = 0f
        var sumY = 0f
        var count = 0

        for (id in pointerIds) {
            val idx = event.findPointerIndex(id)
            if (idx < 0) return null
            sumX += event.getX(idx)
            sumY += event.getY(idx)
            count++
        }

        if (count == 0) return null
        return PointF(sumX / count, sumY / count)
    }

    private fun finishGestureAndReset(): Boolean {
        val fingers = trackingFingers
        val start = startCentroid
        val end = lastCentroid

        if (fingers == null || start == null || end == null) {
            val safeFingers = fingers ?: 0
            onResult(
                Result(
                    fingers = safeFingers,
                    candidateDirection = null,
                    gesture = null,
                    invalidReason = InvalidReason.Incomplete,
                    metrics = null,
                ),
            )
            reset()
            return true
        }

        // Use peak displacement (not end position), then apply strict validation to avoid
        // ambiguous / wiggly / reversal-heavy motion.
        val peakRight = maxDx
        val peakLeft = -minDx
        val peakDown = maxDy
        val peakUp = -minDy

        val bestHorizontal = max(peakRight, peakLeft)
        val bestVertical = max(peakDown, peakUp)

        val horizontal = bestHorizontal >= bestVertical
        val candidateDirection = if (horizontal) {
            if (peakRight >= peakLeft) Direction.Right else Direction.Left
        } else {
            if (peakDown >= peakUp) Direction.Down else Direction.Up
        }

        val primaryPx = if (horizontal) bestHorizontal else bestVertical
        val offAxisPx = if (horizontal) bestVertical else bestHorizontal
        val oppositePx = when (candidateDirection) {
            Direction.Right -> peakLeft
            Direction.Left -> peakRight
            Direction.Down -> peakUp
            Direction.Up -> peakDown
        }

        val netDx = end.x - start.x
        val netDy = end.y - start.y
        val netDistance = hypot(netDx, netDy)
        val linearity = if (totalPathPx > 0f) (netDistance / totalPathPx).coerceIn(0f, 1f) else 1f
        val durationMs = (lastTimeMs - startTimeMs).coerceAtLeast(0L)
        val endPrimaryPx = when (candidateDirection) {
            Direction.Right -> netDx
            Direction.Left -> -netDx
            Direction.Down -> netDy
            Direction.Up -> -netDy
        }

        val metrics = Metrics(
            primaryPx = primaryPx,
            offAxisPx = offAxisPx,
            oppositePx = oppositePx,
            endPrimaryPx = endPrimaryPx,
            linearity = linearity,
            durationMs = durationMs,
        )

        val invalidReason = when {
            primaryPx < minDistancePx -> InvalidReason.TooShort
            offAxisPx > primaryPx * maxOffAxisRatio -> InvalidReason.OffAxis
            oppositePx >= reverseMinDistancePx && oppositePx > primaryPx * reverseMaxRatio -> InvalidReason.Reversed
            endPrimaryPx < primaryPx * endKeepRatio -> InvalidReason.EndMismatch
            linearity < minLinearityRatio -> InvalidReason.Wiggly
            else -> null
        }

        if (invalidReason == null) {
            val gesture = Gesture(fingers = fingers, direction = candidateDirection)
            onResult(
                Result(
                    fingers = fingers,
                    candidateDirection = candidateDirection,
                    gesture = gesture,
                    invalidReason = null,
                    metrics = metrics,
                ),
            )
        } else {
            onResult(
                Result(
                    fingers = fingers,
                    candidateDirection = candidateDirection,
                    gesture = null,
                    invalidReason = invalidReason,
                    metrics = metrics,
                ),
            )
        }

        reset()
        return true
    }

    private fun reset() {
        trackingFingers = null
        trackingPointerIds = null
        startCentroid = null
        lastCentroid = null
        lastMoveCentroid = null
        maxDx = 0f
        minDx = 0f
        maxDy = 0f
        minDy = 0f
        totalPathPx = 0f
        startTimeMs = 0L
        lastTimeMs = 0L
        tracking = false
    }
}
