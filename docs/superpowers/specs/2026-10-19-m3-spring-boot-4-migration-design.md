# M3 — Spring Boot 4 Migration (Design)

> Stand 2026-10-19. Backlog-Eintrag: `2026-07-07-backlog-overview.md` → M3.
> Zwei Phasen, je eine Session mit eigenem Plan. **Ein gemeinsames Release (v1.0.21) nach Phase 2.**

## Ziel

Backend von Spring Boot 3.5.16 (Framework 6.2.19) auf **Spring Boot 4.1.1** (Framework **7.0.9**)
heben, damit die zwei per `.trivyignore` geparkten spring-webmvc-CVEs echt behoben sind und
künftige Framework-CVEs wieder einen Fix-Pfad haben:

- CVE-2026-47884 — XsltView path limitation (PR #148)
- CVE-2026-47890 — SSE + View-Fragments stream corruption (PR #151)

Anschließend Umstellung auf Jackson 3 (Boot-4-Default) und vollständige Entfernung von Jackson 2.

## Nicht-Ziele / Invarianten

- Keine Frontend-Änderung. Die API-Antworten (JSON-Shapes, Datumsformat, Fehlerformat,
  i18n-Texte) bleiben **unverändert**.
- Kein DB-Schema-Change; der Flyway-Migrationsstand bleibt gleich.
- Bestehende JWTs bleiben nach dem Upgrade gültig.
- Kotlin 2.4.20, JVM-Target 21 und Runtime-Image Temurin 25 bleiben.

## Verifizierte Ausgangslage (Maven Central, 2026-10-19)

Boot 4.1.1 BOM: Framework 7.0.9, Security 7.1.1, Hibernate 7.4.5.Final, Tomcat 11.0.24,
Testcontainers 2.0.5, Flyway 12.4.0, postgresql 42.7.13, logback 1.5.38, commons-lang3 3.20.0,
log4j2 2.25.5, jackson-bom 3.1.5, **jackson-2-bom 2.21.5**, Kotlin 2.3.21 (managed; das
Kotlin-Plugin 2.4.20 hat Vorrang).
springdoc-openapi neueste Version: 3.1.1. jjwt neueste Version: 0.13.0 (`jjwt-jackson` nur für Jackson 2).

---

## Phase 1 — Boot 4.1.1 mit Jackson-2-Kompatmodus (Session 1)

Ansatz: **explizite modulare Starter**. Keine `*-classic`-Starter und kein OpenRewrite; die
Änderungen werden von Hand gemacht und über Compiler und Test-Suite abgesichert.

### Build (`backend/build.gradle.kts`)

- Plugin `org.springframework.boot` 3.5.16 → 4.1.1. Kompatible Version von
  `io.spring.dependency-management` prüfen.
- Starter:
  - `spring-boot-starter-web` → `spring-boot-starter-webmvc`
  - `flyway-core` → **`spring-boot-starter-flyway`** (`flyway-database-postgresql` bleibt).
    ⚠️ Kritisch: In Boot 4 löst `flyway-core` allein keine Autoconfiguration mehr aus.
    Fehlt der Starter, laufen die Migrationen **still nicht**.
  - `spring-boot-jackson2` (Kompatmodul, nur Phase 1)
  - Übrige Starter (data-jpa, security, oauth2-client, websocket, mail, validation) auf ihre
    Boot-4-Namen prüfen.
  - Tests: `spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test`, Security-Test
    nach Bedarf statt eines Sammelstarters.
- springdoc 2.9.1 → 3.1.1.
- Testcontainers: BOM-Override `1.21.4` **entfernen** (Boot managt 2.0.5). Artefakte auf
  TC-2-Namen umstellen (`testcontainers-postgresql`, `testcontainers-junit-jupiter`) und Pakete
  bzw. Klassen-Generics anpassen (`PostgreSQLContainer<Nothing>`). Prüfen, ob der
  Windows-Workaround (`DOCKER_HOST=tcp://localhost:2375`, `api.version=1.44`) im `Test`-Task noch
  nötig ist; ist er überflüssig, kommt er raus.
- Security-Overrides:
  - **entfernen:** `commons-lang3`, `logback`, `postgresql`, `tomcat`, `log4j2`
    (der BOM ist jeweils gleich oder neuer).
  - **umbenennen:** `jackson-bom.version = 2.21.7` → `jackson-2-bom.version = 2.21.7`,
    weil Boot 4.1.1 nur 2.21.5 pinnt (sonst kommen CVE-2026-68497/91776/91777 zurück).
    CVE-Kommentare kürzen.
- `commons-compress`-Test-Constraint: prüfen, ob er mit TC 2 noch nötig ist.
- `gradle.lockfile` neu erzeugen (`./gradlew dependencies --write-locks`).

### Code

- Test-Imports aus verschobenen Paketen: `AutoConfigureMockMvc`, `DataJpaTest`,
  `AutoConfigureTestDatabase`.
- `@MockBean` → `@MockitoBean` (einzige Stelle: `IntegrationTestBase`).
- Kompilier- und Laufzeitfehler aus Framework 7 / Security 7 / Hibernate 7 / Spring Data 4
  iterativ beheben. Bekannte Kandidaten:
  - `issues/infrastructure/IssueSpecification.kt` (Specification-API)
  - `servicedesk/domain/EscalationRule.kt` (`@JdbcTypeCode(SqlTypes.ARRAY)`)
  - `auth/infrastructure/JwtStompInterceptor.kt`, `core/infrastructure/WebSocketConfig.kt`
  - `auth/infrastructure/DbClientRegistrationRepository.kt` (OAuth2-Client)
  - `SecurityConfig` nutzt schon die Lambda-DSL; dort werden voraussichtlich keine Änderungen nötig.
- `application*.yml`: Für die Property-Umbenennungen wird `spring-boot-properties-migrator`
  **vorübergehend** eingebunden. Die gemeldeten Keys werden umbenannt, danach kommt der Migrator
  wieder raus.

### Abschluss Phase 1 (Definition of Done)

- Beide Einträge aus `.trivyignore` entfernt.
- Lokaler Trivy-Lauf (fs + Backend-Image) gegen eine frische DB: 0 HIGH/CRITICAL.
- Volle Backend-Suite inkl. Postgres-Testcontainers grün, ebenso die 3 i18n-Gates
  (MessagesParity / KeyedReferenceIntegrity / NoUnkeyedUserFacingThrow).
- Flyway-Migrationen laufen im Integrationstest nachweislich (Gegencheck zur Starter-Falle).
- PR → CI grün → Merge auf `main`. **Kein Release.**

---

## Phase 2 — Jackson 3 + jjwt-gson (Session 2)

### 1. Sicherheitsnetz zuerst (noch auf Jackson 2)

Bevor ein Import umgestellt wird, kommen JSON-Charakterisierungstests dazu. Sie müssen auf
Jackson 2 grün sein und **nach** der Umstellung unverändert grün bleiben:

- Issue-Antwort: Datum/Zeit, Enum, null-Felder, verschachtelte Objekte, Property-Set
- `ErrorResponse` (lokalisiert)
- `TransitionGuard` (`@JsonTypeInfo`/`@JsonSubTypes`) als Roundtrip in beide Richtungen
- Audit-`details` (Text-Spalte, siehe `project-db-workarounds`)
- `StringListConverter`: DB-Format eines `List<String>` bleibt bytegleich

Die Tests vergleichen Feldwerte und -typen und das Datumsformat, **nicht** die
Property-Reihenfolge. Die Reihenfolge ist für JSON-Clients irrelevant; das Frontend nutzt
JSON.parse.

### 2. Default-Unterschiede Jackson 3

Wo ein geänderter Jackson-3-Default die Antwort sichtbar ändert (z. B. Datums-Serialisierung oder
null-Handling bei Primitiven), wird das alte Verhalten explizit über `spring.jackson.*` bzw. einen
`JsonMapperBuilderCustomizer` gesetzt. Den neuen Default übernehmen wir nur, wenn die
Charakterisierungstests zeigen, dass niemand betroffen ist. Jede so gesetzte Option bekommt einen
kurzen Begründungs-Kommentar.

### 3. Umstellung

- `com.fasterxml.jackson.databind|core|module.kotlin` → `tools.jackson.*`
  (11 Main-Dateien, ca. 29 Testdateien). **Annotationen bleiben** in
  `com.fasterxml.jackson.annotation.*` (Jackson-3-Design).
- `ObjectMapper` → injizierter `JsonMapper` / `ObjectMapper` aus `tools.jackson`.
- Exceptions: In Jackson 3 ist `JacksonException` unchecked. Die `catch`-Blöcke für
  `JsonProcessingException`/`JsonMappingException` werden geprüft und angepasst
  (`IncomingWebhookService`, `WebhookDispatcher`, `WebhookService`, `AutomationEngine`,
  `ActionExecutor`, `ConditionEvaluator`, `WorkflowService`).
- `StringListConverter`: statischer, einmalig gebauter `JsonMapper` statt `ObjectMapper()` pro
  Instanz (JPA-Converter werden nicht zuverlässig injiziert).
- `com.fasterxml.jackson.module:jackson-module-kotlin` → `tools.jackson.module:jackson-module-kotlin`.
- `spring-boot-jackson2` entfernen.

### 4. jjwt auf Gson

- `jjwt-jackson` → `jjwt-gson` (0.13.0). Gson wird nur intern von jjwt für die
  JWT-(De)Serialisierung genutzt.
- Test: Ein vor dem Wechsel (Jackson-Serializer) ausgestelltes Token wird nach dem Wechsel
  akzeptiert; die Claims sind identisch.
- Danach **ist Jackson 2 nicht mehr im Runtime-Classpath** (per `./gradlew dependencies`
  verifiziert, Runtime- und Test-Konfiguration). Der `jackson-2-bom`-Override samt Kommentar
  fällt weg.

### 5. Verifikation und Release

- Volle Backend-Suite inkl. Testcontainers und die 3 i18n-Gates grün; Frontend-Typecheck und
  -Build unverändert grün.
- Lokaler Trivy-Lauf (fs + Image) gegen eine frische DB: 0 HIGH/CRITICAL. `.trivyignore` enthält
  keine Spring-Einträge mehr.
- Docker-Smoke über `docker-compose.prod.yml` mit lokal gebautem Backend-Image (Frontend-Image
  gepullt): Login (lokal + bestehendes Token), Issue anlegen/bearbeiten, Workflow-Transition mit
  Guard, WebSocket-Live-Update, Webhook-Eingang, Swagger in prod aus, Flyway-Stand unverändert.
- Dependabot-Alerts #109/#110 prüfen bzw. schließen.
- Doku: Backlog-Eintrag M3 aktualisieren, `ai-guide.md` um die Boot-4-/Jackson-3-Patterns
  ergänzen (`tools.jackson`-Imports, modulare Starter, `@MockitoBean`, Flyway-Starter-Falle).
- Release **v1.0.21** nach bekanntem Ablauf (package.json bump → `chore(release)` →
  `gh release create` → docker-publish grün).

## Risiken

| Risiko | Gegenmaßnahme |
|---|---|
| Flyway läuft still nicht (fehlender Starter) | expliziter `spring-boot-starter-flyway` + Integrationstest-Gegencheck |
| Jackson-3-Defaults ändern API-Shapes | Charakterisierungstests vor der Umstellung; alte Defaults explizit setzen |
| Hibernate 7 / Spring Data 4 Verhaltensänderung bei Queries | volle Suite gegen echtes Postgres (Testcontainers), nicht nur H2 |
| Testcontainers 2 auf Windows/Docker Desktop | Workaround erst nach grünem Lauf entfernen |
| JWT-Inkompatibilität nach jjwt-gson | Cross-Serializer-Token-Test |
| Phase 1 gemergt, aber unreleased, während ein neues CVE kommt | Phase 2 zeitnah anschließen; im Notfall kann `main` nach Phase 1 trotzdem released werden |
