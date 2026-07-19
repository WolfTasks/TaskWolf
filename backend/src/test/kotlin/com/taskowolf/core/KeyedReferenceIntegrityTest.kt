package com.taskowolf.core

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.Properties

/**
 * Static guard: every message key referenced in production code — via
 * `SomeException.keyed("key", ...)` or a Bean-Validation `message = "{key}"` —
 * must exist in the base catalog `messages.properties`. Catches mistyped keys
 * across the whole i18n sweep without needing to hit each throw-site at runtime.
 */
class KeyedReferenceIntegrityTest {

    private fun catalogKeys(): Set<String> {
        val props = Properties()
        this::class.java.classLoader.getResourceAsStream("messages.properties").use {
            requireNotNull(it) { "messages.properties not found on classpath" }
            props.load(it)
        }
        return props.stringPropertyNames()
    }

    private fun sourceRoot(): File {
        // Gradle runs tests with the module dir (backend/) as the working dir.
        val direct = File("src/main/kotlin")
        return if (direct.isDirectory) direct else File("backend/src/main/kotlin")
    }

    @Test
    fun `all keyed and validation message keys exist in the catalog`() {
        val root = sourceRoot()
        assertTrue(root.isDirectory) {
            "sourceRoot() did not resolve to a directory: ${root.absolutePath}"
        }

        val keys = catalogKeys()
        val keyedRef = Regex("""\.keyed\(\s*"([^"]+)"""")
        val validationRef = Regex("""message\s*=\s*"\{([^}]+)}"""")

        val missing = sortedSetOf<String>()
        var referencesScanned = 0
        root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val text = file.readText()
                (keyedRef.findAll(text) + validationRef.findAll(text)).forEach { m ->
                    referencesScanned++
                    val key = m.groupValues[1]
                    if (key !in keys) missing.add("$key  (${file.name})")
                }
            }

        assertTrue(referencesScanned > 0) {
            "No .keyed(...) or message=\"{...}\" references were found under ${root.absolutePath} — " +
                "the gate is not actually scanning anything"
        }
        assertTrue(missing.isEmpty()) { "Referenced message keys missing from messages.properties: $missing" }
    }
}
