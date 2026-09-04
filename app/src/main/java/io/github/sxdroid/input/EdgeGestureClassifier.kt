package io.github.sxdroid.input

import kotlin.math.abs

/** Geometry, timing, and bindings kept independent of Compose and Android events. */
data class EdgeGestureConfig(
    val edgeSizePx: Float = 72f,
    val minimumDistancePx: Float = 96f,
    val longSwipeMinimumDistancePx: Float = 160f,
    val longSwipeMinimumDurationMillis: Long = 0L,
    val longPressMillis: Long = 500L,
    val longPressSlopPx: Float = 24f,
    val bindings: List<GestureBinding> = defaultGestureBindings(),
)

data class GesturePoint(val x: Float, val y: Float)
data class GestureBounds(val width: Float, val height: Float)

enum class GestureEdge { TOP, RIGHT, BOTTOM, LEFT }
enum class GestureCorner { BOTTOM_LEFT, BOTTOM_RIGHT }
enum class GestureDirection { UP, DOWN, LEFT, RIGHT }
enum class GestureKind { SWIPE, LONG_SWIPE, LONG_PRESS }

data class GestureBinding(
    val kind: GestureKind,
    val action: EdgeGestureAction,
    val direction: GestureDirection? = null,
    val startEdge: GestureEdge? = null,
    val endEdge: GestureEdge? = null,
    val startCorner: GestureCorner? = null,
    val diagonal: Boolean? = null,
)

enum class EdgeGestureAction {
    SHOW_MENU,
    CLOSE_MENUS,
    SELECT,
    BACKSPACE,
    NEXT,
    PREVIOUS,
    BACK,
    RIGHT_KEY,
    VOLUME_UP,
    VOLUME_DOWN,
    BRIGHTNESS_UP,
    BRIGHTNESS_DOWN,
    LOCK_FALLBACK,
    ROTATE_FALLBACK,
    OPEN_CONTEXT,
    OPEN_ACTION_MENU,
}

fun defaultGestureBindings(): List<GestureBinding> = listOf(
    GestureBinding(GestureKind.SWIPE, EdgeGestureAction.LOCK_FALLBACK, startCorner = GestureCorner.BOTTOM_LEFT, diagonal = true),
    GestureBinding(GestureKind.SWIPE, EdgeGestureAction.ROTATE_FALLBACK, startCorner = GestureCorner.BOTTOM_RIGHT, diagonal = true),
    GestureBinding(GestureKind.SWIPE, EdgeGestureAction.CLOSE_MENUS, GestureDirection.UP, endEdge = GestureEdge.TOP),
    GestureBinding(GestureKind.SWIPE, EdgeGestureAction.SHOW_MENU, GestureDirection.DOWN, startEdge = GestureEdge.TOP),
    GestureBinding(GestureKind.SWIPE, EdgeGestureAction.BRIGHTNESS_DOWN, GestureDirection.LEFT, startEdge = GestureEdge.TOP),
    GestureBinding(GestureKind.SWIPE, EdgeGestureAction.BRIGHTNESS_UP, GestureDirection.RIGHT, startEdge = GestureEdge.TOP),
    GestureBinding(GestureKind.SWIPE, EdgeGestureAction.PREVIOUS, GestureDirection.UP, startEdge = GestureEdge.LEFT),
    GestureBinding(GestureKind.SWIPE, EdgeGestureAction.NEXT, GestureDirection.DOWN, startEdge = GestureEdge.LEFT),
    GestureBinding(GestureKind.SWIPE, EdgeGestureAction.BACK, GestureDirection.LEFT, endEdge = GestureEdge.LEFT),
    GestureBinding(GestureKind.SWIPE, EdgeGestureAction.PREVIOUS, GestureDirection.RIGHT, startEdge = GestureEdge.LEFT),
    GestureBinding(GestureKind.SWIPE, EdgeGestureAction.VOLUME_UP, GestureDirection.UP, startEdge = GestureEdge.RIGHT),
    GestureBinding(GestureKind.SWIPE, EdgeGestureAction.VOLUME_DOWN, GestureDirection.DOWN, startEdge = GestureEdge.RIGHT),
    GestureBinding(GestureKind.SWIPE, EdgeGestureAction.NEXT, GestureDirection.LEFT, startEdge = GestureEdge.RIGHT),
    GestureBinding(GestureKind.SWIPE, EdgeGestureAction.RIGHT_KEY, GestureDirection.RIGHT, endEdge = GestureEdge.RIGHT),
    GestureBinding(GestureKind.LONG_SWIPE, EdgeGestureAction.BACKSPACE, GestureDirection.LEFT, startEdge = GestureEdge.BOTTOM),
    GestureBinding(GestureKind.LONG_SWIPE, EdgeGestureAction.SELECT, GestureDirection.RIGHT, startEdge = GestureEdge.BOTTOM),
    GestureBinding(GestureKind.SWIPE, EdgeGestureAction.OPEN_ACTION_MENU, GestureDirection.UP, startEdge = GestureEdge.BOTTOM),
    GestureBinding(GestureKind.SWIPE, EdgeGestureAction.OPEN_ACTION_MENU, GestureDirection.DOWN, endEdge = GestureEdge.BOTTOM),
    GestureBinding(GestureKind.LONG_PRESS, EdgeGestureAction.OPEN_CONTEXT),
)

