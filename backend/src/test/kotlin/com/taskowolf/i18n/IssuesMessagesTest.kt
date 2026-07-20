package com.taskowolf.i18n

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.context.support.ResourceBundleMessageSource
import java.util.Locale

class IssuesMessagesTest {
    private val src = ResourceBundleMessageSource().apply {
        setBasename("messages"); setDefaultEncoding("UTF-8"); setFallbackToSystemLocale(false)
    }
    private fun en(key: String, vararg a: Any?) = src.getMessage(key, a, Locale.ENGLISH)
    private fun de(key: String, vararg a: Any?) = src.getMessage(key, a, Locale.GERMAN)

    @Test fun `issue keys render en and de`() {
        assertEquals("Parent issue not found: PROJ-1", en("issue.parentNotFound", "PROJ-1"))
        assertEquals("Übergeordneter Vorgang nicht gefunden: PROJ-1", de("issue.parentNotFound", "PROJ-1"))
        // reuse P1 keys — render "Issue {0} not found" / "Assignee {0} not found" word order
        assertEquals("Issue PROJ-2 not found", en("issue.notFound", "PROJ-2"))
        assertEquals("Vorgang PROJ-2 nicht gefunden", de("issue.notFound", "PROJ-2"))
        assertEquals("Assignee 5 not found", en("issue.assigneeNotFound", 5))
        assertEquals("Bearbeiter 5 nicht gefunden", de("issue.assigneeNotFound", 5))
        // reuse Task 4 keys
        assertEquals("Sprint not found: 8", en("sprint.notFound", 8))
        assertEquals("Sprint nicht gefunden: 8", de("sprint.notFound", 8))
        assertEquals("Issue not found", en("issue.notFoundGeneric"))
        assertEquals("Vorgang nicht gefunden", de("issue.notFoundGeneric"))
        // folded-in extras
        assertEquals("Status does not belong to project's workflow", en("issue.statusNotInWorkflow"))
        assertEquals("Status gehört nicht zum Workflow des Projekts", de("issue.statusNotInWorkflow"))
        assertEquals("Invalid number for field 'Severity': abc", en("customField.invalidNumber", "Severity", "abc"))
        assertEquals("Ungültige Zahl für Feld 'Severity': abc", de("customField.invalidNumber", "Severity", "abc"))
        assertEquals("Invalid date for field 'Due': xx", en("customField.invalidDate", "Due", "xx"))
        assertEquals("Ungültiges Datum für Feld 'Due': xx", de("customField.invalidDate", "Due", "xx"))
        assertEquals("Invalid option ID for field 'Team'", en("customField.invalidOptionId", "Team"))
        assertEquals("Ungültige Options-ID für Feld 'Team'", de("customField.invalidOptionId", "Team"))
        assertEquals("Option not found: 7", en("customField.optionNotFound", 7))
        assertEquals("Option nicht gefunden: 7", de("customField.optionNotFound", 7))
        assertEquals("Required custom field 'Severity' must have a value", en("customField.requiredMissing", "Severity"))
        assertEquals("Pflichtfeld 'Severity' muss einen Wert haben", de("customField.requiredMissing", "Severity"))
    }
}
