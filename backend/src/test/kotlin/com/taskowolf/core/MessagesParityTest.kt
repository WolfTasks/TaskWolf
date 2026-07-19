package com.taskowolf.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Properties

class MessagesParityTest {

    private fun load(name: String): Properties {
        val props = Properties()
        this::class.java.classLoader.getResourceAsStream(name).use {
            requireNotNull(it) { "$name not found on classpath" }
            props.load(it)
        }
        return props
    }

    @Test
    fun `en and de catalogs have identical key sets`() {
        val en = load("messages.properties").stringPropertyNames()
        val de = load("messages_de.properties").stringPropertyNames()

        val missingInDe = (en - de).sorted()
        val missingInEn = (de - en).sorted()

        assertTrue(missingInDe.isEmpty()) { "Keys present in en but missing in de: $missingInDe" }
        assertTrue(missingInEn.isEmpty()) { "Keys present in de but missing in en: $missingInEn" }
        assertEquals(en.size, de.size)
    }

    @Test
    fun `no catalog value is blank`() {
        listOf("messages.properties", "messages_de.properties").forEach { file ->
            val props = load(file)
            props.stringPropertyNames().forEach { key ->
                assertTrue(props.getProperty(key).isNotBlank()) { "$file: '$key' has a blank value" }
            }
        }
    }

    @Test
    fun `en and de use the same placeholder set per key`() {
        val en = load("messages.properties")
        val de = load("messages_de.properties")
        val placeholder = Regex("""\{(\d+)}""")
        fun indices(v: String) = placeholder.findAll(v).map { it.groupValues[1] }.toSortedSet()

        val mismatches = en.stringPropertyNames()
            .filter { de.getProperty(it) != null }
            .filter { indices(en.getProperty(it)) != indices(de.getProperty(it)) }
            .sorted()

        assertTrue(mismatches.isEmpty()) {
            "Keys whose en/de placeholder sets differ: $mismatches"
        }
    }
}
