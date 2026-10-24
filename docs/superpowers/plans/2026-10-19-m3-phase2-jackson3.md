# M3 Phase 2 — Jackson 3 + jjwt-gson + Release v1.0.21 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Das Backend nutzt Jackson 3 (`tools.jackson`) als einzigen aktiven JSON-Mapper. jjwt serialisiert über Gson. Das Draht-Format der API, das gespeicherte JSON in der DB und bestehende JWTs bleiben unverändert. Danach gemeinsames Release **v1.0.21** (M3 Phase 1 + 2).

**Architecture:** Zuerst kommen Charakterisierungstests dazu, die auf Jackson 2 grün sind: gespeichertes JSON, Request-Toleranz, Fehlerformat, JWT-Fixture und STOMP-Konverter. Dann folgt die Umstellung in einem atomaren Schritt (Build, Config, Imports). Damit die Ausgabe gleich bleibt, setzen wir `spring.jackson.use-jackson2-defaults: true`, denn die Jackson-3-Defaults unterscheiden sich nachweislich. Danach jjwt-gson, Security-Nachweis, Smoke, Doku, PR und Release.

**Tech Stack:** Kotlin 2.4.20, Spring Boot 4.1.1, Jackson 3.1.7 (`tools.jackson.*`, Annotationen bleiben `com.fasterxml.jackson.annotation`), jackson-module-kotlin 3.1.7, jjwt 0.13.0 + jjwt-gson (Gson 2.13.2 aus dem Boot-BOM), JUnit 5 + MockK, Testcontainers 2, Trivy.

