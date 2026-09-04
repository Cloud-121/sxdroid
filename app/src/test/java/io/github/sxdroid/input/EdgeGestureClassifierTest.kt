package io.github.sxdroid.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EdgeGestureClassifierTest {
    private val classifier = EdgeGestureClassifier(EdgeGestureConfig(edgeSizePx = 20f, minimumDistancePx = 40f, longSwipeMinimumDistancePx = 80f))
    private val bounds = GestureBounds(200f, 400f)

    @Test fun maps_every_authoritative_edge_direction() {
        assertAction(EdgeGestureAction.BRIGHTNESS_DOWN, 100f, 10f, 40f, 10f)
        assertAction(EdgeGestureAction.BRIGHTNESS_UP, 100f, 10f, 160f, 10f)
        assertAction(EdgeGestureAction.CLOSE_MENUS, 100f, 100f, 100f, 10f)
        assertAction(EdgeGestureAction.SHOW_MENU, 100f, 10f, 100f, 70f)
        assertAction(EdgeGestureAction.PREVIOUS, 10f, 160f, 10f, 100f)
        assertAction(EdgeGestureAction.NEXT, 10f, 100f, 10f, 160f)
        assertAction(EdgeGestureAction.BACK, 70f, 100f, 10f, 100f)
        assertAction(EdgeGestureAction.PREVIOUS, 10f, 100f, 70f, 100f)
        assertAction(EdgeGestureAction.VOLUME_UP, 190f, 160f, 190f, 100f)
        assertAction(EdgeGestureAction.VOLUME_DOWN, 190f, 100f, 190f, 160f)
        assertAction(EdgeGestureAction.NEXT, 190f, 100f, 130f, 100f)
        assertAction(EdgeGestureAction.RIGHT_KEY, 130f, 100f, 190f, 100f)
    }

    @Test fun maps_long_bottom_swipes_and_vertical_context_menu() {
        assertAction(EdgeGestureAction.BACKSPACE, 150f, 390f, 50f, 390f)
        assertAction(EdgeGestureAction.SELECT, 50f, 390f, 150f, 390f)
        assertAction(EdgeGestureAction.OPEN_ACTION_MENU, 100f, 390f, 100f, 330f)
        assertAction(EdgeGestureAction.OPEN_ACTION_MENU, 100f, 330f, 100f, 390f)
    }

    @Test fun center_diagonal_swipes_do_not_match_corner_bindings() {
        assertNull(classifier.classify(point(100f, 200f), point(160f, 140f), bounds))
        assertNull(classifier.classify(point(100f, 200f), point(160f, 260f), bounds))
        assertNull(classifier.classify(point(100f, 200f), point(40f, 260f), bounds))
        assertNull(classifier.classify(point(100f, 200f), point(40f, 140f), bounds))
    }

    @Test fun corner_diagonals_take_precedence_over_edge_bindings() {
        assertAction(EdgeGestureAction.LOCK_FALLBACK, 10f, 390f, 70f, 330f)
        assertAction(EdgeGestureAction.ROTATE_FALLBACK, 190f, 390f, 130f, 330f)
        assertAction(EdgeGestureAction.BACKSPACE, 150f, 390f, 50f, 390f)
    }

    @Test fun long_swipe_is_distinguishable_by_distance_and_timing() {
        val timed = EdgeGestureClassifier(EdgeGestureConfig(edgeSizePx = 20f, minimumDistancePx = 40f, longSwipeMinimumDistancePx = 80f, longSwipeMinimumDurationMillis = 300L))
        assertNull(timed.classify(point(150f, 390f), point(50f, 390f), bounds, durationMillis = 100L))
        assertEquals(EdgeGestureAction.BACKSPACE, timed.classify(point(150f, 390f), point(50f, 390f), bounds, durationMillis = 300L))
        assertNull(classifier.classify(point(150f, 390f), point(100f, 390f), bounds))
    }

    @Test fun stationary_long_press_opens_context_but_taps_and_drags_do_not() {
        assertEquals(EdgeGestureAction.OPEN_CONTEXT, classifier.classify(point(100f, 200f), point(105f, 204f), bounds, 600L))
        assertNull(classifier.classify(point(100f, 200f), point(105f, 204f), bounds, 100L))
        assertNull(classifier.classify(point(100f, 200f), point(150f, 200f), bounds, 600L))
    }

    @Test fun rejects_multiple_pointers_invalid_bounds_and_out_of_bounds_points() {
        assertNull(classifier.classify(point(100f, 10f), point(100f, 70f), bounds, pointerCount = 2))
        assertNull(classifier.classify(point(100f, 10f), point(100f, 70f), GestureBounds(0f, 400f)))
        assertNull(classifier.classify(point(-1f, 10f), point(100f, 70f), bounds))
        assertNull(classifier.classify(point(100f, 10f), point(100f, 401f), bounds))
    }

    @Test fun custom_bindings_replace_default_mapping() {
        val custom = EdgeGestureClassifier(EdgeGestureConfig(edgeSizePx = 20f, minimumDistancePx = 40f, bindings = listOf(
            GestureBinding(GestureKind.SWIPE, EdgeGestureAction.RIGHT_KEY, GestureDirection.DOWN, startEdge = GestureEdge.TOP),
        )))
        assertEquals(EdgeGestureAction.RIGHT_KEY, custom.classify(point(100f, 10f), point(100f, 70f), bounds))
    }

    @Test fun default_phone_sized_edge_swipes_are_recognized() {
        val phone = GestureBounds(1080f, 1920f)
        val defaults = EdgeGestureClassifier()
        assertEquals(EdgeGestureAction.SHOW_MENU, defaults.classify(GesturePoint(540f, 90f), GesturePoint(540f, 190f), phone))
        assertEquals(EdgeGestureAction.NEXT, defaults.classify(GesturePoint(90f, 960f), GesturePoint(90f, 1060f), phone))
        assertEquals(EdgeGestureAction.VOLUME_UP, defaults.classify(GesturePoint(990f, 960f), GesturePoint(990f, 860f), phone))
    }

    private fun assertAction(action: EdgeGestureAction, startX: Float, startY: Float, endX: Float, endY: Float) =
        assertEquals(action, classifier.classify(point(startX, startY), point(endX, endY), bounds))

    private fun point(x: Float, y: Float) = GesturePoint(x, y)
}
