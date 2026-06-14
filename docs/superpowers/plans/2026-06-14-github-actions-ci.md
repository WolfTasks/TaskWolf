# GitHub Actions CI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a GitHub Actions CI workflow that runs backend tests and frontend build checks in parallel, then builds and pushes Docker images to ghcr.io on merges to `main`.

**Architecture:** A single `.github/workflows/ci.yml` with four jobs: `backend-test` and `frontend-build` run in parallel; `backend-docker` and `frontend-docker` run after their respective build jobs succeed. Docker images are only pushed on non-PR events (push to main, manual dispatch).

**Tech Stack:** GitHub Actions, `actions/setup-java@v4` (JDK 21 Temurin), `actions/setup-node@v4` (Node 20), `docker/login-action@v3`, `docker/metadata-action@v5`, `docker/build-push-action@v6`, GitHub Container Registry (ghcr.io).

---

### Task 1: Create workflow file with `backend-test` job

**Files:**
- Create: `.github/workflows/ci.yml`

- [ ] **Step 1: Create the workflows directory and file**

```bash
mkdir -p .github/workflows
```

Create `.github/workflows/ci.yml` with the following content — this adds the workflow skeleton and the `backend-test` job only:

```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]
  workflow_dispatch:

permissions:
  contents: read
  packages: write

jobs:
  backend-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: 'gradle'

      - name: Make Gradle wrapper executable
        run: chmod +x backend/gradlew

      - name: Run backend tests
        working-directory: backend
        run: ./gradlew test
```

- [ ] **Step 2: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: add workflow skeleton with backend-test job"
```

---

### Task 2: Add `frontend-build` job

**Files:**
- Modify: `.github/workflows/ci.yml`

- [ ] **Step 1: Append `frontend-build` job after `backend-test` in `ci.yml`**

Add the following under `jobs:` (parallel to `backend-test` — same indentation level):

```yaml
  frontend-build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'npm'
          cache-dependency-path: frontend/package-lock.json

      - name: Install dependencies
        working-directory: frontend
        run: npm ci

      - name: Build frontend
        working-directory: frontend
        run: npm run build
```

`npm run build` runs `tsc && vite build`. TypeScript errors fail the job.

- [ ] **Step 2: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: add frontend-build job"
```

---

### Task 3: Add `backend-docker` job

**Files:**
- Modify: `.github/workflows/ci.yml`

- [ ] **Step 1: Append `backend-docker` job after `frontend-build` in `ci.yml`**

Add under `jobs:`:

```yaml
  backend-docker:
    needs: backend-test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: 'gradle'

      - name: Make Gradle wrapper executable
        run: chmod +x backend/gradlew

      - name: Build JAR
        working-directory: backend
        run: ./gradlew bootJar

      - name: Log in to GitHub Container Registry
        if: github.event_name != 'pull_request'
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Extract Docker metadata
        id: meta
        uses: docker/metadata-action@v5
        with:
          images: ghcr.io/${{ github.repository_owner }}/taskowolf-backend
          tags: |
            type=raw,value=latest,enable=${{ github.ref == 'refs/heads/main' }}
            type=sha,prefix=sha-,format=short

      - name: Build and push backend image
        uses: docker/build-push-action@v6
        with:
          context: ./backend
          push: ${{ github.event_name != 'pull_request' }}
          tags: ${{ steps.meta.outputs.tags }}
          labels: ${{ steps.meta.outputs.labels }}
```

**Notes:**
- `needs: backend-test` ensures this job only runs after tests pass
- `push: ${{ github.event_name != 'pull_request' }}` — builds the image on PRs (validates Dockerfile) but only pushes on `push` to main and `workflow_dispatch`
- `bootJar` produces `backend/build/libs/*.jar` which the `backend/Dockerfile` expects via `COPY build/libs/*.jar app.jar`

- [ ] **Step 2: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: add backend-docker job"
```

---

### Task 4: Add `frontend-docker` job

**Files:**
- Modify: `.github/workflows/ci.yml`

- [ ] **Step 1: Append `frontend-docker` job after `backend-docker` in `ci.yml`**

Add under `jobs:`:

```yaml
  frontend-docker:
    needs: frontend-build
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Log in to GitHub Container Registry
        if: github.event_name != 'pull_request'
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Extract Docker metadata
        id: meta
        uses: docker/metadata-action@v5
        with:
          images: ghcr.io/${{ github.repository_owner }}/taskowolf-frontend
          tags: |
            type=raw,value=latest,enable=${{ github.ref == 'refs/heads/main' }}
            type=sha,prefix=sha-,format=short

      - name: Build and push frontend image
        uses: docker/build-push-action@v6
        with:
          context: ./frontend
          push: ${{ github.event_name != 'pull_request' }}
          tags: ${{ steps.meta.outputs.tags }}
          labels: ${{ steps.meta.outputs.labels }}
```

**Notes:**
- No `npm ci`/`npm run build` step — `frontend/Dockerfile` is a multi-stage build that runs the Node build internally (stage 1: Node 20 + `npm ci` + `npm run build`, stage 2: nginx)
- `needs: frontend-build` ensures the Dockerfile build only runs after the TypeScript check passed

- [ ] **Step 2: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: add frontend-docker job"
```

---

### Task 5: Push and verify

**Files:** none

- [ ] **Step 1: Push to GitHub**

```bash
git push origin main
```

- [ ] **Step 2: Watch the workflow run**

```bash
gh run watch
```

Expected: all four jobs appear (`backend-test`, `frontend-build`, `backend-docker`, `frontend-docker`). `backend-docker` and `frontend-docker` start only after their respective build jobs complete.

- [ ] **Step 3: Verify Docker images in registry**

After a successful run on `main`, check the published images:

```bash
gh api /user/packages?package_type=container --jq '.[].name'
```

Expected output includes `taskowolf-backend` and `taskowolf-frontend`.

- [ ] **Step 4: Test manual trigger**

```bash
gh workflow run ci.yml
gh run watch
```

Expected: a new run starts and all four jobs complete successfully.

- [ ] **Step 5: Verify CLI commands from spec**

```bash
# List recent runs
gh run list --workflow=ci.yml
```

Expected: shows the two runs (push + manual dispatch) with status `completed`.
