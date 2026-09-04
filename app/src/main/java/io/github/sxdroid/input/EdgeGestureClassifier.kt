package io.github.sxdroid.input

import kotlin.math.abs

/** Geometry and timing settings kept independent of Compose and Android events. */
data class EdgeGestureConfig(
    val edgeSizePx: Float = 72f,
    val minimumDistancePx: Float = 96f,
    val longPressMillis: Long = 500L,
    val longPressSlopPx: Float = 24f,
    val bindings: List<GestureBinding> = defaultGestureBindings(),
)

data class GesturePoint(val x: Float, val y: Float)
data class GestureBounds(val width: Float, val height: Float)

enum class GestureEdge { TOP, RIGHT, BOTTOM, LEFT }
enum class GestureDirection { UP, DOWN, LEFT, RIGHT }
enum class GestureKind { SWIPE, LONG_PRESS }

data class GestureBinding(
    val kind: GestureKind,
    val action: EdgeGestureAction,
    val direction: GestureDirection? = null,
    val startEdge: GestureEdge? = null,
    val endEdge: GestureEdge? = null,
)

enum class EdgeGestureAction {
    OPEN_PALETTE,
    CLOSE_MENUS,
    OPEN_SELECTED,
    BACKSPACE,
    NEXT,
    PREVIOUS,
    BRIGHTNESS_UP,
    BRIGHTNESS_DOWN,
    OPEN_CONTEXT,
}

fun defaultGestureBindings(): List<GestureBinding> = listOf(
    GestureBinding(GestureKind.SWIPE, EdgeGestureAction.OPEN_PALETTE, GestureDirection.DOWN, startEdge = GestureEdge.TOP),
    GestureBinding(GestureKind.SWIPE, EdgeGestureAction.CLOSE_MENUS, GestureDirection.UP, endEdge = GestureEdge.TOP),
    GestureBinding(GestureKind.SWIPE, EdgeGestureAction.OPEN_SELECTED, GestureDirection.RIGHT, startEdge = GestureEdge.BOTTOM),
    GestureBinding(GestureKind.SWIPE, EdgeGestureAction.BACKSPACE, GestureDirection.LEFT, startEdge = GestureEdge.BOTTOM),
    GestureBinding(GestureKind.SWIPE, EdgeGestureAction.NEXT, GestureDirection.DOWN, startEdge = GestureEdge.RIGHT),
    GestureBinding(GestureKind.SWIPE, EdgeGestureAction.PREVIOUS, GestureDirection.UP, startEdge = GestureEdge.RIGHT),
    GestureBinding(GestureKind.SWIPE, EdgeGestureAction.BRIGHTNESS_UP, GestureDirection.RIGHT, startEdge = GestureEdge.TOP),
    GestureBinding(GestureKind.SWIPE, EdgeGestureAction.BRIGHTNESS_DOWN, GestureDirection.LEFT, startEdge = GestureEdge.TOP),
    GestureBinding(GestureKind.LONG_PRESS, EdgeGestureAction.OPEN_CONTEXT),
)

class EdgeGestureClassifier(private val config: EdgeGestureConfig = EdgeGestureConfig()) {
    fun classify(
        start: GesturePoint,
        end: GesturePoint,
        bounds: GestureBounds,
        durationMillis: Long = 0L,
        pointerCount: Int = 1,
    ): EdgeGestureAction? {
        if (pointerCount != 1 || bounds.width <= 0f || bounds.height <= 0f) return null
        if (!inside(start, bounds) || !inside(end, bounds)) return null

        val dx = end.x - start.x
        val dy = end.y - start.y
        val distance = maxOf(abs(dx), abs(dy))
        val kind = if (durationMillis >= config.longPressMillis && distance <= config.longPressSlopPx) {
            GestureKind.LONG_PRESS
        } else {
            GestureKind.SWIPE
        }
        if (kind == GestureKind.SWIPE && distance < config.minimumDistancePx) return null

        val direction = if (kind == GestureKind.LONG_PRESS) null else when {
            abs(dx) >= abs(dy) && dx > 0f -> GestureDirection.RIGHT
            abs(dx) >= abs(dy) -> GestureDirection.LEFT
            dy > 0f -> GestureDirection.DOWN
            else -> GestureDirection.UP
        }
        val startEdges = edges(start, bounds)
        val endEdges = edges(end, bounds)
        return config.bindings.firstOrNull { binding ->
            binding.kind == kind &&
                (binding.direction == null || binding.direction == direction) &&
                (binding.startEdge == null || binding.startEdge in startEdges) &&
                (binding.endEdge == null || binding.endEdge in endEdges)
        }?.action
    }

    private fun edges(point: GesturePoint, bounds: GestureBounds): Set<GestureEdge> = buildSet {
        if (point.y <= config.edgeSizePx) add(GestureEdge.TOP)
        if (point.x >= bounds.width - config.edgeSizePx) add(GestureEdge.RIGHT)
        if (point.y >= bounds.height - config.edgeSizePx) add(GestureEdge.BOTTOM)
        if (point.x <= config.edgeSizePx) add(GestureEdge.LEFT)
    }

    private fun inside(point: GesturePoint, bounds: GestureBounds): Boolean =
        point.x in 0f..bounds.width && point.y in 0f..bounds.height
}
