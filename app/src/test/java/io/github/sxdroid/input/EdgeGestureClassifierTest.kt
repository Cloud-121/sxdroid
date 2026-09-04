package io.github.sxdroid.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EdgeGestureClassifierTest {
    private val classifier = EdgeGestureClassifier(EdgeGestureConfig(edgeSizePx = 20f, minimumDistancePx = 40f))
    private val bounds = GestureBounds(200f, 400f)

    @Test fun classifies_all_required_directions_and_edges() {
        assertAction(EdgeGestureAction.OPEN_PALETTE, 100f, 10f, 100f, 80f)
        assertAction(EdgeGestureAction.CLOSE_MENUS, 100f, 100f, 100f, 10f)
        assertAction(EdgeGestureAction.OPEN_SELECTED, 10f, 390f, 80f, 390f)
        assertAction(EdgeGestureAction.BACKSPACE, 160f, 390f, 90f, 390f)
        assertAction(EdgeGestureAction.NEXT, 190f, 100f, 190f, 170f)
        assertAction(EdgeGestureAction.PREVIOUS, 190f, 170f, 190f, 100f)
        assertAction(EdgeGestureAction.BRIGHTNESS_UP, 100f, 10f, 160f, 10f)
        assertAction(EdgeGestureAction.BRIGHTNESS_DOWN, 100f, 10f, 40f, 10f)
    }

    @Test fun enforces_threshold_edge_and_one_finger() {
        assertNull(classifier.classify(point(100f, 10f), point(100f, 35f), bounds))
        assertNull(classifier.classify(point(100f, 100f), point(100f, 170f), bounds))
        assertNull(classifier.classify(point(100f, 10f), point(100f, 80f), bounds, pointerCount = 2))
    }

    @Test fun maps_stationary_long_press_but_not_short_tap_or_drag() {
        assertEquals(EdgeGestureAction.OPEN_CONTEXT, classifier.classify(point(100f, 200f), point(105f, 204f), bounds, 600L))
        assertNull(classifier.classify(point(100f, 200f), point(105f, 204f), bounds, 100L))
        assertNull(classifier.classify(point(100f, 200f), point(150f, 200f), bounds, 600L))
    }

    @Test fun custom_bindings_replace_default_mapping() {
        val custom = classifier(EdgeGestureAction.PREVIOUS)
        assertEquals(EdgeGestureAction.PREVIOUS, custom.classify(point(100f, 10f), point(100f, 80f), bounds))
    }

    private fun classifier(action: EdgeGestureAction) = EdgeGestureClassifier(
        EdgeGestureConfig(
            edgeSizePx = 20f,
            minimumDistancePx = 40f,
            bindings = listOf(GestureBinding(GestureKind.SWIPE, action, GestureDirection.DOWN, startEdge = GestureEdge.TOP)),
        ),
    )

    private fun assertAction(action: EdgeGestureAction, startX: Float, startY: Float, endX: Float, endY: Float) =
        assertEquals(action, classifier.classify(point(startX, startY), point(endX, endY), bounds))

    private fun point(x: Float, y: Float) = GesturePoint(x, y)
}
