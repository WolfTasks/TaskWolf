# Backend i18n Phase 2 — Session 5 (servicedesk + sweep close-out) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Localize all user-facing `servicedesk` error text (fixing 6× latent HTTP 500→404 bugs and localizing 2 invalid-enum 400s), and add a durable test guard so no un-keyed user-facing throw — bare or fully-qualified — can ever ship again. Completes the #16 Phase-2 API-error sweep.

**Architecture:** Two REST controllers (`ServiceDeskController`, `IncidentController`) currently throw `?: error(...)` (→ unhandled `IllegalStateException` → HTTP 500) and `ResponseStatusException(BAD_REQUEST, "…")` (English-only body). Convert them to the project's keyed domain exceptions (`NotFoundException.keyed`/`BadRequestException.keyed`), which `GlobalExceptionHandler` maps to 404/400 with a localized `ErrorResponse`. Add a static-analysis test (`NoUnkeyedUserFacingThrowTest`) that walks `src/main/kotlin` and fails on any free-text user-facing throw, allowlisting the two intentional internal invariants.

**Tech Stack:** Kotlin, Spring Boot, Spring `MessageSource` (base `messages.properties` = English, `messages_de.properties` = German `\uXXXX`-escaped), JUnit5, MockK, MockMvc (`IntegrationTestBase`), Gradle.

**Spec:** `docs/superpowers/specs/2026-07-20-backend-i18n-phase2-session5-servicedesk-closeout-design.md`
**Parent plan (Task 9 origin):** `docs/superpowers/plans/2026-07-19-backend-i18n-phase2-api-error-sweep.md` (Task 9, lines 1465–1679; Final verification, lines 1683–1692).

## Global Constraints

- **Always throw keyed errors via the `.keyed(key, vararg args)` factory** — `NotFoundException("some.key")` binds to the free-text `(message: String)` ctor and leaks the raw key. Correct: `NotFoundException.keyed("some.key", arg0)`.
- English lives in the **base bundle** `backend/src/main/resources/messages.properties` (there is NO `messages_en.properties`). German is `messages_de.properties`.
- `ErrorResponse.code` values (`NOT_FOUND`, `BAD_REQUEST`, …) are **stable/machine-readable — never change them.** Only `message`/`details` are localized.
- **Languages: en (default/fallback) and de only.**
- **`.properties` files are ISO-8859-1: every non-ASCII char in `messages_de.properties` MUST be `\uXXXX`-escaped** (`ä`=`ä`, `ö`=`ö`, `ü`=`ü`, `ß`=`ß`, `Ä`=`Ä`, `Ö`=`Ö`, `Ü`=`Ü`). Procedure: write German with raw umlauts, then run the PowerShell non-ASCII→`\uXXXX` pass and confirm 0 non-ASCII bytes remain. UTF-8 resolution tests assert the decoded strings.
- **MessageFormat single-quote rule:** a message resolved *with arguments* runs through `java.text.MessageFormat`, where `'` is an escape char. Any literal single quote in an arg-bearing message must be **doubled** `''` in BOTH en and de. `incident.invalidSeverity` has literal quotes around `{0}` → `''{0}''`. `serviceDesk.invalidPriority` and `serviceDesk.notEnabled` have no literal quotes.
- Keys are namespaced by domain: `serviceDesk.*`, `incident.*`; `project.notFound` is reused (already in the catalog).
- **CI gates that must stay green:** `MessagesParityTest` (en/de key-set + placeholder-`{n}` parity + non-blank), `KeyedReferenceIntegrityTest` (every referenced key exists), and the **new** `NoUnkeyedUserFacingThrowTest`.
- Base branch is fresh `main` (`fcb1f2d` or later). Sessions 2–4 are merged — **no stacking**; the PR targets `main` directly.
- TDD: failing test first → watch it fail → minimal implementation → watch it pass. **One commit** for the whole task (Task 9 conversions + catalog + tests + guard), per the approved design.

---

### Task 1: servicedesk localization + durable un-keyed-throw guard (single commit)

