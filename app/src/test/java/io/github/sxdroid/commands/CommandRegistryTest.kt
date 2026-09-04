package io.github.sxdroid.commands

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandRegistryTest {
    @Test fun top_level_menu_exposes_only_functional_entries() {
        assertEquals(
            listOf("menu.apps", "menu.controls", "menu.system", "menu.configuration"),
            builtInMenuCommands().map { it.id },
        )
    }
}
