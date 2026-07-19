# Backend-i18n Phase 2 — API-error sweep Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Localize the remaining **~130 free-text, user-facing exception sites across all five throw constructs** (not just `throw X("…")`) plus the one remaining custom Bean-Validation message onto Spring `MessageSource` keys (en default, de), reusing Phase 1's `.keyed()` factory, catalog, validator wiring, and parity gate — and **fix latent 500-instead-of-404/403 bugs** (six 500→404 in `servicedesk`, two 500→404 in `organizations`, one 500→403 in the OIDC path) along the way.

> **Scope-correction note (2026-07-19):** The original inventory grepped only `throw X("…")` and found 81 sites. Executing Session 1 (auth, shipped in **PR #84** — not affected by this correction) surfaced that user-facing free-text errors are thrown via **five** constructs, four of which the original grep missed: `throw X("…")`, `.orElseThrow { X("…") }`, `?: error("…")` / `check {…}`, `throw ResponseStatusException(status, "…")`, and `throw AccessDeniedException("…")`. The corrected remaining surface is **~130 sites plus two modules absent from the original plan (`issues`, `servicedesk`)** — including eight sites discovered during plan review (an `OrganizationService.findById` `NoSuchElementException` and seven `IssueService` free-text throws) that Wolfgang folded into scope on 2026-07-19. The authoritative per-site index is **`docs/superpowers/specs/2026-07-19-backend-i18n-phase2-scope-correction.md`** — this plan implements it. Tasks 2–7 gain an **"orElseThrow / error / AccessDenied sites"** subsection; Tasks 8 (`issues`) and 9 (`servicedesk`) are new.

**Architecture:** A mechanical sweep applying the Phase 1 pattern module-by-module. First, three cross-cutting hardening changes (Task 0): a typo safety net in `LocalizedMessages`, a placeholder-parity check in the gate, and a source-scanning gate that asserts every `.keyed(...)`/`message="{…}"` key referenced in code exists in the catalog (this auto-guards every later slice). Then module slices (Tasks 1–9), each adding a keyed catalog section (en + de) + swapping that module's error sites to `Exception.keyed("key", args…)` + a data-driven resolution test, kept green by the parity + reference gates. **Constructs 3–4 (`error()`/`check()`/`ResponseStatusException`) are also status-correctness fixes:** an unhandled `IllegalStateException` currently returns **500**; converting it to a keyed `NotFoundException`/`BadRequestException` both localizes it AND fixes the HTTP status (Decision 3 requires a status-change test at every such site). `ErrorResponse.code` values never change.

**Tech Stack:** Kotlin, Spring Boot, Gradle (`./gradlew`), JUnit 5 + MockK + Testcontainers (Postgres). Backend-only — no frontend changes.

## Global Constraints

- **Prerequisite gate:** Phase 1 (PR #83) MUST be merged to `main` before starting. This plan is built on a fresh `main` containing: base `messages.properties` (English master — **there is NO `messages_en.properties`**) + `messages_de.properties`; `LocalizedMessages`; `LocalizedException` + `.keyed(key, vararg args)` factory on `NotFoundException`/`ForbiddenException`/`ConflictException`/`BadRequestException`; `GlobalExceptionHandler`; `LocaleConfig`; `MessagesParityTest`.
- **Always throw keyed errors via the `.keyed()` factory** — `NotFoundException("some.key")` binds to the free-text `(message: String)` constructor and leaks the raw key. Correct: `NotFoundException.keyed("some.key", arg0, arg1)`.
- `ErrorResponse.code` values (`NOT_FOUND`, `FORBIDDEN`, `CONFLICT`, `BAD_REQUEST`, `VALIDATION_ERROR`, `INTERNAL_ERROR`) are **stable and machine-readable — never change them.** Only `message`/`details` get localized.
- **Languages: en (default/fallback) and de only.** English lives in the base bundle `messages.properties`.
- **`.properties` files are ISO-8859-1: every non-ASCII char in `messages_de.properties` MUST be `\uXXXX`-escaped.** Reference: `ä`=`ä`, `ö`=`ö`, `ü`=`ü`, `ß`=`ß`, `Ä`=`Ä`, `Ö`=`Ö`, `Ü`=`Ü`.
- **MessageFormat single-quote rule (critical):** a message resolved *with arguments* is run through `java.text.MessageFormat`, where a single quote `'` is an escape character. A literal `'{0}'` would render as the literal text `{0}` (no substitution). **Any literal single quote in an arg-bearing message must be doubled `''`** — in BOTH en and de. Messages resolved with no args are not MessageFormat-processed, so their apostrophes stay single. All catalog lines below already follow this rule.
- Keys are namespaced by domain: `auth.*`, `project.*`, `workflow.*`, `sprint.*`, `report.*`, `integration.*`, `org.*`, `customField.*`, `label.*`, `version.*`, `comment.*`, `attachment.*`, `notification.*`, `automation.*`.
- TDD: failing test first → watch it fail → minimal implementation → watch it pass → commit. Backend tests run from `backend/` via `./gradlew`. Frequent commits.
- Each module slice ships as its **own worktree branch + PR**. **Session 1 (Task 0 + Task 1 auth) already shipped in PR #84.** Approved session grouping for the four remaining sessions (Wolfgang, 2026-07-19):
  - **Session 2** = Task 2 (projects + boards) + Task 3 (workflow) — incl. orElseThrow sites.
  - **Session 3** = Task 4 (agile-reports) + Task 8 (issues). **Order matters:** Task 4 defines `sprint.notFound` and `issue.notFoundGeneric`, which Task 8 reuses — run Task 4 first.
  - **Session 4** = Task 5 (integrations) + Task 6 (orgs — incl. `error()` `org.notFound`, the `AccessDeniedException`, `org.memberNotFound`, `AuthController` `ResponseStatusException`, and the OIDC `check` `auth.autoProvisionDisabled` follow-up) + Task 7 (content — incl. `attachment/comment/customField/label/version` `.notFound`).
  - **Session 5** = Task 9 (servicedesk) — the 6× 500→404 fixes + status-change tests.

### Approved decisions (Wolfgang, 2026-07-19)

1. **Sprint-not-found unification:** ONE key `sprint.notFound = "Sprint not found: {0}"` — pass the in-scope `sprintId` at ALL four sites (`ReportsService:32,72`, `SprintService:115`, `IssueService:170`). No separate no-arg `sprint.notFoundWithId`. Matches the codebase's `*.notFound` id-in-message convention.
2. **`auth.autoProvisionDisabled`:** INCLUDE as a keyed `ForbiddenException` (403) for `OidcUserProvisioningService:31` `check`. Still SKIP the two internal invariants (`SsoController:35` `clientSecret required`, `OidcUserProvisioningService:28` `OIDC user has no email`) and the two webhook `SecurityException`s — those are not user-facing.
3. **Status-change tests:** every construct-3/4 conversion that changes the HTTP status (the 6× 500→404 in servicedesk; the 2× 500→404 in `OrganizationService.findBySlug`/`findById`; the OIDC 500→403) gets a test asserting the NEW status + localized body. `ResponseStatusException`→keyed conversions that keep their status (the servicedesk 400s, `AuthController` 403) still get a test asserting status + `$.code` + localized body. Grep existing tests for any assertion of the old 500/RSE behavior and update it (the current servicedesk test dir has no web-layer tests, so none exist to update).
4. **No new exception handler needed** provided every user-facing `error()`/`check()` site is converted to a keyed exception. The sweep must leave **no user-facing `IllegalStateException`** — see Final verification.

### Out of scope (leave as-is)

- `IncomingWebhookService` `SecurityException` ×2 (webhook signature/token) — security internals.
- `SsoController:35` `clientSecret required`, `OidcUserProvisioningService:28` `OIDC user has no email` — internal invariants.
- `log.error(...)` in `EmailIngestionService` — logging, not an exception.

> **Folded into scope (Wolfgang, 2026-07-19):** the two items discovered during plan review are now in scope — `OrganizationService.findById:33` `NoSuchElementException` (Task 6, a second 500→404 fix) and the seven `IssueService` free-text throws `:184, :312, :357, :361, :367, :371, :386` (Task 8). They are no longer flags; their swaps are itemized in those tasks.

---

## File Structure

**Backend (modify — catalogs, both grow every slice):**
- `backend/src/main/resources/messages.properties` — English master
- `backend/src/main/resources/messages_de.properties` — German

**Backend (modify — Task 0 hardening):**
- `backend/src/main/kotlin/com/taskowolf/core/infrastructure/LocalizedMessages.kt`
- `backend/src/test/kotlin/com/taskowolf/core/MessagesParityTest.kt`
- Create: `backend/src/test/kotlin/com/taskowolf/core/KeyedReferenceIntegrityTest.kt`

**Backend (modify — one service group per slice):**
- Task 1 (auth): `AuthService.kt`, `RefreshTokenService.kt`, `UserAccountService.kt`, `AccessTokenService.kt`, `ApiKeyService.kt`, `UserSearchController.kt`
- Task 2 (projects): `ProjectService.kt`, `BoardService.kt`
- Task 3 (workflow): `WorkflowService.kt`
- Task 4 (agile-reports): `SprintService.kt`, `ReportsService.kt`, `DashboardService.kt`
- Task 5 (integrations): `WebhookService.kt`, `ProjectIntegrationService.kt`, `SsrfValidator.kt`, `IncomingWebhookService.kt` (+ `SsrfValidatorTest.kt`)
- Task 6 (organizations): `OrganizationService.kt`, `AutomationService.kt`, `AdminAutomationController.kt`, `organizations/api/dto/CreateOrganizationRequest.kt`, **`auth/api/AuthController.kt`** (RSE follow-up), **`auth/application/OidcUserProvisioningService.kt`** (OIDC `check` follow-up)
- Task 7 (content): `CustomFieldService.kt`, `LabelService.kt`, `VersionService.kt`, `CommentService.kt`, `AttachmentService.kt`, `StorageService.kt`, `NotificationService.kt`, `NotificationPreferenceController.kt`
- Task 8 (issues): `issues/application/IssueService.kt`
- Task 9 (servicedesk): `servicedesk/api/ServiceDeskController.kt`, `servicedesk/api/IncidentController.kt`

**Backend (create — tests, one resolution test per slice + one e2e in Task 1):**
- `backend/src/test/kotlin/com/taskowolf/i18n/AuthMessagesTest.kt` (+ `AuthErrorLocalizationTest.kt`, e2e)
- `backend/src/test/kotlin/com/taskowolf/i18n/ProjectMessagesTest.kt`
- `backend/src/test/kotlin/com/taskowolf/i18n/WorkflowMessagesTest.kt`
- `backend/src/test/kotlin/com/taskowolf/i18n/AgileReportsMessagesTest.kt`
- `backend/src/test/kotlin/com/taskowolf/i18n/IntegrationMessagesTest.kt`
- `backend/src/test/kotlin/com/taskowolf/i18n/OrganizationMessagesTest.kt`
- `backend/src/test/kotlin/com/taskowolf/i18n/ContentMessagesTest.kt`
- `backend/src/test/kotlin/com/taskowolf/i18n/IssuesMessagesTest.kt` (Task 8)
- `backend/src/test/kotlin/com/taskowolf/i18n/ServiceDeskMessagesTest.kt` (Task 9 — resolution)
- `backend/src/test/kotlin/com/taskowolf/servicedesk/ServiceDeskErrorLocalizationTest.kt` (Task 9 — web-layer status-change guard)
- `backend/src/test/kotlin/com/taskowolf/organizations/OrgSwitchLocalizationTest.kt` (Task 6 — `AuthController` RSE→403 guard)

> **Resolution-test pattern (used by every slice).** A fast, no-Spring-context test that loads the real catalog via `ResourceBundleMessageSource` and asserts each new key renders the exact English and German text (exercising `\uXXXX` decoding, placeholder substitution, and the MessageFormat quote-doubling). Each slice writes one such file. The shared shape:
> ```kotlin
> package com.taskowolf.i18n
>
> import org.junit.jupiter.api.Assertions.assertEquals
> import org.junit.jupiter.api.Test
> import org.springframework.context.support.ResourceBundleMessageSource
> import java.util.Locale
>
> class XxxMessagesTest {
>     private val src = ResourceBundleMessageSource().apply {
>         setBasename("messages"); setDefaultEncoding("UTF-8"); setFallbackToSystemLocale(false)
>     }
>     private fun en(key: String, vararg a: Any?) = src.getMessage(key, a, Locale.ENGLISH)
>     private fun de(key: String, vararg a: Any?) = src.getMessage(key, a, Locale.GERMAN)
>
>     @Test fun `renders english and german`() {
>         // one assertEquals(en, en("key", args)) + one assertEquals(de, de("key", args)) per key
>     }
> }
> ```

---

## Task 0: Cross-cutting hardening

**Files:**
- Modify: `backend/src/main/kotlin/com/taskowolf/core/infrastructure/LocalizedMessages.kt`
- Modify: `backend/src/test/kotlin/com/taskowolf/core/MessagesParityTest.kt`
- Create: `backend/src/test/kotlin/com/taskowolf/core/KeyedReferenceIntegrityTest.kt`

**Interfaces:**
- Produces: `LocalizedMessages.get(key, vararg args)` never throws `NoSuchMessageException` (returns the key on miss). `MessagesParityTest` gains a placeholder-parity test. `KeyedReferenceIntegrityTest` fails if any `.keyed("k")` or `message = "{k}"` in `src/main/kotlin` references a key absent from `messages.properties`.

- [ ] **Step 1: Write the failing test for the typo safety net**

Add to `backend/src/test/kotlin/com/taskowolf/core/LocalizedMessagesTest.kt` (created in Phase 1):

```kotlin
    @Test
    fun `request-locale get returns the key itself when the key is unknown (no throw)`() {
        assertEquals("nonexistent.key.zzz", messages().get("nonexistent.key.zzz"))
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.core.LocalizedMessagesTest"`
Expected: FAIL — `NoSuchMessageException` propagates from the unguarded request-locale overload.

- [ ] **Step 3: Add the safety net to the request-locale overload**

In `backend/src/main/kotlin/com/taskowolf/core/infrastructure/LocalizedMessages.kt`, replace the request-locale `get`:

```kotlin
    /** Resolve against the current request locale (LocaleContextHolder). */
    fun get(key: String, vararg args: Any?): String =
        try {
            messageSource.getMessage(key, args, LocaleContextHolder.getLocale())
        } catch (e: NoSuchMessageException) {
            key
        }
```

(`NoSuchMessageException` is already imported in this file from Phase 1.)

- [ ] **Step 4: Run it to verify it passes**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.core.LocalizedMessagesTest"`
Expected: PASS.

- [ ] **Step 5: Add the placeholder-parity test**

Append to `backend/src/test/kotlin/com/taskowolf/core/MessagesParityTest.kt` (inside the class):

```kotlin
    @Test
    fun `en and de use the same placeholder set per key`() {
        val en = load("messages.properties")
        val de = load("messages_de.properties")
        val placeholder = Regex("""\{(\d+)}""")
        fun indices(v: String) = placeholder.findAll(v).map { it.groupValues[1] }.toSortedSet()

        val mismatches = en.stringPropertyNames()
            .filter { de.getProperty(it) != null }
            .filter { indices(en.getProperty(it)) != indices(de.getProperty(it)) }
            .sorted()

        assertTrue(mismatches.isEmpty()) {
            "Keys whose en/de placeholder sets differ: $mismatches"
        }
    }
```

- [ ] **Step 6: Run the parity test (passes on the current catalog)**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.core.MessagesParityTest"`
Expected: PASS (3 tests).

- [ ] **Step 7: Write the keyed-reference integrity gate**

`backend/src/test/kotlin/com/taskowolf/core/KeyedReferenceIntegrityTest.kt`:

```kotlin
package com.taskowolf.core

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.Properties

/**
 * Static guard: every message key referenced in production code — via
 * `SomeException.keyed("key", ...)` or a Bean-Validation `message = "{key}"` —
 * must exist in the base catalog `messages.properties`. Catches mistyped keys
 * across the whole i18n sweep without needing to hit each throw-site at runtime.
 */
class KeyedReferenceIntegrityTest {

    private fun catalogKeys(): Set<String> {
        val props = Properties()
        this::class.java.classLoader.getResourceAsStream("messages.properties").use {
            requireNotNull(it) { "messages.properties not found on classpath" }
            props.load(it)
        }
        return props.stringPropertyNames()
    }

    private fun sourceRoot(): File {
        // Gradle runs tests with the module dir (backend/) as the working dir.
        val direct = File("src/main/kotlin")
        return if (direct.isDirectory) direct else File("backend/src/main/kotlin")
    }

    @Test
    fun `all keyed and validation message keys exist in the catalog`() {
        val keys = catalogKeys()
        val keyedRef = Regex("""\.keyed\(\s*"([^"]+)"""")
        val validationRef = Regex("""message\s*=\s*"\{([^}]+)}"""")

        val missing = sortedSetOf<String>()
        sourceRoot().walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val text = file.readText()
                (keyedRef.findAll(text) + validationRef.findAll(text)).forEach { m ->
                    val key = m.groupValues[1]
                    if (key !in keys) missing.add("$key  (${file.name})")
                }
            }

        assertTrue(missing.isEmpty()) { "Referenced message keys missing from messages.properties: $missing" }
    }
}
```

- [ ] **Step 8: Run the integrity gate (passes on the Phase-1 baseline)**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.core.KeyedReferenceIntegrityTest"`
Expected: PASS — the only `.keyed(...)`/`{…}` references so far are Phase 1's `issue.*`, `project.noWorkflow`, `project.key.pattern`, all present.

- [ ] **Step 9: Prove the integrity gate bites (temporary negative check)**

Temporarily add `throw NotFoundException.keyed("zzz.does.not.exist")` to any service method, run the gate, confirm it FAILS naming `zzz.does.not.exist`, then remove the line and re-run → PASS. (Do not commit the temporary line.)

- [ ] **Step 10: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/core/infrastructure/LocalizedMessages.kt \
        backend/src/test/kotlin/com/taskowolf/core/LocalizedMessagesTest.kt \
        backend/src/test/kotlin/com/taskowolf/core/MessagesParityTest.kt \
        backend/src/test/kotlin/com/taskowolf/core/KeyedReferenceIntegrityTest.kt
git commit -m "feat(i18n): harden MessageSource — typo safety net + placeholder & keyed-reference gates"
```

---

## Task 1: `auth` slice (15 sites) + end-to-end guard

**Files:**
- Modify catalogs; `auth/application/AuthService.kt`, `RefreshTokenService.kt`, `UserAccountService.kt`, `AccessTokenService.kt`, `ApiKeyService.kt`, `auth/api/UserSearchController.kt`
- Create: `backend/src/test/kotlin/com/taskowolf/i18n/AuthMessagesTest.kt`, `backend/src/test/kotlin/com/taskowolf/i18n/AuthErrorLocalizationTest.kt`

**Interfaces:**
- Consumes: `.keyed()` factory (Phase 1). Endpoint `POST /api/v1/auth/register`.

- [ ] **Step 1: Add English keys to `messages.properties`**

Append:

```properties
# --- auth ---
auth.registrationDisabled=Registration is disabled
auth.emailAlreadyRegistered=Email already registered: {0}
auth.invalidCredentials=Invalid credentials
auth.accountDisabled=Account is disabled
auth.invalidRefreshToken=Invalid refresh token
auth.cannotReactivateDeleted=Cannot reactivate a deleted account
auth.noPasswordSet=This account has no password set
auth.currentPasswordIncorrect=Current password is incorrect
auth.unsupportedLanguage=Unsupported language
auth.lastAdmin=Cannot deactivate or delete the last active admin
auth.accessTokenNotFound=Access token not found: {0}
auth.apiKeyNotFound=API key not found: {0}
auth.searchNotAllowed=Not allowed to search users
```

- [ ] **Step 2: Add German keys to `messages_de.properties`** (`\uXXXX`-escaped)

The German is shown below with readable umlauts, but you MUST write it into the ISO-8859-1 `.properties` file with every non-ASCII char `\uXXXX`-escaped (see the escape table in Global Constraints — e.g. `Ungültige`→`Ungültige`, `Für`→`Für`, `gelöscht`→`gelöscht`, `Schlüssel`→`Schlüssel`). This is self-correcting: `AuthMessagesTest` (Step 3) asserts the real umlaut strings, so if you paste raw umlauts into the ISO-8859-1 file they decode as mojibake and the test fails. The same rule applies to every German block in Tasks 2–7.

Append:

```properties
# --- auth ---
auth.registrationDisabled=Registrierung ist deaktiviert
auth.emailAlreadyRegistered=E-Mail bereits registriert: {0}
auth.invalidCredentials=Ungültige Anmeldedaten
auth.accountDisabled=Konto ist deaktiviert
auth.invalidRefreshToken=Ungültiges Aktualisierungstoken
auth.cannotReactivateDeleted=Ein gelöschtes Konto kann nicht reaktiviert werden
auth.noPasswordSet=Für dieses Konto ist kein Passwort festgelegt
auth.currentPasswordIncorrect=Das aktuelle Passwort ist falsch
auth.unsupportedLanguage=Nicht unterstützte Sprache
auth.lastAdmin=Der letzte aktive Administrator kann nicht deaktiviert oder gelöscht werden
auth.accessTokenNotFound=Zugriffstoken nicht gefunden: {0}
auth.apiKeyNotFound=API-Schlüssel nicht gefunden: {0}
auth.searchNotAllowed=Benutzersuche nicht erlaubt
```

- [ ] **Step 3: Write the resolution test (failing until keys exist — they now do; but write test first per TDD)**

`backend/src/test/kotlin/com/taskowolf/i18n/AuthMessagesTest.kt` (use the resolution-test shape from File Structure):

```kotlin
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
    }
}
```

- [ ] **Step 4: Run it (should pass once Steps 1–2 are in)**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.i18n.AuthMessagesTest"`
Expected: PASS.

