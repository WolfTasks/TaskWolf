# Gradle 9 Wrapper Upgrade (two-step) — Design

**Date:** 2026-07-03
**Status:** Approved (design)
**Scope owner:** Wolfgang Kozian
**Branch:** `worktree-kotlin-gradle9-migration`

## Relationship to the parent migration

This **supersedes Step 3 / Task 4** of the parent
[Kotlin 2.4 / Gradle 9 migration](2026-07-02-kotlin-gradle9-migration-design.md).
That document's Gradle step was deliberately loose ("Gradle 9.x", single jump to
9.6.1, plugin bumps "if needed"). Steps 1–2 of the parent (the `compilerOptions{}`
DSL and Kotlin 2.0→2.4) are **done and committed** (`60ca313`, `7e6bf1f`), plus a
follow-up hardening of the issue list/update read endpoints (`d1db913`). This spec
refines only the remaining Gradle-wrapper work into a concrete, two-step plan and
inherits the parent's Non-goals, Global Constraints, and CI-gate definition
unchanged.

## Goal

Move the backend Gradle wrapper from **8.10** to **Gradle 9**, clearing the
"Gradle 8.10 is deprecated" warning the Kotlin 2.4 plugin already emits, without
breaking the 223-test suite or the `bootJar` artifact, and keeping the dependency
lockfile consistent.

## Why two steps

Gradle's own upgrade guidance is: move to the latest minor of the current major,
fix any deprecations, then jump the major. Doing that here isolates the two
distinct risk profiles so a failure points at exactly one cause:

- **Task A (8.10 → 8.14.5):** pure wrapper change, no plugin/API surface touched.
  Near-zero risk. Clears the Kotlin deprecation and proactively satisfies the
  Kotlin ≥2.5.0 Gradle floor (8.14.4). Independently green, committed checkpoint.
- **Task B (8.14.5 → 9.5.0):** the real work, where the plugin-compatibility risk
  lives. Attempted only from a known-green 8.14.5 baseline.

Rejected: single 8.10 → 9.x jump — a break can't be attributed to the wrapper vs.
a plugin incompatibility, and there is no intermediate clean checkpoint.

## Target versions (facts confirmed 2026-07-03)

- **Task A → Gradle 8.14.5** — latest 8.14.x (released 2026-05-07).
- **Task B → Gradle 9.5.0** — the highest Gradle release **fully supported by
  Kotlin 2.4.0** (KGP 2.4.0 tested range: Gradle 7.6.3 – 9.5.0). Deliberately
  **not 9.6.1**: 9.6.x is outside Kotlin 2.4.0's tested range and would couple
  this task to a Kotlin bump. A future Kotlin 2.5+ task can take 9.6.x.
- **`org.owasp.dependencycheck`** — currently 11.1.0. Not verified for Gradle 9;
  bump to **12.2.2** (or latest) **only if** it rejects Gradle 9 in Task B.

## Primary risk: `io.spring.dependency-management` 1.1.7

The plugin officially documents only Gradle 6.8+/7.x/**8.x** — no Gradle 9
support. This build actively relies on it for exactly one thing: the
`dependencyManagement { imports { mavenBom("org.testcontainers:testcontainers-bom:1.20.4") } }`
block (`build.gradle.kts:57–61`). Spring Boot's own BOM is applied via the Spring
Boot plugin, so that Testcontainers import is the **only** hard dependency on the
plugin. Decision tree for Task B:

1. Bump the wrapper to 9.5.0 and run `clean test bootJar --warning-mode all`.
2. **Plugin works** (warnings tolerated) → keep it; record residual warnings.
3. **Plugin breaks on Gradle 9** → migrate that one block to Gradle-native
   `testImplementation(platform("org.testcontainers:testcontainers-bom:1.20.4"))`
   and remove the `io.spring.dependency-management` plugin entirely. Contained,
   reversible change; regenerate the lockfile.

The same "try current, bump only if it rejects Gradle 9" rule applies to
`org.owasp.dependencycheck`.

## Other Gradle 9 surface (low likelihood on this build)

- Removed/renamed APIs — this is a simple single-module `plugins {}` build; scan
  with `--warning-mode all` and fix any `will fail in Gradle 10` warnings
  originating from `build.gradle.kts`.
- `dependencyLocking` — file format is stable across 8→9; just regenerate.
- Config cache, parallel/caching tuning — **out of scope** (not enabling).

## Verification

- No new application tests. The existing **223-test** suite (real Postgres via
  Testcontainers) is the regression gate.
- Each task must independently reach `BUILD SUCCESSFUL` + **223/223** before its
  commit; run with `--warning-mode all` to surface deprecations.
- `backend/gradle.lockfile` regenerated with `--write-locks` in the **same commit**
  as the wrapper change (Gradle 9 may resolve plugin transitives differently), or
  CI fails on a lock mismatch.
- Final gate: the whole-branch PR passes all 11 required CI checks (per the parent
  plan's Task 5) — the PR carries the full branch (Kotlin 2.4 DSL + read-endpoint
  fix + both Gradle bumps).

## Non-goals

Inherited from the parent, plus: config cache, Kotlin 2.5+, Gradle 9.6.x, and the
frontend build. springdoc 3 / Testcontainers 2 / okhttp 5 / Spring Boot 4 remain
deferred under the blanket backend `semver-major` Dependabot ignore.

## Process

Continue on `worktree-kotlin-gradle9-migration`: Task A commit → Task B commit →
the branch's single PR to `main` (parent plan Task 5) → squash-merge when the 11
required checks are green. Spec + plan committed on the same branch.
