# Dependabot-Alerts Remediation (#12) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Die per Version-Bump lösbaren Dependabot-Alerts (logback-core #79, commons-compress #1/#2) schließen und die nicht lösbaren (commons-lang3 #31 bereits gefixt, jackson-databind #78 ohne Patch) sauber triagieren.

**Architecture:** Zwei Version-Overrides der Spring-Boot-3.5.16-BOM in `backend/build.gradle.kts` (logback via BOM-Property, commons-compress via Dependency-Constraint), danach `backend/gradle.lockfile` neu schreiben. Anschließend Alert-Triage über die GitHub-API (`gh`). Kein Anwendungscode betroffen.

**Tech Stack:** Gradle (Kotlin DSL) mit `dependencyLocking { lockAllConfigurations() }`, Spring Boot 3.5.16 BOM, Kotlin 2.4.0, JUnit/MockK + Testcontainers/Postgres. GitHub CLI (`gh`) für Alert-Triage.

## Global Constraints

- Nur `backend/build.gradle.kts` + `backend/gradle.lockfile` ändern. Kein Anwendungscode.
- Alerts immer via `gh api --paginate /repos/WolfTasks/TaskWolf/dependabot/alerts?state=open` abrufen (Memory: Supply-Chain-Security).
- **jackson-databind NICHT** auf 2.21.5 forcieren (unreleased, außerhalb akzeptiertem BOM-Range) — nur deferren/dismissen.
- Zielversionen (verifiziert 2026-07-10, Lockfile-Ist → Soll):
  - `ch.qos.logback:logback-core` 1.5.34 → **1.5.35**
  - `org.apache.commons:commons-compress` 1.24.0 → **1.26.0** (nur Test-Scope)
  - `org.apache.commons:commons-lang3` bereits **3.18.0** (unverändert, Override `build.gradle.kts:59`)
  - `com.fasterxml.jackson.core:jackson-databind` bleibt **2.21.4** (kein Patch)
- Overrides mit kurzem CVE-Kommentar versehen (Stil `build.gradle.kts:55`).

---

### Task 1: Version-Bumps + Lockfile + Build

**Files:**
- Modify: `backend/build.gradle.kts` (Override-Block bei Zeile 55–59; Constraint im `dependencies`-Block)
- Modify: `backend/gradle.lockfile` (generiert)

**Interfaces:**
- Consumes: bestehendes Override-Muster `extra["commons-lang3.version"] = "3.18.0"` (`build.gradle.kts:59`).
- Produces: Lockfile mit `logback-core:1.5.35` und `commons-compress:1.26.0`.

- [ ] **Step 1: Offenen Alert-Stand als Baseline festhalten**

