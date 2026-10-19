# M3 Phase 1 — Spring Boot 4.1.1 (Jackson-2-Kompat) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Backend von Spring Boot 3.5.16 auf 4.1.1 (Framework 7.0.9) heben. Jackson 2 bleibt über den Kompatmodus aktiv. Danach werden die beiden spring-webmvc-CVEs aus `.trivyignore` entfernt, das Ergebnis wird auf `main` gemergt. **Kein Release.**

**Architecture:** Explizite modulare Boot-4-Starter statt `*-classic`, alles von Hand umgestellt. Zuerst kommen auf dem Boot-3-Stand Guard-Tests dazu (Flyway läuft, HTTP-JSON kommt von Jackson 2, Draht-Format unverändert), die nach dem Upgrade unverändert grün bleiben müssen. Danach Build, Main-Code, Test-Code, Config und Security-Nachweis in dieser Reihenfolge.

**Tech Stack:** Kotlin 2.4.20, Gradle 9.8 (Kotlin DSL, Dependency-Locking), Spring Boot 4.1.1, Spring Security 7.1.1, Hibernate 7.4.5, Testcontainers 2.0.5, springdoc 3.1.1, JUnit 5 + MockK, Trivy.

**Spec:** `docs/superpowers/specs/2026-10-19-m3-spring-boot-4-migration-design.md` (Abschnitt „Phase 1")

## Global Constraints

- Spring Boot exakt **4.1.1** (Framework 7.0.9). Kein 4.2.0-Milestone.
- Kotlin-Plugins bleiben **2.4.20**; `jvmTarget` 21; `java.sourceCompatibility` 21; Runtime-Image unverändert.
- Jackson bleibt in Phase 1 **Jackson 2** (`com.fasterxml.jackson.*`): `spring-boot-jackson2` + `spring.http.converters.preferred-json-mapper: jackson2` + `spring.websocket.messaging.preferred-json-mapper: jackson2`.
- `jackson-2-bom.version` = **2.21.7** (Boot 4.1.1 pinnt 2.21.5 → CVE-2026-68497/91776/91777).
- Keine Änderung an API-Shapes, Fehlerformat, i18n-Texten, DB-Schema/Flyway-Migrationen (Stand V30), Frontend.
- Backend-Unit-Tests nutzen **MockK** (nicht Mockito). Ausnahme: `@MockitoBean` für den Spring-Context-Mock in `IntegrationTestBase`, so wie bisher `@MockBean`.
- Alle Arbeiten in einem isolierten Worktree; nach Subagent-Arbeit `git status` im Haupt-Checkout prüfen (muss sauber sein).
- Commit-Messages enden mit `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`; PR-Body endet mit `🤖 Generated with [Claude Code](https://claude.com/claude-code)`.

## Review Focus

1. **Flyway läuft still nicht** (Boot 4 konfiguriert Flyway nur mit `spring-boot-starter-flyway`). Erwartung: Alle 30 Migrationen sind beim Start angewendet. → Task 1 `FlywayMigrationsAppliedIntegrationTest`.
2. **HTTP-JSON kommt plötzlich von Jackson 3**, weil `starter-webmvc` `starter-jackson` (Jackson 3) zieht. Das würde Datums-/null-Handling ändern. Erwartung: Der aktive JSON-Converter ist `MappingJackson2HttpMessageConverter`. → Task 1 `JsonWireFormatGuardIntegrationTest`.
3. **Draht-Format einer Issue-Antwort ändert sich** (`createdAt` als ISO-String, `description: null` bleibt als Key vorhanden). Erwartung: bytegleich zu Boot 3. → Task 1 `JsonWireFormatGuardIntegrationTest`.
4. **Dev-Profil: `/h2-console` liefert 404**, weil die H2-Konsole in Boot 4 ein eigenes Modul ist. Erwartung: Die Konsole funktioniert im Dev-Profil weiter. → Task 2 (`spring-boot-h2console`) + manueller Check in Task 4.
5. **Umbenannte Properties werden still ignoriert** (z. B. Multipart-Limit, Mail, Messages). Erwartung: Nach dem Upgrade gibt es keine Migrator-Warnung mehr. → Task 4 (properties-migrator).

---

## File Structure

| Datei | Änderung | Verantwortung |
|---|---|---|
| `backend/build.gradle.kts` | Modify | Boot-Plugin, Starter, Overrides, TC-BOM |
| `backend/gradle.lockfile` | Regenerate | gelockte Versionen |
| `backend/src/main/resources/application.yml` | Modify | `preferred-json-mapper` (HTTP + STOMP) auf jackson2 |
| `backend/src/main/kotlin/**` | Modify (compiler-getrieben) | Framework-7/Security-7/Hibernate-7-API-Brüche |
| `backend/src/test/kotlin/com/taskowolf/IntegrationTestBase.kt` | Modify | `@MockitoBean`, TC-2-Container, neue Paketpfade |
| `backend/src/test/kotlin/com/taskowolf/audit/AuditEventRepositoryTest.kt` | Modify | neue `DataJpaTest`-/`AutoConfigureTestDatabase`-Pakete |
| `backend/src/test/kotlin/com/taskowolf/auth/SsoConfigRepositoryTest.kt` | Modify | dito |
| `backend/src/test/kotlin/com/taskowolf/core/FlywayMigrationsAppliedIntegrationTest.kt` | Create | Guard gegen die Flyway-Starter-Falle |
| `backend/src/test/kotlin/com/taskowolf/core/JsonWireFormatGuardIntegrationTest.kt` | Create | Guard: Jackson 2 aktiv + Draht-Format |
| `.trivyignore` | Modify | zwei Spring-Einträge entfernen |
| `docs/superpowers/specs/2026-07-07-backlog-overview.md` | Modify | M3-Status |
| `mkdocs/developer-guide/ai-guide.md` | Modify | Boot-4-Patterns, Flyway-Stand V30 |

---

### Task 1: Worktree, Baseline und Guard-Tests (noch auf Boot 3.5.16)

**Files:**
- Create: `backend/src/test/kotlin/com/taskowolf/core/FlywayMigrationsAppliedIntegrationTest.kt`
- Create: `backend/src/test/kotlin/com/taskowolf/core/JsonWireFormatGuardIntegrationTest.kt`

**Interfaces:**
- Consumes: `com.taskowolf.IntegrationTestBase` (Postgres-Testcontainer, `@SpringBootTest` + `@AutoConfigureMockMvc`)
- Produces: zwei Guard-Testklassen, die Task 3 unverändert grün halten muss. Die Datei-Imports passt Task 3 nur an, wenn ein Paket umgezogen ist.

- [ ] **Step 1: Worktree anlegen**

Per `superpowers:using-git-worktrees` bzw. `EnterWorktree` einen Worktree anlegen, Branch `m3-phase1-spring-boot-4`, Basis `main`. Auf `main` liegen lokal **ungepushte** Commits (Spec `5ce3b8c`, dieser Plan). Wenn der Worktree von `origin/main` abzweigt, im Worktree `git merge --ff-only main` ausführen, damit Spec und Plan vorhanden sind.

- [ ] **Step 2: Baseline-Suite laufen lassen**

Run (im Worktree): `cd backend && ./gradlew test`
Expected: `BUILD SUCCESSFUL`. Die Anzahl der Tests aus `backend/build/test-results/test/*.xml` notieren:
```bash
cd backend && grep -ho 'tests="[0-9]*"' build/test-results/test/*.xml | grep -o '[0-9]*' | paste -sd+ | bc
```
Diese Zahl ist die Baseline N. Nach Task 3 muss die Suite N + 3 Tests haben (die 3 neuen Guard-Tests).

- [ ] **Step 3: Flyway-Guard schreiben**

`backend/src/test/kotlin/com/taskowolf/core/FlywayMigrationsAppliedIntegrationTest.kt`:
```kotlin
package com.taskowolf.core

import com.taskowolf.IntegrationTestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.jdbc.core.JdbcTemplate

/**
 * Guard for the Spring Boot 4 starter split: flyway-core alone no longer triggers
 * auto-configuration, so a missing spring-boot-starter-flyway would silently skip all
 * migrations. Every versioned script on the classpath must be recorded as applied.
 */
class FlywayMigrationsAppliedIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `all versioned migrations are applied on startup`() {
        val scripts = PathMatchingResourcePatternResolver()
            .getResources("classpath:db/migration/V*__*.sql")
            .size
        val applied = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true AND version IS NOT NULL",
            Int::class.java
        )
        assertEquals(scripts, applied)
    }
}
```

- [ ] **Step 4: JSON-Guard schreiben**

`backend/src/test/kotlin/com/taskowolf/core/JsonWireFormatGuardIntegrationTest.kt`:
```kotlin
package com.taskowolf.core

import com.fasterxml.jackson.databind.ObjectMapper
import com.taskowolf.IntegrationTestBase
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter

/**
 * M3 guard: while the backend still runs Jackson 2 (Phase 1 of the Boot 4 migration),
 * HTTP JSON must be written by the Jackson 2 converter and the wire format must not drift.
 * Phase 2 (Jackson 3) replaces the converter assertion but keeps the wire-format assertions.
 */
class JsonWireFormatGuardIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var handlerAdapter: RequestMappingHandlerAdapter

    @Test
    fun `first JSON converter is the Jackson 2 converter`() {
        val firstJson = handlerAdapter.messageConverters
            .first { it.canWrite(Map::class.java, MediaType.APPLICATION_JSON) }
        assertTrue(
            firstJson is MappingJackson2HttpMessageConverter,
            "expected Jackson 2 converter first, got ${firstJson::class.qualifiedName}"
        )
    }

    @Test
    fun `issue response keeps ISO instants and explicit nulls`() {
        val token = objectMapper.readTree(
            mockMvc.perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"email":"jsonguard@test.com","displayName":"Json Guard","password":"password123"}""")
            ).andReturn().response.contentAsString
        ).get("accessToken").asText()

        mockMvc.perform(
            post("/api/v1/projects")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"key":"JSG","name":"Json Guard"}""")
        ).andExpect(status().isCreated)

        val issue = objectMapper.readTree(
            mockMvc.perform(
                post("/api/v1/projects/JSG/issues")
                    .header("Authorization", "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"title":"Wire format"}""")
            ).andExpect(status().isCreated).andReturn().response.contentAsString
        )

        val isoInstant = Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?Z$""")
        assertTrue(issue.get("createdAt").isTextual, "createdAt must be a string, was ${issue.get("createdAt")}")
        assertTrue(isoInstant.matches(issue.get("createdAt").asText()), "createdAt not ISO-8601: ${issue.get("createdAt")}")
        assertTrue(issue.has("description") && issue.get("description").isNull, "description must be present as explicit null")
        assertTrue(issue.has("dueDate") && issue.get("dueDate").isNull, "dueDate must be present as explicit null")
        assertTrue(issue.get("labels").isArray && issue.get("labels").isEmpty, "labels must be an empty array")
        assertTrue(issue.get("type").isTextual && issue.get("type").asText() == "TASK", "enum must serialize by name")
    }
}
```

- [ ] **Step 5: Guards auf Boot 3 laufen lassen**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.core.FlywayMigrationsAppliedIntegrationTest" --tests "com.taskowolf.core.JsonWireFormatGuardIntegrationTest"`
Expected: 3 Tests PASS. Die Tests halten das **heutige** Verhalten fest. Schlägt eine Assertion auf Boot 3 fehl (z. B. weil Boot 3 null-Felder doch weglässt), ist nicht Boot 3 falsch, sondern die Assertion: Sie wird auf das beobachtete Verhalten korrigiert. Dafür einmal `issue.toPrettyString()` ausgeben, die Assertion anpassen und die Abweichung im Commit-Text nennen.

- [ ] **Step 6: Commit**

```bash
git add backend/src/test/kotlin/com/taskowolf/core/FlywayMigrationsAppliedIntegrationTest.kt backend/src/test/kotlin/com/taskowolf/core/JsonWireFormatGuardIntegrationTest.kt
git commit -m "test(m3): guard tests for Flyway auto-config and Jackson 2 wire format

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Build auf Boot 4.1.1 umstellen und Main-Code kompilierbar machen

**Files:**
- Modify: `backend/build.gradle.kts`
- Modify: `backend/src/main/resources/application.yml`
- Regenerate: `backend/gradle.lockfile`
- Modify (compiler-getrieben): Dateien unter `backend/src/main/kotlin/`

**Interfaces:**
- Consumes: nichts aus Task 1 (Testcode wird in diesem Task bewusst nicht kompiliert)
- Produces: `./gradlew compileKotlin` grün auf Boot 4.1.1; Jackson 2 bleibt der aktive HTTP/STOMP-Mapper (Properties s. u.)

- [ ] **Step 1: Plugin-Version**

In `backend/build.gradle.kts`:
```kotlin
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
```
(1.1.7 ist die neueste Version des Plugins auf dem Gradle Plugin Portal, Stand 2026-10-19, und funktioniert mit Boot 4.)

- [ ] **Step 2: `dependencies`-Block ersetzen**

Den gesamten `dependencies { ... }`-Block ersetzen durch:
```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    // Boot 4 split auto-configuration into modules: flyway-core alone no longer runs migrations.
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    // M3 Phase 1: keep Jackson 2 as the active mapper (HTTP + STOMP, see application.yml).
    // Removed in Phase 2 together with all com.fasterxml.jackson.databind usages.
    implementation("org.springframework.boot:spring-boot-jackson2")
    implementation("org.springframework.integration:spring-integration-mail")
    implementation("org.springframework.integration:spring-integration-core")
    implementation("jakarta.mail:jakarta.mail-api")
    implementation("org.eclipse.angus:angus-mail")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")
    runtimeOnly("com.h2database:h2")
    // Boot 4 moved the H2 web console (dev profile, /h2-console) into its own module.
    runtimeOnly("org.springframework.boot:spring-boot-h2console")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("io.mockk:mockk:1.14.11")
    // okhttp3 is not managed by the Spring Boot BOM; pin explicitly.
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")

    // commons-compress 1.24.0 -> 1.26.0 fixes DoS (infinite loop on DUMP file / OOM on Pack200,
    // Dependabot #1/#2). Test-scope only (transitive via Testcontainers).
    constraints {
        testImplementation("org.apache.commons:commons-compress:1.28.0")
    }
}
```

- [ ] **Step 3: Security-Overrides ersetzen**

Alle `extra[...]`-Zeilen samt ihren Kommentarblöcken (von `// Security override of a Spring Boot 3.5.16 ...` bis einschließlich `extra["log4j2.version"] = "2.25.5"`) ersetzen durch:
```kotlin
// Security override of a Spring Boot 4.1.1 BOM-managed transitive version.
// jackson-2-bom 2.21.5 -> 2.21.7: Boot 4.1.1 still pins Jackson 2 at 2.21.5, which re-opens
// CVE-2026-68497 (quadratic BigInteger/BigDecimal parsing in CoreXMLDeserializers), CVE-2026-91776
// and CVE-2026-91777. 2.21.6 is NOT sufficient (verified against a current Trivy DB), 2.21.7 is the
// newest 2.x on Maven Central. Jackson 2 is only needed until M3 Phase 2 (Jackson 3 + jjwt-gson);
// this override goes away with it.
extra["jackson-2-bom.version"] = "2.21.7"
```
Begründung (für den Commit-Text): Boot 4.1.1 managt commons-lang3 3.20.0 (≥3.18.0), logback 1.5.38 (≥1.5.35), postgresql 42.7.13 (≥42.7.12), Tomcat 11.0.24 (neue Linie), log4j2 2.25.5 (=). Die alten Overrides sind damit überflüssig, und `tomcat.version=10.1.59` würde mit Boot 4 sogar brechen.

