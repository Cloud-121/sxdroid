package io.github.sxdroid.input

import android.view.KeyEvent

enum class LauncherAction { PREVIOUS, NEXT, SELECT, CONTEXT, BACK, NONE }
enum class PressKind { SHORT, LONG, REPEAT }

data class KeyBindings(
    val volumeUp: Map<PressKind, LauncherAction> = mapOf(
        PressKind.SHORT to LauncherAction.PREVIOUS,
        PressKind.LONG to LauncherAction.PREVIOUS,
        PressKind.REPEAT to LauncherAction.PREVIOUS,
    ),
    val volumeDown: Map<PressKind, LauncherAction> = mapOf(
        PressKind.SHORT to LauncherAction.NEXT,
        PressKind.LONG to LauncherAction.NEXT,
        PressKind.REPEAT to LauncherAction.NEXT,
    ),
)

class KeyMapper(private val bindings: KeyBindings) {
    fun actionFor(keyCode: Int, kind: PressKind): LauncherAction = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP -> bindings.volumeUp[kind] ?: LauncherAction.NONE
        KeyEvent.KEYCODE_VOLUME_DOWN -> bindings.volumeDown[kind] ?: LauncherAction.NONE
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> LauncherAction.SELECT
        KeyEvent.KEYCODE_BACK -> LauncherAction.BACK
        else -> LauncherAction.NONE
    }
}
