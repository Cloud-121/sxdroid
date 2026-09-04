package io.github.sxdroid.input

class DualVolumeController(
    private val chordWindowMillis: Long = 300L,
    private val longPressMillis: Long = 500L,
    private val longPressGraceMillis: Long = 75L,
) {
    private var pending: VolumeKey? = null
    private var pendingSince = 0L

    fun onDown(key: VolumeKey, nowMillis: Long, isLongPress: Boolean = false): List<LauncherAction> {
        val actions = expire(nowMillis).toMutableList()
        if (isLongPress && key == VolumeKey.UP && pending == VolumeKey.UP) {
            pending = null
            return actions + LauncherAction.CONTEXT
        }
        if (isLongPress || pending == key) return actions
        if (pending != null && nowMillis - pendingSince <= chordWindowMillis) {
            pending = null
            actions += LauncherAction.SELECT
        } else {
            pending = key
            pendingSince = nowMillis
        }
        return actions
    }

    fun onUp(key: VolumeKey, nowMillis: Long): List<LauncherAction> {
        val actions = expire(nowMillis).toMutableList()
        if (pending == key) { pending = null; actions += key.action() }
        return actions
    }

    fun onTime(nowMillis: Long): List<LauncherAction> = expire(nowMillis)
    fun nextDeadlineMillis(): Long? = pending?.let { pendingSince + if (it == VolumeKey.UP) longPressMillis + longPressGraceMillis else chordWindowMillis }
    fun clear() { pending = null }

    private fun expire(nowMillis: Long): List<LauncherAction> {
        val key = pending ?: return emptyList()
        if (nowMillis < (nextDeadlineMillis() ?: return emptyList())) return emptyList()
        pending = null
        return listOf(key.action())
    }

    private fun VolumeKey.action() = if (this == VolumeKey.UP) LauncherAction.PREVIOUS else LauncherAction.NEXT
}

enum class VolumeKey { UP, DOWN }