- [ ] **Step 4: Testcontainers-BOM-Override entfernen**

Den Block löschen:
```kotlin
// Override Spring Boot BOM version for Testcontainers to support Docker Desktop 4.x on Windows
dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:1.21.4")
    }
}
```
Boot 4.1.1 managt Testcontainers 2.0.5. Den Windows-Block in `tasks.withType<Test>` vorerst **unverändert lassen**; über ihn wird in Task 3 entschieden.

- [ ] **Step 5: Jackson 2 als aktiven Mapper festnageln**

In `backend/src/main/resources/application.yml` unter dem bestehenden `spring:`-Knoten (direkt nach `application.name`) einfügen:
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

- [ ] **Step 6: Lockfile neu schreiben**

Run: `cd backend && ./gradlew dependencies --write-locks > /dev/null && git diff --stat gradle.lockfile`
Expected: `BUILD SUCCESSFUL`; das Lockfile zeigt `spring-boot-*:4.1.1`, `spring-webmvc:7.0.9`, `testcontainers-*:2.0.5`, `jackson-databind:2.21.7`.
Verifizieren:
```bash
cd backend && grep -E "^org.springframework:spring-webmvc:|^com.fasterxml.jackson.core:jackson-databind:|^org.apache.tomcat.embed:tomcat-embed-core:|^org.flywaydb:flyway-core:" gradle.lockfile
```
Expected: `spring-webmvc:7.0.9`, `jackson-databind:2.21.7`, `tomcat-embed-core:11.0.24`, `flyway-core:12.4.0`.

