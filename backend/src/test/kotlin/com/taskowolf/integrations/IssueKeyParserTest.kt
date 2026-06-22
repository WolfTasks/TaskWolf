package com.taskowolf.integrations

import com.taskowolf.integrations.application.IssueKeyParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IssueKeyParserTest {
    private val parser = IssueKeyParser()

    @Test
    fun `parses single key from commit message`() {
        val keys = parser.parseKeys("fix: resolve WOLF-42 crash on login")
        assertEquals(listOf("WOLF-42"), keys)
    }

    @Test
    fun `parses multiple keys from PR title`() {
        val keys = parser.parseKeys("feat: implement WOLF-10 and WLF-23 feature")
        assertEquals(listOf("WOLF-10", "WLF-23"), keys)
    }

    @Test
    fun `returns empty list when no keys found`() {
        val keys = parser.parseKeys("fix typo in README")
        assertEquals(emptyList<String>(), keys)
    }

    @Test
    fun `ignores lowercase matches`() {
        val keys = parser.parseKeys("wolf-42 is not a key")
        assertEquals(emptyList<String>(), keys)
    }

    @Test
    fun `deduplicates repeated keys`() {
        val keys = parser.parseKeys("WOLF-1 and WOLF-1 again")
        assertEquals(listOf("WOLF-1"), keys)
    }

    @Test
    fun `parses key at start of string`() {
        val keys = parser.parseKeys("WOLF-99: fix bug")
        assertEquals(listOf("WOLF-99"), keys)
    }
}
