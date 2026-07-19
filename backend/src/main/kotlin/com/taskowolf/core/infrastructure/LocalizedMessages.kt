package com.taskowolf.core.infrastructure

import com.taskowolf.auth.domain.User
import org.springframework.context.MessageSource
import org.springframework.context.NoSuchMessageException
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.stereotype.Component
import java.util.Locale

@Component
class LocalizedMessages(private val messageSource: MessageSource) {

    /** Resolve against the current request locale (LocaleContextHolder). */
    fun get(key: String, vararg args: Any?): String =
        try {
            messageSource.getMessage(key, args, LocaleContextHolder.getLocale())
        } catch (e: NoSuchMessageException) {
            key
        }

    /** Resolve against an explicit locale (async: email / notification). */
    fun get(key: String, locale: Locale, vararg args: Any?): String =
        try {
            messageSource.getMessage(key, args, locale)
        } catch (e: NoSuchMessageException) {
            messageSource.getMessage(key, args, Locale.ENGLISH)
        }

    /** The recipient's stored language, defaulting to English. */
    fun localeOf(user: User): Locale =
        user.language?.takeIf { it.isNotBlank() }?.let(Locale::forLanguageTag) ?: Locale.ENGLISH
}
