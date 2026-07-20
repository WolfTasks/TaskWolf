package com.taskowolf.i18n

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.context.support.ResourceBundleMessageSource
import java.util.Locale

class IntegrationMessagesTest {
    private val src = ResourceBundleMessageSource().apply {
        setBasename("messages"); setDefaultEncoding("UTF-8"); setFallbackToSystemLocale(false)
    }
    private fun en(key: String, vararg a: Any?) = src.getMessage(key, a, Locale.ENGLISH)
    private fun de(key: String, vararg a: Any?) = src.getMessage(key, a, Locale.GERMAN)

    @Test fun `integration keys render en and de`() {
        assertEquals("Webhook not found: 3", en("integration.webhookNotFound", 3))
        assertEquals("Webhook nicht gefunden: 3", de("integration.webhookNotFound", 3))
        assertEquals("Unknown provider: slack", en("integration.unknownProvider", "slack"))
        assertEquals("Unbekannter Anbieter: slack", de("integration.unknownProvider", "slack"))
        assertEquals("Integration already exists for GITHUB in project DEMO", en("integration.alreadyExists", "GITHUB", "DEMO"))
        assertEquals("Integration für GITHUB in Projekt DEMO existiert bereits", de("integration.alreadyExists", "GITHUB", "DEMO"))
        assertEquals("Invalid URL: x", en("integration.invalidUrl", "x"))
        assertEquals("Ungültige URL: x", de("integration.invalidUrl", "x"))
        assertEquals("Invalid JSON payload", en("integration.invalidJsonPayload"))
        assertEquals("Ungültige JSON-Nutzlast", de("integration.invalidJsonPayload"))
    }
}