Run: `gh api --paginate '/repos/WolfTasks/TaskWolf/dependabot/alerts?state=open' --jq '.[] | {number, pkg: .dependency.package.name}'`
Expected: 5 Einträge (#79 logback-core, #78 jackson-databind, #31 commons-lang3, #1/#2 commons-compress). Dient als Vorher-Vergleich.

- [ ] **Step 2: logback-core-Override ergänzen**

In `backend/build.gradle.kts`, direkt bei der bestehenden `extra[...]`-Zeile (Zeile 59), ergänzen:

```kotlin
// logback-core 1.5.34 -> 1.5.35 fixes CVE (Object Injection via HardenedObjectInputStream); Spring Boot 3.5.16 BOM pins 1.5.34.
extra["logback.version"] = "1.5.35"
```

Die bestehende `extra["commons-lang3.version"] = "3.18.0"`-Zeile und der Kommentarblock darüber bleiben unverändert.

- [ ] **Step 3: commons-compress-Constraint ergänzen**

Im `dependencies { … }`-Block von `backend/build.gradle.kts` (nach den `testImplementation`-Zeilen, vor der schließenden Klammer bei Zeile 53) ergänzen:

```kotlin
    // commons-compress 1.24.0 -> 1.26.0 fixes DoS (Infinite-Loop DUMP / OOM Pack200). Nur Test-Scope (transitiv via Testcontainers).
    constraints {
        testImplementation("org.apache.commons:commons-compress:1.26.0")
    }
```

- [ ] **Step 4: Lockfile neu schreiben**

Run (aus `backend/`): `./gradlew dependencies --write-locks`
Expected: Build erfolgreich; `backend/gradle.lockfile` wird aktualisiert. (Reines Auflösen der Configs — kein Docker/Testcontainers nötig.)

- [ ] **Step 5: Lockfile-Diff verifizieren**

Run (aus Repo-Wurzel): `git diff --unified=0 backend/gradle.lockfile`
Expected: **genau** diese Änderungen — `logback-core` 1.5.34→1.5.35 (und, falls die Property `logback-classic` mitzieht, dieses konsistent 1.5.35) sowie `commons-compress` 1.24.0→1.26.0. `commons-lang3` bleibt 3.18.0, `jackson-databind` bleibt 2.21.4.

Falls `logback.version` **nicht** greift (logback-core unverändert 1.5.34), stattdessen in Step 2 ein Constraint verwenden und Step 4–5 wiederholen:

```kotlin
    constraints {
        implementation("ch.qos.logback:logback-core:1.5.35")
        implementation("ch.qos.logback:logback-classic:1.5.35")
    }
```

- [ ] **Step 6: Build + Tests grün**

Run (aus `backend/`): `./gradlew build`
Expected: BUILD SUCCESSFUL, inkl. der Testcontainers-/Postgres-Tests, die commons-compress ziehen. (Auf Windows sorgt der bestehende `DOCKER_HOST`/`api.version`-Block in `build.gradle.kts:75-84` für die Testcontainer-Anbindung — Docker Desktop muss laufen.)

- [ ] **Step 7: Commit**

```bash
git add backend/build.gradle.kts backend/gradle.lockfile
git commit -m "fix(deps): bump logback-core 1.5.35 & commons-compress 1.26.0 to clear Dependabot alerts (#12)"
```

---

### Task 2: Alert-Triage (nach Merge auf `main`)

> Dependabot re-scannt den **Default-Branch**. Diese Task erst ausführen, wenn Task 1 auf `main` gemergt/gepusht ist.

**Files:** keine (GitHub-API-Aktionen).

**Interfaces:**
- Consumes: gemergter Lockfile-Stand aus Task 1.

- [ ] **Step 1: Rescan abwarten und Alerts prüfen**

Run: `gh api --paginate '/repos/WolfTasks/TaskWolf/dependabot/alerts?state=open' --jq '.[] | {number, pkg: .dependency.package.name}'`
Expected nach Rescan: #79 (logback), #1, #2 (commons-compress) **geschlossen**; #31 (commons-lang3) sollte ebenfalls automatisch schließen (Lockfile schon 3.18.0). Übrig: **#78 jackson-databind**.

- [ ] **Step 2: #31 falls noch offen dismissen**

Nur falls #31 nach dem Rescan **nicht** automatisch geschlossen ist:

Run: `gh api -X PATCH /repos/WolfTasks/TaskWolf/dependabot/alerts/31 -f state=dismissed -f dismissed_reason=not_used -f dismissed_comment="commons-lang3 already pinned to 3.18.0 via build.gradle.kts override; lockfile resolves 3.18.0."`
Expected: JSON mit `"state": "dismissed"`.

- [ ] **Step 3: #78 jackson-databind als tolerable_risk dismissen**

Run: `gh api -X PATCH /repos/WolfTasks/TaskWolf/dependabot/alerts/78 -f state=dismissed -f dismissed_reason=tolerable_risk -f dismissed_comment="No installable fix: jackson-databind 2.21.5 unreleased, 2.18.9 is a downgrade, 3.x is a major jump. Deferring until the Spring Boot 3.5 BOM ships 2.21.5 (cf. issue #33)."`
Expected: JSON mit `"state": "dismissed"`.

- [ ] **Step 4: Endzustand verifizieren**

Run: `gh api --paginate '/repos/WolfTasks/TaskWolf/dependabot/alerts?state=open' --jq '.[].number'`
Expected: **leere Ausgabe** (keine offenen Alerts mehr) — jackson (#78) und ggf. commons-lang3 (#31) sind als dismissed nicht mehr `state=open`.

- [ ] **Step 5: Backlog-Status aktualisieren**

In `docs/superpowers/specs/2026-07-07-backlog-overview.md` die #12-Zeile auf erledigt setzen (Muster wie #3/#7/#8, inkl. „jackson #78 deferred") und committen:

```bash
git add docs/superpowers/specs/2026-07-07-backlog-overview.md
git commit -m "docs(backlog): mark #12 done (Dependabot alerts cleared; jackson #78 deferred)"
```

---

## Self-Review (Plan ↔ Spec)

- **Spec-Abdeckung:** logback-Bump → Task 1 Step 2; commons-compress-Bump → Task 1 Step 3; Lockfile-Update → Steps 4–5; Build/Tests → Step 6; #31-Triage → Task 2 Steps 1–2; #78-Defer/Dismiss → Task 2 Step 3; „Alert-Liste sauber" → Task 2 Step 4. Out-of-Scope (jackson forcieren) explizit in Global Constraints ausgeschlossen.
- **Placeholder-Scan:** keine TBD/TODO; der Fallback (logback-Property greift nicht) ist mit konkretem Constraint-Code hinterlegt.
- **Typ-Konsistenz:** Paket-Koordinaten und Zielversionen überall identisch zu Global Constraints (logback-core 1.5.35, commons-compress 1.26.0, commons-lang3 3.18.0, jackson 2.21.4). Alert-Nummern konsistent (#79/#78/#31/#1/#2).
