package io.github.sxdroid.menu

import io.github.sxdroid.commands.Command

enum class SxmoMenuState { TOP_LEVEL, APPS, SYSTEM, CONFIGURATION, HELP }

data class CommandMenu(
    val id: String,
    val title: String,
    val commands: List<Command>,
    val state: SxmoMenuState = SxmoMenuState.TOP_LEVEL,
)

class MenuNavigator(private val root: CommandMenu, private val menus: Map<String, CommandMenu>) {
    private val stack = mutableListOf(root.id)
    val current: CommandMenu get() = menus.getValue(stack.last())
    val depth: Int get() = stack.size
    val state: SxmoMenuState get() = current.state

    fun enter(menuId: String): Boolean {
        if (menus[menuId] == null) return false
        stack += menuId
        return true
    }

    fun back(): Boolean {
        if (stack.size == 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun closeAll(): Boolean {
        if (stack.size == 1) return false
        stack.subList(1, stack.size).clear()
        return true
    }

    fun resetToTopLevel(): Boolean = closeAll()

}
