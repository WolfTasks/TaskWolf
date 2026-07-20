package com.taskowolf.i18n

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.context.support.ResourceBundleMessageSource
import java.util.Locale

class ContentMessagesTest {
    private val src = ResourceBundleMessageSource().apply {
        setBasename("messages"); setDefaultEncoding("UTF-8"); setFallbackToSystemLocale(false)
    }
    private fun en(key: String, vararg a: Any?) = src.getMessage(key, a, Locale.ENGLISH)
    private fun de(key: String, vararg a: Any?) = src.getMessage(key, a, Locale.GERMAN)

    @Test fun `content keys render en and de`() {
        assertEquals("Custom field 'Severity' already exists in this project", en("customField.alreadyExists", "Severity"))
        assertEquals("Benutzerdefiniertes Feld 'Severity' existiert bereits in diesem Projekt", de("customField.alreadyExists", "Severity"))
        assertEquals("Label 'bug' already exists in this project", en("label.alreadyExists", "bug"))
        assertEquals("Label 'bug' existiert bereits in diesem Projekt", de("label.alreadyExists", "bug"))
        assertEquals("File not found: a.pdf", en("attachment.fileNotFound", "a.pdf"))
        assertEquals("Datei nicht gefunden: a.pdf", de("attachment.fileNotFound", "a.pdf"))
        assertEquals("Cannot delete this comment", en("comment.cannotDelete"))
        assertEquals("Dieser Kommentar kann nicht gelöscht werden", de("comment.cannotDelete"))
        assertEquals("Unknown notification type: sla", en("notification.unknownType", "sla"))
        assertEquals("Unbekannter Benachrichtigungstyp: sla", de("notification.unknownType", "sla"))
        assertEquals("Custom field not found: 5", en("customField.notFound", 5))
        assertEquals("Benutzerdefiniertes Feld nicht gefunden: 5", de("customField.notFound", 5))
        assertEquals("Option not found: 7", en("customField.optionNotFound", 7))   // reuse — key defined in Task 8
        assertEquals("Option nicht gefunden: 7", de("customField.optionNotFound", 7))
        assertEquals("Label not found: 9", en("label.notFound", 9))
        assertEquals("Label nicht gefunden: 9", de("label.notFound", 9))
        assertEquals("Version not found: 3", en("version.notFound", 3))
        assertEquals("Version nicht gefunden: 3", de("version.notFound", 3))
        assertEquals("Comment not found: 2", en("comment.notFound", 2))
        assertEquals("Kommentar nicht gefunden: 2", de("comment.notFound", 2))
        assertEquals("Attachment not found: 4", en("attachment.notFound", 4))
        assertEquals("Anhang nicht gefunden: 4", de("attachment.notFound", 4))
    }
}