- [ ] **Step 5: Swap the auth throw-sites to keyed exceptions**

Exact replacements (message text → `.keyed(...)`):

`AuthService.kt`:
- `throw ForbiddenException("Registration is disabled")` → `throw ForbiddenException.keyed("auth.registrationDisabled")`
- `throw ConflictException("Email already registered: ${request.email}")` → `throw ConflictException.keyed("auth.emailAlreadyRegistered", request.email)`
- `throw ForbiddenException("Invalid credentials")` → `throw ForbiddenException.keyed("auth.invalidCredentials")`
- `throw ForbiddenException("Account is disabled")` → `throw ForbiddenException.keyed("auth.accountDisabled")`
- `throw ForbiddenException("Invalid refresh token")` → `throw ForbiddenException.keyed("auth.invalidRefreshToken")`

`RefreshTokenService.kt` (both occurrences):
- `throw ForbiddenException("Invalid refresh token")` → `throw ForbiddenException.keyed("auth.invalidRefreshToken")`

`UserAccountService.kt`:
- `throw ConflictException("Cannot reactivate a deleted account")` → `throw ConflictException.keyed("auth.cannotReactivateDeleted")`
- `throw ConflictException("This account has no password set")` → `throw ConflictException.keyed("auth.noPasswordSet")`
- `throw ForbiddenException("Current password is incorrect")` → `throw ForbiddenException.keyed("auth.currentPasswordIncorrect")`
- `throw BadRequestException("Unsupported language")` → `throw BadRequestException.keyed("auth.unsupportedLanguage")`
- `throw ConflictException("Cannot deactivate or delete the last active admin")` → `throw ConflictException.keyed("auth.lastAdmin")`

`AccessTokenService.kt`:
- `throw NotFoundException("Access token not found: $tokenId")` → `throw NotFoundException.keyed("auth.accessTokenNotFound", tokenId)`

`ApiKeyService.kt`:
- `throw NotFoundException("API key not found: $keyId")` → `throw NotFoundException.keyed("auth.apiKeyNotFound", keyId)`

`UserSearchController.kt`:
- `throw ForbiddenException("Not allowed to search users")` → `throw ForbiddenException.keyed("auth.searchNotAllowed")`

(All six files already import the relevant exception classes; no new imports.)

- [ ] **Step 6: Write the one end-to-end localization guard**

`backend/src/test/kotlin/com/taskowolf/i18n/AuthErrorLocalizationTest.kt` — proves the interceptor→resolver→handler chain still renders localized text after the sweep, using the easiest-to-reach site (duplicate registration → 409):

```kotlin
package com.taskowolf.i18n

import com.taskowolf.IntegrationTestBase
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.junit.jupiter.api.Test

class AuthErrorLocalizationTest : IntegrationTestBase() {

    @Autowired private lateinit var mockMvc: MockMvc

    private fun register(email: String, lang: String) = mockMvc.perform(
        post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
            .header("Accept-Language", lang)
            .content("""{"email":"$email","displayName":"User","password":"password123"}""")
    )

    @Test
    fun `duplicate registration returns localized german conflict`() {
        register("dupe-de@test.com", "en").andExpect(status().isOk)
        register("dupe-de@test.com", "de")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("CONFLICT"))
            .andExpect(jsonPath("$.message").value("E-Mail bereits registriert: dupe-de@test.com"))
    }

    @Test
    fun `duplicate registration returns english by default`() {
        register("dupe-en@test.com", "en").andExpect(status().isOk)
        register("dupe-en@test.com", "en")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("Email already registered: dupe-en@test.com"))
    }
}
```

> If `/api/v1/auth/register` returns 201 rather than 200, adjust `isOk` to `isCreated`; check the existing `AuthControllerIntegrationTest` for the expected status.

- [ ] **Step 7: Run the auth tests**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.i18n.Auth*"`
Expected: PASS.

- [ ] **Step 8: Run the full suite + gates**

