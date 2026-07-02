# Backend Kotlin 2.4 / Gradle 9 Toolchain Migration — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the backend build to Gradle 9.6.1 and Kotlin 2.4.0 with the modern `compilerOptions{}` Kotlin DSL, keeping the full CI gate green.

**Architecture:** Staged, independently-verified commits on one branch (`worktree-kotlin-gradle9-migration`): (1) migrate the Kotlin compiler DSL on the current toolchain, (2) bump Kotlin, (3) bump Gradle + required plugins. The dependency lockfile is regenerated in the same commit as any dependency/plugin change. One PR to `main`; merge when the 11 required checks are green.

**Tech Stack:** Gradle (Kotlin DSL build script), Kotlin Gradle Plugin, Spring Boot 3.5.x, JDK 21 (Temurin), dependency locking, Testcontainers (integration tests).

## Global Constraints

Copied verbatim from the spec; every task's requirements implicitly include these.

- **Build JDK:** Temurin **JDK 21** (matches CI). `jvmTarget` = `JvmTarget.JVM_21`.
- **Dependency locking is active** (`dependencyLocking { lockAllConfigurations() }` + `backend/gradle.lockfile`). Every dependency/plugin version change MUST regenerate the lockfile with `--write-locks` in the **same commit**, or the build fails on a lock mismatch.
- **Toolchain-only scope.** Do NOT bump springdoc-openapi, Testcontainers, okhttp3 mockwebserver, or Spring Boot. They stay deferred (covered by the blanket backend `semver-major` ignore in `.github/dependabot.yml`).
- **Target versions:** Gradle **9.6.1**, Kotlin **2.4.0**, `org.owasp.dependencycheck` **12.2.2**. `io.spring.dependency-management` stays **1.1.7** unless Gradle 9 requires a bump (see Task 4 fallback).
- **Merge gate:** all 11 required CI checks green before merge (esp. `backend-test`, `backend-docker`, `dependency-submission`, `analyze (java-kotlin)`).
- **Hard fallback:** if a required plugin has no Gradle-9-compatible release, STOP after Task 3 (Kotlin 2.4 + DSL shipped) and defer Gradle 9 — do not force it.

**Command context:** All `gradlew` commands run from the `backend/` directory. Examples use the POSIX wrapper (`./gradlew`, git-bash); on Windows PowerShell use `.\gradlew.bat` with the same arguments. Integration tests use Testcontainers and require a running Docker daemon locally; if local Docker is unavailable, use the compile-only fast check noted in each task and rely on CI for the full `test` run.

---

### Task 1: Establish a green baseline

Confirm the freshly-branched worktree builds and tests green **before** any change, so later failures are unambiguously attributable to a step.

**Files:**
- Modify: none (verification only)

- [ ] **Step 1: Confirm toolchain versions**

Run (from `backend/`):
```bash
./gradlew --version
```
Expected: `Gradle 8.10`, `JVM: 21` (Temurin). If JVM is not 21, switch the active JDK before continuing.

- [ ] **Step 2: Run the full build**

Run (from `backend/`):
```bash
./gradlew clean test bootJar
```
Expected: `BUILD SUCCESSFUL`. (Requires Docker for Testcontainers. Docker unavailable? Run `./gradlew clean compileKotlin compileTestKotlin bootJar` instead and rely on CI for `test`.)

- [ ] **Step 3: Confirm the lockfile is clean**

Run (from repo root):
```bash
git status --short backend/gradle.lockfile
```
Expected: no output (baseline build did not alter the lockfile).

---

### Task 2: Migrate `kotlinOptions{}` → `compilerOptions{}`

The `compilerOptions{}` DSL is already stable in KGP 2.0, so this compiles green on the current toolchain in isolation. No dependency change → no lockfile change.

**Files:**
- Modify: `backend/build.gradle.kts:1` (imports), `backend/build.gradle.kts:71-76` (the `KotlinCompile` block)

**Interfaces:**
- Consumes: green baseline from Task 1.
- Produces: build script using the modern Kotlin compiler DSL, required by Kotlin 2.4 / Gradle 9 in later tasks.

- [ ] **Step 1: Add the `JvmTarget` import**

At the top of `backend/build.gradle.kts`, alongside the existing `import org.jetbrains.kotlin.gradle.tasks.KotlinCompile`, add:
```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
```

- [ ] **Step 2: Replace the `kotlinOptions` block**

Replace `backend/build.gradle.kts:71-76`:
```kotlin
tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "21"
    }
}
```
with:
```kotlin
tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
        jvmTarget.set(JvmTarget.JVM_21)
    }
}
```
Note the DSL differences: `freeCompilerArgs` is a `ListProperty` (`.add(...)`, not `=`), and `jvmTarget` takes the `JvmTarget` enum (not the string `"21"`).