**Files:**
- Modify: `backend/src/main/resources/messages.properties` (append `serviceDesk.*`, `incident.*`)
- Modify: `backend/src/main/resources/messages_de.properties` (append German, `\uXXXX`-escaped)
- Modify: `backend/src/main/kotlin/com/taskowolf/servicedesk/api/ServiceDeskController.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/servicedesk/api/IncidentController.kt`
- Create: `backend/src/test/kotlin/com/taskowolf/i18n/ServiceDeskMessagesTest.kt`
- Create: `backend/src/test/kotlin/com/taskowolf/servicedesk/ServiceDeskErrorLocalizationTest.kt`
- Create: `backend/src/test/kotlin/com/taskowolf/core/NoUnkeyedUserFacingThrowTest.kt`

**Interfaces:**
- Consumes: `NotFoundException.keyed(key, vararg args)` and `BadRequestException.keyed(key, vararg args)` from `com.taskowolf.core.infrastructure` (existing). `GlobalExceptionHandler` maps `NotFoundException`→404 `code=NOT_FOUND`, `BadRequestException`→400 `code=BAD_REQUEST`, both with a localized `ErrorResponse { code, message, … }`.
- Consumes: `IssuePriority.entries.joinToString()` = `"CRITICAL, HIGH, MEDIUM, LOW"`; `IncidentSeverity.entries.joinToString()` = `"P1, P2, P3, P4"`.
- Produces: three new catalog keys and a permanent static gate; no new production API.

All work runs from the repo root; Gradle commands run from `backend/`.

---

#### Step 1: Append English catalog keys

In `backend/src/main/resources/messages.properties`, append at the end:

```properties
# --- serviceDesk ---
serviceDesk.notEnabled=Service desk not enabled for project: {0}
serviceDesk.invalidPriority=Invalid priority: {0}. Valid values: {1}
# --- incident ---
incident.invalidSeverity=Invalid severity ''{0}''. Must be one of: {1}
```

- [ ] Done.

#### Step 2: Append German catalog keys (raw umlauts first)

In `backend/src/main/resources/messages_de.properties`, append at the end:

```properties
# --- serviceDesk ---
serviceDesk.notEnabled=Servicedesk ist für Projekt {0} nicht aktiviert
serviceDesk.invalidPriority=Ungültige Priorität: {0}. Gültige Werte: {1}
# --- incident ---
incident.invalidSeverity=Ungültiger Schweregrad ''{0}''. Muss einer von: {1}
```

- [ ] Done.

#### Step 3: Escape the German file (non-ASCII → `\uXXXX`)

Run this PowerShell pass, then confirm zero non-ASCII bytes remain:

```powershell
$p = "backend/src/main/resources/messages_de.properties"
$lines = Get-Content -Encoding UTF8 $p
$out = foreach ($line in $lines) {
  -join ($line.ToCharArray() | ForEach-Object {
    if ([int]$_ -gt 127) { '\u{0:x4}' -f [int]$_ } else { $_ }
  })
}
Set-Content -Encoding ascii $p $out
Select-String -Pattern '[^\x00-\x7f]' $p   # expect: no output
```

After escaping, the three new German lines read:
```properties
serviceDesk.notEnabled=Servicedesk ist für Projekt {0} nicht aktiviert
serviceDesk.invalidPriority=Ungültige Priorität: {0}. Gültige Werte: {1}
incident.invalidSeverity=Ungültiger Schweregrad ''{0}''. Muss einer von: {1}
```
(The pass is idempotent — it leaves already-escaped prior lines untouched.)

- [ ] Done, and `Select-String` printed nothing.

#### Step 4: Write the resolution test

Create `backend/src/test/kotlin/com/taskowolf/i18n/ServiceDeskMessagesTest.kt`:

```kotlin
package com.taskowolf.i18n

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.context.support.ResourceBundleMessageSource
import java.util.Locale

class ServiceDeskMessagesTest {
    private val src = ResourceBundleMessageSource().apply {
        setBasename("messages"); setDefaultEncoding("UTF-8"); setFallbackToSystemLocale(false)
    }
    private fun en(key: String, vararg a: Any?) = src.getMessage(key, a, Locale.ENGLISH)
    private fun de(key: String, vararg a: Any?) = src.getMessage(key, a, Locale.GERMAN)

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
}
```

- [ ] Done.

#### Step 5: Run the resolution test — expect PASS

Run: `cd backend && ./gradlew test --tests "com.taskowolf.i18n.ServiceDeskMessagesTest"`
Expected: **PASS**. (If `''{0}''` renders as a literal `{0}`, the quote-doubling is wrong — fix the catalog.)

- [ ] Passed.

#### Step 6: Write the durable guard test

