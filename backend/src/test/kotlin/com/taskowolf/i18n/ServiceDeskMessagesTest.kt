package com.taskowolf.i18n

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.context.support.ResourceBundleMessageSource
import java.util.Locale

class ServiceDeskMessagesTest {
    private val src = ResourceBundleMessageSource().apply {
        setBasename("messages"); setDefaultEncoding("UTF-8"); setFallbackToSystemLocale(false)
    }
    private fun en(key: String, vararg a: Any?) = src.getMessage(key, a, Locale.ENGLISH)
    private fun de(key: String, vararg a: Any?) = src.getMessage(key, a, Locale.GERMAN)

    @Test fun `servicedesk keys render en and de`() {
        assertEquals("Service desk not enabled for project: DEMO", en("serviceDesk.notEnabled", "DEMO"))
        assertEquals("Servicedesk ist für Projekt DEMO nicht aktiviert", de("serviceDesk.notEnabled", "DEMO"))
        assertEquals("Invalid priority: X. Valid values: CRITICAL, HIGH, MEDIUM, LOW",
            en("serviceDesk.invalidPriority", "X", "CRITICAL, HIGH, MEDIUM, LOW"))
        assertEquals("Ungültige Priorität: X. Gültige Werte: CRITICAL, HIGH, MEDIUM, LOW",
            de("serviceDesk.invalidPriority", "X", "CRITICAL, HIGH, MEDIUM, LOW"))
        assertEquals("Invalid severity 'SEV5'. Must be one of: P1, P2, P3, P4",
            en("incident.invalidSeverity", "SEV5", "P1, P2, P3, P4"))
        assertEquals("Ungültiger Schweregrad 'SEV5'. Muss einer von: P1, P2, P3, P4",
            de("incident.invalidSeverity", "SEV5", "P1, P2, P3, P4"))
    }
}
