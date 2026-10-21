package com.taskowolf.integrations

import com.taskowolf.integrations.infrastructure.StringListConverter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** M3 Phase 2: the DB format of webhook event lists must not change with the Jackson 3 switch. */
class StringListConverterTest {

    private val converter = StringListConverter()

    @Test
    fun `writes a compact JSON array`() {
        assertEquals("""["issue.created","issue.updated"]""", converter.convertToDatabaseColumn(listOf("issue.created", "issue.updated")))
    }

    @Test
    fun `writes null and empty as empty array`() {
        assertEquals("[]", converter.convertToDatabaseColumn(null))
        assertEquals("[]", converter.convertToDatabaseColumn(emptyList()))
    }

    @Test
    fun `reads stored arrays and treats blank as empty`() {
        assertEquals(listOf("issue.created", "issue.updated"), converter.convertToEntityAttribute("""["issue.created","issue.updated"]"""))
        assertEquals(emptyList<String>(), converter.convertToEntityAttribute(""))
        assertEquals(emptyList<String>(), converter.convertToEntityAttribute(null))
    }
}