Run: `cd backend && ./gradlew test`
Expected: BUILD SUCCESSFUL — existing `AuthServiceTest`/`UserAccountServiceTest`/`RefreshTokenServiceTest` assert exception *type* (unaffected); `KeyedReferenceIntegrityTest` + `MessagesParityTest` green.

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/resources/messages.properties backend/src/main/resources/messages_de.properties \
        backend/src/main/kotlin/com/taskowolf/auth backend/src/test/kotlin/com/taskowolf/i18n/AuthMessagesTest.kt \
        backend/src/test/kotlin/com/taskowolf/i18n/AuthErrorLocalizationTest.kt
git commit -m "feat(i18n): localize auth module error messages"
```

---

## Task 2: `projects` slice (13 sites)

**Files:** catalogs; `projects/application/ProjectService.kt`, `boards/application/BoardService.kt`; create `backend/src/test/kotlin/com/taskowolf/i18n/ProjectMessagesTest.kt`.

**Interfaces:** Consumes `.keyed()`. `BoardService`'s "Project has no workflow" reuses the existing `project.noWorkflow` key (added in Phase 1 — do NOT add a new key).

- [ ] **Step 1: Add English keys to `messages.properties`**

```properties
# --- project ---
project.keyExists=Project key already exists: {0}
project.notFound=Project not found: {0}
project.notMember=Not a member of project {0}
project.adminRequired=Project admin role required
project.alreadyMember=User is already a member of this project
project.cannotChangeOwnRole=You cannot change your own role
project.cannotChangeOwnerRole=Cannot change the project owner's role
project.memberNotFound=Member not found
project.cannotRemoveOwner=Cannot remove the project owner
project.targetOrgAdminRequired=Must be an admin of the target organization
```

- [ ] **Step 2: Add German keys to `messages_de.properties`**

```properties
# --- project ---
project.keyExists=Projektschlüssel existiert bereits: {0}
project.notFound=Projekt nicht gefunden: {0}
project.notMember=Kein Mitglied des Projekts {0}
project.adminRequired=Projekt-Administratorrolle erforderlich
project.alreadyMember=Benutzer ist bereits Mitglied dieses Projekts
project.cannotChangeOwnRole=Sie können Ihre eigene Rolle nicht ändern
project.cannotChangeOwnerRole=Die Rolle des Projektinhabers kann nicht geändert werden
project.memberNotFound=Mitglied nicht gefunden
project.cannotRemoveOwner=Der Projektinhaber kann nicht entfernt werden
project.targetOrgAdminRequired=Administratorrechte für die Zielorganisation erforderlich
```

- [ ] **Step 3: Write the resolution test**

`ProjectMessagesTest.kt` (resolution-test shape). Assertions:

```kotlin
    @Test fun `project keys render en and de`() {
        assertEquals("Project not found: DEMO", en("project.notFound", "DEMO"))
        assertEquals("Projekt nicht gefunden: DEMO", de("project.notFound", "DEMO"))
        assertEquals("Not a member of project DEMO", en("project.notMember", "DEMO"))
        assertEquals("Kein Mitglied des Projekts DEMO", de("project.notMember", "DEMO"))
        assertEquals("Cannot change the project owner's role", en("project.cannotChangeOwnerRole"))
        assertEquals("Die Rolle des Projektinhabers kann nicht geändert werden", de("project.cannotChangeOwnerRole"))
        assertEquals("Sie können Ihre eigene Rolle nicht ändern", de("project.cannotChangeOwnRole"))
    }
```

- [ ] **Step 4: Run it**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.i18n.ProjectMessagesTest"` → PASS.

- [ ] **Step 5: Swap `ProjectService.kt` throw-sites**

- `throw ConflictException("Project key already exists: ${request.key}")` → `throw ConflictException.keyed("project.keyExists", request.key)`
- `throw NotFoundException("Project not found: $key")` → `throw NotFoundException.keyed("project.notFound", key)`
- `throw ForbiddenException("Not a member of project $projectKey")` → `throw ForbiddenException.keyed("project.notMember", projectKey)`
- `throw ForbiddenException("Project admin role required")` (both occurrences) → `throw ForbiddenException.keyed("project.adminRequired")`
- `throw ConflictException("User is already a member of this project")` → `throw ConflictException.keyed("project.alreadyMember")`
- `throw ForbiddenException("You cannot change your own role")` → `throw ForbiddenException.keyed("project.cannotChangeOwnRole")`
- `throw ForbiddenException("Cannot change the project owner's role")` → `throw ForbiddenException.keyed("project.cannotChangeOwnerRole")`
- `throw NotFoundException("Member not found")` (both occurrences) → `throw NotFoundException.keyed("project.memberNotFound")`
- `throw ForbiddenException("Cannot remove the project owner")` → `throw ForbiddenException.keyed("project.cannotRemoveOwner")`
- `throw ForbiddenException("Must be an admin of the target organization")` → `throw ForbiddenException.keyed("project.targetOrgAdminRequired")`

- [ ] **Step 6: Swap `BoardService.kt`**

- `throw NotFoundException("Project has no workflow")` → `throw NotFoundException.keyed("project.noWorkflow")`

#### orElseThrow / error / AccessDenied sites

