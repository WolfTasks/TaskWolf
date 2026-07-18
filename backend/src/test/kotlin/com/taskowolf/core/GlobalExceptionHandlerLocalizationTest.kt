package com.taskowolf.core

import com.taskowolf.core.infrastructure.GlobalExceptionHandler
import com.taskowolf.core.infrastructure.LocalizedMessages
import com.taskowolf.core.infrastructure.NotFoundException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.context.support.ResourceBundleMessageSource
import java.util.Locale

class GlobalExceptionHandlerLocalizationTest {

    private val handler = GlobalExceptionHandler(
        LocalizedMessages(ResourceBundleMessageSource().apply {
            setBasename("messages"); setDefaultEncoding("UTF-8"); setFallbackToSystemLocale(false)
        })
    )

    @AfterEach fun reset() = LocaleContextHolder.resetLocaleContext()

    @Test
    fun `keyed NotFound resolves to german under german locale`() {
        LocaleContextHolder.setLocale(Locale.GERMAN)
        val body = handler.handleNotFound(NotFoundException("issue.notFound", "PROJ-1")).body!!
        assertEquals("NOT_FOUND", body.code)
        assertEquals("Vorgang PROJ-1 nicht gefunden", body.message)
    }

    @Test
    fun `free-text NotFound is returned verbatim (backward compat)`() {
        LocaleContextHolder.setLocale(Locale.GERMAN)
        val body = handler.handleNotFound(NotFoundException("Some literal message")).body!!
        assertEquals("Some literal message", body.message)
    }
}
