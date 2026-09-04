package io.github.sxdroid.menu

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class MenuNavigatorTest {
    @Test fun enters_and_returns_from_nested_menu() {
        val root = CommandMenu("root", "home", emptyList())
        val system = CommandMenu("system", "system", emptyList())
        val navigator = MenuNavigator(root, mapOf("root" to root, "system" to system))
        assertTrue(navigator.enter("system"))
        assertEquals("system", navigator.current.id)
        assertTrue(navigator.back())
        assertEquals("root", navigator.current.id)
        assertFalse(navigator.back())
    }

    @Test fun close_all_returns_directly_to_root() {
        val root = CommandMenu("root", "home", emptyList())
        val first = CommandMenu("first", "first", emptyList())
        val second = CommandMenu("second", "second", emptyList())
        val navigator = MenuNavigator(root, listOf(root, first, second).associateBy { it.id })
        assertTrue(navigator.enter("first"))
        assertTrue(navigator.enter("second"))
        assertTrue(navigator.closeAll())
        assertEquals("root", navigator.current.id)
        assertEquals(1, navigator.depth)
        assertFalse(navigator.closeAll())
    }

}
