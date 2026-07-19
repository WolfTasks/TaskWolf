package com.taskowolf.core.infrastructure

import org.hibernate.validator.messageinterpolation.ResourceBundleMessageInterpolator
import org.springframework.context.MessageSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.validation.beanvalidation.LocaleContextMessageInterpolator
import org.springframework.validation.beanvalidation.MessageSourceResourceBundleLocator
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean
import org.springframework.web.servlet.LocaleResolver
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver
import java.util.Locale

@Configuration
class LocaleConfig {

    @Bean
    fun localeResolver(): LocaleResolver = AcceptHeaderLocaleResolver().apply {
        supportedLocales = listOf(Locale.ENGLISH, Locale.GERMAN)
        setDefaultLocale(Locale.ENGLISH)
    }

    /**
     * Bind Bean-Validation message interpolation to the MessageSource so
     * `{key}` annotation messages resolve through messages_*.properties.
     * Named `defaultValidator` to replace Spring Boot's auto-configured one.
     *
     * IMPORTANT: a plain message-source interpolator interpolates with
     * `Locale.getDefault()`. Wrapping it in `LocaleContextMessageInterpolator`
     * makes interpolation use the *request* locale from `LocaleContextHolder`
     * (populated by the `AcceptHeaderLocaleResolver`), so validation messages
     * are localized per request. The `MessageSourceResourceBundleLocator` routes
     * `{key}` lookups through our `messages_*.properties` catalog.
     */
    @Bean
    fun defaultValidator(messageSource: MessageSource): LocalValidatorFactoryBean {
        val bean = LocalValidatorFactoryBean()
        bean.messageInterpolator = LocaleContextMessageInterpolator(
            ResourceBundleMessageInterpolator(MessageSourceResourceBundleLocator(messageSource))
        )
        return bean
    }
}
