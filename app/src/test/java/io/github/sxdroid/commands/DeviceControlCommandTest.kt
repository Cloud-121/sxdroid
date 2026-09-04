package io.github.sxdroid.commands

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceControlCommandTest {
    @Test fun control_commands_have_unique_stable_ids() {
        val commands = DeviceControl.entries.map(::DeviceControlCommand)

        assertEquals(commands.size, commands.map { it.id }.distinct().size)
        assertEquals("control.flashlight", commands.first().id)
    }
}
