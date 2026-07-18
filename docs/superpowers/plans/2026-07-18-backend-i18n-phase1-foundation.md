# Backend-i18n Phase 1 — Fundament + Pilot (`issues`) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish the server-side `MessageSource` localization foundation (en/de) and prove it end-to-end by localizing the `issues` module's error messages, resolved per request via `Accept-Language`.

**Architecture:** Spring `MessageSource` with `messages_en/de.properties` (en = default/fallback). Request locale from an `AcceptHeaderLocaleResolver` (frontend sends its active i18next language via an axios interceptor). Domain exceptions gain a backward-compatible `LocalizedException` key path; the `GlobalExceptionHandler` resolves keys against the request locale. Bean-Validation is wired to the same `MessageSource`. An en/de key-parity JUnit test guards the catalog in CI.

**Tech Stack:** Kotlin, Spring Boot, Gradle (`./gradlew`), JUnit 5 + MockK + Testcontainers (backend); React + axios + i18next (frontend).

## Global Constraints

- Languages: **en** and **de** only. **en is the default/fallback** (`fallbackToSystemLocale=false`, `defaultLocale=en`).
- `ErrorResponse.code` values (`NOT_FOUND`, `FORBIDDEN`, `CONFLICT`, `BAD_REQUEST`, `VALIDATION_ERROR`, `INTERNAL_ERROR`) are **stable and machine-readable — never change them**. Only `message`/`details` get localized.
- **Backward compatibility:** the existing free-text exception constructors MUST keep working; unmigrated throw-sites continue to return their English free text. No big-bang.
- Message keys are **namespaced by domain**: `common.*`, `issue.*`, `project.*`, … Placeholders are positional `{0}`, `{1}` (`java.text.MessageFormat` style).
- Backend tests run from the `backend/` directory with `./gradlew`. Frontend has **no test framework** — its verification is `npm run build` (tsc) + a described manual check.
- TDD: write the failing test first, watch it fail, implement minimally, watch it pass, commit.

---

## File Structure

**Backend (create):**
- `backend/src/main/resources/messages_en.properties` — English catalog (master)
- `backend/src/main/resources/messages_de.properties` — German catalog
- `backend/src/main/kotlin/com/taskowolf/core/infrastructure/LocaleConfig.kt` — `localeResolver` bean + validator wiring
- `backend/src/main/kotlin/com/taskowolf/core/infrastructure/LocalizedMessages.kt` — resolution helper
- `backend/src/test/kotlin/com/taskowolf/core/LocalizedMessagesTest.kt`
- `backend/src/test/kotlin/com/taskowolf/core/GlobalExceptionHandlerLocalizationTest.kt`
- `backend/src/test/kotlin/com/taskowolf/core/MessagesParityTest.kt`
- `backend/src/test/kotlin/com/taskowolf/projects/ProjectValidationLocalizationTest.kt`
- `backend/src/test/kotlin/com/taskowolf/issues/IssueErrorLocalizationTest.kt`

**Backend (modify):**
- `backend/src/main/resources/application.yml` — `spring.messages`
- `backend/src/main/kotlin/com/taskowolf/core/infrastructure/GlobalExceptionHandler.kt` — `LocalizedException` + resolution
- `backend/src/main/kotlin/com/taskowolf/projects/api/dto/CreateProjectRequest.kt` — validation message → key
- `backend/src/main/kotlin/com/taskowolf/issues/application/IssueService.kt` — 3 throw-sites → keys

**Frontend (modify):**
- `frontend/src/api/client.ts` — `Accept-Language` request interceptor

---

## Task 1: MessageSource config + catalog scaffolding

**Files:**
- Modify: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/resources/messages_en.properties`
- Create: `backend/src/main/resources/messages_de.properties`

**Interfaces:**
- Produces: a Spring `MessageSource` bean (Boot autoconfig from `spring.messages`) with basename `messages`, UTF-8, no system-locale fallback. Catalog keys `common.*` (see below).

- [ ] **Step 1: Add `spring.messages` to `application.yml`**

Under the existing top-level `spring:` block in `backend/src/main/resources/application.yml`, add:

```yaml
  messages:
    basename: messages
    encoding: UTF-8
    fallback-to-system-locale: false
