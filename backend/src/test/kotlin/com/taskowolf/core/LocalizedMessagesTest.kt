package com.taskowolf.core

import com.taskowolf.core.infrastructure.LocalizedMessages
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.context.support.ResourceBundleMessageSource
import java.util.Locale

class LocalizedMessagesTest {

    private fun messages(): LocalizedMessages {
        val source = ResourceBundleMessageSource().apply {
            setBasename("messages")
            setDefaultEncoding("UTF-8")
            setFallbackToSystemLocale(false)
        }
        return LocalizedMessages(source)
    }

    @Test
    fun `resolves key with args in explicit german locale`() {
        assertEquals("Vorgang PROJ-1 nicht gefunden",
            messages().get("issue.notFound", Locale.GERMAN, "PROJ-1"))
    }

    @Test
    fun `resolves key in english`() {
        assertEquals("Issue PROJ-1 not found",
            messages().get("issue.notFound", Locale.ENGLISH, "PROJ-1"))
    }

    @Test
    fun `unknown locale falls back to english`() {
        assertEquals("Issue X not found",
            messages().get("issue.notFound", Locale.FRENCH, "X"))
    }
}