- [ ] **Step 7: Main kompilieren und Brüche beheben**

Run: `cd backend && ./gradlew compileKotlin`
Jeden Fehler einzeln beheben. Kotlin ist bei der Nullability strenger, weil Spring 7 auf JSpecify-Annotationen umgestellt hat. Bekannte Bruchkategorien und ihre Lösung:
- **JSpecify-Nullability** (z. B. Override-Signaturen von `ClientRegistrationRepository.findByRegistrationId`, `ChannelInterceptor.preSend`, `OncePerRequestFilter.doFilterInternal`, `AttributeConverter`): Die Parameter- und Rückgabetypen an die neue Signatur anpassen, die der Compiler in der Fehlermeldung nennt (z. B. `Message<*>?` → `Message<*>`). Dabei keine Logik ändern.
- **Spring Data 4 `Specification`** (`IssueSpecification.kt`): `Specification.where(x)` ist deprecated bzw. entfernt → durch `Specification.allOf(...)` oder direkt `x.and(y)` ersetzen. Die Semantik bleibt gleich (AND-Verknüpfung, `null`-Specs werden übersprungen, also vorher per `listOfNotNull` filtern).
- **Hibernate 7** (`EscalationRule.kt`, `@JdbcTypeCode(SqlTypes.ARRAY)`): Das sollte kompilieren. Es gibt nur einen Laufzeitfehler, falls das Mapping bricht; der zeigt sich in Task 3.
- **Entfernte deprecated APIs** aus Framework 6.x/Security 6.x: die Alternative aus der Deprecation-Notiz der 6.x-Javadoc verwenden.

