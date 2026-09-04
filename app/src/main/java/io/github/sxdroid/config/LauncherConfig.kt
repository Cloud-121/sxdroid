package io.github.sxdroid.config

import io.github.sxdroid.input.KeyBindings

/** In-memory configuration shaped for a future TOML source. */
data class LauncherConfig(val keyBindings: KeyBindings = KeyBindings())