- [ ] **Step 3: Verify the build (and no deprecation on the DSL)**

Run (from `backend/`):
```bash
./gradlew clean test bootJar --warning-mode=all
```
Expected: `BUILD SUCCESSFUL`, and no `kotlinOptions`/`jvmTarget` deprecation warnings in the output. (Docker unavailable? Use the compile-only fast check from Task 1 Step 2.)

- [ ] **Step 4: Confirm the lockfile did not change**

Run (from repo root):
```bash
git status --short backend/gradle.lockfile
```
Expected: no output.

- [ ] **Step 5: Commit**

```bash
git add backend/build.gradle.kts
git commit -m "refactor(backend): migrate Kotlin kotlinOptions{} to compilerOptions{} DSL"
```

---

### Task 3: Bump Kotlin 2.0.0 → 2.4.0

**Files:**
- Modify: `backend/build.gradle.kts:7-9` (the three `kotlin(...)` plugin versions)
- Modify: `backend/gradle.lockfile` (regenerated)

**Interfaces:**
- Consumes: `compilerOptions{}` DSL from Task 2 (Kotlin 2.4 removes/deprecates the old `kotlinOptions` path).
- Produces: Kotlin 2.4.0 toolchain, prerequisite for Gradle 9 (Task 4).

- [ ] **Step 1: Bump the Kotlin plugin versions**

In `backend/build.gradle.kts`, change lines 7-9 from `version "2.0.0"` to `version "2.4.0"`:
```kotlin
    kotlin("jvm") version "2.4.0"
    kotlin("plugin.spring") version "2.4.0"
    kotlin("plugin.jpa") version "2.4.0"
```

- [ ] **Step 2: Regenerate the lockfile**

Run (from `backend/`):
```bash
./gradlew test bootJar --write-locks
```
Expected: `BUILD SUCCESSFUL`; `backend/gradle.lockfile` now shows Kotlin artifacts at 2.4.0. (Docker unavailable? Use `./gradlew compileKotlin compileTestKotlin bootJar --write-locks`.)

- [ ] **Step 3: Verify a clean build against the new lockfile**

Run (from `backend/`):
```bash
./gradlew clean test bootJar
```
Expected: `BUILD SUCCESSFUL`, no lock-mismatch error.

- [ ] **Step 4: Sanity-check the lockfile diff**

Run (from repo root):
```bash
git --no-pager diff backend/gradle.lockfile | grep -iE "kotlin"
```
Expected: Kotlin coordinates moved `2.0.0` → `2.4.0`; no unrelated major jumps for springdoc/testcontainers/okhttp.

- [ ] **Step 5: Commit**

```bash
git add backend/build.gradle.kts backend/gradle.lockfile
git commit -m "build(deps): bump Kotlin 2.0.0 -> 2.4.0"
```

---

### Task 4: Bump Gradle 8.10 → 9.6.1 (+ required plugin bumps)

**Files:**
- Modify: `backend/gradle/wrapper/gradle-wrapper.properties`, `backend/gradle/wrapper/gradle-wrapper.jar`, `backend/gradlew`, `backend/gradlew.bat` (wrapper upgrade)
- Modify: `backend/build.gradle.kts:6` (`org.owasp.dependencycheck` version)
- Modify: `backend/gradle.lockfile` (regenerated)

**Interfaces:**
- Consumes: Kotlin 2.4.0 (Task 3) — required for Gradle 9 KGP support.
- Produces: Gradle 9.6.1 build; the migration deliverable.

- [ ] **Step 1: Bump the OWASP dependency-check plugin**

