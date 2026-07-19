package com.taskowolf.i18n

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.context.support.ResourceBundleMessageSource
import java.util.Locale

class AuthMessagesTest {
    private val src = ResourceBundleMessageSource().apply {
        setBasename("messages"); setDefaultEncoding("UTF-8"); setFallbackToSystemLocale(false)
    }
    private fun en(key: String, vararg a: Any?) = src.getMessage(key, a, Locale.ENGLISH)
    private fun de(key: String, vararg a: Any?) = src.getMessage(key, a, Locale.GERMAN)

    @Test fun `auth keys render en and de`() {
        assertEquals("Email already registered: a@b.com", en("auth.emailAlreadyRegistered", "a@b.com"))
        assertEquals("E-Mail bereits registriert: a@b.com", de("auth.emailAlreadyRegistered", "a@b.com"))
        assertEquals("Invalid credentials", en("auth.invalidCredentials"))
        assertEquals("Ungültige Anmeldedaten", de("auth.invalidCredentials"))
        assertEquals("Unsupported language", en("auth.unsupportedLanguage"))
        assertEquals("Nicht unterstützte Sprache", de("auth.unsupportedLanguage"))
        assertEquals("API key not found: 7", en("auth.apiKeyNotFound", 7))
        assertEquals("API-Schlüssel nicht gefunden: 7", de("auth.apiKeyNotFound", 7))
        assertEquals("User not found", en("user.notFound"))
        assertEquals("Benutzer nicht gefunden", de("user.notFound"))
    }
}
