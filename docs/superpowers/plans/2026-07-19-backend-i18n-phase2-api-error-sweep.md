# Backend-i18n Phase 2 — API-error sweep Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Localize the remaining 81 free-text exception throw-sites (27 files) and the one remaining custom Bean-Validation message onto Spring `MessageSource` keys (en default, de), reusing Phase 1's `.keyed()` factory, catalog, validator wiring, and parity gate.

**Architecture:** A mechanical sweep applying the Phase 1 pattern module-by-module. First, three cross-cutting hardening changes (Task 0): a typo safety net in `LocalizedMessages`, a placeholder-parity check in the gate, and a source-scanning gate that asserts every `.keyed(...)`/`message="{…}"` key referenced in code exists in the catalog (this auto-guards every later slice). Then seven module slices, each adding a keyed catalog section (en + de) + swapping that module's throws to `Exception.keyed("key", args…)` + a data-driven resolution test, kept green by the parity + reference gates. `ErrorResponse.code` values never change.

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
- Each module slice ships as its **own worktree branch + PR**. Suggested session grouping at the ~70% usage checkpoint: **Session 1** = Task 0 + Task 1; **Session 2** = Tasks 2–4; **Session 3** = Tasks 5–7.

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
- Task 6 (organizations): `OrganizationService.kt`, `AutomationService.kt`, `AdminAutomationController.kt`, `organizations/api/dto/CreateOrganizationRequest.kt`
- Task 7 (content): `CustomFieldService.kt`, `LabelService.kt`, `VersionService.kt`, `CommentService.kt`, `AttachmentService.kt`, `StorageService.kt`, `NotificationService.kt`, `NotificationPreferenceController.kt`

**Backend (create — tests, one resolution test per slice + one e2e in Task 1):**
- `backend/src/test/kotlin/com/taskowolf/i18n/AuthMessagesTest.kt` (+ `AuthErrorLocalizationTest.kt`, e2e)
- `backend/src/test/kotlin/com/taskowolf/i18n/ProjectMessagesTest.kt`
- `backend/src/test/kotlin/com/taskowolf/i18n/WorkflowMessagesTest.kt`
- `backend/src/test/kotlin/com/taskowolf/i18n/AgileReportsMessagesTest.kt`
- `backend/src/test/kotlin/com/taskowolf/i18n/IntegrationMessagesTest.kt`
- `backend/src/test/kotlin/com/taskowolf/i18n/OrganizationMessagesTest.kt`
- `backend/src/test/kotlin/com/taskowolf/i18n/ContentMessagesTest.kt`

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

- `AutomationService.kt`: `throw NotFoundException("Rule not found: $ruleId")` → `throw NotFoundException.keyed("automation.ruleNotFound", ruleId)`
- `AdminAutomationController.kt`: `throw ForbiddenException("System admin role required")` → `throw ForbiddenException.keyed("automation.systemAdminRequired")`

- [ ] **Step 7: Key the slug validation message**

In `organizations/api/dto/CreateOrganizationRequest.kt`, change:

```kotlin
    @field:NotBlank @field:Pattern(regexp = "^[a-z0-9-]+$", message = "{org.slug.pattern}") val slug: String
```

- [ ] **Step 8: Full suite + gates** → `cd backend && ./gradlew test` → BUILD SUCCESSFUL. (`OrganizationServiceTest`/`OrganizationMemberIntegrationTest` assert type/status.)

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/resources/messages*.properties \
        backend/src/main/kotlin/com/taskowolf/organizations backend/src/main/kotlin/com/taskowolf/automation \
        backend/src/test/kotlin/com/taskowolf/i18n/OrganizationMessagesTest.kt
git commit -m "feat(i18n): localize organizations and automation errors + org slug validation"
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

## Final verification

- [ ] **Full backend suite green:** `cd backend && ./gradlew test` → BUILD SUCCESSFUL.
- [ ] **Gates green:** `MessagesParityTest` (key-set + non-blank + placeholder-parity), `KeyedReferenceIntegrityTest` (every referenced key exists) both pass.
- [ ] **Sweep complete:** `grep -rEn 'throw (NotFoundException|ForbiddenException|ConflictException|BadRequestException)\("' backend/src/main/kotlin` returns only intentional free-text throws (if any remain, they are deliberate English-only internal errors — none expected after this plan). User-facing `IllegalArgumentException` throw-sites (SsrfValidator, IncomingWebhookService, ProjectIntegrationService unknown-provider) are now keyed `BadRequestException`.
- [ ] **Manual smoke** (see `reference-local-docker-run`): run the app, switch UI to Deutsch, trigger errors in a few migrated modules (e.g. duplicate project key, unknown webhook, remove last org owner) → German text; switch to English → English. Confirm `Accept-Language` in the Network tab.

## Notes for Phase 3 (not in scope here)

- Phase 3: `EmailService` + `NotificationService` (incl. the 3 `createDirect` callers) render in the **recipient's** `user.language` via `LocalizedMessages.get(key, localeOf(user), args…)`; notification `title` rendered at creation, `body` stays user content.