class EdgeGestureClassifier(private val config: EdgeGestureConfig = EdgeGestureConfig()) {
    fun classify(start: GesturePoint, end: GesturePoint, bounds: GestureBounds, durationMillis: Long = 0L, pointerCount: Int = 1): EdgeGestureAction? {
        if (pointerCount != 1 || bounds.width <= 0f || bounds.height <= 0f || !inside(start, bounds) || !inside(end, bounds)) return null
        val dx = end.x - start.x
        val dy = end.y - start.y
        val distance = maxOf(abs(dx), abs(dy))
        if (durationMillis >= config.longPressMillis && distance <= config.longPressSlopPx) return match(GestureKind.LONG_PRESS, null, false, start, end, bounds)
        if (distance < config.minimumDistancePx) return null
        val direction = when {
            abs(dx) >= abs(dy) && dx > 0f -> GestureDirection.RIGHT
            abs(dx) >= abs(dy) -> GestureDirection.LEFT
            dy > 0f -> GestureDirection.DOWN
            else -> GestureDirection.UP
        }
        val isLongSwipe = distance >= config.longSwipeMinimumDistancePx && durationMillis >= config.longSwipeMinimumDurationMillis
        val diagonal = abs(dx) >= config.minimumDistancePx / 2f && abs(dy) >= config.minimumDistancePx / 2f
        return if (isLongSwipe) match(GestureKind.LONG_SWIPE, direction, diagonal, start, end, bounds) ?: match(GestureKind.SWIPE, direction, diagonal, start, end, bounds)
        else match(GestureKind.SWIPE, direction, diagonal, start, end, bounds)
    }

    private fun match(kind: GestureKind, direction: GestureDirection?, diagonal: Boolean, start: GesturePoint, end: GesturePoint, bounds: GestureBounds): EdgeGestureAction? {
        val corner = corner(start, bounds)
        val edges = edges(start, bounds)
        val endEdges = edges(end, bounds)
        return corner?.let { startCorner ->
            config.bindings.firstOrNull { it.kind == kind && it.startCorner == startCorner &&
                (it.direction == null || it.direction == direction) &&
                (it.diagonal == null || it.diagonal == diagonal) }?.action
        }
            ?: config.bindings.firstOrNull { it.kind == kind && it.startCorner == null && (it.direction == null || it.direction == direction) && (it.diagonal == null || it.diagonal == diagonal) && (it.startEdge == null || it.startEdge in edges) && (it.endEdge == null || it.endEdge in endEdges) }?.action
    }

    private fun corner(point: GesturePoint, bounds: GestureBounds): GestureCorner? = when {
        point.x <= config.edgeSizePx && point.y >= bounds.height - config.edgeSizePx -> GestureCorner.BOTTOM_LEFT
        point.x >= bounds.width - config.edgeSizePx && point.y >= bounds.height - config.edgeSizePx -> GestureCorner.BOTTOM_RIGHT
        else -> null
    }

    private fun edges(point: GesturePoint, bounds: GestureBounds): Set<GestureEdge> = buildSet {
        if (point.y <= config.edgeSizePx) add(GestureEdge.TOP)
        if (point.x >= bounds.width - config.edgeSizePx) add(GestureEdge.RIGHT)
        if (point.y >= bounds.height - config.edgeSizePx) add(GestureEdge.BOTTOM)
        if (point.x <= config.edgeSizePx) add(GestureEdge.LEFT)
    }

    private fun inside(point: GesturePoint, bounds: GestureBounds) = point.x in 0f..bounds.width && point.y in 0f..bounds.height
}
