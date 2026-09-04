package io.github.sxdroid.commands

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test

class CommandRankerTest {
    private class TestCommand(
        override val id: String,
        override val name: String,
        override val description: String = "",
        override val keywords: List<String> = emptyList(),
    ) : Command {
        override suspend fun execute(context: Context) = Unit
    }

    @Test fun exact_name_ranks_before_prefix_and_substring() {
        val commands = listOf(
            TestCommand("1", "Calculator"),
            TestCommand("2", "Calc"),
            TestCommand("3", "Recalculate"),
        )
        assertEquals(listOf("2", "1", "3"), CommandRanker.rank(commands, "calc").map { it.id })
    }

    @Test fun token_prefix_and_keywords_are_searchable() {
        val commands = listOf(
            TestCommand("1", "System Settings", keywords = listOf("preferences")),
            TestCommand("2", "Wireless", description = "Wi-Fi controls"),
        )
        assertEquals(listOf("1"), CommandRanker.rank(commands, "sys set").map { it.id })
        assertEquals(listOf("1"), CommandRanker.rank(commands, "pref").map { it.id })
        assertEquals(listOf("2"), CommandRanker.rank(commands, "wifi").map { it.id })
    }

    @Test fun blank_query_is_sorted_deterministically() {
        val commands = listOf(TestCommand("z", "Alpha"), TestCommand("a", "Alpha"), TestCommand("b", "Beta"))
        assertEquals(listOf("a", "z", "b"), CommandRanker.rank(commands, " ").map { it.id })
    }
}