Create `backend/src/test/kotlin/com/taskowolf/core/NoUnkeyedUserFacingThrowTest.kt`:

```kotlin
package com.taskowolf.core

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Static guard: no user-facing free-text throw may reach production code — every such
 * throw must go through the `.keyed(...)` factory so it localizes. Catches BARE and
 * FULLY-QUALIFIED throws alike (`throw NotFoundException("…")` AND
 * `throw com.taskowolf...NotFoundException("…")`) — the FQ form is the blind spot that
 * let an un-keyed AttachmentController throw ship through the sweep. `.keyed(` never
 * matches (the exception name is followed by `.keyed(`, not `("`).
 *
 * Allowlisted: the two intentional internal invariants (Decision 2), which are NOT
 * user-facing and stay as `?: error(...)`.
 */
class NoUnkeyedUserFacingThrowTest {

    private fun sourceRoot(): File {
        val direct = File("src/main/kotlin")
        return if (direct.isDirectory) direct else File("backend/src/main/kotlin")
    }

    // (fileName, substring of the offending line) — Decision-2 internal invariants.
    private val allowlist = listOf(
        "SsoController.kt" to "clientSecret required",
        "OidcUserProvisioningService.kt" to "OIDC user has no email",
    )

    private val patterns = listOf(
        // free-text throw of a keyed domain exception (bare or fully-qualified)
        Regex("""throw\s+(?:[\w.]+\.)?(?:NotFoundException|ForbiddenException|ConflictException|BadRequestException)\s*\(\s*""""),
        // free-text orElseThrow lambda (bare or fully-qualified)
        Regex("""orElseThrow\s*\{\s*(?:[\w.]+\.)?(?:NotFoundException|ForbiddenException|ConflictException|BadRequestException)\s*\(\s*""""),
        // nullable-fallback error() → user-facing IllegalStateException
        Regex("""\?:\s*error\s*\("""),
        // web-layer / security constructs that should be keyed instead
        Regex("""throw\s+ResponseStatusException\s*\("""),
        Regex("""throw\s+AccessDeniedException\s*\("""),
    )

    private fun allowlisted(fileName: String, line: String) =
        allowlist.any { (f, s) -> fileName == f && line.contains(s) }

    @Test
    fun `no un-keyed user-facing throw in production code`() {
        val root = sourceRoot()
        assertTrue(root.isDirectory) { "sourceRoot() did not resolve to a directory: ${root.absolutePath}" }

        val violations = mutableListOf<String>()
        var filesScanned = 0
        root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                filesScanned++
                file.readLines().forEachIndexed { i, line ->
                    if (patterns.any { it.containsMatchIn(line) } && !allowlisted(file.name, line)) {
                        violations.add("${file.name}:${i + 1}  ${line.trim()}")
                    }
                }
            }

        assertTrue(filesScanned > 0) { "guard scanned no files under ${root.absolutePath}" }
        assertTrue(violations.isEmpty()) {
            "Un-keyed user-facing throw(s) found — use `.keyed(\"key\", …)` " +
                "(or allowlist a genuine internal invariant):\n" + violations.joinToString("\n")
        }
    }
}
```

- [ ] Done.

#### Step 7: Run the guard — expect FAIL (proves it has teeth)

Run: `cd backend && ./gradlew test --tests "com.taskowolf.core.NoUnkeyedUserFacingThrowTest"`
Expected: **FAIL**. The failure message must list exactly the 11 servicedesk sites about to be converted (and nothing else — the two internal invariants are allowlisted):
```
IncidentController.kt:32  throw ResponseStatusException(
IncidentController.kt:50  val project = projectRepository.findByKey(key) ?: error("Project not found: $key")
ServiceDeskController.kt:32  … ?: error("Project not found: $key")
ServiceDeskController.kt:38  … ?: error("Project not found: $key")
ServiceDeskController.kt:40  … ?: error("Service desk not enabled for project: $key")
ServiceDeskController.kt:51  … ?: error("Project not found: $key")
ServiceDeskController.kt:72  … ?: error("Project not found: $key")
ServiceDeskController.kt:73  … ?: error("Service desk not enabled for project: $key")
ServiceDeskController.kt:77  throw ResponseStatusException(HttpStatus.BAD_REQUEST,
ServiceDeskController.kt:87  … ?: error("Project not found: $key")
ServiceDeskController.kt:88  … ?: error("Service desk not enabled for project: $key")
```
If the list contains any file OTHER than the two servicedesk controllers, STOP — the guard has a false positive; tighten the regex (do NOT broaden the allowlist) and report it.

- [ ] Failed with exactly the 11 servicedesk lines.

#### Step 8: Write the web-layer status/localization guard (Decision 3)

Create `backend/src/test/kotlin/com/taskowolf/servicedesk/ServiceDeskErrorLocalizationTest.kt`:

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

- [ ] Done.

#### Step 9: Run the web guard — expect FAIL

Run: `cd backend && ./gradlew test --tests "com.taskowolf.servicedesk.ServiceDeskErrorLocalizationTest"`
Expected: **FAIL** — the unknown-project / not-enabled cases currently return **500** (unhandled `IllegalStateException`), not 404; the invalid-enum cases return an unlocalized `ResponseStatusException` body.

> If the first two tests see **403** instead of 500/404, a project-scoped security interceptor is short-circuiting the unknown key before the controller runs. `GET /service-desk` and `GET /incidents` carry no method-level `@PreAuthorize`, so this is not expected — but if it happens, note it and adjust the test to a controller path that reaches the `?: throw` (do not weaken the status assertion).

- [ ] Failed as expected.

#### Step 10: Convert `ServiceDeskController.kt`

In `backend/src/main/kotlin/com/taskowolf/servicedesk/api/ServiceDeskController.kt`:

Add two imports (after the existing `com.taskowolf.*` imports, keep alphabetical grouping loose to match the file):
```kotlin
import com.taskowolf.core.infrastructure.BadRequestException
import com.taskowolf.core.infrastructure.NotFoundException
```
Remove this import (its only use, line 77, is being replaced):
```kotlin
import org.springframework.web.server.ResponseStatusException
```
Keep `import org.springframework.http.HttpStatus` (still used by `@ResponseStatus(HttpStatus.CREATED)` / `@ResponseStatus(HttpStatus.NO_CONTENT)`).

Replace the five `?: error("Project not found: $key")` occurrences (lines 32, 38, 51, 72, 87) — each identical:
```kotlin
        val project = projectRepository.findByKey(key) ?: error("Project not found: $key")
```
with:
```kotlin
        val project = projectRepository.findByKey(key) ?: throw NotFoundException.keyed("project.notFound", key)
```

Replace the three `?: error("Service desk not enabled for project: $key")` occurrences (lines 40, 73, 88):
```kotlin
            serviceDeskService.findByProject(project.id) ?: error("Service desk not enabled for project: $key")
```
with (preserve each site's own left-hand side — `return ServiceDeskResponse.from(serviceDeskService.findByProject(project.id) ?: …)` at :40, and `val sd = serviceDeskService.findByProject(project.id) ?: …` at :73/:88):
```kotlin
            serviceDeskService.findByProject(project.id) ?: throw NotFoundException.keyed("serviceDesk.notEnabled", key)
```

Replace the invalid-priority block (lines 77–78):
```kotlin
            throw ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Invalid priority: ${req.priority}. Valid values: ${IssuePriority.entries.joinToString()}")
```
with:
```kotlin
            throw BadRequestException.keyed("serviceDesk.invalidPriority", req.priority, IssuePriority.entries.joinToString())
```

- [ ] Done.

#### Step 11: Convert `IncidentController.kt`

In `backend/src/main/kotlin/com/taskowolf/servicedesk/api/IncidentController.kt`:

Add imports:
```kotlin
import com.taskowolf.core.infrastructure.BadRequestException
import com.taskowolf.core.infrastructure.NotFoundException
```
Remove:
```kotlin
import org.springframework.web.server.ResponseStatusException
```
Keep `import org.springframework.http.HttpStatus` (used by `@ResponseStatus`).

Replace the invalid-severity block (lines 32–35):
```kotlin
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Invalid severity '${req.severity}'. Must be one of: ${IncidentSeverity.entries.joinToString()}"
            )
```
with:
```kotlin
            throw BadRequestException.keyed("incident.invalidSeverity", req.severity, IncidentSeverity.entries.joinToString())
```

Replace the project lookup (line 50):
```kotlin
        val project = projectRepository.findByKey(key) ?: error("Project not found: $key")
```
with:
```kotlin
        val project = projectRepository.findByKey(key) ?: throw NotFoundException.keyed("project.notFound", key)
```

- [ ] Done.

#### Step 12: Re-run the guard + web guard — expect PASS

Run: `cd backend && ./gradlew test --tests "com.taskowolf.core.NoUnkeyedUserFacingThrowTest" --tests "com.taskowolf.servicedesk.ServiceDeskErrorLocalizationTest"`
Expected: **PASS** — the guard now finds zero violations (only the two allowlisted invariants remain), and the web guard returns 404/400 with localized German bodies.

- [ ] Both passed.

#### Step 13: Full suite + all gates

Run: `cd backend && ./gradlew test`
Expected: **BUILD SUCCESSFUL**, including `MessagesParityTest`, `KeyedReferenceIntegrityTest`, `NoUnkeyedUserFacingThrowTest`, `ServiceDeskMessagesTest`, `ServiceDeskErrorLocalizationTest`. (`ServiceDeskServiceTest`, `IncidentServiceTest`, `SlaMonitorJobTest` are service-layer and assert domain behavior — unaffected. If any asserted the old 500/RSE web behavior, update it; the servicedesk test dir has none.)

- [ ] Passed.

#### Step 14: Commit (single commit for the whole task)

```bash
git add backend/src/main/resources/messages.properties \
        backend/src/main/resources/messages_de.properties \
        backend/src/main/kotlin/com/taskowolf/servicedesk/api/ServiceDeskController.kt \
        backend/src/main/kotlin/com/taskowolf/servicedesk/api/IncidentController.kt \
        backend/src/test/kotlin/com/taskowolf/i18n/ServiceDeskMessagesTest.kt \
        backend/src/test/kotlin/com/taskowolf/servicedesk/ServiceDeskErrorLocalizationTest.kt \
        backend/src/test/kotlin/com/taskowolf/core/NoUnkeyedUserFacingThrowTest.kt
git commit -m "feat(i18n): localize servicedesk errors + guard against un-keyed throws

Task 9 of the #16 Phase-2 sweep: 6x 500->404 (project/service-desk not-found),
2x ResponseStatusException->BadRequestException.keyed (invalid priority/severity,
now localized 400). New keys serviceDesk.notEnabled/invalidPriority +
incident.invalidSeverity (en/de). Adds NoUnkeyedUserFacingThrowTest, a static
gate that fails on any un-keyed user-facing throw (bare OR fully-qualified) —
closing the FQ-prefix blind spot that hid the AttachmentController throw.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

- [ ] Committed.

---

## Final verification (controller-run, after the task review — no code changes expected)

The widened audit already came back clean on `main` outside servicedesk; after Task 1 it should be clean everywhere except the two allowlisted invariants.

- [ ] **Full suite green:** `cd backend && ./gradlew test` → BUILD SUCCESSFUL.
- [ ] **All three static gates green:** `MessagesParityTest`, `KeyedReferenceIntegrityTest`, `NoUnkeyedUserFacingThrowTest`.
- [ ] **Widened construct sweep (the FQ blind spot):**
  - `grep -rEn 'throw ([a-zA-Z0-9_.]+\.)?(NotFoundException|ForbiddenException|ConflictException|BadRequestException)\("' backend/src/main/kotlin | grep -v '\.keyed('` → **none**
  - `grep -rEn 'orElseThrow \{ ([a-zA-Z0-9_.]+\.)?(NotFoundException|ForbiddenException|ConflictException|BadRequestException)\("' backend/src/main/kotlin | grep -v '\.keyed('` → **none**
  - `grep -rEn '\?: error\(|throw ResponseStatusException|throw AccessDeniedException' backend/src/main/kotlin | grep -vE 'SsoController|OidcUserProvisioningService'` → **none**
- [ ] **No user-facing `IllegalStateException` remains** (only the 2 allowlisted `?: error(...)` invariants).
- [ ] **Status fixes confirmed** by `ServiceDeskErrorLocalizationTest` (404 not 500; localized 400s).

## Handoff (out of scope for this session)

- Open a PR to `main` (single commit). No stacking.
- **Owed by Wolfgang, after merge:** manual DE/EN browser smoke (parent plan line 1692 — duplicate project key, unknown webhook, remove last org owner, GET a service desk on a project that has none → German 404 not 500; switch to English → English; confirm `Accept-Language` in the Network tab), then the v1.0.x release.
- Phase 3 (emails + notifications, recipient-locale rendering) remains open — separate effort.
```