```

- [ ] **Step 2: Create `messages_en.properties`**

```properties
# --- common (GlobalExceptionHandler fallbacks) ---
common.notFound=Resource not found
common.forbidden=Access denied
common.conflict=Conflict
common.badRequest=Bad request
common.validationFailed=Validation failed
common.malformedBody=Malformed or invalid request body
common.dataConflict=A data conflict occurred
common.internalError=An unexpected error occurred

# --- issue (pilot) ---
issue.notFound=Issue {0} not found
issue.assigneeNotFound=Assignee {0} not found
project.noWorkflow=Project has no workflow

# --- validation ---
project.key.pattern=Key must be uppercase letters and digits
```

- [ ] **Step 3: Create `messages_de.properties`**

```properties
# --- common (GlobalExceptionHandler fallbacks) ---
common.notFound=Ressource nicht gefunden
common.forbidden=Zugriff verweigert
common.conflict=Konflikt
common.badRequest=Ungültige Anfrage
common.validationFailed=Validierung fehlgeschlagen
common.malformedBody=Ungültiger oder fehlerhafter Request-Body
common.dataConflict=Ein Datenkonflikt ist aufgetreten
common.internalError=Ein unerwarteter Fehler ist aufgetreten

# --- issue (pilot) ---
issue.notFound=Vorgang {0} nicht gefunden
issue.assigneeNotFound=Bearbeiter {0} nicht gefunden
project.noWorkflow=Projekt hat keinen Workflow

# --- validation ---
project.key.pattern=Schlüssel muss aus Großbuchstaben und Ziffern bestehen
```

> `.properties` files are ISO-8859-1; non-ASCII MUST be `\uXXXX`-escaped (`ü`=`ü`, `ß`=`ß`, `Ö`=`Ö`).

- [ ] **Step 4: Verify the app context still boots**

Run: `cd backend && ./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/application.yml backend/src/main/resources/messages_en.properties backend/src/main/resources/messages_de.properties
git commit -m "feat(i18n): add MessageSource catalog scaffolding (en/de)"
```

---

## Task 2: `LocalizedMessages` helper

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/core/infrastructure/LocalizedMessages.kt`
- Test: `backend/src/test/kotlin/com/taskowolf/core/LocalizedMessagesTest.kt`

**Interfaces:**
- Consumes: Spring `MessageSource` (Task 1), `com.taskowolf.auth.domain.User`.
- Produces:
  - `LocalizedMessages.get(key: String, vararg args: Any?): String` — resolves against `LocaleContextHolder.getLocale()`
  - `LocalizedMessages.get(key: String, locale: Locale, vararg args: Any?): String`
  - `LocalizedMessages.localeOf(user: User): Locale`

- [ ] **Step 1: Write the failing test**

`backend/src/test/kotlin/com/taskowolf/core/LocalizedMessagesTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.core.LocalizedMessagesTest"`
Expected: FAIL — `LocalizedMessages` does not exist (compilation error).

- [ ] **Step 3: Write minimal implementation**

`backend/src/main/kotlin/com/taskowolf/core/infrastructure/LocalizedMessages.kt`:

```kotlin
package com.taskowolf.core.infrastructure

import com.taskowolf.auth.domain.User
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.stereotype.Component
import java.util.Locale

@Component
class LocalizedMessages(private val messageSource: MessageSource) {

    /** Resolve against the current request locale (LocaleContextHolder). */
    fun get(key: String, vararg args: Any?): String =
        messageSource.getMessage(key, args, LocaleContextHolder.getLocale())

    /** Resolve against an explicit locale (async: email / notification). */
    fun get(key: String, locale: Locale, vararg args: Any?): String =
        messageSource.getMessage(key, args, locale)

    /** The recipient's stored language, defaulting to English. */
    fun localeOf(user: User): Locale =
        user.language?.takeIf { it.isNotBlank() }?.let(Locale::forLanguageTag) ?: Locale.ENGLISH
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.core.LocalizedMessagesTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/core/infrastructure/LocalizedMessages.kt backend/src/test/kotlin/com/taskowolf/core/LocalizedMessagesTest.kt
git commit -m "feat(i18n): add LocalizedMessages resolution helper"
```

---

## Task 3: `LocalizedException` + `GlobalExceptionHandler` resolution

