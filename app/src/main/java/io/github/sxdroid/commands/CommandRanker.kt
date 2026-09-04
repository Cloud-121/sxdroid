package io.github.sxdroid.commands

import java.util.Locale

object CommandRanker {
    fun rank(commands: List<Command>, query: String): List<Command> {
        val needle = query.trim().lowercase(Locale.ROOT)
        if (needle.isEmpty()) return commands.withIndex().sortedWith(compareBy<IndexedValue<Command>> { if (it.value is MenuCommand) 0 else 1 }.thenBy { if (it.value is MenuCommand) it.index else 0 }.thenBy { it.value.name.lowercase(Locale.ROOT) }.thenBy { it.value.id }).map { it.value }
        val tokens = needle.split(Regex("\\s+")).filter(String::isNotBlank)
        return commands.mapNotNull { command ->
            score(command, needle, tokens)?.let { it to command }
        }.sortedWith(compareBy<Pair<Int, Command>> { it.first }.thenBy { it.second.name.lowercase(Locale.ROOT) }.thenBy { it.second.id })
            .map { it.second }
    }

    private fun score(command: Command, query: String, tokens: List<String>): Int? {
        val name = command.name.lowercase(Locale.ROOT)
        val keywords = command.keywords.joinToString(" ").lowercase(Locale.ROOT)
        val description = command.description.lowercase(Locale.ROOT)
        val normalizedQuery = query.replace(Regex("[^a-z0-9]+"), "")
        val normalizedName = name.replace(Regex("[^a-z0-9]+"), "")
        val normalizedKeywords = keywords.replace(Regex("[^a-z0-9]+"), "")
        val normalizedDescription = description.replace(Regex("[^a-z0-9]+"), "")
        return when {
            name == query -> 0
            name.startsWith(query) -> 10
            tokenPrefix(name, tokens) -> 20
            keywords.startsWith(query) || tokenPrefix(keywords, tokens) -> 30
            description.startsWith(query) || tokenPrefix(description, tokens) -> 40
            normalizedName.contains(normalizedQuery) || normalizedKeywords.contains(normalizedQuery) || normalizedDescription.contains(normalizedQuery) -> 45
            name.contains(query) -> 50
            keywords.contains(query) -> 60
            description.contains(query) -> 70
            else -> null
        }
    }

    private fun tokenPrefix(text: String, queryTokens: List<String>): Boolean {
        val words = text.split(Regex("[^a-z0-9]+"))
        return queryTokens.all { token -> words.any { it.startsWith(token) } }
    }

    private fun byName() = compareBy<Command> { it.name.lowercase(Locale.ROOT) }.thenBy { it.id }
}
