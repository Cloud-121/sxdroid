package io.github.sxdroid.menu

import io.github.sxdroid.commands.Command

data class CommandMenu(val id: String, val title: String, val commands: List<Command>)

class MenuNavigator(private val root: CommandMenu, private val menus: Map<String, CommandMenu>) {
    private val stack = mutableListOf(root.id)
    val current: CommandMenu get() = menus.getValue(stack.last())
    val depth: Int get() = stack.size

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
}
