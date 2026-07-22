package com.taskowolf.i18n

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.context.support.ResourceBundleMessageSource
import java.util.Locale

class EmailNotificationMessagesTest {
    private val src = ResourceBundleMessageSource().apply {
        setBasename("messages"); setDefaultEncoding("UTF-8"); setFallbackToSystemLocale(false)
    }
    private fun en(key: String, vararg a: Any?) = src.getMessage(key, a, Locale.ENGLISH)
    private fun de(key: String, vararg a: Any?) = src.getMessage(key, a, Locale.GERMAN)

    @Test
    fun `email keys render en and de`() {
        assertEquals("You were mentioned in WOLF-1", en("email.mention.subject", "WOLF-1"))
        assertEquals("Sie wurden in WOLF-1 erwähnt", de("email.mention.subject", "WOLF-1"))
        assertEquals("WOLF-1: My Issue\n\nGreat comment",
            en("email.mention.body", "WOLF-1", "My Issue", "Great comment"))
        assertEquals("WOLF-1: My Issue\n\nGreat comment",
            de("email.mention.body", "WOLF-1", "My Issue", "Great comment"))
        assertEquals("You were assigned to WOLF-1", en("email.assigned.subject", "WOLF-1"))
        assertEquals("Ihnen wurde WOLF-1 zugewiesen", de("email.assigned.subject", "WOLF-1"))
        assertEquals("You have been assigned to: WOLF-1\nMy Issue",
            en("email.assigned.body", "WOLF-1", "My Issue"))
        assertEquals("Sie wurden zugewiesen zu: WOLF-1\nMy Issue",
            de("email.assigned.body", "WOLF-1", "My Issue"))
    }

    @Test
    fun `notification title keys render en and de`() {
        assertEquals("You were mentioned in WOLF-1", en("notification.mention.title", "WOLF-1"))
        assertEquals("Sie wurden in WOLF-1 erwähnt", de("notification.mention.title", "WOLF-1"))
        assertEquals("You were assigned to WOLF-1", en("notification.assigned.title", "WOLF-1"))
        assertEquals("Ihnen wurde WOLF-1 zugewiesen", de("notification.assigned.title", "WOLF-1"))
        assertEquals("Automation: WOLF-1", en("notification.automation.title", "WOLF-1"))
        assertEquals("Automatisierung: WOLF-1", de("notification.automation.title", "WOLF-1"))
    }

    @Test
    fun `incident and sla keys render en and de (issue key + numeric-as-string)`() {
        assertEquals("Incident declared: P1 on issue WOLF-1", en("notification.incident.title", "P1", "WOLF-1"))
        assertEquals("Incident gemeldet: P1 für Vorgang WOLF-1", de("notification.incident.title", "P1", "WOLF-1"))
        assertEquals("A P1 incident has been declared for issue WOLF-1.",
            en("notification.incident.body", "P1", "WOLF-1"))
        assertEquals("Ein P1-Incident wurde für Vorgang WOLF-1 gemeldet.",
            de("notification.incident.body", "P1", "WOLF-1"))
        assertEquals("SLA Breached: WOLF-1", en("notification.slaBreached.title", "WOLF-1"))
        assertEquals("SLA verletzt: WOLF-1", de("notification.slaBreached.title", "WOLF-1"))
        assertEquals("Issue WOLF-1 has exceeded its SLA resolution time of 1440 minutes.",
            en("notification.slaBreached.body", "WOLF-1", "1440"))
        assertEquals("Vorgang WOLF-1 hat seine SLA-Lösungszeit von 1440 Minuten überschritten.",
            de("notification.slaBreached.body", "WOLF-1", "1440"))
    }
}
