package io.github.sxdroid.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherStateTest {
    @Test fun home_starts_closed_without_commands_to_render() {
        val state = LauncherState()

        assertFalse(state.menuVisible)
        assertEquals("root", state.menuId)
        assertTrue(state.commands.isEmpty())
    }
}
