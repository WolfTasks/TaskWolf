# Backend Kotlin 2.4 / Gradle 9 Toolchain Migration — Design

**Date:** 2026-07-02
**Status:** Approved (design)
**Scope owner:** Wolfgang Kozian
**Branch:** `worktree-kotlin-gradle9-migration`

## Background

Dependabot's `backend-deps` group PR #17 batched several breaking major bumps into a
single PR (Gradle 8.10→9, Kotlin 2.0→2.4, springdoc 2→3, Testcontainers 1→2,
okhttp mockwebserver 4→5, owasp-dependency-check 11→12) and failed CI. It was
closed, and a blanket `dependency-name: "*"` + `version-update:semver-major`
ignore was added for the backend Gradle ecosystem in `.github/dependabot.yml`
(PR #21), on the principle that backend majors are handled as deliberate,
hand-driven migrations rather than automated batched bumps.

This is the first of those deliberate migrations: bring the **build toolchain**
(Gradle + Kotlin) up to date, including the `kotlinOptions{}` →
`compilerOptions{}` Kotlin Gradle Plugin (KGP) DSL change.

## Goal

Move the backend build to **Gradle 9.x** and **Kotlin 2.4.x** with the modern
`compilerOptions{}` DSL, keeping the full CI gate (11 required checks) green and
the dependency lockfile consistent — without pulling in unrelated library-API
major upgrades.

## Non-goals (explicitly deferred)

These remain deferred and continue to be covered by the blanket backend
`semver-major` ignore. Each gets its own effort if/when wanted:

- springdoc-openapi 2.8.x → 3.x (real API changes)
- Testcontainers 1.20.x → 2.x
- okhttp3 mockwebserver 4.12.x → 5.x
- Spring Boot 3.5.x → 4.x

## Current state (facts)

- `backend/build.gradle.kts` plugins: Spring Boot 3.5.16, `io.spring.dependency-management` 1.1.7,
  `org.owasp.dependencycheck` 11.1.0, `kotlin("jvm"|"plugin.spring"|"plugin.jpa")` 2.0.0.
- Java: `sourceCompatibility = 21`; CI builds on Temurin **JDK 21** (`./gradlew test`, `./gradlew bootJar`).
- Gradle wrapper: **8.10** (`backend/gradle/wrapper/gradle-wrapper.properties`).
- The DSL block to migrate, `backend/build.gradle.kts:71–76`:
  ```kotlin
  import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
  tasks.withType<KotlinCompile> {
      kotlinOptions {
          freeCompilerArgs = listOf("-Xjsr305=strict")
          jvmTarget = "21"
      }
  }
  ```
- **Dependency locking is active**: `dependencyLocking { lockAllConfigurations() }` +
  `backend/gradle.lockfile`. Any dependency/plugin version change requires
  regenerating the lockfile with `--write-locks`, or the build fails on a lock
  mismatch.
- Windows-only Testcontainers Docker workaround in the `Test` task
  (`build.gradle.kts:78–87`) — unchanged by this migration.
- CI relevant checks: `backend-test`, `backend-docker`, `dependency-submission`
  (`gradle/actions/dependency-submission@v6`), `analyze (java-kotlin)`.

## Approach: staged commits, one branch → one PR

Land the migration in three independently-verified steps on a single branch,
regenerating the lockfile at each dependency change so a failure points at
exactly one cause. (Rejected alternatives: "big bang" — intertwined failure
causes; "Gradle-first" — Gradle 9 chokes on old KGP/`kotlinOptions`, debugging
against a moving target.)

### Step 1 — `kotlinOptions{}` → `compilerOptions{}` DSL

The `compilerOptions{}` DSL is already stable in KGP 2.0, so this compiles green
on the *current* toolchain in isolation. New form:

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
        jvmTarget.set(JvmTarget.JVM_21)
    }
}
```

Notes: `freeCompilerArgs` is now a `ListProperty` (`.add(...)` / `.addAll(...)`,
not `=`); `jvmTarget` takes the `JvmTarget` enum, not a `"21"` string. No
dependency change → no lockfile change. Verify: `./gradlew test bootJar`.

### Step 2 — Kotlin 2.0.0 → 2.4.x

Bump the three `kotlin(...)` plugin versions to the latest stable 2.4.x.
Regenerate the lockfile (`./gradlew dependencies --write-locks` or
`./gradlew test --write-locks`). Verify: `./gradlew test bootJar`.

### Step 3 — Gradle 8.10 → 9.x (+ required plugin bumps)

Bump the wrapper via `./gradlew wrapper --gradle-version <latest-9.x>`
(updates `gradle-wrapper.properties`, `gradle-wrapper.jar`, `gradlew`,
`gradlew.bat`). Bump `org.owasp.dependencycheck` (11→12.x) and, if needed,
`io.spring.dependency-management`, **only** as far as Gradle 9 compatibility
requires. Regenerate the lockfile. Verify: `./gradlew test bootJar` and
`./gradlew help --warning-mode=all` to surface any remaining Gradle 9
deprecations.

## Verification

- Local: `./gradlew test bootJar` on JDK 21 after each step; `--warning-mode=all`
  clean at the end.
- CI gate: all 11 required checks green on the PR — especially `backend-test`,
  `backend-docker`, `dependency-submission`, `analyze (java-kotlin)`.
- Lockfile: `backend/gradle.lockfile` regenerated and committed; no lock-mismatch
  failures in CI.

## Risks & rollback

- **A plugin lacks Gradle 9 support.** Most likely `io.spring.dependency-management`
  or `org.owasp.dependencycheck`. Mitigation: bump to its Gradle-9-compatible
  release. **Fallback:** if no compatible release exists, stop after Step 2
  (Kotlin 2.4 + DSL shipped) and defer Gradle 9 — do not force it.
- **Lockfile drift.** Every dependency/plugin change regenerates the lockfile in
  the same commit; CI verifies consistency.
- **Staged commits** mean any failure reverts cleanly to the previous green step.

## Target versions

Pinned to latest-stable at implementation time (baseline from #17: Gradle 9.6.1,
Kotlin 2.4.0, dependency-check 12.2.2). Exact patch versions confirmed during the
implementation plan.

## Process

Isolated worktree `worktree-kotlin-gradle9-migration` → staged commits (Steps
1–3) → single PR to `main` → merge when the 11 required checks are green (squash
+ delete branch). Spec and implementation plan committed on the same branch,
consistent with prior phases.