Bei jedem Fix die eigene Änderung minimal halten und nicht refactoren. Wiederholen, bis:
Expected: `BUILD SUCCESSFUL` für `compileKotlin`.

- [ ] **Step 8: Commit**

```bash
git add backend/build.gradle.kts backend/gradle.lockfile backend/src/main
git commit -m "build(m3): Spring Boot 4.1.1 with modular starters and Jackson 2 compat

- explicit spring-boot-starter-flyway (flyway-core no longer auto-configures)
- spring-boot-jackson2 + preferred-json-mapper=jackson2 for HTTP and STOMP
- springdoc 3.1.1, Testcontainers 2.0.5 via Boot BOM, H2 console module
- drop commons-lang3/logback/postgresql/tomcat/log4j2 overrides (BOM is newer)
- jackson-bom override -> jackson-2-bom 2.21.7

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Testcode auf Boot 4 / Testcontainers 2 und volle Suite grün

**Files:**
- Modify: `backend/src/test/kotlin/com/taskowolf/IntegrationTestBase.kt`
- Modify: `backend/src/test/kotlin/com/taskowolf/audit/AuditEventRepositoryTest.kt`
- Modify: `backend/src/test/kotlin/com/taskowolf/auth/SsoConfigRepositoryTest.kt`
- Modify (falls nötig): `backend/build.gradle.kts` (Windows-Block, commons-compress-Constraint)
- Modify (laufzeitgetrieben): `backend/src/main/kotlin/**`

**Interfaces:**
- Consumes: Build aus Task 2; Guard-Tests aus Task 1 (die Assertions bleiben unverändert)
- Produces: `./gradlew test` grün mit N + 3 Tests

- [ ] **Step 1: `IntegrationTestBase` umstellen**

Ganze Datei ersetzen durch:
```kotlin
package com.taskowolf

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.testcontainers.postgresql.PostgreSQLContainer

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
abstract class IntegrationTestBase {

    @MockitoBean
    protected lateinit var mailSender: JavaMailSender

    companion object {
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine").apply {
            withDatabaseName("taskowolf_test")
            withUsername("test")
            withPassword("test")
            start()
        }

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.datasource.driver-class-name") { "org.postgresql.Driver" }
            registry.add("spring.flyway.enabled") { "true" }
            registry.add("taskowolf.jwt.secret") { "integration-test-secret-0123456789abcdef" }
            registry.add("taskowolf.auth.rate-limit.max-requests") { "10000" }
        }
    }
}
```
Es gibt drei Änderungen gegenüber Boot 3. `org.testcontainers.postgresql.PostgreSQLContainer` ist in TC 2 nicht mehr generisch. `@MockBean` (in Boot 4 entfernt) wird zu `@MockitoBean`. `AutoConfigureMockMvc` ist in `org.springframework.boot.webmvc.test.autoconfigure` umgezogen.

- [ ] **Step 2: `@DataJpaTest`-Imports umstellen**

In `AuditEventRepositoryTest.kt` **und** `SsoConfigRepositoryTest.kt` jeweils ersetzen:
```kotlin
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
```
durch
```kotlin
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
```

- [ ] **Step 3: Testcode kompilieren**

Run: `cd backend && ./gradlew compileTestKotlin`
Expected: `BUILD SUCCESSFUL`. Weitere Fehler nach demselben Muster beheben: neues Paket per `unzip -l` im Jar aus `~/.gradle/caches` suchen und den Import anpassen, ohne die Test-Logik zu ändern.

- [ ] **Step 4: Volle Suite**

Run: `cd backend && ./gradlew test`
Expected: `BUILD SUCCESSFUL`, N + 3 Tests (Zählbefehl aus Task 1, Step 2).
Fehlschläge nach `superpowers:systematic-debugging` angehen: Root Cause finden, Fix nur im Main-Code oder an Imports, **keine Assertion lockern**. Ausnahme: Ein Test prüft eine Framework-Interna-Klasse, die es nicht mehr gibt. Typische Ursachen:
- Hibernate 7: geänderte HQL-/Criteria-Semantik oder ARRAY-Mapping (`EscalationRule`) → das Mapping auf die Hibernate-7-Form bringen.
- Security 7: geänderte Default-Reihenfolge/Matcher (z. B. `PathPatternRequestMatcher` statt MVC-Matcher), CSRF/CORS-Defaults → das Verhalten aus Boot 3 explizit in `SecurityConfig` konfigurieren.
- Fehlerformat/i18n-Gates (MessagesParity/KeyedReferenceIntegrity/NoUnkeyedUserFacingThrow): Sie müssen unverändert grün sein.
Die Guard-Tests aus Task 1 müssen **unverändert** grün sein. Wird `first JSON converter is the Jackson 2 converter` rot, fehlt oder greift die Property aus Task 2, Step 5 nicht. Das zuerst klären.

- [ ] **Step 5: Windows-Docker-Workaround prüfen**

Den Block in `tasks.withType<Test>` vorübergehend auskommentieren (`if (... "win") { ... }`) und laufen lassen:
`cd backend && ./gradlew test --tests "com.taskowolf.core.FlywayMigrationsAppliedIntegrationTest"`
- Grün → Block **samt Kommentar löschen** (TC 2 / docker-java verhandelt die API-Version selbst).
- Rot mit Docker-API-/Verbindungsfehler → Auskommentierung zurücknehmen und den Kommentar auf `Testcontainers 2.x` aktualisieren.

- [ ] **Step 6: commons-compress-Constraint prüfen**

Run: `cd backend && ./gradlew dependencyInsight --dependency commons-compress --configuration testRuntimeClasspath`
Wenn die Version **ohne** Constraint ≥ 1.28.0 wäre (in der Ausgabe ist der Pfad über Testcontainers 2 zu sehen), den `constraints { ... }`-Block samt Kommentar entfernen und das Lockfile neu schreiben (`./gradlew dependencies --write-locks`). Sonst bleibt er.

- [ ] **Step 7: Nochmals volle Suite und Commit**

Run: `cd backend && ./gradlew test`
Expected: `BUILD SUCCESSFUL`, N + 3 Tests.
```bash
git add backend/
git commit -m "test(m3): migrate tests to Boot 4 test modules and Testcontainers 2

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Property-Audit mit dem Properties-Migrator und Dev-Smoke

**Files:**
- Modify (temporär): `backend/build.gradle.kts`
- Modify (falls gemeldet): `backend/src/main/resources/application*.yml`

**Interfaces:**
- Consumes: grüne Suite aus Task 3
- Produces: keine Migrator-Warnungen; `application*.yml` mit Boot-4-Keys; Migrator ist wieder entfernt

- [ ] **Step 1: Migrator temporär einbinden**

In `dependencies` ergänzen: `runtimeOnly("org.springframework.boot:spring-boot-properties-migrator")`

- [ ] **Step 2: App im Dev-Profil starten und Warnungen sammeln**

Run: `cd backend && ./gradlew bootRun --args='--spring.profiles.active=dev' > ../m3-bootrun.log 2>&1` (im Hintergrund). Warten, bis `Started TaskWolfApplication` im Log steht, dann:
```bash
grep -A30 -i "properties-migrator\|has been renamed\|no longer supported\|PropertiesMigrationListener" ../m3-bootrun.log
```
Prüfen, dass der Dev-Stack funktioniert:
```bash
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/h2-console/
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/v1/admin/sso/public
```
Expected: `/h2-console/` → `200`, `/api/v1/admin/sso/public` → `200`. Prozess beenden.

Den Migrator braucht nur der Dev-Lauf. Die Prod-Keys (`application-prod.yml`) prüft er ebenfalls, sobald das Profil aktiv ist, deshalb denselben Start einmal mit `--spring.profiles.active=prod` wiederholen. Ohne Postgres darf der Prod-Start am Datasource-Fehler scheitern; der Migrator-Report erscheint davor.

- [ ] **Step 3: Gemeldete Keys umbenennen**

Jeden gemeldeten Key in der betroffenen `application*.yml` auf den neuen Namen aus dem Report umbenennen, ohne Wertänderung. Werden keine Keys gemeldet, gibt es nichts zu tun. Das notieren.

- [ ] **Step 4: Migrator wieder entfernen und verifizieren**

Die Zeile aus Step 1 löschen.
Run: `cd backend && ./gradlew dependencies --write-locks > /dev/null && git diff --stat gradle.lockfile && ./gradlew test`
Expected: Das Lockfile zeigt netto keinen Migrator-Eintrag, `BUILD SUCCESSFUL`, N + 3 Tests. `../m3-bootrun.log` löschen (nicht committen).

- [ ] **Step 5: Commit (nur falls Keys umbenannt wurden)**

```bash
git add backend/src/main/resources
git commit -m "chore(m3): rename Spring Boot 4 configuration properties

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: CVE-Nachweis — `.trivyignore` bereinigen und lokaler Trivy-Lauf

**Files:**
- Modify: `.trivyignore`

**Interfaces:**
- Consumes: `backend/gradle.lockfile` (spring-webmvc 7.0.9) aus Task 2
- Produces: `.trivyignore` ohne Spring-Einträge; dokumentierter Trivy-Lauf ohne HIGH/CRITICAL

- [ ] **Step 1: Einträge entfernen**

Aus `.trivyignore` genau die beiden Zeilen löschen, die mit `CVE-2026-47884` und `CVE-2026-47890` beginnen. Die Kommentar-Kopfzeilen und alle anderen Einträge bleiben.

- [ ] **Step 2: Trivy-FS-Scan gegen frische DB**

Run (Repo-Root; Trivy lokal oder per Docker):
```bash
docker run --rm -v "$PWD:/src" aquasec/trivy:latest fs --scanners vuln --severity HIGH,CRITICAL --ignorefile /src/.trivyignore --exit-code 1 /src/backend
```
Expected: Exit-Code 0, keine HIGH/CRITICAL (Trivy lädt bei `--rm` jedes Mal eine frische DB). Taucht ein **neues** Finding in einer Boot-4-managed Library auf: Fix-Version prüfen und als `extra["<lib>.version"]`-Override mit CVE-Kommentar (wie bisher) ergänzen, Lockfile neu schreiben, Suite laufen lassen. Kein neuer `.trivyignore`-Eintrag ohne Rückfrage bei Wolfgang.

- [ ] **Step 3: Backend-Image bauen und scannen**

```bash
cd backend && ./gradlew bootJar && cd .. && docker build -t taskowolf-backend:m3-local backend
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v "$PWD/.trivyignore:/.trivyignore" aquasec/trivy:latest image --severity HIGH,CRITICAL --ignorefile /.trivyignore --exit-code 1 taskowolf-backend:m3-local
```
Expected: Exit-Code 0. Unter Windows/Git Bash bei Pfadproblemen `MSYS_NO_PATHCONV=1` voranstellen.

- [ ] **Step 4: Container-Smoke gegen echtes Postgres**

Lokal über `docker-compose.prod.yml` mit dem Image `taskowolf-backend:m3-local` statt `:latest` für den Backend-Service (Override per `docker compose -f docker-compose.prod.yml -f <tmp-override>.yml up -d`; die Override-Datei liegt im Scratchpad und wird nicht committet). Danach:
```bash
docker compose -f docker-compose.prod.yml logs backend | grep -E "Successfully applied|Schema .* is up to date|Started TaskWolfApplication"
```
Expected: Der Flyway-Report zeigt Schema-Version 30 (up to date bzw. applied), und die App ist gestartet. Login im Browser (bzw. per `curl` `POST /api/v1/auth/login`) liefert ein Token. Nach dem Recreate kann nginx kurz 502 liefern → `docker compose restart nginx`. Danach die normalen `:latest`-Images wiederherstellen (`up -d` ohne Override).

- [ ] **Step 5: Commit**

```bash
git add .trivyignore
git commit -m "chore(security): drop spring-webmvc trivyignores fixed by Boot 4 (Framework 7.0.9)

CVE-2026-47884 (XsltView) and CVE-2026-47890 (SSE fragments) are fixed in
spring-webmvc 7.0.9, which Spring Boot 4.1.1 manages.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Doku

**Files:**
- Modify: `docs/superpowers/specs/2026-07-07-backlog-overview.md`
- Modify: `mkdocs/developer-guide/ai-guide.md`

**Interfaces:**
- Consumes: Ergebnisse aus Task 2–5 (Windows-Block entfernt ja/nein, umbenannte Properties)
- Produces: aktualisierte Doku

- [ ] **Step 1: Backlog**

In der Tabellenzeile `| M3 | ...` den Status ersetzen durch:
`🚧 **Phase 1 auf main** (Boot 4.1.1 + Jackson-2-Kompat, spring-webmvc 7.0.9, beide Spring-trivyignores entfernt, PR #<nr>); Phase 2 (Jackson 3 + jjwt-gson) offen, **Release erst nach Phase 2**`
Im Abschnitt `## M3 — ...` die Zeile `> 🔜 **Backlog** ...` ersetzen durch einen Status-Block mit Spec- und Plan-Pfad und dem Hinweis auf die Notfall-Ausnahme (siehe Spec, Risiken).

- [ ] **Step 2: ai-guide**

In `mkdocs/developer-guide/ai-guide.md`:
- Checklist-Punkt 3 korrigieren: aktueller Stand **V30** (`V30__add_user_language.sql`), die nächste Migration ist **V31**.
- Neuer Abschnitt `### Backend: Spring Boot 4 Conventions` vor `### Backend: Flyway Migration`:
```markdown
### Backend: Spring Boot 4 Conventions

- **Modulare Starter:** Jede genutzte Auto-Configuration braucht ihren Starter. Insbesondere
  `spring-boot-starter-flyway` — `flyway-core` allein führt in Boot 4 **keine** Migrationen aus
  (Guard: `FlywayMigrationsAppliedIntegrationTest`).
- **Test-Annotationen** liegen in Modul-Paketen: `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`,
  `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`,
  `org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase`.
- **Spring-Context-Mocks:** `@MockitoBean` (`org.springframework.test.context.bean.override.mockito`); `@MockBean` existiert nicht mehr.
  Unit-Tests weiterhin mit MockK.
- **Testcontainers 2:** `org.testcontainers.postgresql.PostgreSQLContainer` (nicht generisch), Artefakte `testcontainers-*`.
- **Jackson:** bis M3 Phase 2 Jackson 2 (`com.fasterxml.jackson.databind`) via `spring-boot-jackson2` und
  `preferred-json-mapper: jackson2`. Keine neuen `tools.jackson`-Imports vor Phase 2.
- **Security-Overrides:** nur per `extra["<lib>.version"]` mit CVE-Kommentar; bei jedem Boot-Upgrade prüfen, ob der BOM ihn überholt hat.
```

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/specs/2026-07-07-backlog-overview.md mkdocs/developer-guide/ai-guide.md
git commit -m "docs(m3): Phase 1 status and Spring Boot 4 conventions in ai-guide

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: PR, CI und Merge

**Files:** keine

- [ ] **Step 1: Haupt-Checkout prüfen**

Run (Haupt-Checkout, nicht der Worktree): `git -C /c/Users/Admin/IdeaProjects/TaskWolf status --short`
Expected: leer (keine versehentlichen Edits im Haupt-Checkout).

- [ ] **Step 2: Push und PR**

```bash
git push -u origin m3-phase1-spring-boot-4
gh pr create --title "M3 Phase 1: Spring Boot 4.1.1 (Jackson 2 compat)" --body "<Zusammenfassung: Versionen, entfernte Overrides, Guard-Tests, Trivy-Ergebnis, Windows-Block-Entscheidung, umbenannte Properties; Hinweis: kein Release bis Phase 2>

🤖 Generated with [Claude Code](https://claude.com/claude-code)"
```
(Achtung: `main` liegt lokal mit Spec/Plan-Commits vor `origin/main`. Diese Commits gehen über den Feature-Branch mit in den PR. Das ist gewollt.)

- [ ] **Step 3: CI abwarten**

Run: `gh pr checks --watch`
Expected: alle Required Checks grün (Backend-Tests, Trivy backend/frontend, CodeQL; `java-kotlin` CodeQL ist per continue-on-error toleriert, Issue #26).

- [ ] **Step 4: Merge (nach Wolfgangs OK)**

Wolfgang bestätigt den Merge. Danach Squash-Merge per `gh pr merge --squash --delete-branch`. Das kann vom Auto-Mode-Classifier blockiert werden; dann soll Wolfgang `! gh pr merge <nr> --squash --delete-branch` selbst ausführen. Danach lokal `git checkout main && git pull`, Worktree entfernen. **Kein Release** (Spec: gemeinsames Release nach Phase 2; Notfall-Ausnahme nur bei neuer Framework-CVE).
