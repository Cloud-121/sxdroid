package io.github.sxdroid.config

import io.github.sxdroid.input.KeyBindings
import io.github.sxdroid.input.EdgeGestureConfig

/** In-memory configuration shaped for a future TOML source. */
data class LauncherConfig(
    val keyBindings: KeyBindings = KeyBindings(),
    val edgeGestures: EdgeGestureConfig = EdgeGestureConfig(),
)
