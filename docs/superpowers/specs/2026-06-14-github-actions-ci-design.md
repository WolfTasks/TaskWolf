# GitHub Actions CI — Design Spec

**Date:** 2026-06-14
**Status:** Approved

## Overview

Add a GitHub Actions CI workflow that runs backend tests and frontend build checks in parallel on every push and pull request to `main`, and builds + pushes Docker images to GitHub Container Registry (ghcr.io) on successful merges to `main`. The workflow can also be triggered manually via `gh workflow run`.

## Workflow File

**Path:** `.github/workflows/ci.yml`

**Triggers:**
- `push` to `main`
- `pull_request` targeting `main`
- `workflow_dispatch` (manual trigger via `gh workflow run ci.yml`)

## Job Structure

```
backend-test ──► backend-docker
frontend-build ──► frontend-docker
```

`backend-test` and `frontend-build` run in parallel. Each Docker job runs only after its corresponding build/test job succeeds (`needs:`).

## Job Definitions

### `backend-test` (`ubuntu-latest`)

1. Checkout code
2. Set up JDK 21 (Temurin distribution) with Gradle cache
3. Run `./gradlew test` in `backend/`

**Notes:**
- Testcontainers (PostgreSQL) works out of the box on GitHub Actions — Docker is available on `ubuntu-latest`
- The local Windows override (`DOCKER_HOST=tcp://localhost:2375`, `api.version=1.44`) in `build.gradle.kts` is not needed on Linux runners and is not set in CI

### `frontend-build` (`ubuntu-latest`)

1. Checkout code
2. Set up Node.js 20 with npm cache
3. `npm ci` in `frontend/`
4. `npm run build` → runs `tsc && vite build`; TypeScript errors fail the job

### `backend-docker` (needs: `backend-test`)

1. Checkout code
2. Set up JDK 21 with Gradle cache
3. `./gradlew bootJar` to produce the JAR
4. Log in to `ghcr.io` with `GITHUB_TOKEN`
5. Build Docker image using `backend/Dockerfile`
6. Push image only on `push` to `main` (not on PRs)

**Image:** `ghcr.io/${{ github.repository_owner }}/taskowolf-backend`
**Tags:** `latest`, `sha-<7-char-commit-sha>` (resolved from `${{ github.sha }}`)

### `frontend-docker` (needs: `frontend-build`)

1. Checkout code
2. Log in to `ghcr.io` with `GITHUB_TOKEN`
3. Build Docker image using `frontend/Dockerfile` (multi-stage: Node build → nginx serve)
4. Push image only on `push` to `main` (not on PRs)

**Image:** `ghcr.io/${{ github.repository_owner }}/taskowolf-frontend`
**Tags:** `latest`, `sha-<7-char-commit-sha>` (resolved from `${{ github.sha }}`)

**Notes:**
- No separate `npm ci`/`npm run build` step needed — the Dockerfile handles the full Node build internally
- The PR build verifies the Dockerfile is valid without polluting the registry with unmerged code

## Permissions

The workflow uses the auto-provided `GITHUB_TOKEN` with:
```yaml
permissions:
  contents: read
  packages: write
```

No additional secrets required.

## Image Push Strategy

| Event | backend-test | frontend-build | Docker push |
|-------|-------------|----------------|-------------|
| PR | runs | runs | no |
| push to main | runs | runs | yes |
| workflow_dispatch | runs | runs | yes |

## CLI Usage

```bash
# Trigger manually
gh workflow run ci.yml

# Watch live output
gh run watch

# List recent runs
gh run list --workflow=ci.yml
```