**Files:**
- Modify: `backend/src/main/kotlin/com/taskowolf/core/infrastructure/GlobalExceptionHandler.kt`
- Test: `backend/src/test/kotlin/com/taskowolf/core/GlobalExceptionHandlerLocalizationTest.kt`

**Interfaces:**
- Consumes: `LocalizedMessages` (Task 2).
- Produces:
  - `interface LocalizedException { val messageKey: String?; val args: Array<out Any?> }`
  - The four exceptions (`NotFoundException`, `ForbiddenException`, `ConflictException`, `BadRequestException`) implement `LocalizedException` with **two** constructors: `(message: String)` (free-text, unchanged behavior) and `(messageKey: String, vararg args: Any?)`.
  - `GlobalExceptionHandler` becomes a Spring bean taking `LocalizedMessages` via constructor.

- [ ] **Step 1: Write the failing test**

`backend/src/test/kotlin/com/taskowolf/core/GlobalExceptionHandlerLocalizationTest.kt`. This is a slice test using the real `MessageSource` catalog and `LocaleContextHolder`:

```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.core.GlobalExceptionHandlerLocalizationTest"`
Expected: FAIL — `GlobalExceptionHandler` has no constructor taking `LocalizedMessages`; `NotFoundException` has no `(key, vararg)` constructor.

- [ ] **Step 3: Rewrite `GlobalExceptionHandler.kt`**

Replace the file contents with:

```kotlin
package com.taskowolf.core.infrastructure

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/** Exceptions that can carry a MessageSource key + args instead of free text. */
interface LocalizedException {
    val messageKey: String?
    val args: Array<out Any?>
}

class NotFoundException : RuntimeException, LocalizedException {
    override val messageKey: String?
    override val args: Array<out Any?>
    constructor(message: String) : super(message) { messageKey = null; args = emptyArray() }
    constructor(messageKey: String, vararg args: Any?) : super(messageKey) { this.messageKey = messageKey; this.args = args }
}

class ForbiddenException : RuntimeException, LocalizedException {
    override val messageKey: String?
    override val args: Array<out Any?>
    constructor(message: String) : super(message) { messageKey = null; args = emptyArray() }
    constructor(messageKey: String, vararg args: Any?) : super(messageKey) { this.messageKey = messageKey; this.args = args }
}

class ConflictException : RuntimeException, LocalizedException {
    override val messageKey: String?
    override val args: Array<out Any?>
    constructor(message: String) : super(message) { messageKey = null; args = emptyArray() }
    constructor(messageKey: String, vararg args: Any?) : super(messageKey) { this.messageKey = messageKey; this.args = args }
}

class BadRequestException : RuntimeException, LocalizedException {
    override val messageKey: String?
    override val args: Array<out Any?>
    constructor(message: String) : super(message) { messageKey = null; args = emptyArray() }
    constructor(messageKey: String, vararg args: Any?) : super(messageKey) { this.messageKey = messageKey; this.args = args }
}

@RestControllerAdvice
class GlobalExceptionHandler(private val messages: LocalizedMessages) {

    /** Key path → resolve against request locale; else the exception's own message; else the fallback key. */
    private fun resolve(ex: RuntimeException, fallbackKey: String): String {
        val loc = ex as? LocalizedException
        return loc?.messageKey?.let { messages.get(it, *loc.args) } ?: ex.message ?: messages.get(fallbackKey)
    }

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(ex: NotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse("NOT_FOUND", resolve(ex, "common.notFound")))

    @ExceptionHandler(ForbiddenException::class)
    fun handleForbidden(ex: ForbiddenException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse("FORBIDDEN", resolve(ex, "common.forbidden")))

    @ExceptionHandler(ConflictException::class)
    fun handleConflict(ex: ConflictException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse("CONFLICT", resolve(ex, "common.conflict")))

    @ExceptionHandler(BadRequestException::class)
    fun handleBadRequest(ex: BadRequestException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse("BAD_REQUEST", resolve(ex, "common.badRequest")))

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse("BAD_REQUEST", ex.message ?: messages.get("common.badRequest")))

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadable(ex: HttpMessageNotReadableException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse("BAD_REQUEST", messages.get("common.malformedBody")))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val details = ex.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "invalid") }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse("VALIDATION_ERROR", messages.get("common.validationFailed"), details))
    }

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolation(ex: DataIntegrityViolationException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse("CONFLICT", messages.get("common.dataConflict")))

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(ex: AccessDeniedException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse("FORBIDDEN", ex.message ?: messages.get("common.forbidden")))

    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception) =
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse("INTERNAL_ERROR", messages.get("common.internalError")))
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.core.GlobalExceptionHandlerLocalizationTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Run the full backend suite (no regressions from the exception refactor)**

Run: `cd backend && ./gradlew test`
Expected: BUILD SUCCESSFUL — existing tests still green (free-text constructors unchanged).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/core/infrastructure/GlobalExceptionHandler.kt backend/src/test/kotlin/com/taskowolf/core/GlobalExceptionHandlerLocalizationTest.kt
git commit -m "feat(i18n): resolve keyed exceptions via MessageSource in GlobalExceptionHandler"
```

