package com.taskowolf.i18n

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.context.support.ResourceBundleMessageSource
import java.util.Locale

class AgileReportsMessagesTest {
    private val src = ResourceBundleMessageSource().apply {
        setBasename("messages"); setDefaultEncoding("UTF-8"); setFallbackToSystemLocale(false)
    }
    private fun en(key: String, vararg a: Any?) = src.getMessage(key, a, Locale.ENGLISH)
    private fun de(key: String, vararg a: Any?) = src.getMessage(key, a, Locale.GERMAN)

    @Test fun `sprint and report keys render en and de`() {
        assertEquals("Project already has an active sprint", en("sprint.alreadyActive"))
        assertEquals("Projekt hat bereits einen aktiven Sprint", de("sprint.alreadyActive"))
        assertEquals("Cannot assign issues to a closed sprint", en("sprint.cannotAssignClosed"))
        assertEquals("Vorgänge können keinem geschlossenen Sprint zugewiesen werden", de("sprint.cannotAssignClosed"))
        assertEquals("Cannot change sprint dates once sprint is started", en("sprint.cannotChangeDatesStarted"))
        assertEquals("Sprint-Daten können nach dem Start nicht mehr geändert werden", de("sprint.cannotChangeDatesStarted"))
        assertEquals("Sprint does not belong to this project", en("sprint.notInProject"))
        assertEquals("Sprint gehört nicht zu diesem Projekt", de("sprint.notInProject"))
        assertEquals("Dashboard not found", en("report.dashboardNotFound"))
        assertEquals("Dashboard nicht gefunden", de("report.dashboardNotFound"))
        assertEquals("Widget not found: 8", en("report.widgetNotFound", 8))
        assertEquals("Widget nicht gefunden: 8", de("report.widgetNotFound", 8))
        assertEquals("Sprint not found: 42", en("sprint.notFound", 42))
        assertEquals("Sprint nicht gefunden: 42", de("sprint.notFound", 42))
        assertEquals("Issue not found", en("issue.notFoundGeneric"))
        assertEquals("Vorgang nicht gefunden", de("issue.notFoundGeneric"))
    }
}