Two `.orElseThrow` sites in `ProjectService.kt` reuse keys that already exist — `project.notFound` (added in Step 1 of this task) and `user.notFound` (shipped in PR #84). **No new keys, no catalog or resolution-test changes.** (`ProjectService.kt` already imports `NotFoundException`.)

- [ ] **Step 6b: Swap the two `ProjectService.kt` orElseThrow sites**

- `ProjectService.kt:59` `.orElseThrow { NotFoundException("Project not found: $projectId") }` → `.orElseThrow { NotFoundException.keyed("project.notFound", projectId) }`
- `ProjectService.kt:106` `.orElseThrow { NotFoundException("User not found") }` → `.orElseThrow { NotFoundException.keyed("user.notFound") }`

- [ ] **Step 7: Full suite + gates**

Run: `cd backend && ./gradlew test` → BUILD SUCCESSFUL. (`ProjectServiceTest`, `ProjectMemberIntegrationTest`, etc. assert type/status, not message text.)

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/resources/messages*.properties \
        backend/src/main/kotlin/com/taskowolf/projects backend/src/main/kotlin/com/taskowolf/boards \
        backend/src/test/kotlin/com/taskowolf/i18n/ProjectMessagesTest.kt
git commit -m "feat(i18n): localize projects and boards error messages"
```

---

## Task 3: `workflow` slice (7 sites)

**Files:** catalogs; `workflows/application/WorkflowService.kt`; create `WorkflowMessagesTest.kt`.

**Interfaces:** Consumes `.keyed()`. Note `transitionNotAllowed` takes two args (`{0}`=source status name, `{1}`=target status id) and uses **doubled single quotes** per the MessageFormat rule.

- [ ] **Step 1: English keys**

```properties
# --- workflow ---
workflow.noTodoStatus=No TODO status in workflow {0}
workflow.noneForProject=No workflow for project {0}
workflow.transitionNotAllowed=Transition from ''{0}'' to status {1} is not allowed
workflow.transitionFieldRequired=Transition blocked: field ''{0}'' is required
workflow.transitionRoleNotPermitted=Transition blocked: role ''{0}'' not permitted
workflow.statusNotFound=Status not found: {0}
workflow.transitionNotFound=Transition not found: {0}
```

- [ ] **Step 2: German keys**

```properties
# --- workflow ---
workflow.noTodoStatus=Kein TODO-Status im Workflow {0}
workflow.noneForProject=Kein Workflow für Projekt {0}
workflow.transitionNotAllowed=Übergang von ''{0}'' zu Status {1} ist nicht erlaubt
workflow.transitionFieldRequired=Übergang blockiert: Feld ''{0}'' ist erforderlich
workflow.transitionRoleNotPermitted=Übergang blockiert: Rolle ''{0}'' nicht erlaubt
workflow.statusNotFound=Status nicht gefunden: {0}
workflow.transitionNotFound=Übergang nicht gefunden: {0}
```

- [ ] **Step 3: Resolution test** — `WorkflowMessagesTest.kt`:

```kotlin
    @Test fun `workflow keys render en and de with doubled quotes and two args`() {
        assertEquals("Transition from 'TODO' to status 5 is not allowed", en("workflow.transitionNotAllowed", "TODO", 5))
        assertEquals("Übergang von 'TODO' zu Status 5 ist nicht erlaubt", de("workflow.transitionNotAllowed", "TODO", 5))
        assertEquals("Transition blocked: field 'assignee' is required", en("workflow.transitionFieldRequired", "assignee"))
        assertEquals("Übergang blockiert: Feld 'assignee' ist erforderlich", de("workflow.transitionFieldRequired", "assignee"))
        assertEquals("Status not found: 9", en("workflow.statusNotFound", 9))
        assertEquals("Status nicht gefunden: 9", de("workflow.statusNotFound", 9))
    }
```

- [ ] **Step 4: Run it** → `./gradlew test --tests "com.taskowolf.i18n.WorkflowMessagesTest"` → PASS. (If the assertion shows `{0}` literally instead of `TODO`, the quotes were not doubled — fix the catalog.)

- [ ] **Step 5: Swap `WorkflowService.kt` throw-sites**

- `throw NotFoundException("No TODO status in workflow $workflowId")` → `throw NotFoundException.keyed("workflow.noTodoStatus", workflowId)`
- `throw NotFoundException("No workflow for project $projectId")` → `throw NotFoundException.keyed("workflow.noneForProject", projectId)`
- `throw BadRequestException("Transition from '${issue.status.name}' to status $toStatusId is not allowed")` → `throw BadRequestException.keyed("workflow.transitionNotAllowed", issue.status.name, toStatusId)`
- `throw BadRequestException("Transition blocked: field '${guard.field}' is required")` → `throw BadRequestException.keyed("workflow.transitionFieldRequired", guard.field)`
- `throw BadRequestException("Transition blocked: role '$userRole' not permitted")` → `throw BadRequestException.keyed("workflow.transitionRoleNotPermitted", userRole)`
- `throw NotFoundException("Status not found: $statusId")` → `throw NotFoundException.keyed("workflow.statusNotFound", statusId)`
- `throw NotFoundException("Transition not found: $transitionId")` → `throw NotFoundException.keyed("workflow.transitionNotFound", transitionId)`

#### orElseThrow / error / AccessDenied sites

`WorkflowService.kt` has eight `.orElseThrow` sites. Seven reuse the `workflow.statusNotFound` / `workflow.transitionNotFound` keys added in Step 1; three introduce one **new** key `workflow.notFound` (distinct from `workflow.noneForProject`).

- [ ] **Step 5b: Add the `workflow.notFound` key (en + de)**

Append to `messages.properties` (under the `# --- workflow ---` section):

```properties
workflow.notFound=Workflow not found: {0}
```

Append to `messages_de.properties` (under `# --- workflow ---`, `\uXXXX`-escaped — `nicht` has no non-ASCII, so no escape needed here):

```properties
workflow.notFound=Workflow nicht gefunden: {0}
```

- [ ] **Step 5c: Extend `WorkflowMessagesTest.kt` with the new key**

Add to the `@Test fun ...` body:

```kotlin
        assertEquals("Workflow not found: 12", en("workflow.notFound", 12))
        assertEquals("Workflow nicht gefunden: 12", de("workflow.notFound", 12))
```

Run: `cd backend && ./gradlew test --tests "com.taskowolf.i18n.WorkflowMessagesTest"` → PASS.

- [ ] **Step 5d: Swap the eight `WorkflowService.kt` orElseThrow sites**

- `:46` `.orElseThrow { NotFoundException("Status not found: $statusId") }` → `.orElseThrow { NotFoundException.keyed("workflow.statusNotFound", statusId) }`
- `:51` `.orElseThrow { NotFoundException("Workflow not found: $workflowId") }` → `.orElseThrow { NotFoundException.keyed("workflow.notFound", workflowId) }`
- `:117` `.orElseThrow { NotFoundException("Workflow not found: $workflowId") }` → `.orElseThrow { NotFoundException.keyed("workflow.notFound", workflowId) }`
- `:125` `.orElseThrow { NotFoundException("Status not found: $statusId") }` → `.orElseThrow { NotFoundException.keyed("workflow.statusNotFound", statusId) }`
- `:143` `.orElseThrow { NotFoundException("Workflow not found: $workflowId") }` → `.orElseThrow { NotFoundException.keyed("workflow.notFound", workflowId) }`
- `:145` `.orElseThrow { NotFoundException("Status not found: $toStatusId") }` → `.orElseThrow { NotFoundException.keyed("workflow.statusNotFound", toStatusId) }`
- `:147` `statusRepository.findById(it).orElseThrow { NotFoundException("Status not found: $it") }` → `statusRepository.findById(it).orElseThrow { NotFoundException.keyed("workflow.statusNotFound", it) }`
- `:161` `.orElseThrow { NotFoundException("Transition not found: $transitionId") }` → `.orElseThrow { NotFoundException.keyed("workflow.transitionNotFound", transitionId) }`

- [ ] **Step 6: Full suite + gates** → `cd backend && ./gradlew test` → BUILD SUCCESSFUL. (`WorkflowTransitionGuardTest` asserts `BadRequestException` type.)

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/resources/messages*.properties backend/src/main/kotlin/com/taskowolf/workflows \
        backend/src/test/kotlin/com/taskowolf/i18n/WorkflowMessagesTest.kt
git commit -m "feat(i18n): localize workflow error messages"
```

---

## Task 4: `agile-reports` slice (11 sites)

**Files:** catalogs; `sprints/application/SprintService.kt`, `reports/application/ReportsService.kt`, `reports/application/DashboardService.kt`; create `AgileReportsMessagesTest.kt`.

**Interfaces:** Consumes `.keyed()`. `ReportsService`'s two "Sprint does not belong to this project" reuse `sprint.notInProject`. `DashboardService`'s two "Dashboard not found" reuse `report.dashboardNotFound`.

- [ ] **Step 1: English keys**

```properties
# --- sprint ---
sprint.cannotChangeDatesStarted=Cannot change sprint dates once sprint is started
sprint.notPlanned=Sprint is not in PLANNED state
sprint.alreadyActive=Project already has an active sprint
sprint.notActive=Sprint is not ACTIVE
sprint.cannotAssignClosed=Cannot assign issues to a closed sprint
sprint.issueNotInSprint=Issue is not in this sprint
sprint.notInProject=Sprint does not belong to this project
# --- report ---
report.dashboardNotFound=Dashboard not found
```

- [ ] **Step 2: German keys**

```properties
# --- sprint ---
sprint.cannotChangeDatesStarted=Sprint-Daten können nach dem Start nicht mehr geändert werden
sprint.notPlanned=Sprint ist nicht im Status PLANNED
sprint.alreadyActive=Projekt hat bereits einen aktiven Sprint
sprint.notActive=Sprint ist nicht ACTIVE
sprint.cannotAssignClosed=Vorgänge können keinem geschlossenen Sprint zugewiesen werden
sprint.issueNotInSprint=Vorgang ist nicht in diesem Sprint
sprint.notInProject=Sprint gehört nicht zu diesem Projekt
# --- report ---
report.dashboardNotFound=Dashboard nicht gefunden
```

- [ ] **Step 3: Resolution test** — `AgileReportsMessagesTest.kt`:

```kotlin
    @Test fun `sprint and report keys render en and de`() {
        assertEquals("Project already has an active sprint", en("sprint.alreadyActive"))
        assertEquals("Projekt hat bereits einen aktiven Sprint", de("sprint.alreadyActive"))
        assertEquals("Cannot assign issues to a closed sprint", en("sprint.cannotAssignClosed"))
        assertEquals("Vorgänge können keinem geschlossenen Sprint zugewiesen werden", de("sprint.cannotAssignClosed"))
        assertEquals("Sprint does not belong to this project", en("sprint.notInProject"))
        assertEquals("Sprint gehört nicht zu diesem Projekt", de("sprint.notInProject"))
        assertEquals("Dashboard not found", en("report.dashboardNotFound"))
        assertEquals("Dashboard nicht gefunden", de("report.dashboardNotFound"))
    }
```

- [ ] **Step 4: Run it** → PASS.

- [ ] **Step 5: Swap `SprintService.kt`**

- `throw ConflictException("Cannot change sprint dates once sprint is started")` → `throw ConflictException.keyed("sprint.cannotChangeDatesStarted")`
- `throw ConflictException("Sprint is not in PLANNED state")` → `throw ConflictException.keyed("sprint.notPlanned")`
- `throw ConflictException("Project already has an active sprint")` → `throw ConflictException.keyed("sprint.alreadyActive")`
- `throw ConflictException("Sprint is not ACTIVE")` → `throw ConflictException.keyed("sprint.notActive")`
- `throw ConflictException("Cannot assign issues to a closed sprint")` → `throw ConflictException.keyed("sprint.cannotAssignClosed")`
- `throw ConflictException("Issue is not in this sprint")` → `throw ConflictException.keyed("sprint.issueNotInSprint")`
- `throw ForbiddenException("Sprint does not belong to this project")` → `throw ForbiddenException.keyed("sprint.notInProject")`

- [ ] **Step 6: Swap `ReportsService.kt`** (both occurrences)

- `throw ForbiddenException("Sprint does not belong to this project")` → `throw ForbiddenException.keyed("sprint.notInProject")`

- [ ] **Step 7: Swap `DashboardService.kt`** (both occurrences)

- `throw NotFoundException("Dashboard not found")` → `throw NotFoundException.keyed("report.dashboardNotFound")`

#### orElseThrow / error / AccessDenied sites

Three **new** keys, six `.orElseThrow` swaps. Per **Decision 1**, `sprint.notFound` carries the id at every site (`ReportsService:32,72`, `SprintService:115`, and — in Task 8 — `IssueService:170`). `issue.notFoundGeneric` is the no-arg "Issue not found" (distinct from the P1 `issue.notFound` = "Issue {0} not found"); Task 8 reuses it too.

- [ ] **Step 7b: Add English keys**

Append to `messages.properties`:

```properties
report.widgetNotFound=Widget not found: {0}
sprint.notFound=Sprint not found: {0}
issue.notFoundGeneric=Issue not found
```

- [ ] **Step 7c: Add German keys** (`\uXXXX`-escaped where needed — none of these three have non-ASCII characters)

Append to `messages_de.properties`:

```properties
report.widgetNotFound=Widget nicht gefunden: {0}
sprint.notFound=Sprint nicht gefunden: {0}
issue.notFoundGeneric=Vorgang nicht gefunden
```

- [ ] **Step 7d: Extend `AgileReportsMessagesTest.kt`**

Add to the test body:

```kotlin
        assertEquals("Widget not found: 8", en("report.widgetNotFound", 8))
        assertEquals("Widget nicht gefunden: 8", de("report.widgetNotFound", 8))
        assertEquals("Sprint not found: 42", en("sprint.notFound", 42))
        assertEquals("Sprint nicht gefunden: 42", de("sprint.notFound", 42))
        assertEquals("Issue not found", en("issue.notFoundGeneric"))
        assertEquals("Vorgang nicht gefunden", de("issue.notFoundGeneric"))
```

Run: `cd backend && ./gradlew test --tests "com.taskowolf.i18n.AgileReportsMessagesTest"` → PASS.

- [ ] **Step 7e: Swap the six orElseThrow sites**

`DashboardService.kt`:
- `:36` `.orElseThrow { NotFoundException("Widget not found: ${item.widgetId}") }` → `.orElseThrow { NotFoundException.keyed("report.widgetNotFound", item.widgetId) }`
- `:71` `.orElseThrow { NotFoundException("Widget not found: $widgetId") }` → `.orElseThrow { NotFoundException.keyed("report.widgetNotFound", widgetId) }`

`ReportsService.kt` (both occurrences, var `sprintId` in scope):
- `:32` `.orElseThrow { NotFoundException("Sprint not found") }` → `.orElseThrow { NotFoundException.keyed("sprint.notFound", sprintId) }`
- `:72` `.orElseThrow { NotFoundException("Sprint not found") }` → `.orElseThrow { NotFoundException.keyed("sprint.notFound", sprintId) }`

`SprintService.kt`:
- `:97` `.orElseThrow { NotFoundException("Issue not found") }` → `.orElseThrow { NotFoundException.keyed("issue.notFoundGeneric") }`
- `:108` `.orElseThrow { NotFoundException("Issue not found") }` → `.orElseThrow { NotFoundException.keyed("issue.notFoundGeneric") }`
- `:115` `.orElseThrow { NotFoundException("Sprint not found") }` → `.orElseThrow { NotFoundException.keyed("sprint.notFound", sprintId) }`

(All three files already import `NotFoundException`.)

- [ ] **Step 8: Full suite + gates** → `cd backend && ./gradlew test` → BUILD SUCCESSFUL. (`SprintServiceTest` asserts `ConflictException` type.)

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/resources/messages*.properties \
        backend/src/main/kotlin/com/taskowolf/sprints backend/src/main/kotlin/com/taskowolf/reports \
        backend/src/test/kotlin/com/taskowolf/i18n/AgileReportsMessagesTest.kt
git commit -m "feat(i18n): localize sprint and reports error messages"
```

---

## Task 5: `integrations` slice (11 sites) + IllegalArgumentException→keyed

**Files:** catalogs; `integrations/application/WebhookService.kt`, `ProjectIntegrationService.kt`, `SsrfValidator.kt`, `IncomingWebhookService.kt`; modify `backend/src/test/kotlin/com/taskowolf/integrations/SsrfValidatorTest.kt`; create `IntegrationMessagesTest.kt`.

**Interfaces:** Consumes `.keyed()`. **Converts user-facing `IllegalArgumentException` throws to `BadRequestException.keyed(...)`** so they localize (they currently return English `ex.message` via the generic handler). `BadRequestException` already routes to 400 in `GlobalExceptionHandler`.

> **Caution — SsrfValidator type change:** `SsrfValidator.validate` currently throws `IllegalArgumentException`; `SsrfValidatorTest` asserts that type. Switching to `BadRequestException` (a `RuntimeException`, NOT an `IllegalArgumentException`) **breaks those 4 assertions** — this task updates them. Also confirm no caller does `try { ssrfValidator.validate(...) } catch (e: IllegalArgumentException)`; the full-suite run in Step 8 will surface any such caller. (`BadRequestException` still yields HTTP 400, so external behavior is unchanged aside from the now-localized body.)

- [ ] **Step 1: English keys**

```properties
# --- integration ---
integration.webhookNotFound=Webhook not found: {0}
integration.unknownProvider=Unknown provider: {0}
integration.alreadyExists=Integration already exists for {0} in project {1}
integration.notFound=Integration not found: {0}
integration.invalidUrl=Invalid URL: {0}
integration.blockedAddress=Webhook URL resolves to a private or reserved IP address
integration.invalidJsonPayload=Invalid JSON payload
```

- [ ] **Step 2: German keys**

```properties
# --- integration ---
integration.webhookNotFound=Webhook nicht gefunden: {0}
integration.unknownProvider=Unbekannter Anbieter: {0}
integration.alreadyExists=Integration für {0} in Projekt {1} existiert bereits
integration.notFound=Integration nicht gefunden: {0}
integration.invalidUrl=Ungültige URL: {0}
integration.blockedAddress=Webhook-URL verweist auf eine private oder reservierte IP-Adresse
integration.invalidJsonPayload=Ungültige JSON-Nutzlast
```

- [ ] **Step 3: Resolution test** — `IntegrationMessagesTest.kt`:

```kotlin
    @Test fun `integration keys render en and de`() {
        assertEquals("Webhook not found: 3", en("integration.webhookNotFound", 3))
        assertEquals("Webhook nicht gefunden: 3", de("integration.webhookNotFound", 3))
        assertEquals("Unknown provider: slack", en("integration.unknownProvider", "slack"))
        assertEquals("Unbekannter Anbieter: slack", de("integration.unknownProvider", "slack"))
        assertEquals("Integration already exists for GITHUB in project DEMO", en("integration.alreadyExists", "GITHUB", "DEMO"))
        assertEquals("Integration für GITHUB in Projekt DEMO existiert bereits", de("integration.alreadyExists", "GITHUB", "DEMO"))
        assertEquals("Invalid URL: x", en("integration.invalidUrl", "x"))
        assertEquals("Ungültige URL: x", de("integration.invalidUrl", "x"))
        assertEquals("Invalid JSON payload", en("integration.invalidJsonPayload"))
        assertEquals("Ungültige JSON-Nutzlast", de("integration.invalidJsonPayload"))
    }
```

- [ ] **Step 4: Run it** → PASS.

- [ ] **Step 5: Swap `WebhookService.kt`** (all four occurrences)

- `throw NotFoundException("Webhook not found: $webhookId")` → `throw NotFoundException.keyed("integration.webhookNotFound", webhookId)`

- [ ] **Step 6: Swap `ProjectIntegrationService.kt`**

Add import `import com.taskowolf.core.infrastructure.BadRequestException` (file currently imports only `ConflictException`, `NotFoundException`).

- `catch (e: IllegalArgumentException) { throw IllegalArgumentException("Unknown provider: ${req.provider}") }` → `catch (e: IllegalArgumentException) { throw BadRequestException.keyed("integration.unknownProvider", req.provider) }`
- `throw ConflictException("Integration already exists for $provider in project $projectKey")` → `throw ConflictException.keyed("integration.alreadyExists", provider, projectKey)`
- `throw NotFoundException("Integration not found: $integrationId")` → `throw NotFoundException.keyed("integration.notFound", integrationId)`

- [ ] **Step 7: Swap `SsrfValidator.kt`**

Add import `import com.taskowolf.core.infrastructure.BadRequestException`.

- `throw IllegalArgumentException("Invalid URL: $url")` → `throw BadRequestException.keyed("integration.invalidUrl", url)`
- The private-IP block:
  ```kotlin
  throw IllegalArgumentException(
      "Webhook URL resolves to a private or reserved IP address"
  )
  ```
  → `throw BadRequestException.keyed("integration.blockedAddress")`

- [ ] **Step 8: Swap `IncomingWebhookService.kt`** (both `handleGitHub` and `handleGitLab`)

Add import `import com.taskowolf.core.infrastructure.BadRequestException`.

- `catch (e: Exception) { throw IllegalArgumentException("Invalid JSON payload") }` → `catch (e: Exception) { throw BadRequestException.keyed("integration.invalidJsonPayload") }`

- [ ] **Step 9: Update `SsrfValidatorTest.kt`**

Replace the four private-address assertions' expected type from `IllegalArgumentException` to `BadRequestException`, and add the import:

```kotlin
import com.taskowolf.core.infrastructure.BadRequestException
```

Each of the four (`localhost is rejected`, `127_0_0_1 is rejected`, `10_x private range is rejected`, `192_168_x private range is rejected`):

```kotlin
        assertThrows(BadRequestException::class.java) {
            validator.validate("http://localhost:8080/hook")
        }
```

(Leave `public URL is accepted` unchanged.)

#### orElseThrow / error / AccessDenied sites

**None.** Per the scope-correction inventory the `integrations` module has no `.orElseThrow`, `error()`/`check()`, `ResponseStatusException`, or `AccessDeniedException` user-facing sites. Its `IllegalArgumentException`→`BadRequestException` conversions (the only non-`throw`-keyword construct present) are already handled in Steps 6–9 above. Nothing to add here.

- [ ] **Step 10: Full suite + gates**

Run: `cd backend && ./gradlew test`
Expected: BUILD SUCCESSFUL. If a caller of `SsrfValidator.validate` or the incoming-webhook JSON parse caught `IllegalArgumentException`, fix that catch to `BadRequestException` (or a broader `RuntimeException`) and re-run.

- [ ] **Step 11: Commit**

```bash
git add backend/src/main/resources/messages*.properties backend/src/main/kotlin/com/taskowolf/integrations \
        backend/src/test/kotlin/com/taskowolf/integrations/SsrfValidatorTest.kt \
        backend/src/test/kotlin/com/taskowolf/i18n/IntegrationMessagesTest.kt
git commit -m "feat(i18n): localize integrations errors and key user-facing IllegalArgumentExceptions"
```

---

## Task 6: `organizations` slice (10 sites) + slug validation

**Files:** catalogs; `organizations/application/OrganizationService.kt`, `automation/application/AutomationService.kt`, `automation/api/AdminAutomationController.kt`, `organizations/api/dto/CreateOrganizationRequest.kt`; create `OrganizationMessagesTest.kt`.

**Interfaces:** Consumes `.keyed()`. `OrganizationService`'s two "Only an owner or system admin can grant the OWNER role" reuse `org.ownerRoleGrantRestricted`. The slug validation follows the Phase-1 `project.key.pattern` precedent (validator already wired).

- [ ] **Step 1: English keys**

```properties
# --- org ---
org.ownerRoleGrantRestricted=Only an owner or system admin can grant the OWNER role
org.alreadyMember=User is already a member of this organization
org.cannotRemoveOwner=Cannot remove an owner
org.cannotRemoveLastOwner=Cannot remove the last owner
org.cannotChangeOwnRole=You cannot change your own role
org.cannotChangeOwnerRole=Cannot change an owner's role
org.cannotDemoteLastOwner=Cannot demote the last owner
org.slug.pattern=Slug must be lowercase alphanumeric with hyphens
# --- automation ---
automation.ruleNotFound=Rule not found: {0}
automation.systemAdminRequired=System admin role required
```

- [ ] **Step 2: German keys**

```properties
# --- org ---
org.ownerRoleGrantRestricted=Nur ein Inhaber oder Systemadministrator kann die OWNER-Rolle vergeben
org.alreadyMember=Benutzer ist bereits Mitglied dieser Organisation
org.cannotRemoveOwner=Ein Inhaber kann nicht entfernt werden
org.cannotRemoveLastOwner=Der letzte Inhaber kann nicht entfernt werden
org.cannotChangeOwnRole=Sie können Ihre eigene Rolle nicht ändern
org.cannotChangeOwnerRole=Die Rolle eines Inhabers kann nicht geändert werden
org.cannotDemoteLastOwner=Der letzte Inhaber kann nicht herabgestuft werden
org.slug.pattern=Slug muss aus Kleinbuchstaben, Ziffern und Bindestrichen bestehen
# --- automation ---
automation.ruleNotFound=Regel nicht gefunden: {0}
automation.systemAdminRequired=Systemadministratorrolle erforderlich
```

- [ ] **Step 3: Resolution test** — `OrganizationMessagesTest.kt`:

```kotlin
    @Test fun `org and automation keys render en and de`() {
        assertEquals("Only an owner or system admin can grant the OWNER role", en("org.ownerRoleGrantRestricted"))
        assertEquals("Nur ein Inhaber oder Systemadministrator kann die OWNER-Rolle vergeben", de("org.ownerRoleGrantRestricted"))
        assertEquals("Cannot demote the last owner", en("org.cannotDemoteLastOwner"))
        assertEquals("Der letzte Inhaber kann nicht herabgestuft werden", de("org.cannotDemoteLastOwner"))
        assertEquals("Rule not found: 4", en("automation.ruleNotFound", 4))
        assertEquals("Regel nicht gefunden: 4", de("automation.ruleNotFound", 4))
        assertEquals("Slug must be lowercase alphanumeric with hyphens", en("org.slug.pattern"))
        assertEquals("Slug muss aus Kleinbuchstaben, Ziffern und Bindestrichen bestehen", de("org.slug.pattern"))
    }
```

- [ ] **Step 4: Run it** → PASS.

- [ ] **Step 5: Swap `OrganizationService.kt`**

- `throw ForbiddenException("Only an owner or system admin can grant the OWNER role")` (both occurrences) → `throw ForbiddenException.keyed("org.ownerRoleGrantRestricted")`
- `throw ConflictException("User is already a member of this organization")` → `throw ConflictException.keyed("org.alreadyMember")`
- `throw ForbiddenException("Cannot remove an owner")` → `throw ForbiddenException.keyed("org.cannotRemoveOwner")`
- `throw ForbiddenException("Cannot remove the last owner")` → `throw ForbiddenException.keyed("org.cannotRemoveLastOwner")`
- `throw ForbiddenException("You cannot change your own role")` → `throw ForbiddenException.keyed("org.cannotChangeOwnRole")`
- `throw ForbiddenException("Cannot change an owner's role")` → `throw ForbiddenException.keyed("org.cannotChangeOwnerRole")`
- `throw ForbiddenException("Cannot demote the last owner")` → `throw ForbiddenException.keyed("org.cannotDemoteLastOwner")`

- [ ] **Step 6: Swap `AutomationService.kt` + `AdminAutomationController.kt`**

- `AutomationService.kt:64` (this site is an `.orElseThrow` lambda, not a bare `throw`): `.orElseThrow { NotFoundException("Rule not found: $ruleId") }` → `.orElseThrow { NotFoundException.keyed("automation.ruleNotFound", ruleId) }`
- `AdminAutomationController.kt`: `throw ForbiddenException("System admin role required")` → `throw ForbiddenException.keyed("automation.systemAdminRequired")`

- [ ] **Step 7: Key the slug validation message**

In `organizations/api/dto/CreateOrganizationRequest.kt`, change:

```kotlin
    @field:NotBlank @field:Pattern(regexp = "^[a-z0-9-]+$", message = "{org.slug.pattern}") val slug: String
```

#### orElseThrow / error / AccessDenied sites

This task also absorbs the auth follow-ups (the auth slice already merged in PR #84, so these are handled here, NOT via a re-open of #84). Covers all five constructs:

- **Construct 2 (`orElseThrow`):** `OrganizationService:51,99` User → `user.notFound` (reuse, PR #84); `OrganizationService:59,89` Member → `org.memberNotFound` (**new**). `AutomationService:64` already handled in Step 6.
- **Construct 3 (`error()` / `NoSuchElementException`):** `OrganizationService:30` `findBySlug` and `OrganizationService:33` `findById` → `org.notFound` (**new**, **two 500→404 fixes** — both lookups currently throw unhandled exceptions that surface as 500).
- **Construct 3 (`check`):** `OidcUserProvisioningService:31` → `auth.autoProvisionDisabled` (**new**, **500→403 fix**, Decision 2).
- **Construct 4 (`ResponseStatusException`):** `AuthController:55` `switch-org` → `org.notMemberCurrent` (**new**, stays 403).
- **Construct 5 (`AccessDeniedException`):** `OrganizationService:82` → `org.notMember` (**new**, stays 403).

- [ ] **Step 7b: Add English keys**

Append to `messages.properties`:

```properties
org.memberNotFound=Member not found
org.notFound=Organization not found: {0}
org.notMember=Not a member of organization {0}
org.notMemberCurrent=Not a member of this organization
```

Append to the `# --- auth ---` section:

```properties
auth.autoProvisionDisabled=Auto-provisioning is disabled
```

- [ ] **Step 7c: Add German keys** (`\uXXXX`-escaped where needed)

Append to `messages_de.properties` (`Organisation`, `Mitglied` have no non-ASCII):

```properties
org.memberNotFound=Mitglied nicht gefunden
org.notFound=Organisation nicht gefunden: {0}
org.notMember=Kein Mitglied der Organisation {0}
org.notMemberCurrent=Kein Mitglied dieser Organisation
```

Append to the `# --- auth ---` section:

```properties
auth.autoProvisionDisabled=Automatische Bereitstellung ist deaktiviert
```

- [ ] **Step 7d: Extend `OrganizationMessagesTest.kt`**

Add to the test body:

```kotlin
        assertEquals("Member not found", en("org.memberNotFound"))
        assertEquals("Mitglied nicht gefunden", de("org.memberNotFound"))
        assertEquals("Organization not found: acme", en("org.notFound", "acme"))
        assertEquals("Organisation nicht gefunden: acme", de("org.notFound", "acme"))
        assertEquals("Not a member of this organization", en("org.notMemberCurrent"))
        assertEquals("Kein Mitglied dieser Organisation", de("org.notMemberCurrent"))
        assertEquals("Auto-provisioning is disabled", en("auth.autoProvisionDisabled"))
        assertEquals("Automatische Bereitstellung ist deaktiviert", de("auth.autoProvisionDisabled"))
```

Run: `cd backend && ./gradlew test --tests "com.taskowolf.i18n.OrganizationMessagesTest"` → PASS.

- [ ] **Step 7e: Swap the `OrganizationService.kt` orElseThrow + error + AccessDenied sites**

- `:30` `fun findBySlug(slug: String) = orgRepo.findBySlug(slug) ?: error("Org not found: $slug")` → `fun findBySlug(slug: String) = orgRepo.findBySlug(slug) ?: throw NotFoundException.keyed("org.notFound", slug)`
- `:33` `fun findById(id: UUID) = orgRepo.findById(id).orElseThrow { NoSuchElementException("Org not found: $id") }` → `fun findById(id: UUID) = orgRepo.findById(id).orElseThrow { NotFoundException.keyed("org.notFound", id) }` (reuses `org.notFound`; renders "Organization not found: &lt;id&gt;")
- `:51` `.orElseThrow { NotFoundException("User not found") }` → `.orElseThrow { NotFoundException.keyed("user.notFound") }`
- `:59` `.orElseThrow { NotFoundException("Member not found") }` → `.orElseThrow { NotFoundException.keyed("org.memberNotFound") }`
- `:82` `if (!isMember) throw AccessDeniedException("Not a member of organization $orgId")` → `if (!isMember) throw ForbiddenException.keyed("org.notMember", orgId)`
- `:89` `.orElseThrow { NotFoundException("Member not found") }` → `.orElseThrow { NotFoundException.keyed("org.memberNotFound") }`
- `:99` `.orElseThrow { NotFoundException("User not found") }` → `.orElseThrow { NotFoundException.keyed("user.notFound") }`

Then **remove the now-unused import** `import org.springframework.security.access.AccessDeniedException` (line 13). (`NotFoundException`, `ForbiddenException`, `ConflictException` remain imported.) `requireMembershipOrAdmin` is service code — no security filter matches on the `AccessDeniedException` type here, so the switch to `ForbiddenException` (still HTTP 403 via `GlobalExceptionHandler`) is safe.

- [ ] **Step 7f: Swap `AuthController.kt:55` (ResponseStatusException → keyed, stays 403)**

In `auth/api/AuthController.kt`, `switchOrg`:

```kotlin
        if (userOrgs.none { it.id == orgId }) {
            throw ForbiddenException.keyed("org.notMemberCurrent")
        }
```

Add `import com.taskowolf.core.infrastructure.ForbiddenException`. If `ResponseStatusException` / `HttpStatus` are now unused in the file, remove those imports (verify — other handlers in the file may still use them).

- [ ] **Step 7g: Swap `OidcUserProvisioningService.kt:31` (`check` → keyed, 500→403)**

Replace the `check(...)`:

```kotlin
            if (config?.autoProvision == false) throw ForbiddenException.keyed("auth.autoProvisionDisabled")
```

Add `import com.taskowolf.core.infrastructure.ForbiddenException`. Leave `OidcUserProvisioningService:28` `?: error("OIDC user has no email")` untouched (Decision 2 — internal invariant).

- [ ] **Step 7h: Write the `AuthController` switch-org status/localization guard**

`backend/src/test/kotlin/com/taskowolf/organizations/OrgSwitchLocalizationTest.kt` — proves the RSE→keyed swap keeps 403 and renders the standard localized `ErrorResponse`:

```kotlin
package com.taskowolf.organizations

import com.fasterxml.jackson.databind.ObjectMapper
import com.taskowolf.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

class OrgSwitchLocalizationTest : IntegrationTestBase() {

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
    fun `switching to a non-member org returns localized 403 (de)`() {
        val token = register("switch-de@test.com")
        mockMvc.perform(
            post("/api/v1/auth/switch-org/${UUID.randomUUID()}")
                .header("Authorization", "Bearer $token")
                .header("Accept-Language", "de")
        ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("FORBIDDEN"))
            .andExpect(jsonPath("$.message").value("Kein Mitglied dieser Organisation"))
    }

    @Test
    fun `switching to a non-member org returns english by default`() {
        val token = register("switch-en@test.com")
        mockMvc.perform(
            post("/api/v1/auth/switch-org/${UUID.randomUUID()}")
                .header("Authorization", "Bearer $token")
                .header("Accept-Language", "en")
        ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.message").value("Not a member of this organization"))
    }
}
```

> **Status-change coverage (Decision 3):** `findBySlug` + `findById` (500→404) and the OIDC `check` (500→403) are exercised at the service/unit layer rather than via MockMvc (the OIDC path needs a full OAuth2 login the web layer can't easily drive). After swapping, grep `OidcUserProvisioningServiceTest` and any `OrganizationServiceTest` for assertions of `IllegalStateException` / `NoSuchElementException` / a 500 status and update them to `ForbiddenException` / `NotFoundException` — in particular any existing test asserting `findById`/`findBySlug` throws `NoSuchElementException`/`IllegalStateException` must switch to `NotFoundException`. If no such assertion exists, add one MockK service test: `handleOidcLogin` with `config.autoProvision = false` and an unknown email → `assertThrows<ForbiddenException>`.

Run: `cd backend && ./gradlew test --tests "com.taskowolf.organizations.OrgSwitchLocalizationTest"` → PASS.

- [ ] **Step 8: Full suite + gates** → `cd backend && ./gradlew test` → BUILD SUCCESSFUL. (`OrganizationServiceTest`/`OrganizationMemberIntegrationTest` assert type/status.)

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/resources/messages*.properties \
        backend/src/main/kotlin/com/taskowolf/organizations backend/src/main/kotlin/com/taskowolf/automation \
        backend/src/main/kotlin/com/taskowolf/auth/api/AuthController.kt \
        backend/src/main/kotlin/com/taskowolf/auth/application/OidcUserProvisioningService.kt \
        backend/src/test/kotlin/com/taskowolf/i18n/OrganizationMessagesTest.kt \
        backend/src/test/kotlin/com/taskowolf/organizations/OrgSwitchLocalizationTest.kt
git commit -m "feat(i18n): localize organizations + automation + auth follow-ups (org.notFound 500->404, switch-org 403, OIDC auto-provision 403)"
```

---

## Task 7: `content` slice (14 sites)

**Files:** catalogs; `customfields/application/CustomFieldService.kt`, `labels/application/LabelService.kt`, `versions/application/VersionService.kt`, `comments/application/CommentService.kt`, `attachments/application/AttachmentService.kt`, `attachments/application/StorageService.kt`, `notifications/application/NotificationService.kt`, `notifications/api/NotificationPreferenceController.kt`; create `ContentMessagesTest.kt`.

**Interfaces:** Consumes `.keyed()`. The `''{0}''`-quoted keys (`customField.*`, `label.*`, `version.*`) follow the MessageFormat doubled-quote rule.

- [ ] **Step 1: English keys**

```properties
# --- customField ---
customField.alreadyExists=Custom field ''{0}'' already exists in this project
customField.optionAlreadyExists=Option ''{0}'' already exists for this field
# --- label ---
label.alreadyExists=Label ''{0}'' already exists in this project
# --- version ---
version.alreadyExists=Version ''{0}'' already exists in this project
# --- comment ---
comment.notAuthor=Not the comment author
comment.cannotDelete=Cannot delete this comment
# --- attachment ---
attachment.cannotDelete=Cannot delete this attachment
attachment.fileNotFound=File not found: {0}
# --- notification ---
notification.notFound=Notification not found
notification.unknownType=Unknown notification type: {0}
```

- [ ] **Step 2: German keys**

```properties
# --- customField ---
customField.alreadyExists=Benutzerdefiniertes Feld ''{0}'' existiert bereits in diesem Projekt
customField.optionAlreadyExists=Option ''{0}'' existiert bereits für dieses Feld
# --- label ---
label.alreadyExists=Label ''{0}'' existiert bereits in diesem Projekt
# --- version ---
version.alreadyExists=Version ''{0}'' existiert bereits in diesem Projekt
# --- comment ---
comment.notAuthor=Nicht der Autor des Kommentars
comment.cannotDelete=Dieser Kommentar kann nicht gelöscht werden
# --- attachment ---
attachment.cannotDelete=Dieser Anhang kann nicht gelöscht werden
attachment.fileNotFound=Datei nicht gefunden: {0}
# --- notification ---
notification.notFound=Benachrichtigung nicht gefunden
notification.unknownType=Unbekannter Benachrichtigungstyp: {0}
```

- [ ] **Step 3: Resolution test** — `ContentMessagesTest.kt`:

```kotlin
    @Test fun `content keys render en and de`() {
        assertEquals("Custom field 'Severity' already exists in this project", en("customField.alreadyExists", "Severity"))
        assertEquals("Benutzerdefiniertes Feld 'Severity' existiert bereits in diesem Projekt", de("customField.alreadyExists", "Severity"))
        assertEquals("Label 'bug' already exists in this project", en("label.alreadyExists", "bug"))
        assertEquals("Label 'bug' existiert bereits in diesem Projekt", de("label.alreadyExists", "bug"))
        assertEquals("File not found: a.pdf", en("attachment.fileNotFound", "a.pdf"))
        assertEquals("Datei nicht gefunden: a.pdf", de("attachment.fileNotFound", "a.pdf"))
        assertEquals("Cannot delete this comment", en("comment.cannotDelete"))
        assertEquals("Dieser Kommentar kann nicht gelöscht werden", de("comment.cannotDelete"))
        assertEquals("Unknown notification type: sla", en("notification.unknownType", "sla"))
        assertEquals("Unbekannter Benachrichtigungstyp: sla", de("notification.unknownType", "sla"))
    }
```

- [ ] **Step 4: Run it** → PASS. (If `''{0}''` renders as literal `{0}`, the doubling is wrong.)

- [ ] **Step 5: Swap the throw-sites**

`CustomFieldService.kt`:
- `throw ConflictException("Custom field '${request.name}' already exists in this project")` (both occurrences) → `throw ConflictException.keyed("customField.alreadyExists", request.name)`
- `throw ConflictException("Option '${request.label}' already exists for this field")` → `throw ConflictException.keyed("customField.optionAlreadyExists", request.label)`

`LabelService.kt` (both occurrences):
- `throw ConflictException("Label '${request.name}' already exists in this project")` → `throw ConflictException.keyed("label.alreadyExists", request.name)`

`VersionService.kt` (both occurrences):
- `throw ConflictException("Version '${request.name}' already exists in this project")` → `throw ConflictException.keyed("version.alreadyExists", request.name)`

`CommentService.kt`:
- `throw ForbiddenException("Not the comment author")` → `throw ForbiddenException.keyed("comment.notAuthor")`
- `throw ForbiddenException("Cannot delete this comment")` → `throw ForbiddenException.keyed("comment.cannotDelete")`

`AttachmentService.kt`:
- `throw ForbiddenException("Cannot delete this attachment")` → `throw ForbiddenException.keyed("attachment.cannotDelete")`

`StorageService.kt` (both occurrences):
- `throw NotFoundException("File not found: $storedName")` → `throw NotFoundException.keyed("attachment.fileNotFound", storedName)`

`NotificationService.kt`:
- `throw NotFoundException("Notification not found")` → `throw NotFoundException.keyed("notification.notFound")`

`NotificationPreferenceController.kt`:
- `throw BadRequestException("Unknown notification type: ${item.type}")` → `throw BadRequestException.keyed("notification.unknownType", item.type)`

(Confirm `NotificationPreferenceController.kt` imports `BadRequestException`; if not, add `import com.taskowolf.core.infrastructure.BadRequestException`.)

#### orElseThrow / error / AccessDenied sites

Five **new** `.notFound` keys (all id-in-message, `{0}` arg) + reuse of `customField.optionNotFound`, 13 `.orElseThrow` swaps across five content services. No quotes → no MessageFormat doubling concerns.

> **`customField.optionNotFound` is already defined by Task 8** (which runs earlier, in Session 3). Do NOT re-append it here — just reference it in the swaps. It is the only shared key; the other five below are new.

- [ ] **Step 5b: Add English keys**

Append to `messages.properties` (each under its existing `# --- <domain> ---` section):

```properties
customField.notFound=Custom field not found: {0}
label.notFound=Label not found: {0}
version.notFound=Version not found: {0}
comment.notFound=Comment not found: {0}
attachment.notFound=Attachment not found: {0}
```

- [ ] **Step 5c: Add German keys** (`\uXXXX`-escaped where needed — `Benutzerdefiniertes` etc. have no non-ASCII)

Append to `messages_de.properties`:

```properties
customField.notFound=Benutzerdefiniertes Feld nicht gefunden: {0}
label.notFound=Label nicht gefunden: {0}
version.notFound=Version nicht gefunden: {0}
comment.notFound=Kommentar nicht gefunden: {0}
attachment.notFound=Anhang nicht gefunden: {0}
```

- [ ] **Step 5d: Extend `ContentMessagesTest.kt`**

Add to the test body:

```kotlin
        assertEquals("Custom field not found: 5", en("customField.notFound", 5))
        assertEquals("Benutzerdefiniertes Feld nicht gefunden: 5", de("customField.notFound", 5))
        assertEquals("Option not found: 7", en("customField.optionNotFound", 7))   // reuse — key defined in Task 8
        assertEquals("Option nicht gefunden: 7", de("customField.optionNotFound", 7))
        assertEquals("Label not found: 9", en("label.notFound", 9))
        assertEquals("Label nicht gefunden: 9", de("label.notFound", 9))
        assertEquals("Version not found: 3", en("version.notFound", 3))
        assertEquals("Version nicht gefunden: 3", de("version.notFound", 3))
        assertEquals("Comment not found: 2", en("comment.notFound", 2))
        assertEquals("Kommentar nicht gefunden: 2", de("comment.notFound", 2))
        assertEquals("Attachment not found: 4", en("attachment.notFound", 4))
        assertEquals("Anhang nicht gefunden: 4", de("attachment.notFound", 4))
```

Run: `cd backend && ./gradlew test --tests "com.taskowolf.i18n.ContentMessagesTest"` → PASS.

- [ ] **Step 5e: Swap the 13 orElseThrow sites**

`CustomFieldService.kt`:
- `:56, :81, :90, :102, :116` (five occurrences) `.orElseThrow { NotFoundException("Custom field not found: $fieldId") }` → `.orElseThrow { NotFoundException.keyed("customField.notFound", fieldId) }`
- `:105, :119` (two occurrences) `.orElseThrow { NotFoundException("Option not found: $optId") }` → `.orElseThrow { NotFoundException.keyed("customField.optionNotFound", optId) }` (reuses the key defined in Task 8)

`LabelService.kt` (both occurrences, `:39, :53`):
- `.orElseThrow { NotFoundException("Label not found: $labelId") }` → `.orElseThrow { NotFoundException.keyed("label.notFound", labelId) }`

`VersionService.kt` (both occurrences, `:40, :53`):
- `.orElseThrow { NotFoundException("Version not found: $versionId") }` → `.orElseThrow { NotFoundException.keyed("version.notFound", versionId) }`

`CommentService.kt` (both occurrences, `:39, :57`):
- `.orElseThrow { NotFoundException("Comment not found: $commentId") }` → `.orElseThrow { NotFoundException.keyed("comment.notFound", commentId) }`

`AttachmentService.kt` (`:47`):
- `.orElseThrow { NotFoundException("Attachment not found: $attachmentId") }` → `.orElseThrow { NotFoundException.keyed("attachment.notFound", attachmentId) }`

(All five files already import `NotFoundException`.)

- [ ] **Step 6: Full suite + gates** → `cd backend && ./gradlew test` → BUILD SUCCESSFUL. (`CustomFieldServiceTest`, `LabelServiceTest`, `VersionServiceTest`, `CommentServiceTest`, `AttachmentServiceTest`, `StorageServiceTest`, `NotificationServiceTest` all assert type.)

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/resources/messages*.properties \
        backend/src/main/kotlin/com/taskowolf/customfields backend/src/main/kotlin/com/taskowolf/labels \
        backend/src/main/kotlin/com/taskowolf/versions backend/src/main/kotlin/com/taskowolf/comments \
        backend/src/main/kotlin/com/taskowolf/attachments backend/src/main/kotlin/com/taskowolf/notifications \
        backend/src/test/kotlin/com/taskowolf/i18n/ContentMessagesTest.kt
git commit -m "feat(i18n): localize content module error messages (custom fields, labels, versions, comments, attachments, notifications)"
```

---

## Task 8: `issues` slice (5 orElseThrow + 7 folded-in free-text sites)

**Files:** catalogs; `issues/application/IssueService.kt`; create `backend/src/test/kotlin/com/taskowolf/i18n/IssuesMessagesTest.kt`.

**Interfaces:** Consumes `.keyed()`. **Depends on Task 4** (same Session 3, runs first): reuses `sprint.notFound` and `issue.notFoundGeneric` defined there, plus the P1 keys `issue.notFound` (="Issue {0} not found"), `issue.assigneeNotFound` (="Assignee {0} not found"), and `project.noWorkflow`. **New keys:** `issue.parentNotFound`, `issue.statusNotInWorkflow`, and the custom-field-validation set `customField.invalidNumber`, `customField.invalidDate`, `customField.invalidOptionId`, `customField.optionNotFound`, `customField.requiredMissing`.

> **Cross-task ordering — `customField.optionNotFound` is defined HERE.** Task 8 (Session 3) runs **before** Task 7 (Session 4), and both hit the free-text `"Option not found: {0}"`. This task introduces `customField.optionNotFound`; Task 7 **reuses** it (its subsection says so — do NOT re-append it there). The other four `customField.*` validation keys are Task-8-only (those throws live in `IssueService.applyCustomFieldValues`, not `CustomFieldService`).

The Phase-1 pilot keyed the `throw`-keyword issue sites but left these `.orElseThrow` sites — and the seven free-text `throw`/`orElseThrow` sites Wolfgang folded in on 2026-07-19 — untouched.

- [ ] **Step 1: Add the new English keys**

Append to `messages.properties`. The `# --- issue (pilot) ---` section exists from Phase 1; **this task is the first to introduce `customField.*` keys** (Task 7 runs later), so add a `# --- customField ---` comment before the customField lines:

```properties
issue.parentNotFound=Parent issue not found: {0}
issue.statusNotInWorkflow=Status does not belong to project's workflow
customField.invalidNumber=Invalid number for field ''{0}'': {1}
customField.invalidDate=Invalid date for field ''{0}'': {1}
customField.invalidOptionId=Invalid option ID for field ''{0}''
customField.optionNotFound=Option not found: {0}
customField.requiredMissing=Required custom field ''{0}'' must have a value
```

> `issue.statusNotInWorkflow` is resolved with **no args**, so its apostrophe in `project's` stays single (no MessageFormat). The four `customField.*` messages that carry a field name (`''{0}''`) ARE arg-bearing, so their literal single quotes MUST be doubled (`''`). `customField.optionNotFound` has no literal quotes.

- [ ] **Step 1b: Add the new German keys** (`\uXXXX`-escaped where needed)

Append to `messages_de.properties`. Shown with readable umlauts; write `Ü`=`Ü`, `ö`=`ö`, `ü`=`ü` — `IssuesMessagesTest` (Step 2) asserts the decoded strings, so mojibake fails the test:

```properties
issue.parentNotFound=Übergeordneter Vorgang nicht gefunden: {0}
issue.statusNotInWorkflow=Status gehört nicht zum Workflow des Projekts
customField.invalidNumber=Ungültige Zahl für Feld ''{0}'': {1}
customField.invalidDate=Ungültiges Datum für Feld ''{0}'': {1}
customField.invalidOptionId=Ungültige Options-ID für Feld ''{0}''
customField.optionNotFound=Option nicht gefunden: {0}
customField.requiredMissing=Pflichtfeld ''{0}'' muss einen Wert haben
```

- [ ] **Step 2: Write the resolution test**

`backend/src/test/kotlin/com/taskowolf/i18n/IssuesMessagesTest.kt` (resolution-test shape from File Structure). Asserts the new key plus the reused keys render correctly (guards the Decision-1 id-in-message form and the P1 word order):

```kotlin
    @Test fun `issue keys render en and de`() {
        assertEquals("Parent issue not found: PROJ-1", en("issue.parentNotFound", "PROJ-1"))
        assertEquals("Übergeordneter Vorgang nicht gefunden: PROJ-1", de("issue.parentNotFound", "PROJ-1"))
        assertEquals("Issue not found: PROJ-2", en("issue.notFound", "PROJ-2"))     // reuse P1 → renders "Issue {0} not found"
        assertEquals("Vorgang PROJ-2 nicht gefunden", de("issue.notFound", "PROJ-2"))
        assertEquals("Sprint not found: 8", en("sprint.notFound", 8))               // reuse Task 4
        assertEquals("Sprint nicht gefunden: 8", de("sprint.notFound", 8))
        assertEquals("Issue not found", en("issue.notFoundGeneric"))               // reuse Task 4
        assertEquals("Vorgang nicht gefunden", de("issue.notFoundGeneric"))
        assertEquals("Assignee not found: 5", en("issue.assigneeNotFound", 5))      // reuse P1 → renders "Assignee {0} not found"
        assertEquals("Bearbeiter 5 nicht gefunden", de("issue.assigneeNotFound", 5))
        // folded-in extras
        assertEquals("Status does not belong to project's workflow", en("issue.statusNotInWorkflow"))
        assertEquals("Status gehört nicht zum Workflow des Projekts", de("issue.statusNotInWorkflow"))
        assertEquals("Invalid number for field 'Severity': abc", en("customField.invalidNumber", "Severity", "abc"))
        assertEquals("Ungültige Zahl für Feld 'Severity': abc", de("customField.invalidNumber", "Severity", "abc"))
        assertEquals("Invalid date for field 'Due': xx", en("customField.invalidDate", "Due", "xx"))
        assertEquals("Ungültiges Datum für Feld 'Due': xx", de("customField.invalidDate", "Due", "xx"))
        assertEquals("Invalid option ID for field 'Team'", en("customField.invalidOptionId", "Team"))
        assertEquals("Ungültige Options-ID für Feld 'Team'", de("customField.invalidOptionId", "Team"))
        assertEquals("Option not found: 7", en("customField.optionNotFound", 7))
        assertEquals("Option nicht gefunden: 7", de("customField.optionNotFound", 7))
        assertEquals("Required custom field 'Severity' must have a value", en("customField.requiredMissing", "Severity"))
        assertEquals("Pflichtfeld 'Severity' muss einen Wert haben", de("customField.requiredMissing", "Severity"))
    }
```

> The `customField.*` arg-bearing messages render `''{0}''` as a single-quoted `'Severity'` — if you see the literal `{0}` instead, the quote-doubling in the catalog is wrong.

> Note: `issue.notFound` = `Issue {0} not found`, so `en("issue.notFound", "PROJ-2")` renders **"Issue PROJ-2 not found"** — the assertion above must match that exact word order, NOT "Issue not found: PROJ-2". Same for `issue.assigneeNotFound` = "Assignee {0} not found". The P1 key text intentionally differs from the current free-text ("Issue not found: $issueId"); the scope-correction chose reuse (Decision noted in the spec's construct-2 table).

- [ ] **Step 3: Run it** → `cd backend && ./gradlew test --tests "com.taskowolf.i18n.IssuesMessagesTest"` → PASS. (Fix the assertions to the real rendered word order if they fail — the catalog is authoritative.)

- [ ] **Step 4: Swap the five `IssueService.kt` orElseThrow sites**

- `:66` `.orElseThrow { NotFoundException("Parent issue not found: $parentId") }` → `.orElseThrow { NotFoundException.keyed("issue.parentNotFound", parentId) }`
- `:81` `.orElseThrow { NotFoundException("Issue not found: $issueId") }` → `.orElseThrow { NotFoundException.keyed("issue.notFound", issueId) }`
- `:170` `.orElseThrow { NotFoundException("Sprint not found: ${request.sprintId}") }` → `.orElseThrow { NotFoundException.keyed("sprint.notFound", request.sprintId) }`
- `:301` `.orElseThrow { NotFoundException("Issue not found") }` → `.orElseThrow { NotFoundException.keyed("issue.notFoundGeneric") }`
- `:393` `.orElseThrow { NotFoundException("Assignee not found: $assigneeId") }` → `.orElseThrow { NotFoundException.keyed("issue.assigneeNotFound", assigneeId) }`

- [ ] **Step 4b: Swap the seven folded-in free-text sites**

These use fully-qualified `com.taskowolf.core.infrastructure.*` names in the source (only `NotFoundException` is imported at the top of the file); preserve the FQN form so the `old_string` matches exactly:

- `:184` `throw com.taskowolf.core.infrastructure.ForbiddenException("Status does not belong to project's workflow")` → `throw com.taskowolf.core.infrastructure.ForbiddenException.keyed("issue.statusNotInWorkflow")`
- `:312` `?: throw com.taskowolf.core.infrastructure.NotFoundException("Project has no workflow")` → `?: throw com.taskowolf.core.infrastructure.NotFoundException.keyed("project.noWorkflow")` (reuses the Phase-1 key)
- `:357` `?: throw com.taskowolf.core.infrastructure.BadRequestException("Invalid number for field '${definition.name}': ${input.value}")` → `?: throw com.taskowolf.core.infrastructure.BadRequestException.keyed("customField.invalidNumber", definition.name, input.value)`
- `:361` `throw com.taskowolf.core.infrastructure.BadRequestException("Invalid date for field '${definition.name}': ${input.value}")` → `throw com.taskowolf.core.infrastructure.BadRequestException.keyed("customField.invalidDate", definition.name, input.value)`
- `:367` `throw com.taskowolf.core.infrastructure.BadRequestException("Invalid option ID for field '${definition.name}'")` → `throw com.taskowolf.core.infrastructure.BadRequestException.keyed("customField.invalidOptionId", definition.name)`
- `:371` `.orElseThrow { com.taskowolf.core.infrastructure.BadRequestException("Option not found: $optId") }` → `.orElseThrow { com.taskowolf.core.infrastructure.BadRequestException.keyed("customField.optionNotFound", optId) }`
- `:386` `throw com.taskowolf.core.infrastructure.BadRequestException("Required custom field '${field.name}' must have a value")` → `throw com.taskowolf.core.infrastructure.BadRequestException.keyed("customField.requiredMissing", field.name)`

(`:184` is a 403, `:312` a 404, and the five `customField.*` sites stay 400 — none of these seven change HTTP status; they only localize. `IssueService.kt` already imports `NotFoundException`; the `Forbidden`/`BadRequest` FQN references need no new imports.)

- [ ] **Step 5: Full suite + gates** → `cd backend && ./gradlew test` → BUILD SUCCESSFUL. (`IssueServiceTest` asserts exception type; `IssueErrorLocalizationTest` (P1) already covers the `issue.notFound` web path. If any existing `IssueServiceTest`/custom-field test asserts an exception *message* string for the seven folded-in sites, update it — grep `IssueServiceTest` for `"Invalid number"`/`"Option not found"`/`"Required custom field"`/`"Status does not belong"`.)

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/resources/messages*.properties \
        backend/src/main/kotlin/com/taskowolf/issues \
        backend/src/test/kotlin/com/taskowolf/i18n/IssuesMessagesTest.kt
git commit -m "feat(i18n): localize issues errors (orElseThrow + custom-field validation + status-not-in-workflow)"
```

---

## Task 9: `servicedesk` slice (9 error() + 2 ResponseStatusException — fixes 6× 500→404)

**Files:** catalogs; `servicedesk/api/ServiceDeskController.kt`, `servicedesk/api/IncidentController.kt`; create `backend/src/test/kotlin/com/taskowolf/i18n/ServiceDeskMessagesTest.kt` and `backend/src/test/kotlin/com/taskowolf/servicedesk/ServiceDeskErrorLocalizationTest.kt`.

**Interfaces:** Consumes `.keyed()`. This module was entirely absent from the original plan. Nine `?: error("…")` sites currently throw unhandled `IllegalStateException` → **HTTP 500**; converting them to keyed `NotFoundException` fixes the status to **404** (the 6× `project.notFound` conversions are the headline fix; the 3× `serviceDesk.notEnabled` conversions are also 500→404). Two `ResponseStatusException(BAD_REQUEST, …)` sites become keyed `BadRequestException` (stay 400, but gain the standard localized `ErrorResponse` body). New keys: `serviceDesk.notEnabled`, `serviceDesk.invalidPriority`, `incident.invalidSeverity`; `project.notFound` is reused (defined in Task 2). Enum lists: `IssuePriority.entries.joinToString()` = `"CRITICAL, HIGH, MEDIUM, LOW"`; `IncidentSeverity.entries.joinToString()` = `"P1, P2, P3, P4"`.

- [ ] **Step 1: Confirm no existing servicedesk web test asserts the old 500/RSE behavior**

Run: `cd backend && grep -rEn 'isInternalServerError|is5xxServerError|ResponseStatusException|IllegalStateException' backend/src/test/kotlin/com/taskowolf/servicedesk`
Expected: no matches (the servicedesk test dir has only service-layer tests). If any match asserts the old behavior, note it and update it in Step 7's run.

- [ ] **Step 2: Add English keys**

Append to `messages.properties`:

```properties
# --- serviceDesk ---
serviceDesk.notEnabled=Service desk not enabled for project: {0}
serviceDesk.invalidPriority=Invalid priority: {0}. Valid values: {1}
# --- incident ---
incident.invalidSeverity=Invalid severity ''{0}''. Must be one of: {1}
```

> `incident.invalidSeverity` contains literal single quotes around `{0}` and is resolved **with args**, so the quotes MUST be doubled (`''{0}''`) per the MessageFormat rule — in both en and de. `serviceDesk.invalidPriority` has no literal quotes.

- [ ] **Step 3: Add German keys** (`\uXXXX`-escaped)

Append to `messages_de.properties`:

```properties
# --- serviceDesk ---
serviceDesk.notEnabled=Servicedesk ist für Projekt {0} nicht aktiviert
serviceDesk.invalidPriority=Ungültige Priorität: {0}. Gültige Werte: {1}
# --- incident ---
incident.invalidSeverity=Ungültiger Schweregrad ''{0}''. Muss einer von: {1}
```

- [ ] **Step 4: Write the resolution test**

`backend/src/test/kotlin/com/taskowolf/i18n/ServiceDeskMessagesTest.kt` (resolution-test shape). The two-arg messages carry the enum list as `{1}`:

```kotlin
    @Test fun `servicedesk keys render en and de`() {
        assertEquals("Service desk not enabled for project: DEMO", en("serviceDesk.notEnabled", "DEMO"))
        assertEquals("Servicedesk ist für Projekt DEMO nicht aktiviert", de("serviceDesk.notEnabled", "DEMO"))
        assertEquals("Invalid priority: X. Valid values: CRITICAL, HIGH, MEDIUM, LOW",
            en("serviceDesk.invalidPriority", "X", "CRITICAL, HIGH, MEDIUM, LOW"))
        assertEquals("Ungültige Priorität: X. Gültige Werte: CRITICAL, HIGH, MEDIUM, LOW",
            de("serviceDesk.invalidPriority", "X", "CRITICAL, HIGH, MEDIUM, LOW"))
        assertEquals("Invalid severity 'SEV5'. Must be one of: P1, P2, P3, P4",
            en("incident.invalidSeverity", "SEV5", "P1, P2, P3, P4"))
        assertEquals("Ungültiger Schweregrad 'SEV5'. Muss einer von: P1, P2, P3, P4",
            de("incident.invalidSeverity", "SEV5", "P1, P2, P3, P4"))
    }
```

- [ ] **Step 5: Run it** → `cd backend && ./gradlew test --tests "com.taskowolf.i18n.ServiceDeskMessagesTest"` → PASS. (If `''{0}''` renders as a literal `{0}`, the quote-doubling is wrong.)

- [ ] **Step 6: Swap `ServiceDeskController.kt` (5× project.notFound + 3× serviceDesk.notEnabled + 1 RSE)**

Add imports:
```kotlin
import com.taskowolf.core.infrastructure.NotFoundException
import com.taskowolf.core.infrastructure.BadRequestException
```
Then swap:
- `:32, :38, :51, :72, :87` (five occurrences) `projectRepository.findByKey(key) ?: error("Project not found: $key")` → `projectRepository.findByKey(key) ?: throw NotFoundException.keyed("project.notFound", key)`
- `:40, :73, :88` (three occurrences) `serviceDeskService.findByProject(project.id) ?: error("Service desk not enabled for project: $key")` → `serviceDeskService.findByProject(project.id) ?: throw NotFoundException.keyed("serviceDesk.notEnabled", key)`
- `:77` the invalid-priority block:
  ```kotlin
          throw ResponseStatusException(HttpStatus.BAD_REQUEST,
              "Invalid priority: ${req.priority}. Valid values: ${IssuePriority.entries.joinToString()}")
  ```
  →
  ```kotlin
          throw BadRequestException.keyed("serviceDesk.invalidPriority", req.priority, IssuePriority.entries.joinToString())
  ```

Then **remove the now-unused import** `import org.springframework.web.server.ResponseStatusException` (the only RSE in the file was `:77`). Keep the `HttpStatus` import — it is still used by `@ResponseStatus(HttpStatus.CREATED)` / `@ResponseStatus(HttpStatus.NO_CONTENT)`.

- [ ] **Step 7: Swap `IncidentController.kt` (1× project.notFound + 1 RSE)**

Add imports:
```kotlin
import com.taskowolf.core.infrastructure.NotFoundException
import com.taskowolf.core.infrastructure.BadRequestException
```
Then swap:
- `:50` `projectRepository.findByKey(key) ?: error("Project not found: $key")` → `projectRepository.findByKey(key) ?: throw NotFoundException.keyed("project.notFound", key)`
- `:32` the invalid-severity block:
  ```kotlin
          throw ResponseStatusException(
              HttpStatus.BAD_REQUEST,
              "Invalid severity '${req.severity}'. Must be one of: ${IncidentSeverity.entries.joinToString()}"
          )
  ```
  →
  ```kotlin
          throw BadRequestException.keyed("incident.invalidSeverity", req.severity, IncidentSeverity.entries.joinToString())
  ```

Then **remove the now-unused import** `import org.springframework.web.server.ResponseStatusException`. Keep `HttpStatus` (used by `@ResponseStatus`).

- [ ] **Step 8: Write the status-change / localization web guard (Decision 3)**

`backend/src/test/kotlin/com/taskowolf/servicedesk/ServiceDeskErrorLocalizationTest.kt` — asserts the corrected status (404 for the ex-500 sites, 400 for the RSE sites) AND the localized `ErrorResponse` body. The registering user is the project owner/admin, satisfying `@projectSecurity.isProjectAdmin`. For invalid-severity, `severity` is parsed **before** `issueId` is used, so a random UUID reaches the 400 path.

```kotlin
package com.taskowolf.servicedesk

import com.fasterxml.jackson.databind.ObjectMapper
import com.taskowolf.IntegrationTestBase
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

class ServiceDeskErrorLocalizationTest : IntegrationTestBase() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper

    private fun register(email: String): String {
        val res = mockMvc.perform(
            post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","displayName":"User","password":"password123"}""")
        ).andReturn()
        return objectMapper.readTree(res.response.contentAsString).get("accessToken").asText()
    }
    private fun createProject(token: String, key: String) =
        mockMvc.perform(
            post("/api/v1/projects").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"key":"$key","name":"Demo"}""")
        ).andExpect(status().isCreated)
    private fun enableServiceDesk(token: String, key: String) =
        mockMvc.perform(
            post("/api/v1/projects/$key/service-desk/enable").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"emailAddress":"desk@test.com"}""")
        ).andExpect(status().isOk)

    @Test
    fun `unknown project returns 404 not 500 (localized de)`() {
        val token = register("sd-nf-de@test.com")
        mockMvc.perform(
            get("/api/v1/projects/NOPE/service-desk").header("Authorization", "Bearer $token")
                .header("Accept-Language", "de")
        ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("Projekt nicht gefunden: NOPE"))
    }

    @Test
    fun `service desk not enabled returns 404 not 500 (localized de)`() {
        val token = register("sd-off-de@test.com")
        createProject(token, "SDOFF")
        mockMvc.perform(
            get("/api/v1/projects/SDOFF/service-desk").header("Authorization", "Bearer $token")
                .header("Accept-Language", "de")
        ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("Servicedesk ist für Projekt SDOFF nicht aktiviert"))
    }

    @Test
    fun `invalid priority returns 400 with localized body (de)`() {
        val token = register("sd-prio-de@test.com")
        createProject(token, "SDPRIO")
        enableServiceDesk(token, "SDPRIO")
        mockMvc.perform(
            post("/api/v1/projects/SDPRIO/service-desk/sla-policies").header("Authorization", "Bearer $token")
                .header("Accept-Language", "de").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"P1","priority":"BOGUS","responseMinutes":10,"resolutionMinutes":60}""")
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
            .andExpect(jsonPath("$.message", startsWith("Ungültige Priorität: BOGUS")))
    }

    @Test
    fun `invalid severity returns 400 with localized body (de)`() {
        val token = register("sd-sev-de@test.com")
        createProject(token, "SDSEV")
        mockMvc.perform(
            post("/api/v1/projects/SDSEV/incidents").header("Authorization", "Bearer $token")
                .header("Accept-Language", "de").contentType(MediaType.APPLICATION_JSON)
                .content("""{"issueId":"${UUID.randomUUID()}","severity":"BOGUS","onCallAssigneeId":null,"notifyUserIds":[]}""")
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
            .andExpect(jsonPath("$.message", startsWith("Ungültiger Schweregrad 'BOGUS'")))
    }
}
```

> If a project-scoped security interceptor short-circuits an unknown project key with 403 before the controller runs, the first two tests would see 403 — but `GET /service-desk` and `GET /incidents` carry no method-level `@PreAuthorize`, so the request reaches the controller's `?: throw` and yields 404. Adjust only if the run proves otherwise.

- [ ] **Step 9: Run the servicedesk tests + full suite + gates**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.servicedesk.*" --tests "com.taskowolf.i18n.ServiceDeskMessagesTest"` → PASS, then `cd backend && ./gradlew test` → BUILD SUCCESSFUL. (`ServiceDeskServiceTest`, `IncidentServiceTest`, `SlaMonitorJobTest` are service-layer and assert domain behavior, unaffected.)

- [ ] **Step 10: Commit**

```bash
git add backend/src/main/resources/messages*.properties \
        backend/src/main/kotlin/com/taskowolf/servicedesk \
        backend/src/test/kotlin/com/taskowolf/i18n/ServiceDeskMessagesTest.kt \
        backend/src/test/kotlin/com/taskowolf/servicedesk/ServiceDeskErrorLocalizationTest.kt
git commit -m "feat(i18n): localize servicedesk errors + fix 6x 500->404 (project/service-desk not-found) and key invalid severity/priority"
```

---

## Final verification

- [ ] **Full backend suite green:** `cd backend && ./gradlew test` → BUILD SUCCESSFUL.
- [ ] **Gates green:** `MessagesParityTest` (key-set + non-blank + placeholder-parity), `KeyedReferenceIntegrityTest` (every referenced key exists) both pass.
- [ ] **Construct-1 sweep complete:** `grep -rEn 'throw (NotFoundException|ForbiddenException|ConflictException|BadRequestException)\("' backend/src/main/kotlin` returns only intentional free-text throws — none expected after this plan (the seven folded-in `IssueService` sites are now keyed in Task 8). User-facing `IllegalArgumentException` throw-sites (SsrfValidator, IncomingWebhookService, ProjectIntegrationService unknown-provider) are now keyed `BadRequestException`.
- [ ] **Construct-2 sweep complete:** `grep -rEn 'orElseThrow \{ (NotFoundException|ForbiddenException|ConflictException|BadRequestException)\("' backend/src/main/kotlin` returns nothing (every user-facing `.orElseThrow` free-text lambda is now `.keyed(...)`). The only remaining bare-`orElseThrow` calls are arg-less `orElseThrow()` (e.g. `AuditService`, `IncidentService:43`) which throw `NoSuchElementException` for internal invariants — acceptable.
- [ ] **Construct 3/4/5 sweep — NO user-facing 500s (Decision 4):** `grep -rEn '\?: error\(|throw ResponseStatusException|throw AccessDeniedException|check\(' backend/src/main/kotlin` returns only the deliberately-skipped internal invariants (`SsoController:35` `clientSecret required`, `OidcUserProvisioningService:28` `OIDC user has no email`). No user-facing `IllegalStateException` remains — every `error()`/`check()` user path was converted to a keyed exception with the correct status.
- [ ] **Status fixes confirmed:** the servicedesk web guard (`ServiceDeskErrorLocalizationTest`) shows 404 (not 500) for unknown project + service-desk-not-enabled, and 400 with a localized `ErrorResponse` body for invalid severity/priority. The `OrgSwitchLocalizationTest` shows a localized 403.
- [ ] **Folded-in extras done:** `OrganizationService.findById:33` (Task 6, now keyed `NotFoundException` → 404) and the seven `IssueService` free-text throws `:184, :312, :357, :361, :367, :371, :386` (Task 8) are all keyed. Grep confirms no remaining `NoSuchElementException("Org not found` or free-text `BadRequestException("Invalid number`/`"Option not found`/`"Required custom field`/`ForbiddenException("Status does not belong` in `src/main/kotlin`.
- [ ] **Manual smoke** (see `reference-local-docker-run`): run the app, switch UI to Deutsch, trigger errors in a few migrated modules (e.g. duplicate project key, unknown webhook, remove last org owner, GET a service desk on a project that has none → should be a German 404, not a 500) → German text; switch to English → English. Confirm `Accept-Language` in the Network tab.

## Notes for Phase 3 (not in scope here)

- Phase 3: `EmailService` + `NotificationService` (incl. the 3 `createDirect` callers) render in the **recipient's** `user.language` via `LocalizedMessages.get(key, localeOf(user), args…)`; notification `title` rendered at creation, `body` stays user content.