---

## Task 4: Wire Bean-Validation to MessageSource + migrate one message

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/core/infrastructure/LocaleConfig.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/projects/api/dto/CreateProjectRequest.kt`
- Test: `backend/src/test/kotlin/com/taskowolf/projects/ProjectValidationLocalizationTest.kt`

**Interfaces:**
- Consumes: Spring `MessageSource` (Task 1). Endpoint `POST /api/v1/projects` with body `{"key","name","description?"}`.
- Produces: a `LocalValidatorFactoryBean` named `defaultValidator` bound to the `MessageSource` (overrides Boot's auto-configured validator by type + name), so `@Pattern(message="{key}")` resolves through the catalog in the request locale.

- [ ] **Step 1: Write the failing integration test**

`backend/src/test/kotlin/com/taskowolf/projects/ProjectValidationLocalizationTest.kt`:

```kotlin
package com.taskowolf.projects

import com.fasterxml.jackson.databind.ObjectMapper
import com.taskowolf.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class ProjectValidationLocalizationTest : IntegrationTestBase() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper

    private fun register(email: String): String {
        val res = mockMvc.perform(
            post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","displayName":"User","password":"password123"}""")
        ).andReturn()
        return objectMapper.readTree(res.response.contentAsString).get("accessToken").asText()
    }

    @Test
    fun `invalid project key pattern returns localized german validation message`() {
        val token = register("proj-val-de@test.com")
        mockMvc.perform(
            post("/api/v1/projects").header("Authorization", "Bearer $token")
                .header("Accept-Language", "de")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"key":"lowercase","name":"Demo"}""")
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.details.key").value("Schlüssel muss aus Großbuchstaben und Ziffern bestehen"))
    }

    @Test
    fun `invalid project key pattern returns english by default`() {
        val token = register("proj-val-en@test.com")
        mockMvc.perform(
            post("/api/v1/projects").header("Authorization", "Bearer $token")
                .header("Accept-Language", "en")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"key":"lowercase","name":"Demo"}""")
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.details.key").value("Key must be uppercase letters and digits"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.projects.ProjectValidationLocalizationTest"`
Expected: FAIL — message is still the literal `"Key must be uppercase letters and digits"` even under `Accept-Language: de` (validator not bound to MessageSource; annotation not keyed).

- [ ] **Step 3: Add the validator wiring**

`backend/src/main/kotlin/com/taskowolf/core/infrastructure/LocaleConfig.kt`:

```kotlin
package com.taskowolf.core.infrastructure

import org.hibernate.validator.messageinterpolation.ResourceBundleMessageInterpolator
import org.springframework.context.MessageSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.support.MessageSourceResourceBundleLocator
import org.springframework.validation.beanvalidation.LocaleContextMessageInterpolator
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
```

- [ ] **Step 4: Key the annotation message**

In `backend/src/main/kotlin/com/taskowolf/projects/api/dto/CreateProjectRequest.kt`, change the `@Pattern` message:

```kotlin
    @field:Pattern(regexp = "[A-Z0-9]+", message = "{project.key.pattern}")
