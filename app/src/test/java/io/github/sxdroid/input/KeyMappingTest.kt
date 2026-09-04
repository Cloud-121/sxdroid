package io.github.sxdroid.input

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyMappingTest {
    @Test fun volume_defaults_navigate_for_short_long_and_repeat() {
        val mapper = KeyMapper(KeyBindings())
        assertEquals(LauncherAction.PREVIOUS, mapper.actionFor(KeyEvent.KEYCODE_VOLUME_UP, PressKind.SHORT))
        assertEquals(LauncherAction.PREVIOUS, mapper.actionFor(KeyEvent.KEYCODE_VOLUME_UP, PressKind.LONG))
        assertEquals(LauncherAction.PREVIOUS, mapper.actionFor(KeyEvent.KEYCODE_VOLUME_UP, PressKind.REPEAT))
        assertEquals(LauncherAction.NEXT, mapper.actionFor(KeyEvent.KEYCODE_VOLUME_DOWN, PressKind.LONG))
    }

    @Test fun custom_mapping_can_disable_a_press_kind() {
        val mapper = KeyMapper(KeyBindings(volumeUp = mapOf(PressKind.SHORT to LauncherAction.NONE)))
        assertEquals(LauncherAction.NONE, mapper.actionFor(KeyEvent.KEYCODE_VOLUME_UP, PressKind.REPEAT))
    }
}
