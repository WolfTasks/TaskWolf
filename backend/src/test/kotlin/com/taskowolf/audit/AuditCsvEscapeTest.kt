package com.taskowolf.audit

import com.taskowolf.audit.api.AuditController
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AuditCsvEscapeTest {

    @Test
    fun `escapeCsvCell prefixes formula-injection characters`() {
        assertEquals("'=SUM(A1:B1)", AuditController.escapeCsvCell("=SUM(A1:B1)"))
        assertEquals("'+1234", AuditController.escapeCsvCell("+1234"))
        assertEquals("'-1234", AuditController.escapeCsvCell("-1234"))
        assertEquals("'@user", AuditController.escapeCsvCell("@user"))
        assertEquals("'\tcell", AuditController.escapeCsvCell("\tcell"))
        assertEquals("'\rcell", AuditController.escapeCsvCell("\rcell"))
    }

    @Test
    fun `escapeCsvCell passes safe values through unchanged`() {
        assertEquals("LOGIN_SUCCESS", AuditController.escapeCsvCell("LOGIN_SUCCESS"))
        assertEquals("user@example.com", AuditController.escapeCsvCell("user@example.com"))
        assertEquals("", AuditController.escapeCsvCell(""))
        assertEquals("", AuditController.escapeCsvCell(null))
    }
}
