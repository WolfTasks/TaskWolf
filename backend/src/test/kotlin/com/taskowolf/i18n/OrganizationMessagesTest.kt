package com.taskowolf.i18n

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.context.support.ResourceBundleMessageSource
import java.util.Locale

class OrganizationMessagesTest {
    private val src = ResourceBundleMessageSource().apply {
        setBasename("messages"); setDefaultEncoding("UTF-8"); setFallbackToSystemLocale(false)
    }
    private fun en(key: String, vararg a: Any?) = src.getMessage(key, a, Locale.ENGLISH)
    private fun de(key: String, vararg a: Any?) = src.getMessage(key, a, Locale.GERMAN)

    @Test fun `org and automation keys render en and de`() {
        assertEquals("Only an owner or system admin can grant the OWNER role", en("org.ownerRoleGrantRestricted"))
        assertEquals("Nur ein Inhaber oder Systemadministrator kann die OWNER-Rolle vergeben", de("org.ownerRoleGrantRestricted"))
        assertEquals("Cannot demote the last owner", en("org.cannotDemoteLastOwner"))
        assertEquals("Der letzte Inhaber kann nicht herabgestuft werden", de("org.cannotDemoteLastOwner"))
        assertEquals("Rule not found: 4", en("automation.ruleNotFound", 4))
        assertEquals("Regel nicht gefunden: 4", de("automation.ruleNotFound", 4))
        assertEquals("Slug must be lowercase alphanumeric with hyphens", en("org.slug.pattern"))
        assertEquals("Slug muss aus Kleinbuchstaben, Ziffern und Bindestrichen bestehen", de("org.slug.pattern"))
        assertEquals("Member not found", en("org.memberNotFound"))
        assertEquals("Mitglied nicht gefunden", de("org.memberNotFound"))
        assertEquals("Organization not found: acme", en("org.notFound", "acme"))
        assertEquals("Organisation nicht gefunden: acme", de("org.notFound", "acme"))
        assertEquals("Not a member of this organization", en("org.notMemberCurrent"))
        assertEquals("Kein Mitglied dieser Organisation", de("org.notMemberCurrent"))
        assertEquals("Auto-provisioning is disabled", en("auth.autoProvisionDisabled"))
        assertEquals("Automatische Bereitstellung ist deaktiviert", de("auth.autoProvisionDisabled"))
    }
}