```

(`project.key.pattern` already exists in both catalogs from Task 1.)

- [ ] **Step 5: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.projects.ProjectValidationLocalizationTest"`
Expected: PASS (2 tests).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/core/infrastructure/LocaleConfig.kt backend/src/main/kotlin/com/taskowolf/projects/api/dto/CreateProjectRequest.kt backend/src/test/kotlin/com/taskowolf/projects/ProjectValidationLocalizationTest.kt
git commit -m "feat(i18n): wire Bean-Validation to MessageSource + localize project key pattern"
```

---

## Task 5: Frontend `Accept-Language` interceptor

**Files:**
- Modify: `frontend/src/api/client.ts`

**Interfaces:**
- Consumes: the i18next instance default-exported from `@/i18n` (`i18n.language` is the active UI language, e.g. `"de"`).
- Produces: every `apiClient` request carries `Accept-Language: <i18n.language>`.

- [ ] **Step 1: Add the header in the existing request interceptor**

In `frontend/src/api/client.ts`, add the import and set the header inside the existing request interceptor:

```ts
import axios from 'axios'
import i18n from '@/i18n'

export const apiClient = axios.create({ baseURL: '/api/v1' })

apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken')
  if (token) config.headers.Authorization = `Bearer ${token}`
  if (i18n.language) config.headers['Accept-Language'] = i18n.language
  return config
})
```

(Leave the response interceptor unchanged.)

- [ ] **Step 2: Typecheck + build (frontend has no test framework)**

Run: `cd frontend && npm run build`
Expected: `tsc` passes and Vite build succeeds (no type errors from the `@/i18n` import).

- [ ] **Step 3: Manual verification (describe, do not automate)**

With the app running, switch UI language to Deutsch, trigger a failing request (e.g. open a non-existent issue), and confirm in the browser Network tab that the request sends `Accept-Language: de` and the error text is German. (Backend pilot lands in Task 6; until then the header is simply sent and honored by the foundation.)

- [ ] **Step 4: Commit**

```bash
git add frontend/src/api/client.ts
git commit -m "feat(i18n): send active UI language as Accept-Language header"
```

---

## Task 6: Pilot — localize `issues` throw-sites end-to-end

**Files:**
- Modify: `backend/src/main/kotlin/com/taskowolf/issues/application/IssueService.kt`
- Test: `backend/src/test/kotlin/com/taskowolf/issues/IssueErrorLocalizationTest.kt`

**Interfaces:**
- Consumes: keyed `NotFoundException` (Task 3); catalog keys `issue.notFound`, `issue.assigneeNotFound`, `project.noWorkflow` (Task 1). Endpoints `POST /api/v1/projects`, `GET /api/v1/projects/{key}/issues/{issueKey}`.

- [ ] **Step 1: Write the failing integration test**

`backend/src/test/kotlin/com/taskowolf/issues/IssueErrorLocalizationTest.kt`:

```kotlin
package com.taskowolf.issues