In `backend/build.gradle.kts`, change line 6:
```kotlin
    id("org.owasp.dependencycheck") version "12.2.2"
```
(11.x is not verified for Gradle 9; 12.2.2 is the version proposed alongside Gradle 9 in #17.)

- [ ] **Step 2: Upgrade the Gradle wrapper (two-pass)**

Run twice (from `backend/`) so both the properties and the wrapper scripts/jar update:
```bash
./gradlew wrapper --gradle-version 9.6.1 --distribution-type bin
./gradlew wrapper --gradle-version 9.6.1 --distribution-type bin
```
Then confirm:
```bash
./gradlew --version
```
Expected: `Gradle 9.6.1`. `gradle-wrapper.properties` `distributionUrl` now references `gradle-9.6.1-bin.zip`.

- [ ] **Step 3: Regenerate the lockfile on Gradle 9**

Run (from `backend/`):
```bash
./gradlew test bootJar --write-locks
```
Expected: `BUILD SUCCESSFUL`. (Docker unavailable? Use `./gradlew compileKotlin compileTestKotlin bootJar --write-locks`.)

**If this fails with a plugin incompatibility** (most likely `io.spring.dependency-management` or `org.owasp.dependencycheck` rejecting Gradle 9): bump the offending plugin to its latest release and re-run. Check latest with, e.g.:
```bash
./gradlew dependencyUpdates 2>/dev/null || true   # if available; otherwise check the plugin portal
```
If no Gradle-9-compatible release exists for a required plugin, invoke the **hard fallback**: revert this task's changes (`git checkout -- backend/`), stop with Kotlin 2.4 + DSL (Tasks 2–3) as the shipped result, and record Gradle 9 as still-deferred.

- [ ] **Step 4: Scan for Gradle 9 deprecations**

Run (from `backend/`):
```bash
./gradlew help --warning-mode=all
./gradlew clean test bootJar --warning-mode=all
```
Expected: `BUILD SUCCESSFUL` with no `has been deprecated and will fail in Gradle 10` warnings originating from `build.gradle.kts`. Fix any that do (they are concrete API changes, e.g. renamed properties) before committing.

- [ ] **Step 5: Sanity-check the lockfile diff**

Run (from repo root):
```bash
git --no-pager diff backend/gradle.lockfile | grep -iE "dependency-check|dependencycheck" | head
```
Expected: dependency-check coordinates moved to 12.2.2; no unrelated library major jumps.

- [ ] **Step 6: Commit**

```bash
git add backend/gradle/wrapper/gradle-wrapper.properties backend/gradle/wrapper/gradle-wrapper.jar backend/gradlew backend/gradlew.bat backend/build.gradle.kts backend/gradle.lockfile
git commit -m "build(deps): bump Gradle 8.10 -> 9.6.1 and dependency-check 11 -> 12.2.2"
```

---

### Task 5: Open PR, pass the CI gate, merge

**Files:**
- Modify: none (integration)

- [ ] **Step 1: Push the branch**

```bash
git push -u origin worktree-kotlin-gradle9-migration
```

- [ ] **Step 2: Open the PR**

```bash
gh pr create --base main --head worktree-kotlin-gradle9-migration \
  --title "build(deps): Kotlin 2.4 / Gradle 9 backend toolchain migration" \
  --body "Toolchain-only migration per docs/superpowers/specs/2026-07-02-kotlin-gradle9-migration-design.md: Gradle 8.10->9.6.1, Kotlin 2.0->2.4, kotlinOptions{}->compilerOptions{} DSL, dependency-check 11->12.2.2. Library majors (springdoc 3, Testcontainers 2, okhttp 5, Boot 4) remain deferred. Lockfile regenerated at each step."
```

- [ ] **Step 3: Wait for and verify the CI gate**

```bash
gh pr checks <PR#> --watch --interval 30
```
Expected: all 11 required checks `pass` — especially `backend-test`, `backend-docker`, `dependency-submission`, `analyze (java-kotlin)`. Investigate and fix any failure (push follow-up commits) before merging.

- [ ] **Step 4: Merge**

```bash
gh pr merge <PR#> --squash --delete-branch
```
(If `gh` errors on the post-merge local checkout switch because `main` is checked out in the primary worktree, the merge still succeeds; delete the remote branch with `git push origin --delete worktree-kotlin-gradle9-migration`.)

- [ ] **Step 5: Update memory**

Mark the Kotlin/Gradle 9 migration DONE in the TaskWolf memory (it was tracked as a deferred follow-up in `project-dependabot-ts6-migration` and `project-taskowolf`), recording final versions (Gradle 9.6.1, Kotlin 2.4.0) and any plugin bump forced for Gradle 9 compatibility.

---

## Self-Review

**Spec coverage:**
- Step 1 DSL migration → Task 2. ✓
- Step 2 Kotlin 2.4 + lockfile → Task 3. ✓
- Step 3 Gradle 9 + plugin bumps + deprecation scan + lockfile → Task 4. ✓
- Dependency-locking regeneration at each dep change → Tasks 3 & 4 use `--write-locks` and commit the lockfile. ✓
- Non-goals (springdoc/Testcontainers/okhttp/Boot untouched) → asserted via lockfile diff checks in Tasks 3 & 4; no task touches them. ✓
- Verification against CI gate → Task 5. ✓
- Risk/rollback (stop after Kotlin if a plugin lacks Gradle 9 support) → Task 4 Step 3 hard fallback. ✓
- Windows Testcontainers workaround untouched → no task modifies build.gradle.kts:78-87. ✓

**Placeholder scan:** `<PR#>` in Task 5 is a runtime value (the PR number from Step 2), not an unfilled plan placeholder. No "TODO/TBD/handle edge cases" present. Versions are pinned (9.6.1 / 2.4.0 / 12.2.2).

**Type/name consistency:** `compilerOptions`, `JvmTarget.JVM_21`, `freeCompilerArgs.add`, and the branch/version names are used identically across tasks.
