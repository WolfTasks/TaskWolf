# Gradle 9 Wrapper Upgrade (two-step) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the backend Gradle wrapper from 8.10 to Gradle 9.5.0 via an intermediate 8.14.5 checkpoint, keeping the 223-test suite and `bootJar` green and the dependency lockfile consistent.

**Architecture:** Two independently-verified commits on `worktree-kotlin-gradle9-migration`: (A) low-risk wrapper bump to the latest 8.14.x, (B) the Gradle 9 jump where the plugin-compatibility risk lives. The dependency lockfile is regenerated in the same commit as each wrapper change. The branch already carries the parent migration's committed work (Kotlin 2.4 `compilerOptions{}` DSL + read-endpoint hardening); this plan finishes it and the branch goes to `main` as one PR.

**Tech Stack:** Gradle (Kotlin DSL build), Kotlin Gradle Plugin 2.4.0, Spring Boot 3.5.16, JDK 21 (Temurin), `io.spring.dependency-management` 1.1.7, `org.owasp.dependencycheck` 11.1.0, dependency locking, Testcontainers.

Supersedes Step 3 / Task 4 of `docs/superpowers/plans/2026-07-02-kotlin-gradle9-migration.md`. Design: `docs/superpowers/specs/2026-07-03-gradle9-wrapper-upgrade-design.md`.

## Global Constraints

Copied from the spec; every task's requirements implicitly include these.

