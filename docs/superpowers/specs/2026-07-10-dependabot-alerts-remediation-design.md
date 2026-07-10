# Design: Dependabot-Alerts beheben (Backlog #12)

- **Datum:** 2026-07-10
- **Backlog-Item:** #12 (Ops/Security)
- **Typ:** Dependency-Bumps + Alert-Triage (Backend)
- **Betroffene Dateien:** `backend/build.gradle.kts`, `backend/gradle.lockfile`

## Ausgangslage (verifiziert am 2026-07-10)

5 offene Dependabot-Alerts auf `WolfTasks/TaskWolf`, alle transitiv über die
Spring-Boot-3.5.16-BOM (Manifest `backend/settings.gradle.kts`). Live-Alerts via
`gh api --paginate /repos/WolfTasks/TaskWolf/dependabot/alerts?state=open`
abgeglichen mit den tatsächlich aufgelösten Versionen in `backend/gradle.lockfile`:

| Alert | Paket | Lockfile-Ist | Fix-Version | Bewertung |
|-------|-------|--------------|-------------|-----------|
| #79 (low) | `ch.qos.logback:logback-core` | **1.5.34** | 1.5.35 | echter Bump nötig |
| #1/#2 (medium) | `org.apache.commons:commons-compress` | **1.24.0** (nur Test-Scope) | 1.26.0 | echter Bump nötig |
| #31 (medium) | `org.apache.commons:commons-lang3` | **3.18.0** | 3.18.0 | bereits gefixt → nur Triage |
| #78 (medium) | `com.fasterxml.jackson.core:jackson-databind` | **2.21.4** | *kein Patch* | defer → Triage |

Wichtige Erkenntnis: **#31 ist bereits behoben** — der Override
`extra["commons-lang3.version"] = "3.18.0"` (`build.gradle.kts:59`) hat 3.18.0 ins
Lockfile gezogen; der Alert ist nur noch stale. **#78 hat keinen installierbaren
Patch**: 2.21.5/2.22.1 sind unreleased, 2.18.9 wäre ein Downgrade, 3.x ein
Major-Sprung (vgl. Kommentar `build.gradle.kts:57-58`).

## Ziel

- HIGH/relevante Alerts, die per Version-Bump lösbar sind, schließen
  (logback-core, commons-compress).
- Nicht per Bump lösbare Alerts sauber triagieren (aktiv dismissen mit
  Begründung), sodass die Alert-Liste nur **dokumentierte, nachvollziehbare
  Deferrals** enthält.
- CI-Security-Gates grün.

## Lösung

### 1. Version-Bumps (`backend/build.gradle.kts`)

**logback-core 1.5.34 → 1.5.35** (Alert #79):
- Bevorzugt über die Spring-Boot-BOM-Property `extra["logback.version"] =
  "1.5.35"` (analog zum bestehenden `commons-lang3.version`-Override). Property-
  Name im Plan verifizieren; falls die BOM keine `logback.version`-Property
  exponiert, stattdessen ein `dependencies`-Constraint auf `logback-core` **und**
  `logback-classic` setzen (Versionsgleichheit wahren).

**commons-compress 1.24.0 → 1.26.0** (Alerts #1/#2):
- Nur Test-Scope (`testCompileClasspath`, `testRuntimeClasspath`), transitiv über
  Testcontainers. Über ein Dependency-Constraint anheben:
  ```kotlin
  dependencies {
      constraints {
          implementation("org.apache.commons:commons-compress:1.26.0")
      }
  }
  ```
  Konkrete Konfiguration/Scope im Plan bestätigen (ggf. `testImplementation`-
  Constraint genügt, da nur Test-Classpath betroffen).

Beide Overrides mit kurzem CVE-Kommentar versehen (Stil wie `build.gradle.kts:55`).

### 2. Lockfile aktualisieren

`backend/gradle.lockfile` neu schreiben (`./gradlew dependencies --write-locks`
bzw. das etablierte Lock-Update-Kommando des Projekts). Verifizieren, dass danach
im Lockfile stehen:
- `logback-core:1.5.35` (und ggf. `logback-classic` konsistent),
- `commons-compress:1.26.0`,
- `commons-lang3:3.18.0` (unverändert).

### 3. Build + Tests

- `./gradlew build` (bzw. Test-Task) grün — inkl. der Testcontainers-/Postgres-
  Tests, die commons-compress ziehen.
- CI-Security-Gates (Trivy/Dependabot) grün.

### 4. Alert-Triage (kein Code)

- **#31 commons-lang3:** verifizieren, dass der Alert nach dem Lockfile-Push
  automatisch schließt; falls nicht, aktiv als „fix already applied" dismissen.
- **#78 jackson-databind:** aktiv als **„no fix available / tolerable risk"**
  dismissen mit Begründung (kein installierbarer Patch; Spring-Boot-BOM wird
  2.21.5 nachziehen — analog früherem Jackson-Defer, Issue #33). Dismiss via
  `gh api` (Dependabot-Alert `PATCH` mit `state=dismissed`,
  `dismissed_reason=tolerable_risk`).

## Testplan

1. `git grep`/Lockfile-Diff: nur die drei erwarteten Zeilen ändern sich.
2. Backend-Build + vollständige Testsuite grün (MockK-Unit- + Testcontainers-
   Integrationstests).
3. Nach Push: `gh api --paginate .../dependabot/alerts?state=open` zeigt **nur
   noch #78** (dismissed) bzw. eine leere/dokumentierte Restliste.

## Out of Scope

- **jackson-databind auf 2.21.5 forcieren** — verworfen (unreleased, außerhalb
  des akzeptierten BOM-Range, Kompatibilitätsrisiko).
- Sonstige Dependency-Upgrades / Spring-Boot-Bumps ohne offenen Alert.
- Änderungen an Frontend-Dependencies.

## Referenzen

- `build.gradle.kts:55-59` — bestehendes commons-lang3-Override-Muster + Jackson-
  Defer-Kommentar.
- Issue #33 — Präzedenzfall dokumentierter Jackson-Defer.
- Memory: Supply-Chain-Security (immer `gh api --paginate` für Alerts).
```