import com.fasterxml.jackson.databind.ObjectMapper
import com.taskowolf.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class IssueErrorLocalizationTest : IntegrationTestBase() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper

    private fun register(email: String): String {
        val res = mockMvc.perform(
            post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","displayName":"User","password":"password123"}""")
        ).andReturn()
        return objectMapper.readTree(res.response.contentAsString).get("accessToken").asText()
    }

    private fun createProject(token: String, key: String) {
        mockMvc.perform(
            post("/api/v1/projects").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"key":"$key","name":"Demo"}""")
        ).andExpect(status().isCreated)
    }

    @Test
    fun `unknown issue returns localized german message`() {
        val token = register("issue-de@test.com")
        createProject(token, "PILOT")
        mockMvc.perform(
            get("/api/v1/projects/PILOT/issues/PILOT-999")
                .header("Authorization", "Bearer $token")
                .header("Accept-Language", "de")
        ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("Vorgang PILOT-999 nicht gefunden"))
    }

    @Test
    fun `unknown issue returns english by default`() {
        val token = register("issue-en@test.com")
        createProject(token, "PILOU")
        mockMvc.perform(
            get("/api/v1/projects/PILOU/issues/PILOU-999")
                .header("Authorization", "Bearer $token")
                .header("Accept-Language", "en")
        ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("Issue PILOU-999 not found"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.issues.IssueErrorLocalizationTest"`
Expected: FAIL — the German case gets the English literal `"Issue not found: PILOT-999"` (throw-site not yet keyed).

- [ ] **Step 3: Key the three `issues` throw-sites**

In `backend/src/main/kotlin/com/taskowolf/issues/application/IssueService.kt`, replace the free-text messages:

- Line ~46: `?: throw NotFoundException("Project has no workflow")`
  → `?: throw NotFoundException("project.noWorkflow")`
- Line ~293 (in `findByKey`): `?: throw NotFoundException("Issue not found: $issueKey")`
  → `?: throw NotFoundException("issue.notFound", issueKey)`
- Line ~395: `throw NotFoundException("Assignee not found: $assigneeId")`
  → `throw NotFoundException("issue.assigneeNotFound", assigneeId)`

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.issues.IssueErrorLocalizationTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Run the full backend suite**

Run: `cd backend && ./gradlew test`
Expected: BUILD SUCCESSFUL (no regression; other tests that asserted the old English literal, if any, were updated — search `Issue not found:` in tests if a failure appears).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/issues/application/IssueService.kt backend/src/test/kotlin/com/taskowolf/issues/IssueErrorLocalizationTest.kt
git commit -m "feat(i18n): localize issues module error messages (pilot)"
```

---

## Task 7: en/de parity CI gate

**Files:**
- Test: `backend/src/test/kotlin/com/taskowolf/core/MessagesParityTest.kt`

**Interfaces:**
- Consumes: `messages_en.properties`, `messages_de.properties` from the classpath (Task 1). No production code.

- [ ] **Step 1: Write the test (it should pass immediately if catalogs are in parity)**

`backend/src/test/kotlin/com/taskowolf/core/MessagesParityTest.kt`:

```kotlin
package com.taskowolf.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Properties

class MessagesParityTest {

    private fun load(name: String): Properties {
        val props = Properties()
        this::class.java.classLoader.getResourceAsStream(name).use {
            requireNotNull(it) { "$name not found on classpath" }
            props.load(it)
        }
        return props
    }

    @Test
    fun `en and de catalogs have identical key sets`() {
        val en = load("messages_en.properties").stringPropertyNames()
        val de = load("messages_de.properties").stringPropertyNames()

        val missingInDe = (en - de).sorted()
        val missingInEn = (de - en).sorted()

        assertTrue(missingInDe.isEmpty()) { "Keys present in en but missing in de: $missingInDe" }
        assertTrue(missingInEn.isEmpty()) { "Keys present in de but missing in en: $missingInEn" }
        assertEquals(en.size, de.size)
    }

    @Test
    fun `no catalog value is blank`() {
        listOf("messages_en.properties", "messages_de.properties").forEach { file ->
            val props = load(file)
            props.stringPropertyNames().forEach { key ->
                assertTrue(props.getProperty(key).isNotBlank()) { "$file: '$key' has a blank value" }
            }
        }
    }
}
```

- [ ] **Step 2: Run the test**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.core.MessagesParityTest"`
Expected: PASS (2 tests). This test now runs automatically inside the `backend-test` CI job — no workflow change needed.

- [ ] **Step 3: Prove the gate bites (temporary negative check)**

Temporarily add a line `zzz.temp.only=en side` to `messages_en.properties`, then run the parity test:

Run: `cd backend && ./gradlew test --tests "com.taskowolf.core.MessagesParityTest"`
Expected: FAIL with `Keys present in en but missing in de: [zzz.temp.only]`.
Then remove the temporary line and re-run → PASS. (Do not commit the temporary line.)

- [ ] **Step 4: Commit**

```bash
git add backend/src/test/kotlin/com/taskowolf/core/MessagesParityTest.kt
git commit -m "test(i18n): en/de message catalog parity gate"
```

---

## Final verification

- [ ] **Full backend suite green:** `cd backend && ./gradlew test` → BUILD SUCCESSFUL.
- [ ] **Frontend builds:** `cd frontend && npm run build` → passes.
- [ ] **Manual smoke:** run the app (see `reference-local-docker-run`), switch UI to Deutsch, open a non-existent issue → error text is German; switch to English → English. Confirm `Accept-Language` in the Network tab.

## Notes for Phase 2/3 (not in scope here)

- Phase 2: sweep the remaining ~81 throw-sites and validation messages onto keys, module by module (each module = its own keyed catalog section + a localization test).
- Phase 3: `EmailService` + `NotificationService` (incl. the 3 `createDirect` callers) render in the **recipient's** `user.language` via `LocalizedMessages.get(key, localeOf(user), args…)`.
