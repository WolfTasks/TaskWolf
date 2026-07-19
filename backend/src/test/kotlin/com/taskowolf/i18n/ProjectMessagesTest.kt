package com.taskowolf.i18n

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.context.support.ResourceBundleMessageSource
import java.util.Locale

class ProjectMessagesTest {
    private val src = ResourceBundleMessageSource().apply {
        setBasename("messages"); setDefaultEncoding("UTF-8"); setFallbackToSystemLocale(false)
    }
    private fun en(key: String, vararg a: Any?) = src.getMessage(key, a, Locale.ENGLISH)
    private fun de(key: String, vararg a: Any?) = src.getMessage(key, a, Locale.GERMAN)

    @Test fun `project keys render en and de`() {
        assertEquals("Project not found: DEMO", en("project.notFound", "DEMO"))
        assertEquals("Projekt nicht gefunden: DEMO", de("project.notFound", "DEMO"))
        assertEquals("Not a member of project DEMO", en("project.notMember", "DEMO"))
        assertEquals("Kein Mitglied des Projekts DEMO", de("project.notMember", "DEMO"))
        assertEquals("Cannot change the project owner's role", en("project.cannotChangeOwnerRole"))
        assertEquals("Die Rolle des Projektinhabers kann nicht geändert werden", de("project.cannotChangeOwnerRole"))
        assertEquals("Sie können Ihre eigene Rolle nicht ändern", de("project.cannotChangeOwnRole"))
        assertEquals("Project key already exists: DEMO", en("project.keyExists", "DEMO"))
        assertEquals("Projektschlüssel existiert bereits: DEMO", de("project.keyExists", "DEMO"))
    }
}
