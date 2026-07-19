package com.taskowolf.i18n

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.context.support.ResourceBundleMessageSource
import java.util.Locale

class WorkflowMessagesTest {
    private val src = ResourceBundleMessageSource().apply {
        setBasename("messages"); setDefaultEncoding("UTF-8"); setFallbackToSystemLocale(false)
    }
    private fun en(key: String, vararg a: Any?) = src.getMessage(key, a, Locale.ENGLISH)
    private fun de(key: String, vararg a: Any?) = src.getMessage(key, a, Locale.GERMAN)

    @Test fun `workflow keys render en and de with doubled quotes and two args`() {
        assertEquals("Transition from 'TODO' to status 5 is not allowed", en("workflow.transitionNotAllowed", "TODO", 5))
        assertEquals("Übergang von 'TODO' zu Status 5 ist nicht erlaubt", de("workflow.transitionNotAllowed", "TODO", 5))
        assertEquals("Transition blocked: field 'assignee' is required", en("workflow.transitionFieldRequired", "assignee"))
        assertEquals("Übergang blockiert: Feld 'assignee' ist erforderlich", de("workflow.transitionFieldRequired", "assignee"))
        assertEquals("Transition blocked: role 'VIEWER' not permitted", en("workflow.transitionRoleNotPermitted", "VIEWER"))
        assertEquals("Übergang blockiert: Rolle 'VIEWER' nicht erlaubt", de("workflow.transitionRoleNotPermitted", "VIEWER"))
        assertEquals("Status not found: 9", en("workflow.statusNotFound", 9))
        assertEquals("Status nicht gefunden: 9", de("workflow.statusNotFound", 9))
        assertEquals("Transition not found: 4", en("workflow.transitionNotFound", 4))
        assertEquals("Übergang nicht gefunden: 4", de("workflow.transitionNotFound", 4))
        assertEquals("No TODO status in workflow 7", en("workflow.noTodoStatus", 7))
        assertEquals("Kein TODO-Status im Workflow 7", de("workflow.noTodoStatus", 7))
        assertEquals("No workflow for project 3", en("workflow.noneForProject", 3))
        assertEquals("Kein Workflow für Projekt 3", de("workflow.noneForProject", 3))
        assertEquals("Workflow not found: 12", en("workflow.notFound", 12))
        assertEquals("Workflow nicht gefunden: 12", de("workflow.notFound", 12))
    }
}