- **Build JDK:** Temurin **JDK 21** (matches CI). `jvmTarget` = `JvmTarget.JVM_21` — unchanged by this plan.
- **Dependency locking is active** (`dependencyLocking { lockAllConfigurations() }` + `backend/gradle.lockfile`). Every wrapper/plugin change MUST regenerate the lockfile with `--write-locks` in the **same commit**, or the build fails on a lock mismatch.
- **Toolchain-only scope.** Do NOT bump springdoc-openapi, Testcontainers, okhttp3 mockwebserver, or Spring Boot (deferred under the blanket backend `semver-major` Dependabot ignore). Do NOT enable the config cache.
- **Target versions:** intermediate Gradle **8.14.5**, final Gradle **9.5.0** (Kotlin 2.4.0's tested max — deliberately not 9.6.x). `org.owasp.dependencycheck` 11.1.0 → **12.2.2** and/or `io.spring.dependency-management` migration **only if** Gradle 9 requires it (see Task B).
- **Regression gate:** `./gradlew clean test bootJar` = `BUILD SUCCESSFUL`, **223/223** tests, at each task before commit.
- **Merge gate:** all 11 required CI checks green before merge (esp. `backend-test`, `backend-docker`, `dependency-submission`, `analyze (java-kotlin)`).

**Command context:** All `gradlew` commands run from the `backend/` directory. Examples use the POSIX wrapper (`./gradlew`, git-bash); on Windows PowerShell use `.\gradlew.bat` with the same arguments. Integration tests use Testcontainers and require a running Docker daemon locally (the `Test` task has a Windows/Docker-Desktop workaround at `build.gradle.kts:79-88`); if local Docker is unavailable, use the compile-only fast check noted in each task and rely on CI for the full `test` run.

---

### Task A: Intermediate bump — Gradle 8.10 → 8.14.5

Pure wrapper change: no plugin or build-script API touched. Clears the "Gradle 8.10 is deprecated" warning the Kotlin 2.4 plugin emits and satisfies the Kotlin ≥2.5.0 Gradle floor (8.14.4) proactively. Establishes a known-green 8.14.x baseline for the Gradle 9 jump.

**Files:**
- Modify: `backend/gradle/wrapper/gradle-wrapper.properties`, `backend/gradle/wrapper/gradle-wrapper.jar`, `backend/gradlew`, `backend/gradlew.bat` (wrapper upgrade)
- Modify: `backend/gradle.lockfile` (regenerated only if Gradle 8.14.5 resolves plugin transitives differently)

**Interfaces:**
- Consumes: the current green branch tip (Kotlin 2.4.0, `compilerOptions{}` DSL, read-endpoint fix — all committed).
- Produces: a Gradle 8.14.5 build, deprecation-clean, as the baseline Task B jumps from.

- [ ] **Step 1: Confirm the green starting point**

Run (from `backend/`):
```bash
./gradlew --version
./gradlew clean test bootJar
```
Expected: `Gradle 8.10`, `JVM: 21`; `BUILD SUCCESSFUL` with 223 tests. (Docker unavailable? Run `./gradlew clean compileKotlin compileTestKotlin bootJar` and rely on CI for `test`.)

- [ ] **Step 2: Upgrade the wrapper to 8.14.5 (two-pass)**

The first pass rewrites `gradle-wrapper.properties`; the second (run under the new version) refreshes `gradle-wrapper.jar` + the `gradlew`/`gradlew.bat` scripts. Run (from `backend/`):
```bash
./gradlew wrapper --gradle-version 8.14.5 --distribution-type bin
./gradlew wrapper --gradle-version 8.14.5 --distribution-type bin
```

- [ ] **Step 3: Verify the wrapper version**

Run (from `backend/`):
```bash
./gradlew --version
```
Expected: `Gradle 8.14.5`. `gradle-wrapper.properties` `distributionUrl` now references `gradle-8.14.5-bin.zip`.

- [ ] **Step 4: Build green and scan for new deprecations**

Run (from `backend/`):
```bash
./gradlew clean test bootJar --warning-mode all
```
Expected: `BUILD SUCCESSFUL`, 223 tests. The Kotlin-plugin "Gradle 8.10 is deprecated" warning is **gone**. If any *new* `will fail in Gradle 9`/`Gradle 10` deprecation originating from `build.gradle.kts` appears, fix it here before committing. (Docker unavailable? Use the compile-only check from Step 1.)

- [ ] **Step 5: Regenerate and check the lockfile**

Run (from `backend/`):
```bash
./gradlew test bootJar --write-locks
```
Then (from repo root):
```bash
git --no-pager diff --stat backend/gradle.lockfile
```
Expected: either no change, or only benign plugin-transitive shifts (no application-library major jumps). (Docker unavailable? `./gradlew compileKotlin compileTestKotlin bootJar --write-locks`.)

- [ ] **Step 6: Commit**

```bash
git add backend/gradle/wrapper/gradle-wrapper.properties backend/gradle/wrapper/gradle-wrapper.jar backend/gradlew backend/gradlew.bat backend/gradle.lockfile
git commit -m "build(gradle): bump wrapper 8.10 -> 8.14.5"
```
(If `git status` shows `backend/gradle.lockfile` unchanged, drop it from the `git add` — commit only the wrapper files.)

---

### Task B: Gradle 8.14.5 → 9.5.0

The Gradle 9 jump. Attempted only from the green 8.14.5 baseline. Primary risk: `io.spring.dependency-management` 1.1.7 has no documented Gradle 9 support; `org.owasp.dependencycheck` 11.1.0 is likewise unverified. Both are handled with a "try current, change only if Gradle 9 rejects it" rule so the diff stays minimal.

**Files:**
- Modify: `backend/gradle/wrapper/gradle-wrapper.properties`, `backend/gradle/wrapper/gradle-wrapper.jar`, `backend/gradlew`, `backend/gradlew.bat` (wrapper upgrade)
- Modify (conditional): `backend/build.gradle.kts` — `org.owasp.dependencycheck` version bump and/or the `io.spring.dependency-management` → native `platform()` migration, only if Task B Step 2/3 forces it
- Modify: `backend/gradle.lockfile` (regenerated)

**Interfaces:**
- Consumes: the green Gradle 8.14.5 baseline from Task A.
- Produces: a Gradle 9.5.0 build — the migration deliverable.

- [ ] **Step 1: Upgrade the wrapper to 9.5.0 (two-pass)**

Run (from `backend/`):
```bash
./gradlew wrapper --gradle-version 9.5.0 --distribution-type bin
./gradlew wrapper --gradle-version 9.5.0 --distribution-type bin
./gradlew --version
```
Expected: `Gradle 9.5.0`; `distributionUrl` references `gradle-9.5.0-bin.zip`. (If the first pass fails to even configure the build — e.g. a plugin rejects Gradle 9 at configuration time — proceed to Step 2/3, which fix the plugins, then re-run this two-pass.)

- [ ] **Step 2: First Gradle 9 build + lockfile (surfaces plugin incompatibilities)**

Run (from `backend/`):
```bash
./gradlew clean test bootJar --write-locks --warning-mode all
```
Expected on success: `BUILD SUCCESSFUL`, 223 tests. If it succeeds, skip Step 3 and go to Step 4. (Docker unavailable? `./gradlew clean compileKotlin compileTestKotlin bootJar --write-locks --warning-mode all`.)

- [ ] **Step 3: (Only if Step 2 failed) Fix the incompatible plugin(s)**

Apply whichever of the following the failure names, then re-run Step 1's two-pass (if configuration failed) and Step 2:

**(a) `org.owasp.dependencycheck` rejects Gradle 9** — bump it in `backend/build.gradle.kts` line 7:
```kotlin
    id("org.owasp.dependencycheck") version "12.2.2"
```
(12.2.2 is the version aligned with Gradle 9 from the closed batch PR #17; if it too rejects 9.5.0, take the latest release from the Gradle Plugin Portal.)

**(b) `io.spring.dependency-management` rejects Gradle 9** — remove the plugin and replace its single use with Gradle-native BOM import. In `backend/build.gradle.kts`, delete the plugin line:
```kotlin
    id("io.spring.dependency-management") version "1.1.7"
```
and replace the block at `build.gradle.kts:57-61`:
```kotlin
// Override Spring Boot BOM version for Testcontainers to support Docker Desktop 4.x on Windows
dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:1.20.4")
    }
}
```
with a native platform constraint in the `dependencies { }` block (place next to the Testcontainers test deps):
```kotlin
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.20.4"))
```
Note: the Spring Boot BOM (which manages versions for the unversioned `implementation("org.springframework.boot:...")` entries) is applied by the `org.springframework.boot` plugin itself, **not** by `io.spring.dependency-management`, so removing this plugin does not unmanage the Spring dependencies. After editing, re-run Step 1's two-pass and Step 2.

- [ ] **Step 4: Full deprecation scan on Gradle 9**

Run (from `backend/`):
```bash
./gradlew help --warning-mode all
./gradlew clean test bootJar --warning-mode all
```
Expected: `BUILD SUCCESSFUL`, 223 tests, and no `has been deprecated and will fail in Gradle 10` warnings originating from `build.gradle.kts`. Fix any that do (concrete renamed/removed APIs) before committing.

- [ ] **Step 5: Sanity-check the lockfile diff**

Run (from repo root):
```bash
git --no-pager diff backend/gradle.lockfile | grep -iE "dependency-check|dependencycheck|testcontainers" | head
```
Expected: only the plugin/BOM coordinates you deliberately changed in Step 3 (if any) moved; no unrelated application-library major jumps (springdoc/okhttp/Boot untouched).

- [ ] **Step 6: Commit**

```bash
git add backend/gradle/wrapper/gradle-wrapper.properties backend/gradle/wrapper/gradle-wrapper.jar backend/gradlew backend/gradlew.bat backend/gradle.lockfile
git add backend/build.gradle.kts 2>/dev/null || true   # only if Step 3 edited it
git commit -m "build(gradle): bump wrapper 8.14.5 -> 9.5.0"
```
(If Step 3 changed plugins, expand the commit message body to note the dependency-check bump and/or the dependency-management → native `platform()` migration.)

---

### Task C: Open the branch PR, pass CI, merge

Integration for the whole `worktree-kotlin-gradle9-migration` branch (Kotlin 2.4 DSL + read-endpoint hardening + both Gradle bumps). This realizes the parent plan's Task 5 for the completed branch.

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
  --body "Toolchain migration per docs/superpowers/specs/2026-07-02-kotlin-gradle9-migration-design.md and docs/superpowers/specs/2026-07-03-gradle9-wrapper-upgrade-design.md: kotlinOptions{}->compilerOptions{} DSL, Kotlin 2.0->2.4, Gradle 8.10->8.14.5->9.5.0. Also hardens the issue list/update read endpoints (@Transactional) against LazyInitializationException with OSIV off. Library majors (springdoc 3, Testcontainers 2, okhttp 5, Boot 4) remain deferred. Lockfile regenerated at each step."
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

Mark the Kotlin/Gradle 9 migration DONE in the TaskWolf memory (tracked as a deferred follow-up in `project-dependabot-ts6-migration` and `project-taskowolf`), recording final versions (Gradle 9.5.0, Kotlin 2.4.0) and any plugin change forced for Gradle 9 (dependency-check bump and/or the dependency-management → native `platform()` migration).

---

## Self-Review

**Spec coverage:**
- Two-step rationale + Task A (8.14.5) → Task A. ✓
- Target 9.5.0 (not 9.6.x), Kotlin 2.4.0 range → Global Constraints + Task B Step 1. ✓
- `io.spring.dependency-management` decision tree (keep / migrate to native `platform()`) → Task B Step 3(b). ✓
- `org.owasp.dependencycheck` "bump only if it rejects Gradle 9" → Task B Step 3(a). ✓
- Lockfile regenerated in the same commit as each wrapper change → Task A Step 5, Task B Step 2/6. ✓
- 223-test regression gate at each task → Steps A4/A5, B2/B4. ✓
- Deprecation scan (`--warning-mode all`) → A4, B4. ✓
- Config cache / Kotlin 2.5+ / 9.6.x / frontend out of scope → Global Constraints; no task touches them. ✓
- Whole-branch PR + 11-check CI gate → Task C. ✓
- Windows Testcontainers workaround untouched → no task modifies `build.gradle.kts:79-88`. ✓

**Placeholder scan:** `<PR#>` in Task C is a runtime value (the PR number from Step 2), not an unfilled placeholder. No "TODO/TBD/handle edge cases". Versions pinned (8.14.5 / 9.5.0 / 12.2.2 / 1.20.4).

**Type/name consistency:** wrapper file paths, `--distribution-type bin`, `--write-locks`, `--warning-mode all`, branch/version names, and the `platform("org.testcontainers:testcontainers-bom:1.20.4")` coordinate are used identically across tasks and match the spec.

**Note on line numbers:** `build.gradle.kts` references (plugin line 7; block 57-61; Test task 79-88) are current as of this branch tip; re-confirm by reading the file at execution time, since Task A does not modify it but a re-read is cheap.