**Spec:** `docs/superpowers/specs/2026-10-19-m3-spring-boot-4-migration-design.md` (Abschnitt „Phase 2"). **Abweichung, von Wolfgang am 2026-10-19 entschieden:** springdoc bleibt. Es zieht über `swagger-core-jakarta` 2.2.55 (neueste Version, es gibt keine Jackson-3-Variante) weiterhin Jackson 2 transitiv. Deshalb bleiben `jackson-2-bom.version = 2.21.7` und die zugehörige CVE-Pflege bestehen. Spec-Ziel „Jackson 2 nicht mehr im Runtime-Classpath" wird zu „Jackson 2 nur noch transitiv über springdoc/swagger-core; weder unser Code noch Spring noch jjwt nutzen es". Task 5 passt die Spec an.

## Global Constraints

- Spring Boot bleibt **4.1.1**. `jackson-bom.version` = **3.1.7**, `jackson-2-bom.version` = **2.21.7**, `tomcat.version` = **11.0.25**, buildscript-Constraints Jackson 3 **3.1.7**: alle unverändert lassen.
- Jackson-Annotationen bleiben in `com.fasterxml.jackson.annotation.*` (Jackson-3-Design). Alle anderen Jackson-Imports im Backend-Code wandern nach `tools.jackson.*`. Danach darf `grep -rn "import com.fasterxml.jackson" backend/src` nur noch `.annotation.`-Imports zeigen.
- Keine Änderung an API-Shapes, Fehlerformat, i18n-Texten, DB-Schema (Flyway V30), gespeichertem JSON-Format oder Frontend-Code.
- Bestehende JWTs (mit jjwt-jackson ausgestellt) bleiben nach dem Wechsel auf jjwt-gson gültig.
- Backend-Unit-Tests nutzen **MockK** (nicht Mockito).
- Arbeiten im isolierten Worktree; `gh pr merge` **im Haupt-Checkout** ausführen, nicht im Worktree (sonst schlägt `gh` lokal mit „'main' is already used by worktree" fehl).
- Commit-Messages enden mit `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`; PR-Body endet mit `🤖 Generated with [Claude Code](https://claude.com/claude-code)`.
- Release (Task 7) nur nach Wolfgangs ausdrücklichem OK **und** nachdem er SSO/OIDC-Login (und IMAP, falls genutzt) manuell geprüft hat.

## Verifizierte Fakten (2026-10-19)

- Jackson 3.1.7 Roh-Defaults (jshell): `SORT_PROPERTIES_ALPHABETICALLY=true`, `FAIL_ON_NULL_FOR_PRIMITIVES=true`, `FAIL_ON_TRAILING_TOKENS=true`, `WRITE_DATES_AS_TIMESTAMPS=false` (jetzt `tools.jackson.databind.cfg.DateTimeFeature`), `FAIL_ON_UNKNOWN_PROPERTIES=false`. Instants werden als ISO-String geschrieben, null-Map-Werte bleiben enthalten.
- Boot 4.1.1 hat `spring.jackson.use-jackson2-defaults` (Default `false`): „Whether to configure Jackson 3 with the same defaults as Spring Boot previously used for Jackson 2."
- Spring-7-Konverterklassen: HTTP `org.springframework.http.converter.json.JacksonJsonHttpMessageConverter`, STOMP `org.springframework.messaging.converter.JacksonJsonMessageConverter`. Die Jackson-2-Pendants sind `MappingJackson2HttpMessageConverter` und `MappingJackson2MessageConverter`.
- `tools.jackson.databind.JsonNode`: `asText()` ist **deprecated**, Ersatz ist `asString()` bzw. `asString(default)`; `isTextual()` → `isString()`.
- `tools.jackson.core.type.TypeReference`, `tools.jackson.core.JacksonException` (unchecked), `tools.jackson.databind.json.JsonMapper`. Kotlin-Modul: `tools.jackson.module:jackson-module-kotlin`, `tools.jackson.module.kotlin.jacksonObjectMapper()` und `readValue` existieren.
- Alle `readTree`/`readValue`-Aufrufe in Main fangen `Exception` allgemein ab (`IncomingWebhookService`) oder gar nicht. Unchecked `JacksonException` ändert daran nichts.
- JWT-Fixture, mit jjwt-jackson 0.13.0 erzeugt. Secret `jwt-fixture-secret-0123456789abcdef`, jti `00000000-0000-0000-0000-0000000000aa`, sub `11111111-1111-1111-1111-111111111111`, `type=access`, `orgId=22222222-2222-2222-2222-222222222222`, iat 1760000000, exp 4102444800 (2100-01-01):
  `eyJhbGciOiJIUzI1NiJ9.eyJqdGkiOiIwMDAwMDAwMC0wMDAwLTAwMDAtMDAwMC0wMDAwMDAwMDAwYWEiLCJzdWIiOiIxMTExMTExMS0xMTExLTExMTEtMTExMS0xMTExMTExMTExMTEiLCJ0eXBlIjoiYWNjZXNzIiwib3JnSWQiOiIyMjIyMjIyMi0yMjIyLTIyMjItMjIyMi0yMjIyMjIyMjIyMjIiLCJpYXQiOjE3NjAwMDAwMDAsImV4cCI6NDEwMjQ0NDgwMH0.0EK0B6blrj-HABY2Neh6jz25gaARsfg19kpMo1D2sus`

## Review Focus

1. **Gespeichertes JSON aus der Jackson-2-Zeit** (Transition-Guards, polymorph mit `type`; `StringListConverter`-Listen) muss unter Jackson 3 lesbar bleiben und identisch zurückgeschrieben werden. → Task 1 `JsonCompatCharacterizationIntegrationTest` + `StringListConverterTest`.
2. **Vor dem Deploy ausgestellte JWTs** müssen nach dem jjwt-gson-Wechsel gültig bleiben (sonst werden alle Nutzer ausgeloggt). → Task 1 JWT-Fixture-Test.
3. **Frontend schickt unbekannte Felder** oder Werte, die Jackson 3 strenger behandelt (null für Primitive, Trailing Tokens): Das Verhalten muss gleich bleiben. → Task 1 Unknown-Property-Test + Task 2 Feature-Assertion.
4. **STOMP-Payloads wechseln still den Mapper.** → Task 1 STOMP-Guard (Jackson 2), in Task 2 auf Jackson 3 umgedreht.
5. **Swagger UI / `/v3/api-docs` im Dev-Profil** brechen, weil springdoc (Jackson 2) in einem Jackson-3-Spring-Context läuft. → Task 4 Dev-Smoke.

---

## File Structure

| Datei | Änderung | Verantwortung |
|---|---|---|
| `backend/src/test/kotlin/com/taskowolf/core/JsonCompatCharacterizationIntegrationTest.kt` | Create | gespeichertes JSON, Request-Toleranz, Fehlerformat |
| `backend/src/test/kotlin/com/taskowolf/integrations/StringListConverterTest.kt` | Create | DB-Format des Converters |
| `backend/src/test/kotlin/com/taskowolf/auth/JwtServiceTest.kt` | Modify | JWT-Fixture + Roundtrip |
| `backend/src/test/kotlin/com/taskowolf/core/JsonWireFormatGuardIntegrationTest.kt` | Modify | STOMP-Guard; in Task 2 auf Jackson 3 umgestellt + Feature-Assertion |
| `backend/build.gradle.kts` | Modify | jackson2-Modul raus, Kotlin-Modul 3, jjwt-gson, Kommentare |
| `backend/gradle.lockfile` | Regenerate | |
| `backend/src/main/resources/application.yml` | Modify | `preferred-json-mapper` raus, `use-jackson2-defaults: true` |
| `backend/src/main/kotlin/**` (10 Dateien) | Modify | Imports `tools.jackson`, `asString()` |
| `backend/src/main/kotlin/com/taskowolf/integrations/infrastructure/StringListConverter.kt` | Modify | ein gemeinsamer `JsonMapper` |
| `backend/src/test/kotlin/**` (~30 Dateien) | Modify | Imports `tools.jackson`, `asString()` |
| `docs/superpowers/specs/2026-10-19-m3-spring-boot-4-migration-design.md` | Modify | springdoc-Abweichung |
| `docs/superpowers/specs/2026-07-07-backlog-overview.md` | Modify | M3-Status |
| `mkdocs/developer-guide/ai-guide.md` | Modify | Jackson-3-Konventionen |
| `frontend/package.json`, `frontend/package-lock.json` | Modify (Task 7) | Version 1.0.21 |

---

### Task 1: Worktree, Baseline und Charakterisierungstests (noch auf Jackson 2)

**Files:**
- Create: `backend/src/test/kotlin/com/taskowolf/core/JsonCompatCharacterizationIntegrationTest.kt`
- Create: `backend/src/test/kotlin/com/taskowolf/integrations/StringListConverterTest.kt`
- Modify: `backend/src/test/kotlin/com/taskowolf/auth/JwtServiceTest.kt`
- Modify: `backend/src/test/kotlin/com/taskowolf/core/JsonWireFormatGuardIntegrationTest.kt`

**Interfaces:**
- Consumes: `IntegrationTestBase`; `TransitionGuard`/`RequiredFieldGuard`/`RoleRestrictionGuard` (`com.taskowolf.workflows.domain`); `StringListConverter`; `JwtService(secret, accessExpiry, refreshExpiry)` mit `validateToken(token): UUID?`, `extractOrgId(token): UUID?`, `generateAccessToken(userId, orgId)`.
- Produces: Tests, deren **Assertions** Task 2 und Task 3 unverändert lassen. Ändern dürfen sich nur Jackson-Imports und `asText`/`isTextual`-Aufrufe sowie die zwei Konverter-Assertions im Guard, die Task 2 ausdrücklich umdreht.

- [ ] **Step 1: Worktree anlegen**

`EnterWorktree` mit Namen `m3-phase2-jackson3`. Der Plan liegt lokal auf `main` und ist noch nicht gepusht. Wenn der Worktree von `origin/main` abzweigt, im Worktree `git merge --ff-only main` ausführen.

- [ ] **Step 2: Baseline**

Run: `cd backend && ./gradlew test`
Expected: `BUILD SUCCESSFUL`, **385** Tests (Stand nach Phase 1). Zählen:
```bash
cd backend && cat build/test-results/test/*.xml | awk '/<testsuite /{for(i=1;i<=NF;i++){if($i~/^tests="/){gsub(/[^0-9]/,"",$i);t+=$i} if($i~/^failures="/){gsub(/[^0-9]/,"",$i);f+=$i} if($i~/^errors="/){gsub(/[^0-9]/,"",$i);e+=$i}}} END{print "tests="t" failures="f" errors="e}'
```

- [ ] **Step 3: JSON-Kompatibilitätstests schreiben**

`backend/src/test/kotlin/com/taskowolf/core/JsonCompatCharacterizationIntegrationTest.kt`:
```kotlin
package com.taskowolf.core

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.taskowolf.IntegrationTestBase
import com.taskowolf.workflows.domain.RequiredFieldGuard
import com.taskowolf.workflows.domain.RoleRestrictionGuard
import com.taskowolf.workflows.domain.TransitionGuard
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * M3 Phase 2 characterization: written against Jackson 2, must stay green unchanged on Jackson 3.
 * Covers JSON that already sits in production databases and request tolerance the frontend relies on.
 */
class JsonCompatCharacterizationIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    // Exactly what WorkflowService persisted into workflow_transitions.guards under Jackson 2.
    private val storedGuards =
        """[{"type":"REQUIRED_FIELD","field":"assigneeId"},{"type":"ROLE_RESTRICTION","roles":["ADMIN","MEMBER"]}]"""

    private val guardListType = object : TypeReference<List<TransitionGuard>>() {}

    @Test
    fun `stored transition guards still deserialize polymorphically`() {
        val guards = objectMapper.readValue(storedGuards, guardListType)
        assertEquals(
            listOf(RequiredFieldGuard("assigneeId"), RoleRestrictionGuard(listOf("ADMIN", "MEMBER"))),
            guards
        )
    }

    @Test
    fun `transition guards re-serialize byte-identically`() {
        val guards: List<TransitionGuard> =
            listOf(RequiredFieldGuard("assigneeId"), RoleRestrictionGuard(listOf("ADMIN", "MEMBER")))
        assertEquals(storedGuards, objectMapper.writeValueAsString(guards))
    }

    private fun token(email: String): String =
        objectMapper.readTree(
            mockMvc.perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"email":"$email","displayName":"Compat","password":"password123"}""")
            ).andReturn().response.contentAsString
        ).get("accessToken").asText()

    @Test
    fun `unknown request properties are ignored`() {
        val t = token("jsoncompat-unknown@test.com")
        mockMvc.perform(
            post("/api/v1/projects")
                .header("Authorization", "Bearer $t")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"key":"JCU","name":"Compat","somethingTheFrontendAdds":true}""")
        ).andExpect(status().isCreated)
    }

    @Test
    fun `validation error keeps code, message and details shape`() {
        val t = token("jsoncompat-error@test.com")
        mockMvc.perform(
            post("/api/v1/projects")
                .header("Authorization", "Bearer $t")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"key":"JCE","name":"Compat"}""")
        ).andExpect(status().isCreated)

        val body = objectMapper.readTree(
            mockMvc.perform(
                post("/api/v1/projects/JCE/issues")
                    .header("Authorization", "Bearer $t")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"title":""}""")
            ).andExpect(status().isBadRequest).andReturn().response.contentAsString
        )
        assertEquals(setOf("code", "message", "details"), body.fieldNames().asSequence().toSet())
        assertTrue(body.get("code").isTextual, "code must be a string")
        assertTrue(body.get("message").isTextual, "message must be a string")
        assertTrue(body.get("details").isObject, "details must be an object")
    }
}
```

- [ ] **Step 4: Converter-Test schreiben**

`backend/src/test/kotlin/com/taskowolf/integrations/StringListConverterTest.kt`:
```kotlin
package com.taskowolf.integrations

import com.taskowolf.integrations.infrastructure.StringListConverter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** M3 Phase 2: the DB format of webhook event lists must not change with the Jackson 3 switch. */
class StringListConverterTest {

    private val converter = StringListConverter()

    @Test
    fun `writes a compact JSON array`() {
        assertEquals("""["issue.created","issue.updated"]""", converter.convertToDatabaseColumn(listOf("issue.created", "issue.updated")))
    }

    @Test
    fun `writes null and empty as empty array`() {
        assertEquals("[]", converter.convertToDatabaseColumn(null))
        assertEquals("[]", converter.convertToDatabaseColumn(emptyList()))
    }

    @Test
    fun `reads stored arrays and treats blank as empty`() {
        assertEquals(listOf("issue.created", "issue.updated"), converter.convertToEntityAttribute("""["issue.created","issue.updated"]"""))
        assertEquals(emptyList<String>(), converter.convertToEntityAttribute(""))
        assertEquals(emptyList<String>(), converter.convertToEntityAttribute(null))
    }
}
```

- [ ] **Step 5: JWT-Fixture-Tests ergänzen**

In `backend/src/test/kotlin/com/taskowolf/auth/JwtServiceTest.kt` die Imports ergänzen:
```kotlin
import org.junit.jupiter.api.Assertions.assertEquals
import java.util.UUID
```
und innerhalb der Klasse nach den bestehenden Tests einfügen:
```kotlin
    // Issued with jjwt-jackson 0.13.0 (M3 Phase 2, 2026-10-19), expires 2100-01-01.
    // Must stay valid after the switch to jjwt-gson, otherwise every user is logged out on deploy.
    private val fixtureSecret = "jwt-fixture-secret-0123456789abcdef"
    private val fixtureToken =
        "eyJhbGciOiJIUzI1NiJ9.eyJqdGkiOiIwMDAwMDAwMC0wMDAwLTAwMDAtMDAwMC0wMDAwMDAwMDAwYWEiLCJzdWIiOiIxMTExMTExMS0xMTExLTExMTEtMTExMS0xMTExMTExMTExMTEiLCJ0eXBlIjoiYWNjZXNzIiwib3JnSWQiOiIyMjIyMjIyMi0yMjIyLTIyMjItMjIyMi0yMjIyMjIyMjIyMjIiLCJpYXQiOjE3NjAwMDAwMDAsImV4cCI6NDEwMjQ0NDgwMH0.0EK0B6blrj-HABY2Neh6jz25gaARsfg19kpMo1D2sus"

    @Test
    fun `token issued with the previous JSON serializer stays valid`() {
        val service = JwtService(secret = fixtureSecret, accessExpiry = 900, refreshExpiry = 604800)
        assertEquals(UUID.fromString("11111111-1111-1111-1111-111111111111"), service.validateToken(fixtureToken))
        assertEquals(UUID.fromString("22222222-2222-2222-2222-222222222222"), service.extractOrgId(fixtureToken))
    }

    @Test
    fun `freshly issued access token round-trips user and org`() {
        val service = JwtService(secret = fixtureSecret, accessExpiry = 900, refreshExpiry = 604800)
        val userId = UUID.randomUUID()
        val orgId = UUID.randomUUID()
        val token = service.generateAccessToken(userId, orgId)
        assertEquals(userId, service.validateToken(token))
        assertEquals(orgId, service.extractOrgId(token))
    }
```

- [ ] **Step 6: STOMP-Guard ergänzen (Jackson 2)**

In `backend/src/test/kotlin/com/taskowolf/core/JsonWireFormatGuardIntegrationTest.kt` die Imports ergänzen:
```kotlin
import org.junit.jupiter.api.Assertions.assertFalse
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.messaging.converter.CompositeMessageConverter
import org.springframework.messaging.converter.JacksonJsonMessageConverter
import org.springframework.messaging.converter.MappingJackson2MessageConverter
```
und in der Klasse ergänzen:
```kotlin
    @Autowired
    @Qualifier("brokerMessageConverter")
    private lateinit var brokerMessageConverter: CompositeMessageConverter

    @Test
    fun `STOMP payloads use the Jackson 2 converter`() {
        val converters = brokerMessageConverter.converters
        assertTrue(converters.any { it is MappingJackson2MessageConverter }, "got $converters")
        assertFalse(converters.any { it is JacksonJsonMessageConverter }, "got $converters")
    }
```

- [ ] **Step 7: Neue Tests laufen lassen**

Run:
```bash
cd backend && ./gradlew test --tests "com.taskowolf.core.JsonCompatCharacterizationIntegrationTest" --tests "com.taskowolf.integrations.StringListConverterTest" --tests "com.taskowolf.auth.JwtServiceTest" --tests "com.taskowolf.core.JsonWireFormatGuardIntegrationTest"
```
Expected: alles PASS. Das sind 4 + 3 + 4 (2 bestehende + 2 neue) + 3 (2 bestehende + 1 neuer) = 14 Tests. Die Tests halten den **heutigen** Stand fest. Weicht der Stand ab, wird die Assertion auf den beobachteten Wert korrigiert (Beispiele: `writeValueAsString(guards)` liefert eine andere Reihenfolge; der Validierungsfehler hat einen anderen Status; `details` fehlt). Dafür die tatsächliche Ausgabe einmal per `println` ansehen, die Assertion anpassen und die Abweichung im Ledger und im Commit-Text notieren. **Assertions nie lockern**, nur auf den echten heutigen Wert setzen.

- [ ] **Step 8: Mutationscheck (beweist, dass die Tests rot werden können)**

Temporär in `StringListConverter.kt` `mapper.writeValueAsString(attr ?: emptyList<String>())` durch `mapper.writerWithDefaultPrettyPrinter().writeValueAsString(attr ?: emptyList<String>())` ersetzen und `./gradlew test --tests "com.taskowolf.integrations.StringListConverterTest"` laufen lassen.
Expected: FAIL in `writes a compact JSON array`. Danach die Änderung zurücknehmen (`git checkout -- backend/src/main/kotlin/com/taskowolf/integrations/infrastructure/StringListConverter.kt`) und erneut grün laufen lassen.

- [ ] **Step 9: Volle Suite und Commit**

Run: `cd backend && ./gradlew test`
Expected: `BUILD SUCCESSFUL`, **385 + 10 = 395** Tests (4 Compat + 3 Converter + 2 JWT + 1 STOMP).
```bash
git add backend/src/test
git commit -m "test(m3): characterization tests for the Jackson 3 switch

Stored transition guards (polymorphic), StringListConverter DB format,
unknown-property tolerance, error response shape, a JWT fixture issued
with jjwt-jackson, and the STOMP converter guard. All green on Jackson 2.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Umstellung auf Jackson 3

**Files:**
- Modify: `backend/build.gradle.kts`, `backend/gradle.lockfile`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/kotlin/com/taskowolf/integrations/infrastructure/StringListConverter.kt`
- Modify (Imports/asString): `audit/api/AuditController.kt`, `auth/infrastructure/AuthRateLimitFilter.kt`, `automation/application/ActionExecutor.kt`, `automation/application/AutomationEngine.kt`, `automation/application/ConditionEvaluator.kt`, `integrations/application/IncomingWebhookService.kt`, `integrations/application/WebhookDispatcher.kt`, `integrations/application/WebhookService.kt`, `workflows/application/WorkflowService.kt` (alle unter `backend/src/main/kotlin/com/taskowolf/`)
- Modify: `backend/src/test/kotlin/**` (Imports/asString/isTextual), `JsonWireFormatGuardIntegrationTest.kt` (Konverter-Assertions umdrehen + Feature-Test)

**Interfaces:**
- Consumes: Tests aus Task 1 (Assertions unverändert).
- Produces: Spring-Bean `tools.jackson.databind.ObjectMapper` (konkret `JsonMapper`) als einziger aktiver Mapper. Alle Konstruktor-Injektionen in Main nutzen `tools.jackson.databind.ObjectMapper`.

- [ ] **Step 1: Guard auf Jackson 3 umstellen (RED)**

In `JsonWireFormatGuardIntegrationTest.kt`:
- Import `org.springframework.http.converter.json.MappingJackson2HttpMessageConverter` → `org.springframework.http.converter.json.JacksonJsonHttpMessageConverter`.
- Den Test `first JSON converter is the Jackson 2 converter` ersetzen durch:
```kotlin
    @Test
    fun `first JSON converter is the Jackson 3 converter`() {
        val firstJson = handlerAdapter.messageConverters
            .first { it.canWrite(Map::class.java, MediaType.APPLICATION_JSON) }
        assertTrue(
            firstJson is JacksonJsonHttpMessageConverter,
            "expected Jackson 3 converter first, got ${firstJson::class.qualifiedName}"
        )
    }
```
- Den Test `STOMP payloads use the Jackson 2 converter` ersetzen durch:
```kotlin
    @Test
    fun `STOMP payloads use the Jackson 3 converter`() {
        val converters = brokerMessageConverter.converters
        assertTrue(converters.any { it is JacksonJsonMessageConverter }, "got $converters")
        assertFalse(converters.any { it is MappingJackson2MessageConverter }, "got $converters")
    }
```
- Den KDoc-Satz `while the backend still runs Jackson 2 (Phase 1 of the Boot 4 migration), HTTP JSON must be written by the Jackson 2 converter` ersetzen durch `HTTP and STOMP JSON must be written by the Jackson 3 converters`. Den Satz zu Phase 2 streichen.

Run: `cd backend && ./gradlew test --tests "com.taskowolf.core.JsonWireFormatGuardIntegrationTest"`
Expected: FAIL in beiden Konverter-Tests (noch Jackson 2 aktiv).

- [ ] **Step 2: Build umstellen**

In `backend/build.gradle.kts`:
- Die Zeilen löschen:
```kotlin
    // M3 Phase 1: keep Jackson 2 as the active mapper (HTTP + STOMP, see application.yml).
    // Removed in Phase 2 together with all com.fasterxml.jackson.databind usages.
    implementation("org.springframework.boot:spring-boot-jackson2")
```
- `implementation("com.fasterxml.jackson.module:jackson-module-kotlin")` → `implementation("tools.jackson.module:jackson-module-kotlin")`
- Im Kommentarblock über `extra["jackson-2-bom.version"]` den Satz `Jackson 2 is only needed until M3 Phase 2 (Jackson 3 + jjwt-gson); this override goes away with it.` ersetzen durch:
```kotlin
// Jackson 2 is no longer used by our code or by Spring, but springdoc 3.1.1 still pulls it in
// transitively via swagger-core-jakarta 2.2.55 (no Jackson 3 swagger-core exists yet). Keep this
// override until swagger-core moves to Jackson 3.
```
- Im Kommentarblock über `extra["jackson-bom.version"]` den Satz `Jackson 3 is on the classpath via spring-boot-starter-webmvc even while Phase 1 keeps Jackson 2 as the active mapper.` ersetzen durch `Jackson 3 is the active mapper (HTTP, STOMP, application code).`

- [ ] **Step 3: Config umstellen**

In `backend/src/main/resources/application.yml` den Block
```yaml
  # M3 Phase 1: Spring Boot 4 defaults to Jackson 3. Keep Jackson 2 for HTTP and STOMP payloads
  # until Phase 2 migrates the code base to tools.jackson. Remove both keys in Phase 2.
  http:
    converters:
      preferred-json-mapper: jackson2
  websocket:
    messaging:
      preferred-json-mapper: jackson2
```
ersetzen durch:
```yaml
  jackson:
    # Jackson 3 ships stricter/different defaults (alphabetical property order, FAIL_ON_NULL_FOR_PRIMITIVES,
    # FAIL_ON_TRAILING_TOKENS). Keep the defaults Spring Boot used for Jackson 2 so API payloads and the
    # JSON stored in the database stay identical. Guards: JsonWireFormatGuardIntegrationTest,
    # JsonCompatCharacterizationIntegrationTest.
    use-jackson2-defaults: true
```

- [ ] **Step 4: Imports und deprecated API mechanisch umstellen (Main + Test)**

```bash
cd backend
grep -rlE "com\.fasterxml\.jackson\.(databind|core|module)|\.asText\(|isTextual" src/main/kotlin src/test/kotlin > ../m3-p2-files.txt
while IFS= read -r f; do
  perl -pi -e '
    s/import com\.fasterxml\.jackson\.databind\./import tools.jackson.databind./;
    s/import com\.fasterxml\.jackson\.core\.type\.TypeReference/import tools.jackson.core.type.TypeReference/;
    s/import com\.fasterxml\.jackson\.module\.kotlin\./import tools.jackson.module.kotlin./;
    s/\.asText\(\)/.asString()/g;
    s/\.asText\(""\)/.asString("")/g;
    s/\.isTextual\b/.isString/g;
  ' "$f"
done < ../m3-p2-files.txt
rm ../m3-p2-files.txt
grep -rn "import com.fasterxml.jackson" src | grep -v "\.annotation\."
```
Expected (letzter Befehl): **keine Ausgabe**. Es bleiben nur `com.fasterxml.jackson.annotation.*`-Imports (`TransitionGuard.kt`).
Prüfen, dass `.asString(` nur an `JsonNode`-Ausdrücken ersetzt wurde: `grep -rn "\.asString(" src | grep -vE "path\(|get\(|readTree" ` durchsehen und unpassende Ersetzungen zurücknehmen.

- [ ] **Step 5: `StringListConverter` auf einen gemeinsamen `JsonMapper` umstellen**

`backend/src/main/kotlin/com/taskowolf/integrations/infrastructure/StringListConverter.kt` komplett ersetzen:
```kotlin
package com.taskowolf.integrations.infrastructure

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper

@Converter
class StringListConverter : AttributeConverter<List<String>, String> {

    override fun convertToDatabaseColumn(attr: List<String>?): String =
        mapper.writeValueAsString(attr ?: emptyList<String>())

    override fun convertToEntityAttribute(dbData: String?): List<String> =
        if (dbData.isNullOrBlank()) emptyList() else mapper.readValue(dbData, typeRef)

    private companion object {
        // JPA instantiates converters itself (no Spring injection), so the converter owns one
        // immutable Jackson 3 mapper. A list of strings is unaffected by Jackson 3 default changes.
        val mapper: JsonMapper = JsonMapper.builder().build()
        val typeRef = object : TypeReference<List<String>>() {}
    }
}
```

- [ ] **Step 6: Lockfile, kompilieren**

Run:
```bash
cd backend && ./gradlew dependencies --write-locks > /dev/null && grep -E "^tools.jackson.module:jackson-module-kotlin:|^org.springframework.boot:spring-boot-jackson2:|^com.fasterxml.jackson.module:jackson-module-kotlin:" gradle.lockfile
./gradlew compileKotlin compileTestKotlin
```
Expected: Das Lockfile zeigt `tools.jackson.module:jackson-module-kotlin:3.1.7`; **keine** Zeilen für `spring-boot-jackson2` und `com.fasterxml.jackson.module:jackson-module-kotlin`. Kompilieren endet mit `BUILD SUCCESSFUL`. Bleibende Kompilierfehler beheben, ohne Logik zu ändern. Typische Fälle in Jackson 3:
- `ObjectMapper` ist immutable: `configure(...)`/`registerModule(...)` → `JsonMapper.builder()...build()`.
- `JsonProcessingException` → `tools.jackson.core.JacksonException`.
- `fieldNames()` gibt in Jackson 3 eine `Collection`/`Set` statt eines `Iterator` zurück → im Compat-Test `body.fieldNames().asSequence().toSet()` durch `body.propertyNames().toSet()` ersetzen, falls `fieldNames()` nicht kompiliert. Die Assertion (`setOf("code","message","details")`) bleibt gleich.

- [ ] **Step 7: Feature-Assertion ergänzen und Guard grün sehen**

In `JsonWireFormatGuardIntegrationTest.kt` die Imports ergänzen:
```kotlin
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.MapperFeature
import tools.jackson.databind.cfg.DateTimeFeature
```
und den Test hinzufügen:
```kotlin
    @Test
    fun `Jackson 3 runs with the Jackson 2 defaults`() {
        assertFalse(objectMapper.isEnabled(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY), "property order must stay declaration order")
        assertFalse(objectMapper.isEnabled(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES), "null for primitives must stay lenient")
        assertFalse(objectMapper.isEnabled(DeserializationFeature.FAIL_ON_TRAILING_TOKENS), "trailing tokens must stay lenient")
        assertFalse(objectMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES), "unknown properties must be ignored")
        assertFalse(objectMapper.isEnabled(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS), "dates must be ISO strings")
    }
```
Run: `cd backend && ./gradlew test --tests "com.taskowolf.core.JsonWireFormatGuardIntegrationTest" --tests "com.taskowolf.core.JsonCompatCharacterizationIntegrationTest"`
Expected: alle PASS (4 Guard + 4 Compat). Deckt `use-jackson2-defaults` ein Feature nicht ab, kommt es explizit in `application.yml` unter denselben `spring.jackson`-Knoten, z. B.:
```yaml
    mapper:
      sort-properties-alphabetically: false
    deserialization:
      fail-on-null-for-primitives: false
      fail-on-trailing-tokens: false
```
Die Assertion bleibt dabei unverändert; das Ganze wird als Ruling im Ledger notiert.

- [ ] **Step 8: Volle Suite**

Run: `cd backend && ./gradlew test`
Expected: `BUILD SUCCESSFUL`, **396** Tests (395 + Feature-Test). Fehlschläge nach `superpowers:systematic-debugging` angehen; die Assertions aus Task 1 werden nicht angefasst.

- [ ] **Step 9: Commit**

```bash
git add backend/
git commit -m "refactor(m3): switch backend to Jackson 3 (tools.jackson)

- drop spring-boot-jackson2 and the preferred-json-mapper=jackson2 keys;
  Jackson 3 now serves HTTP and STOMP
- spring.jackson.use-jackson2-defaults=true keeps property order and the
  lenient null/trailing-token handling, so payloads and stored JSON are unchanged
- tools.jackson imports (annotations stay com.fasterxml), asText -> asString
- jackson-module-kotlin 3; StringListConverter owns one immutable JsonMapper

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: jjwt auf Gson

**Files:**
- Modify: `backend/build.gradle.kts`, `backend/gradle.lockfile`

**Interfaces:**
- Consumes: JWT-Fixture-Tests aus Task 1 (`JwtServiceTest`).
- Produces: jjwt ohne Jackson-2-Abhängigkeit; Jackson 2 nur noch transitiv über springdoc.

- [ ] **Step 1: Dependency tauschen**

In `backend/build.gradle.kts`: `runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")` → `runtimeOnly("io.jsonwebtoken:jjwt-gson:0.13.0")`.
Direkt darüber den Kommentar einfügen:
```kotlin
    // jjwt-gson instead of jjwt-jackson (M3 Phase 2): jjwt-jackson only exists for Jackson 2, and
    // untrusted JWT headers/claims should not go through Jackson 2. Token format is unchanged
    // (guard: JwtServiceTest fixture token issued with jjwt-jackson).
```

- [ ] **Step 2: Lockfile und Nachweis, wer noch Jackson 2 zieht**

Run:
```bash
cd backend && ./gradlew dependencies --write-locks > /dev/null
grep -E "^io.jsonwebtoken:|^com.google.code.gson:gson:" gradle.lockfile
./gradlew dependencyInsight --dependency com.fasterxml.jackson.core:jackson-databind --configuration runtimeClasspath | grep -E "jjwt|springdoc|swagger" | sort -u
```
Expected: `jjwt-gson:0.13.0` und `gson:2.13.2` im Lockfile, kein `jjwt-jackson`. Die `dependencyInsight`-Ausgabe nennt nur Pfade über `springdoc`/`swagger-core`, **kein** `jjwt`.

- [ ] **Step 3: Tests**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.auth.JwtServiceTest"` und danach `./gradlew test`
Expected: Der Fixture-Test `token issued with the previous JSON serializer stays valid` ist PASS (unverändert), die volle Suite `BUILD SUCCESSFUL` mit **396** Tests. Gson ist jetzt auf dem Classpath, Spring bekommt aber ohne `spring-boot-gson` keine Gson-Autokonfiguration. Der Guard `first JSON converter is the Jackson 3 converter` belegt das.

- [ ] **Step 4: Commit**

```bash
git add backend/build.gradle.kts backend/gradle.lockfile
git commit -m "build(m3): jjwt-gson instead of jjwt-jackson

Jackson 2 is now only a transitive dependency of springdoc/swagger-core.
Tokens issued before the switch stay valid (JwtServiceTest fixture).

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Security-Nachweis und Smoke

**Files:** keine (außer neuen `extra[...]`-Overrides, falls Trivy etwas findet)

**Interfaces:**
- Consumes: Stand nach Task 3.
- Produces: Belege (Trivy fs + Image = 0 HIGH/CRITICAL; Dev- und Container-Smoke grün).

- [ ] **Step 1: Trivy-FS-Scan gegen frische DB**

Run (Repo-Root des Worktrees, absoluter Windows-Pfad statt `$(pwd)`):
```bash
MSYS_NO_PATHCONV=1 docker run --rm -v "C:/Users/Admin/IdeaProjects/TaskWolf/.claude/worktrees/m3-phase2-jackson3:/src" aquasec/trivy:latest fs --scanners vuln --severity HIGH,CRITICAL --ignorefile /src/.trivyignore --exit-code 1 /src/backend
```
Expected: Exit 0, `gradle.lockfile` mit 0 Findings. Taucht ein neues Finding auf (z. B. in Gson): minimale Fix-Version als `extra["<lib>.version"]`-Override mit CVE-Kommentar ergänzen, Lockfile neu schreiben, Suite laufen lassen, committen. Kein `.trivyignore`-Eintrag ohne Rückfrage bei Wolfgang.

- [ ] **Step 2: Build-Classpath prüfen (dependency-review sieht ihn, Trivy nicht)**

Run: `cd backend && ./gradlew buildEnvironment | grep -E "tools.jackson.core:jackson-(core|databind)|com.fasterxml.jackson.core:jackson-(core|databind)" | sort -u`
Expected: Jackson 3 nur in 3.1.7 (bzw. `-> 3.1.7`). Erscheint Jackson 2 < 2.21.7 im Build-Classpath, im bestehenden `buildscript { dependencies { constraints { ... } } }`-Block `classpath("com.fasterxml.jackson.core:jackson-databind:2.21.7")` (plus core) ergänzen.

- [ ] **Step 3: Backend-Image bauen und scannen**

```bash
cd backend && ./gradlew bootJar && docker build -t taskowolf-backend:m3p2-local .
MSYS_NO_PATHCONV=1 docker run --rm -v //var/run/docker.sock:/var/run/docker.sock -v "C:/Users/Admin/IdeaProjects/TaskWolf/.claude/worktrees/m3-phase2-jackson3/.trivyignore:/.trivyignore" aquasec/trivy:latest image --severity HIGH,CRITICAL --ignorefile /.trivyignore --exit-code 1 taskowolf-backend:m3p2-local
```
Expected: Exit 0; Tabelle mit `alpine` 0 und `app/app.jar` 0.

- [ ] **Step 4: Dev-Smoke inkl. Swagger (Review Focus 5)**

Das Dev-Profil mit H2 startet nicht (V11+ nutzt `gen_random_uuid()`, Nebenbefund schon vor M3), deshalb gegen einen temporären Postgres:
```bash
docker run -d --rm --name m3p2-smoke-pg -e POSTGRES_DB=taskowolf -e POSTGRES_USER=tw -e POSTGRES_PASSWORD=tw -p 55432:5432 postgres:16-alpine
```
Dann im Hintergrund (`run_in_background`):
```bash
cd backend && ./gradlew bootRun --args='--spring.profiles.active=dev --spring.datasource.url=jdbc:postgresql://localhost:55432/taskowolf --spring.datasource.username=tw --spring.datasource.password=tw --spring.datasource.driver-class-name=org.postgresql.Driver'
```
Auf `Started TaskWolfApplicationKt` im Log warten, dann:
```bash
for u in /v3/api-docs /swagger-ui/index.html /api/v1/admin/sso/public; do printf "%s -> " $u; curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080$u; done
curl -s http://localhost:8080/v3/api-docs | head -c 200
```
Expected: alle `200`; `/v3/api-docs` beginnt mit `{"openapi":"3.`.
Aufräumen: Den Gradle-Task stoppen **und** die geforkte JVM beenden. `TaskStop` beendet nur Gradle; die JVM auf Port 8080 per PowerShell `Get-NetTCPConnection -LocalPort 8080 -State Listen` finden, prüfen, dass ihre CommandLine den Worktree-Pfad enthält, und dann `Stop-Process`. Danach `docker stop m3p2-smoke-pg`.

- [ ] **Step 5: Container-Smoke im isolierten Stack**

Override-Datei im Scratchpad (`m3p2-override.yml`):
```yaml
services:
  app:
    image: taskowolf-backend:m3p2-local
```
Env-Datei im Scratchpad (`m3p2smoke.env`):
```
TW_DB_PASS=m3p2smoke-db-pass
TW_JWT_SECRET=m3p2smoke-jwt-secret-0123456789abcdef0123456789
```
Start (aus dem Worktree-Root, Projekt `m3p2smoke`, eigene Volumes; Wolfgangs echter Stack bleibt unberührt):
```bash
docker compose -p m3p2smoke --env-file <scratchpad>/m3p2smoke.env -f docker-compose.prod.yml -f <scratchpad>/m3p2-override.yml up -d
```
Auf `Started TaskWolfApplicationKt` in `docker logs m3p2smoke-app-1` warten. Dann über nginx (Port 80) prüfen:
- Frontend `/`: 200
- Register und Login: Token vorhanden
- Projekt anlegen: 201
- Issue anlegen: 201, Body mit `"description":null`, `"type":"TASK"` und ISO-`createdAt`
- Issue-Liste und Workflows: 200
- `/ws/info`: 200 mit `"websocket":true`
- `/api/v1/projects` ohne Token: 401
- Transition-Guard über das echte Speicherformat (`WorkflowEditorController`, `/api/v1/projects/{key}/workflow`). `TransitionResponse.guards` gibt das gespeicherte JSON als **String** zurück:
  1. `GET /api/v1/projects/SMK/workflow`: aus `statuses` zwei IDs nehmen (A, B).
  2. `POST /api/v1/projects/SMK/workflow/transitions` mit `{"fromStatusId":"<A>","toStatusId":"<B>"}`: daraus die `id` (TID) nehmen.
  3. `PUT /api/v1/projects/SMK/workflow/transitions/<TID>/guards` mit `{"guards":[{"type":"REQUIRED_FIELD","field":"assigneeId"},{"type":"ROLE_RESTRICTION","roles":["ADMIN"]}]}`.
  Expected: 200; im Response ist `guards` exakt der String `[{"type":"REQUIRED_FIELD","field":"assigneeId"},{"type":"ROLE_RESTRICTION","roles":["ADMIN"]}]`, also dasselbe Format wie unter Jackson 2 (vgl. `storedGuards` in Task 1).

Danach abbauen: `docker compose -p m3p2smoke ... down -v`.

---

### Task 5: Doku

**Files:**
- Modify: `docs/superpowers/specs/2026-10-19-m3-spring-boot-4-migration-design.md`
- Modify: `docs/superpowers/specs/2026-07-07-backlog-overview.md`
- Modify: `mkdocs/developer-guide/ai-guide.md`

- [ ] **Step 1: Spec-Abweichung dokumentieren**

In der Spec im Abschnitt `### 4. jjwt auf Gson` den Punkt `- Danach **ist Jackson 2 nicht mehr im Runtime-Classpath** ...` ersetzen durch:
```markdown
- **Angepasst 2026-10-19 (Wolfgangs Entscheidung):** springdoc 3.1.1 zieht über `swagger-core-jakarta`
  2.2.55 (neueste Version, keine Jackson-3-Variante) weiterhin Jackson 2 transitiv. springdoc bleibt;
  Jackson 2 wird von unserem Code, von Spring und von jjwt nicht mehr genutzt, bleibt aber im
  Classpath. `jackson-2-bom.version` (2.21.7) und die zugehörige CVE-Pflege bleiben, bis swagger-core
  auf Jackson 3 umsteigt.
```
Im Abschnitt `### 2. Default-Unterschiede Jackson 3` den Satz ergänzen: `Umgesetzt über spring.jackson.use-jackson2-defaults: true (Boot-4-Schalter); die Feature-Assertion in JsonWireFormatGuardIntegrationTest belegt es.`

- [ ] **Step 2: Backlog**

In der Tabellenzeile `| M3 | ...` den Status ersetzen durch:
`✅ **AUSGELIEFERT** (Phase 1 PR #156: Boot 4.1.1, spring-webmvc 7.0.9, beide Spring-trivyignores entfernt; Phase 2 PR #<nr>: Jackson 3 + jjwt-gson; Release v1.0.21). Jackson 2 bleibt nur transitiv über springdoc/swagger-core`
Den Status-Block im Abschnitt `## M3` entsprechend auf „ausgeliefert" setzen und den Plan-Pfad von Phase 2 ergänzen. `#<nr>` wird nach dem PR-Anlegen in Task 6 nachgetragen.

- [ ] **Step 3: ai-guide**

Im Abschnitt `### Backend: Spring Boot 4 Conventions` den Jackson-Bullet ersetzen durch:
```markdown
- **Jackson 3:** `tools.jackson.databind.ObjectMapper`/`JsonNode`, `tools.jackson.core.type.TypeReference`,
  `tools.jackson.module.kotlin.readValue`. **Annotationen bleiben** `com.fasterxml.jackson.annotation.*`.
  `JsonNode.asText()` ist deprecated → `asString()`. Jackson-Exceptions sind unchecked (`JacksonException`).
  `ObjectMapper` ist immutable → eigene Mapper nur über `JsonMapper.builder()...build()`. Konfiguration über
  `spring.jackson.*`; `use-jackson2-defaults: true` hält Property-Reihenfolge und lockeres null-/Trailing-Token-
  Handling (Guards: `JsonWireFormatGuardIntegrationTest`, `JsonCompatCharacterizationIntegrationTest`).
  Jackson 2 (`com.fasterxml.jackson.databind`) nicht mehr importieren; es ist nur noch transitiv über springdoc da.
```
und den Bullet zu den JSpecify-Beispielen unverändert lassen.

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/specs mkdocs/developer-guide/ai-guide.md
git commit -m "docs(m3): Phase 2 status, Jackson 3 conventions, springdoc deviation

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: PR, CI und Merge

- [ ] **Step 1: Finale Verifikation im Worktree**

Run: `cd backend && ./gradlew test --rerun`
Expected: `BUILD SUCCESSFUL`, 396 Tests, 0 Failures. Außerdem: `grep -rn "import com.fasterxml.jackson" backend/src | grep -v "\.annotation\."` liefert keine Ausgabe.

- [ ] **Step 2: Push und PR**

```bash
git push -u origin <worktree-branch>
gh pr create --base main --head <worktree-branch> --title "M3 Phase 2: Jackson 3 + jjwt-gson" --body-file <scratchpad>/pr-body-p2.md
```
Der PR-Body fasst zusammen:
- die Umstellung inkl. `use-jackson2-defaults`,
- die Charakterisierungstests,
- den JWT-Fixture-Nachweis,
- die springdoc-Abweichung (Jackson 2 bleibt transitiv),
- Trivy- und Smoke-Ergebnisse,
- den Hinweis „nach dem Merge: Release v1.0.21 nach Wolfgangs manuellem SSO/IMAP-Check".

Ende des Bodys: `🤖 Generated with [Claude Code](https://claude.com/claude-code)`. Dann die PR-Nummer in der Backlog-Zeile nachtragen (`#<nr>`), committen und pushen.

- [ ] **Step 3: CI**

Run: `gh pr checks <nr> --watch --interval 30`
Expected: alle Checks grün. Das gilt ausdrücklich auch für `dependency-review` (schaut auf den Build-Classpath und Gson) und für `frontend-docker` (neue Basisimage-CVEs werden wie in Phase 1 per `apk upgrade` im Frontend-Dockerfile behoben, mit CVE-Kommentar). Rote Checks über `gh run view <run-id> --log-failed` analysieren und beheben.

- [ ] **Step 4: Merge (nach Wolfgangs OK)**

Wolfgang führt den Merge **im Haupt-Checkout** aus: `! gh pr merge <nr> --squash --delete-branch`. Danach:
- Worktree verlassen (`ExitWorktree` keep).
- Im Haupt-Checkout `git fetch origin` und `git rebase origin/main`. Lokale Plan-Commits, die schon im Squash stecken, fallen dabei als „patch contents already upstream" weg.
- Auf Wolfgangs Wunsch Worktree, Branch und Smoke-Image (`taskowolf-backend:m3p2-local`) entfernen.

---

### Task 7: Release v1.0.21

**Voraussetzungen (Stopp, wenn nicht erfüllt):** Wolfgang hat den SSO/OIDC-Login unter Security 7 manuell geprüft, ebenso den IMAP-Eingang, falls er ihn nutzt, und gibt das Release ausdrücklich frei.

- [ ] **Step 1: Version anheben**

Im Haupt-Checkout auf `main` (aktuell, sauber):
```bash
cd frontend && npm version 1.0.21 --no-git-tag-version && cd ..
git diff --stat
```
Expected: nur `frontend/package.json` und `frontend/package-lock.json`, je `"version": "1.0.21"`.
```bash
git add frontend/package.json frontend/package-lock.json
git commit -m "chore(release): v1.0.21

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
git push origin main
```
(Ein Push auf `main` kann von der Ruleset/dem Auto-Mode-Classifier blockiert werden; dann führt Wolfgang ihn per `!` aus.)

- [ ] **Step 2: GitHub-Release**

Format der vorigen Release-Notes ansehen: `gh release view v1.0.20`. Danach analog anlegen:
```bash
gh release create v1.0.21 --target main --title "v1.0.21" --notes-file <scratchpad>/release-notes-v1.0.21.md
```
Die Notes enthalten:
- Spring Boot 4.1.1 / Framework 7.0.9; behobene CVEs CVE-2026-47884 und CVE-2026-47890 (vorher nur per trivyignore geparkt), Tomcat 11.0.25 und Jackson 3.1.7 mit ihren CVE-Nummern; Jackson 3 als Mapper; jjwt-gson; libx11 im Frontend-Image (CVE-2026-88806),
- für Betreiber: keine Migration, keine Config-Änderung nötig; bestehende Logins bleiben gültig,
- Hinweis: Das Dev-Profil mit H2 ist weiterhin nicht lauffähig (bekannter Nebenbefund).

- [ ] **Step 3: docker-publish beobachten**

Run: `gh run list --workflow docker-publish.yml --limit 1` und dann `gh run watch <run-id>`
Expected: `success`; Docker Hub hat `kwolfgang/taskowolf-backend:1.0.21` und `kwolfgang/taskowolf-frontend:1.0.21` (prüfen mit `docker manifest inspect kwolfgang/taskowolf-backend:1.0.21 > /dev/null && echo ok`).

- [ ] **Step 4: Abschluss**

- Dependabot-Alerts #109/#110 (spring-webmvc) prüfen; sie sollten nach dem Merge automatisch geschlossen sein: `MSYS_NO_PATHCONV=1 gh api repos/WolfTasks/TaskWolf/dependabot/alerts/109 --jq .state` (ebenso 110).
- Memory `project-m3-spring-boot-4.md` auf „released v1.0.21" setzen.
- Wolfgang auf den Post-Release-Smoke seines echten Stacks hinweisen: `docker-compose.prod.yml` pull und up; nach dem Recreate bei 502 `docker compose restart nginx`.
