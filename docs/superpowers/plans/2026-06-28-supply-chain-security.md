# Software-Supply-Chain-Härtung Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eine umfassende DevSecOps-Pipeline aufbauen, die CVEs automatisch erkennt/blockiert und Library-Angriffe vom Typ Shai-Hulud-Wurm abwehrt.

**Architecture:** Neue CI-Gates (Dependency-Review, npm audit, CodeQL, gitleaks, Trivy) blockieren PRs bei High/Critical; alle Jobs laufen unter `harden-runner` (Egress-Audit); GitHub Actions werden SHA-gepinnt; Tag-Releases erhalten SBOM + SLSA-Provenance + cosign-Signatur; ein nächtlicher Job fängt neue CVEs in unveränderten Deps; Dependabot hält alles aktuell (mit Cooldown); Prozess wird in `SECURITY.md`, einem Policy-Doc und `ai-guide.md` verankert.

**Tech Stack:** Gradle (Kotlin DSL), Spring Boot 3.3, npm/Vite/React, GitHub Actions, Docker (ghcr.io + Docker Hub), Trivy, CodeQL, cosign, OWASP Dependency-Check.

## Global Constraints

- **Gating-Regel (jede Task):** High/Critical (CVSS ≥ 7.0) ⇒ Job schlägt fehl. Medium/Low ⇒ Annotation/Alert, kein Blockieren.
- **Update-Bot:** Dependabot (GitHub-nativ). **Kein** Renovate / keine Drittanbieter-GitHub-App.
- **GitHub Actions:** Immer auf vollen 40-stelligen Commit-SHA pinnen, mit `# vX.Y.Z`-Kommentar. Niemals neue Actions per Tag-Ref hinzufügen.
- **Frontend-Skripte:** `.npmrc` setzt `ignore-scripts=true`; build-kritische Skripte werden explizit & dokumentiert ausgeführt.
- **Signierung:** cosign **keyless** via GitHub-OIDC. Keine neuen Secrets, keine privaten Keys im Repo.
- **Suppressions:** Ausnahmen nur in `dependency-check-suppressions.xml` / `.trivyignore` mit Pflicht-Begründung (CVE-ID + Grund + Datum).
- **Branch/Worktree:** Implementierung in isoliertem Worktree (separate Session), Ziel-Branch `main`.
- Latest Flyway version ist V25 — dieser Plan fügt **keine** DB-Migration hinzu.

---

### Task 1: Gradle Dependency Locking (reproduzierbare Backend-Builds)

**Files:**
- Modify: `backend/build.gradle.kts`
- Create: `backend/gradle.lockfile` (generiert)

**Interfaces:**
- Produces: committed `gradle.lockfile`, auf das spätere Builds/Tasks sich verlassen (offline-reproduzierbar).

- [ ] **Step 1: Locking aktivieren**

In `backend/build.gradle.kts` direkt nach dem `repositories { ... }`-Block einfügen:

```kotlin
dependencyLocking {
    lockAllConfigurations()
}
```

- [ ] **Step 2: Lockfile generieren**

Run: `cd backend && ./gradlew dependencies --write-locks`
Expected: Datei `backend/gradle.lockfile` wird erstellt und listet aufgelöste Versionen inkl. transitiver Deps.

- [ ] **Step 3: Verifizieren, dass der Build mit Lock grün ist**

Run: `cd backend && ./gradlew test`
Expected: BUILD SUCCESSFUL — die gelockten Versionen lösen sauber auf.

- [ ] **Step 4: Negativ-Test (Lock greift wirklich)**

Eine beliebige Zeile im `gradle.lockfile` temporär auf eine nicht existierende Version ändern, dann:
Run: `cd backend && ./gradlew test`
Expected: FAIL mit Lock-Mismatch-Fehler. Danach Änderung zurücknehmen.

- [ ] **Step 5: Commit**

```bash
git add backend/build.gradle.kts backend/gradle.lockfile
git commit -m "build: enable Gradle dependency locking for reproducible builds"
```

---

### Task 2: Backend Dependency-Submission + Dependency-Review-Gate

**Files:**
- Modify: `.github/workflows/ci.yml` (neuer Job `dependency-submission` + Job `dependency-review`)

**Interfaces:**
- Consumes: GitHub Dependency Graph (durch Submission befüllt).
- Produces: PR-Gate, das transitive Java/Kotlin- und npm-Deps mit bekannten High/Critical-CVEs blockiert.

> **SHA-Hinweis:** Den SHA für jede Action so auflösen:
> `gh api repos/<owner>/<repo>/commits/<tag> --jq .sha`
> z.B. `gh api repos/gradle/actions/commits/v4 --jq .sha`. Den 40-stelligen SHA mit `# v4`-Kommentar pinnen.

- [ ] **Step 1: Submission-Job hinzufügen**

In `.github/workflows/ci.yml` als neuen Job einfügen (Actions-SHAs gemäß SHA-Hinweis auflösen):

```yaml
  dependency-submission:
    runs-on: ubuntu-latest
    permissions:
      contents: write   # Dependency Graph schreiben
    steps:
      - uses: actions/checkout@<sha>            # v6
      - uses: actions/setup-java@<sha>          # v5
        with:
          java-version: '21'
          distribution: 'temurin'
      - name: Submit Gradle dependency graph
        uses: gradle/actions/dependency-submission@<sha>   # v4
        with:
          build-root-directory: backend
```

- [ ] **Step 2: Dependency-Review-Gate hinzufügen**

Im selben File als weiteren Job (läuft nur bei PRs):

```yaml
  dependency-review:
    if: github.event_name == 'pull_request'
    runs-on: ubuntu-latest
    permissions:
      contents: read
      pull-requests: write
    steps:
      - uses: actions/checkout@<sha>            # v6
      - name: Dependency Review
        uses: actions/dependency-review-action@<sha>   # v4
        with:
          fail-on-severity: high
          comment-summary-in-pr: on-failure
```

- [ ] **Step 3: YAML-Validität prüfen**

Run: `cd "C:/Users/Admin/IdeaProjects/TaskWolf" && python -c "import yaml,sys; yaml.safe_load(open('.github/workflows/ci.yml')); print('OK')"`
Expected: `OK`

- [ ] **Step 4: Commit & Push-Verifikation**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: add Gradle dependency submission and PR dependency review gate"
```
Nach Push auf den Feature-Branch: im PR prüfen, dass die Jobs `dependency-submission` und `dependency-review` laufen und grün sind (keine bestehenden High/Critical-CVEs). Falls ein echter High-Fund auftritt → in Task-Notiz festhalten und über Dependabot-PR oder Versions-Bump fixen.

---

### Task 3: OWASP Dependency-Check (nächtlicher Backend-Scan)

**Files:**
- Modify: `backend/build.gradle.kts` (Plugin + Config)
- Create: `backend/dependency-check-suppressions.xml`
- Create: `.github/workflows/nightly-security.yml`

**Interfaces:**
- Produces: nächtlicher Scan, der bei CVSS ≥ 7.0 fehlschlägt und ein Issue öffnet; Suppression-Datei für begründete Ausnahmen.

- [ ] **Step 1: Plugin hinzufügen**

In `backend/build.gradle.kts` im `plugins`-Block ergänzen:

```kotlin
    id("org.owasp.dependencycheck") version "11.1.0"
```

Nach dem `dependencyManagement`-Block einfügen:

```kotlin
dependencyCheck {
    failBuildOnCVSS = 7.0f   // High/Critical blockieren
    suppressionFile = "dependency-check-suppressions.xml"
    nvd.apiKey = System.getenv("NVD_API_KEY") ?: ""
    formats = listOf("HTML", "SARIF")
    analyzers.assemblyEnabled = false
    analyzers.nodeEnabled = false
}
```

- [ ] **Step 2: Leere Suppression-Datei anlegen**

Create `backend/dependency-check-suppressions.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<suppressions xmlns="https://jeremylong.github.io/DependencyCheck/dependency-suppression.1.3.xsd">
    <!-- Begründete Ausnahmen hier eintragen. Pflicht-Format pro Eintrag:
         <suppress until="YYYY-MM-DD">
           <notes>CVE-XXXX-YYYY: <Grund, warum nicht ausnutzbar / wann gefixt></notes>
           <cve>CVE-XXXX-YYYY</cve>
         </suppress> -->
</suppressions>
```

- [ ] **Step 3: Lokal verifizieren (Plugin lädt & läuft)**

Run: `cd backend && ./gradlew dependencyCheckAnalyze`
Expected: BUILD SUCCESSFUL (oder kontrolliertes Fehlschlagen bei echtem High-Fund → dann Versions-Bump oder begründete Suppression). NVD-Download kann beim ersten Lauf mehrere Minuten dauern.

- [ ] **Step 4: Nightly-Workflow anlegen**

Create `.github/workflows/nightly-security.yml` (SHAs gemäß SHA-Hinweis aus Task 2):

```yaml
name: Nightly Security Scan

on:
  schedule:
    - cron: '0 3 * * *'   # täglich 03:00 UTC
  workflow_dispatch:

permissions:
  contents: read
  security-events: write
  issues: write

jobs:
  owasp-dependency-check:
    runs-on: ubuntu-latest
    steps:
      - uses: step-security/harden-runner@<sha>   # v2
        with:
          egress-policy: audit
      - uses: actions/checkout@<sha>              # v6
      - uses: actions/setup-java@<sha>            # v5
        with:
          java-version: '21'
          distribution: 'temurin'
      - name: Run OWASP Dependency-Check
        working-directory: backend
        env:
          NVD_API_KEY: ${{ secrets.NVD_API_KEY }}
        run: |
          chmod +x gradlew
          ./gradlew dependencyCheckAnalyze
      - name: Upload SARIF
        if: always()
        uses: github/codeql-action/upload-sarif@<sha>   # v3
        with:
          sarif_file: backend/build/reports/dependency-check-report.sarif
      - name: Open issue on failure
        if: failure()
        uses: actions/github-script@<sha>          # v7
        with:
          script: |
            github.rest.issues.create({
              owner: context.repo.owner,
              repo: context.repo.repo,
              title: `Nightly security scan failed (${new Date().toISOString().slice(0,10)})`,
              body: 'OWASP Dependency-Check meldete High/Critical-CVEs. Siehe Workflow-Run und Security-Tab.',
              labels: ['security']
            })
```

- [ ] **Step 5: Commit**

```bash
git add backend/build.gradle.kts backend/dependency-check-suppressions.xml .github/workflows/nightly-security.yml
git commit -m "ci: add nightly OWASP Dependency-Check with issue-on-failure"
```

> **Manueller Schritt (im Plan vermerkt):** Repo-Secret `NVD_API_KEY` anlegen (kostenloser API-Key von nvd.nist.gov beschleunigt Downloads). Ohne Key läuft der Scan, aber langsamer/rate-limited.

---

### Task 4: Frontend `ignore-scripts` + Build-Verifikation

**Files:**
- Create: `frontend/.npmrc`
- Modify: `frontend/Dockerfile` (falls esbuild-Rebuild nötig)

**Interfaces:**
- Produces: npm-Installs ohne automatische Lifecycle-Skripte (Shai-Hulud-Hauptvektor neutralisiert); dokumentierter Allowlist-Schritt, falls der Build native Skripte braucht.

- [ ] **Step 1: `.npmrc` anlegen**

Create `frontend/.npmrc`:

```
ignore-scripts=true
```

- [ ] **Step 2: Clean-Install testen**

Run: `cd frontend && rm -rf node_modules && npm ci`
Expected: Install ohne Ausführung von postinstall-Skripten.

- [ ] **Step 3: Build verifizieren (esbuild-Risiko)**

Run: `cd frontend && npm run build`
Expected: entweder PASS (dann ist kein Allowlist-Schritt nötig) **oder** FAIL mit fehlendem esbuild-Binary.

- [ ] **Step 4: Falls Build fehlschlägt — esbuild gezielt rebuilden**

Nur wenn Step 3 fehlschlug: in `frontend/Dockerfile` und lokal nach `npm ci` einen expliziten, dokumentierten Schritt ergänzen. In `frontend/Dockerfile`:

```dockerfile
FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json .npmrc ./
RUN npm ci
# ignore-scripts=true verhindert automatische Lifecycle-Skripte (Supply-Chain-Härtung).
# esbuild benötigt seinen Postinstall, um das Plattform-Binary zu installieren — gezielt erlaubt:
RUN npm rebuild esbuild
COPY . .
RUN npm run build
```

Danach erneut `cd frontend && npm run build` ausführen → Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/.npmrc frontend/Dockerfile
git commit -m "build(frontend): disable npm lifecycle scripts; allowlist esbuild rebuild"
```

---

### Task 5: Frontend `npm audit`-Gate in CI

**Files:**
- Modify: `.github/workflows/ci.yml` (Step im `frontend-build`-Job)

**Interfaces:**
- Consumes: `frontend/package-lock.json`.
- Produces: PR-Gate, das bei High/Critical-npm-CVEs blockiert.

- [ ] **Step 1: Audit-Step ergänzen**

Im `frontend-build`-Job in `.github/workflows/ci.yml` nach `npm ci` einfügen:

```yaml
      - name: npm audit (block on high/critical)
        working-directory: frontend
        run: npm audit --audit-level=high
```

- [ ] **Step 2: Lokal verifizieren**

Run: `cd frontend && npm audit --audit-level=high`
Expected: Exit 0 (keine High/Critical) → grün. Bei echtem Fund: Exit ≠ 0 → über Dependabot/Bump fixen, in Task-Notiz festhalten.

- [ ] **Step 3: YAML-Validität prüfen**

Run: `cd "C:/Users/Admin/IdeaProjects/TaskWolf" && python -c "import yaml; yaml.safe_load(open('.github/workflows/ci.yml')); print('OK')"`
Expected: `OK`

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "ci(frontend): gate build on npm audit high/critical"
```

---

### Task 6: GitHub Actions auf Commit-SHA pinnen

**Files:**
- Modify: `.github/workflows/ci.yml`
- Modify: `.github/workflows/docker-publish.yml`
- Modify: `.github/workflows/docs.yml`
- Modify: `.github/workflows/nightly-security.yml`

**Interfaces:**
- Produces: alle Action-Referenzen als 40-stellige SHAs mit Versions-Kommentar (Tampering-Schutz).

- [ ] **Step 1: Alle Tags inventarisieren**

Run: `cd "C:/Users/Admin/IdeaProjects/TaskWolf" && grep -rhoE "uses: [^@]+@v[0-9.]+" .github/workflows | sort -u`
Expected: Liste aller `uses:`-Refs (z.B. `actions/checkout@v6`, `docker/build-push-action@v6`, ...).

- [ ] **Step 2: SHA je Action auflösen**

Für jede Zeile aus Step 1 den SHA holen, z.B.:
Run: `gh api repos/actions/checkout/commits/v6 --jq .sha`
Expected: 40-stelliger Hash. Tabelle Action→SHA notieren.

- [ ] **Step 3: Refs ersetzen**

In allen vier Workflow-Files jedes `uses: owner/action@vX` ersetzen durch:

```yaml
      - uses: owner/action@<40-char-sha>   # vX
```

(Versions-Kommentar zwingend, damit Dependabot und Menschen die Version lesen können.)

- [ ] **Step 4: Validität prüfen**

Run: `cd "C:/Users/Admin/IdeaProjects/TaskWolf" && for f in .github/workflows/*.yml; do python -c "import yaml; yaml.safe_load(open('$f'))" && echo "$f OK"; done`
Expected: jede Datei `OK`. Zusätzlich `grep -rE "uses: [^@]+@v[0-9]" .github/workflows` → Expected: **keine** Treffer mehr (alle gepinnt).

- [ ] **Step 5: Commit**

```bash
git add .github/workflows/
git commit -m "ci: pin all GitHub Actions to commit SHAs"
```

---

### Task 7: harden-runner + minimale Permissions in allen Jobs

**Files:**
- Modify: `.github/workflows/ci.yml`
- Modify: `.github/workflows/docker-publish.yml`
- Modify: `.github/workflows/docs.yml`

**Interfaces:**
- Produces: Egress-Audit pro Job (erkennt Exfiltration während Build); job-spezifische minimale `GITHUB_TOKEN`-Permissions.

- [ ] **Step 1: harden-runner als ersten Step jedes Jobs einfügen**

In jeden Job in `ci.yml`, `docker-publish.yml`, `docs.yml` als allerersten Step (SHA aus Task 6):

```yaml
      - uses: step-security/harden-runner@<sha>   # v2
        with:
          egress-policy: audit
```

- [ ] **Step 2: Permissions verengen**

Top-Level in `ci.yml` `permissions:` auf `contents: read` setzen; `packages: write` nur in den Docker-Push-Jobs job-lokal deklarieren. In `docs.yml` bleibt `contents: write` (gh-deploy braucht es). Beispiel job-lokal:

```yaml
  backend-docker:
    needs: backend-test
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
```

- [ ] **Step 3: Validität prüfen**

Run: `cd "C:/Users/Admin/IdeaProjects/TaskWolf" && for f in .github/workflows/*.yml; do python -c "import yaml; yaml.safe_load(open('$f'))" && echo "$f OK"; done`
Expected: jede Datei `OK`.

- [ ] **Step 4: Commit & Push-Verifikation**

```bash
git add .github/workflows/
git commit -m "ci: add harden-runner egress audit and tighten job permissions"
```
Nach Push: im Job-Summary den harden-runner-Egress-Report prüfen (Liste der Outbound-Verbindungen sichtbar).

---

### Task 8: Trivy Image-Scan in CI

**Files:**
- Modify: `.github/workflows/ci.yml` (Scan-Steps in `backend-docker` und `frontend-docker`)
- Create: `.trivyignore`

**Interfaces:**
- Consumes: die gebauten Docker-Images.
- Produces: PR-Gate, das bei High/Critical-Image-CVEs blockiert; SARIF im Security-Tab.

- [ ] **Step 1: Leere `.trivyignore` anlegen**

Create `.trivyignore`:

```
# Begründete Ausnahmen. Format pro Zeile: CVE-ID  # Grund + Datum
# z.B.: CVE-2024-12345  # nicht ausnutzbar (Komponente ungenutzt), 2026-06-28
```

- [ ] **Step 2: Images im PR bauen (ohne Push) und scannen**

In `ci.yml` die Build-Steps der Docker-Jobs so anpassen, dass im PR ein lokaler Build mit `load: true` entsteht, gefolgt vom Scan. Beispiel für `backend-docker` (SHAs aus Task 6):

```yaml
      - name: Build image for scan
        uses: docker/build-push-action@<sha>   # v6
        with:
          context: ./backend
          load: true
          tags: taskowolf-backend:scan
      - name: Trivy scan
        uses: aquasecurity/trivy-action@<sha>   # 0.28.x
        with:
          image-ref: taskowolf-backend:scan
          severity: HIGH,CRITICAL
          exit-code: '1'
          trivyignores: .trivyignore
          format: sarif
          output: trivy-backend.sarif
      - name: Upload Trivy SARIF
        if: always()
        uses: github/codeql-action/upload-sarif@<sha>   # v3
        with:
          sarif_file: trivy-backend.sarif
```

Analog für `frontend-docker` mit `context: ./frontend`, Tag `taskowolf-frontend:scan`, Output `trivy-frontend.sarif`. Der bestehende Push-Step bleibt für non-PR-Events erhalten.

- [ ] **Step 3: Lokal verifizieren (falls Trivy installiert)**

Run: `cd "C:/Users/Admin/IdeaProjects/TaskWolf" && python -c "import yaml; yaml.safe_load(open('.github/workflows/ci.yml')); print('OK')"`
Expected: `OK`. (Echter Scan-Lauf erfolgt in CI.)

- [ ] **Step 4: Commit & Push-Verifikation**

```bash
git add .github/workflows/ci.yml .trivyignore
git commit -m "ci: scan backend and frontend images with Trivy (block high/critical)"
```
Nach Push: prüfen, dass beide Trivy-Steps laufen; bei echtem High/Critical → Base-Image-Bump (Task 9) oder begründeter `.trivyignore`-Eintrag.

---

### Task 9: Base-Images per Digest pinnen

**Files:**
- Modify: `backend/Dockerfile`
- Modify: `frontend/Dockerfile`

**Interfaces:**
- Produces: unveränderliche Base-Image-Referenzen; Dependabot (`docker`) aktualisiert die Digests.

- [ ] **Step 1: Digests auflösen**

Run: `docker buildx imagetools inspect eclipse-temurin:21-jre-alpine --format '{{.Manifest.Digest}}'`
Run: `docker buildx imagetools inspect node:20-alpine --format '{{.Manifest.Digest}}'`
Run: `docker buildx imagetools inspect nginx:alpine --format '{{.Manifest.Digest}}'`
Expected: je ein `sha256:...`-Digest.

- [ ] **Step 2: Dockerfiles aktualisieren**

`backend/Dockerfile` Zeile 1:

```dockerfile
FROM eclipse-temurin:21-jre-alpine@sha256:<digest>
```

`frontend/Dockerfile` Zeilen 1 und 8 analog für `node:20-alpine@sha256:<digest>` und `nginx:alpine@sha256:<digest>` (den `# tag`-Bezug als Kommentar dahinter, damit Dependabot updaten kann).

- [ ] **Step 3: Builds verifizieren**

Run: `cd backend && docker build -t tw-backend-test . && cd ../frontend && docker build -t tw-frontend-test .`
Expected: beide Builds erfolgreich mit den gepinnten Digests.

- [ ] **Step 4: Commit**

```bash
git add backend/Dockerfile frontend/Dockerfile
git commit -m "build: pin Docker base images by digest"
```

---

### Task 10: SBOM + SLSA-Provenance + cosign im Publish-Workflow

**Files:**
- Modify: `.github/workflows/docker-publish.yml`

**Interfaces:**
- Consumes: gepushte Images (ghcr.io/Docker Hub).
- Produces: SBOM-Attestation, SLSA-Provenance und keyless cosign-Signatur pro Release-Image.

- [ ] **Step 1: SBOM & Provenance im build-push aktivieren**

Im `docker/build-push-action`-Step in `docker-publish.yml` ergänzen:

```yaml
        with:
          context: ${{ matrix.context }}
          platforms: linux/amd64,linux/arm64
          push: true
          sbom: true
          provenance: mode=max
          tags: ${{ steps.meta.outputs.tags }}
          labels: ${{ steps.meta.outputs.labels }}
```

- [ ] **Step 2: OIDC-Permission + Image-Digest-Output**

Top-Level `permissions:` in `docker-publish.yml` erweitern:

```yaml
permissions:
  contents: read
  id-token: write   # cosign keyless via OIDC
  packages: write
```

Dem build-push-Step `id: build` geben, damit `${{ steps.build.outputs.digest }}` verfügbar ist.

- [ ] **Step 3: cosign installieren & signieren**

Nach dem Push-Step einfügen (SHA für cosign-installer auflösen):

```yaml
      - uses: sigstore/cosign-installer@<sha>   # v3
      - name: Sign image (keyless)
        env:
          DIGEST: ${{ steps.build.outputs.digest }}
          IMAGE: ${{ matrix.image }}
        run: cosign sign --yes "${IMAGE}@${DIGEST}"
```

- [ ] **Step 4: Validität prüfen**

Run: `cd "C:/Users/Admin/IdeaProjects/TaskWolf" && python -c "import yaml; yaml.safe_load(open('.github/workflows/docker-publish.yml')); print('OK')"`
Expected: `OK`.

- [ ] **Step 5: Commit & Release-Verifikation**

```bash
git add .github/workflows/docker-publish.yml
git commit -m "ci: attach SBOM + SLSA provenance and cosign-sign release images"
```
Verifikation nach einem Tag-Release: `cosign verify <image>@<digest> --certificate-identity-regexp '.*' --certificate-oidc-issuer https://token.actions.githubusercontent.com` und `docker buildx imagetools inspect <image>` (SBOM/Provenance sichtbar).

---

### Task 11: CodeQL (SAST) für Backend & Frontend

**Files:**
- Create: `.github/workflows/codeql.yml`

**Interfaces:**
- Produces: SAST-Gate für `java-kotlin` und `javascript-typescript`; Findings im Security-Tab.

- [ ] **Step 1: CodeQL-Workflow anlegen**

Create `.github/workflows/codeql.yml` (SHAs auflösen):

```yaml
name: CodeQL

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]
  schedule:
    - cron: '30 3 * * 1'   # wöchentlich Mo 03:30 UTC

jobs:
  analyze:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      security-events: write
    strategy:
      fail-fast: false
      matrix:
        include:
          - language: java-kotlin
            build-mode: autobuild
          - language: javascript-typescript
            build-mode: none
    steps:
      - uses: step-security/harden-runner@<sha>   # v2
        with:
          egress-policy: audit
      - uses: actions/checkout@<sha>              # v6
      - uses: actions/setup-java@<sha>            # v5
        if: matrix.language == 'java-kotlin'
        with:
          java-version: '21'
          distribution: 'temurin'
      - uses: github/codeql-action/init@<sha>     # v3
        with:
          languages: ${{ matrix.language }}
          build-mode: ${{ matrix.build-mode }}
      - uses: github/codeql-action/autobuild@<sha>   # v3
        if: matrix.build-mode == 'autobuild'
        with:
          working-directory: backend
      - uses: github/codeql-action/analyze@<sha>  # v3
        with:
          category: "/language:${{ matrix.language }}"
```

- [ ] **Step 2: Validität prüfen**

Run: `cd "C:/Users/Admin/IdeaProjects/TaskWolf" && python -c "import yaml; yaml.safe_load(open('.github/workflows/codeql.yml')); print('OK')"`
Expected: `OK`.

- [ ] **Step 3: Commit & Push-Verifikation**

```bash
git add .github/workflows/codeql.yml
git commit -m "ci: add CodeQL SAST for java-kotlin and javascript-typescript"
```
Nach Push: beide Matrix-Jobs müssen durchlaufen; Findings erscheinen im Security-Tab → High/Critical triagieren.

---

### Task 12: gitleaks Secret-Scan auf PR-Diffs

**Files:**
- Modify: `.github/workflows/ci.yml` (neuer Job `gitleaks`)

**Interfaces:**
- Produces: PR-Gate, das versehentlich committete Secrets erkennt.

- [ ] **Step 1: gitleaks-Job hinzufügen**

In `ci.yml` (SHA auflösen):

```yaml
  gitleaks:
    runs-on: ubuntu-latest
    permissions:
      contents: read
    steps:
      - uses: step-security/harden-runner@<sha>   # v2
        with:
          egress-policy: audit
      - uses: actions/checkout@<sha>              # v6
        with:
          fetch-depth: 0
      - uses: gitleaks/gitleaks-action@<sha>      # v2
```

- [ ] **Step 2: Validität prüfen**

Run: `cd "C:/Users/Admin/IdeaProjects/TaskWolf" && python -c "import yaml; yaml.safe_load(open('.github/workflows/ci.yml')); print('OK')"`
Expected: `OK`.

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: add gitleaks secret scan"
```

---

### Task 13: Dependabot-Konfiguration (4 Ökosysteme, Cooldown, Gruppierung)

**Files:**
- Create: `.github/dependabot.yml`

**Interfaces:**
- Produces: kontinuierliche Update-PRs für gradle, npm, github-actions, docker — gruppiert, mit Cooldown (kein sofortiges Ziehen frisch-publizierter Versionen).

- [ ] **Step 1: Config anlegen**

Create `.github/dependabot.yml`:

```yaml
version: 2
updates:
  - package-ecosystem: gradle
    directory: /backend
    schedule:
      interval: weekly
    cooldown:
      default-days: 5
    groups:
      backend-deps:
        patterns: ["*"]

  - package-ecosystem: npm
    directory: /frontend
    schedule:
      interval: weekly
    cooldown:
      default-days: 5
    groups:
      frontend-deps:
        patterns: ["*"]

  - package-ecosystem: github-actions
    directory: /
    schedule:
      interval: weekly
    groups:
      actions:
        patterns: ["*"]

  - package-ecosystem: docker
    directories: ["/backend", "/frontend"]
    schedule:
      interval: weekly
```

- [ ] **Step 2: Validität prüfen**

Run: `cd "C:/Users/Admin/IdeaProjects/TaskWolf" && python -c "import yaml; yaml.safe_load(open('.github/dependabot.yml')); print('OK')"`
Expected: `OK`.

- [ ] **Step 3: Commit**

```bash
git add .github/dependabot.yml
git commit -m "ci: configure Dependabot for gradle, npm, actions, docker with cooldown"
```

---

### Task 14: SECURITY.md (Vulnerability-Disclosure)

**Files:**
- Create: `SECURITY.md`

**Interfaces:**
- Produces: öffentlicher Reporting-Weg für OSS-Nutzer.

- [ ] **Step 1: Datei anlegen**

Create `SECURITY.md`:

```markdown
# Security Policy

## Supported Versions

Sicherheitsupdates werden für die jeweils neueste Minor-Release-Linie (`1.x`)
bereitgestellt.

| Version | Unterstützt |
|---------|-------------|
| 1.x     | ✅          |
| < 1.0   | ❌          |

## Eine Schwachstelle melden

Bitte **keine** öffentlichen GitHub-Issues für Sicherheitslücken verwenden.

Nutze stattdessen **Private Vulnerability Reporting** unter
*Security → Report a vulnerability* in diesem Repository.

- **Erstreaktion:** innerhalb von 5 Werktagen.
- **Statusupdate / Triage:** innerhalb von 10 Werktagen.
- **Fix-Ziel:** High/Critical so schnell wie möglich, üblicherweise als
  Patch-Release; Medium/Low im nächsten regulären Release.

Wir bestätigen den Eingang, halten dich über den Fortschritt auf dem Laufenden
und nennen dich auf Wunsch in den Release Notes.
```

- [ ] **Step 2: Commit**

```bash
git add SECURITY.md
git commit -m "docs: add security policy with private vulnerability reporting"
```

---

### Task 15: Supply-Chain-Policy + Shai-Hulud-Playbook (intern)

**Files:**
- Create: `docs/superpowers/supply-chain-policy.md`

**Interfaces:**
- Produces: internes Incident-Response-Dokument inkl. Shai-Hulud-IOCs und Reaktionsschritten.

- [ ] **Step 1: Policy-Dokument anlegen**

Create `docs/superpowers/supply-chain-policy.md`:

```markdown
# Supply-Chain Security Policy (intern)

## CVE-Reaktionsprozess

1. **Erkennung:** Dependabot-Alert, nächtlicher OWASP-Scan (öffnet Issue),
   Trivy-Image-Finding oder externe Meldung (siehe SECURITY.md).
2. **Triage:** Schweregrad (CVSS) bestimmen, Ausnutzbarkeit im TaskWolf-Kontext
   prüfen, betroffene Releases identifizieren.
3. **Fix:** Versions-Bump (bevorzugt via Dependabot-PR) oder Workaround.
   High/Critical blockieren ohnehin die CI.
4. **Ausnahme:** Wenn nicht ausnutzbar → Eintrag in
   `dependency-check-suppressions.xml` bzw. `.trivyignore` **mit Begründung +
   Datum**. Suppressions quartalsweise reviewen.
5. **Release:** Patch-Release + CHANGELOG-Eintrag.

## Update-Kadenz & Cooldown

- Dependabot läuft wöchentlich mit 5 Tagen Cooldown → frisch publizierte
  (potenziell kompromittierte) Versionen werden nicht sofort gezogen.
- Niemals eine < 5 Tage alte Version manuell pinnen (siehe ai-guide.md).

## Shai-Hulud-Wurm — Indicators of Compromise (IOCs)

Hellhörig werden bei:
- `postinstall`/Lifecycle-Skripten, die `curl`/`wget` zu `webhook.site`,
  unbekannten Domains oder Paste-Diensten aufrufen.
- Nutzung von `trufflehog` / Secret-Scanning-Tools innerhalb von Paket-Skripten.
- Unerwarteten neuen GitHub-Workflow-Dateien in eigenen Repos (`.github/workflows/*`).
- Plötzlichen neuen Maintainer-Releases kurz nach Veröffentlichung; ungewöhnliche
  Outbound-Verbindungen im harden-runner-Egress-Report eines CI-Laufs.
- Verdächtigen Dateien wie `bundle.js`/`processor.js` in `node_modules` von
  Paketen, die so etwas nicht brauchen.

## Reaktion bei Verdacht auf Kompromittierung

1. CI sofort stoppen; betroffenen Branch/PR nicht mergen.
2. Alle CI-/Registry-/npm-/GitHub-Tokens **rotieren** (Annahme: exfiltriert).
3. Betroffene Paketversion in Lockfiles auf bekannte gute Version pinnen.
4. `npm ci` mit `ignore-scripts=true` verhindert die Ausführung — Status der
   `.npmrc` bestätigen.
5. Vorfall an npm/GitHub melden; Findings im Security-Tab dokumentieren.

## npm-Token-Hygiene

TaskWolf publiziert **nicht** zu npm. Falls sich das ändert: granulare
Automation-Tokens mit 2FA, minimalen Scopes, niemals im Repo/CI als Plaintext.
```

- [ ] **Step 2: Commit**

```bash
git add docs/superpowers/supply-chain-policy.md
git commit -m "docs: add internal supply-chain policy and Shai-Hulud playbook"
```

---

### Task 16: ai-guide.md — Pflicht-Sektion „Neue Dependency hinzufügen?"

**Files:**
- Modify: `mkdocs/developer-guide/ai-guide.md`

**Interfaces:**
- Consumes: bestehende Struktur (Pre-Implementation Checklist, Pattern Catalogue).
- Produces: verbindliche Vetting-Checkliste vor jedem neuen Package.

- [ ] **Step 1: Checklisten-Punkt verlinken**

In `mkdocs/developer-guide/ai-guide.md` in der „Pre-Implementation Checklist" (nach Punkt 5) ergänzen:

```markdown
6. **Bevor du eine neue Dependency hinzufügst**, arbeite die Sektion
   [Adding a New Dependency](#adding-a-new-dependency) vollständig ab.
```

(bestehenden Punkt 6 „Do not infer patterns…" zu Punkt 7 umnummerieren.)

- [ ] **Step 2: Neue Sektion einfügen**

Vor „## Pattern Catalogue" einfügen:

```markdown
## Adding a New Dependency

Eine neue Library ist eine Erweiterung der Angriffsfläche. Vor dem Hinzufügen
**jede** Frage beantworten:

- **Brauchen wir sie wirklich?** Lässt sich das mit Bordmitteln / vorhandenen
  Deps lösen? (YAGNI)
- **Reife:** Paketalter, letzte Aktivität, Maintainer-Anzahl, wöchentliche
  Downloads. Keine verwaisten oder Ein-Personen-Mikro-Pakete für Kernfunktionen.
- **Bekannte CVEs:** `npm audit` (Frontend) bzw. OWASP-Report (Backend) prüfen.
- **Install-Skripte:** Führt das Paket Lifecycle-Skripte aus? Werden sie
  gebraucht? `.npmrc` setzt `ignore-scripts=true` — bei Bedarf gezielt
  allowlisten und dokumentieren.
- **Cooldown:** Keine < 5 Tage alte Version pinnen (Schutz vor frisch
  kompromittierten Releases — siehe Supply-Chain-Policy).
- **Lizenz:** kompatibel mit dem Projekt?

Nach dem Hinzufügen: Lockfile committen (`gradle.lockfile` bzw.
`package-lock.json`) und sicherstellen, dass die CI-Security-Gates grün sind.
```

- [ ] **Step 3: MkDocs-Build verifizieren**

Run: `cd "C:/Users/Admin/IdeaProjects/TaskWolf" && mkdocs build --strict`
Expected: Build erfolgreich, keine broken-link-Warnungen für den neuen Anker.

- [ ] **Step 4: Commit**

```bash
git add mkdocs/developer-guide/ai-guide.md
git commit -m "docs(ai-guide): add mandatory dependency-vetting checklist"
```

---

### Task 17: Wiki-Doku — Security/Supply-Chain-Seite

**Files:**
- Create: `mkdocs/developer-guide/supply-chain-security.md`
- Modify: `mkdocs.yml` (Nav-Eintrag)

**Interfaces:**
- Produces: Nutzer-/Contributor-Doku der Pipeline (Gates, Scans, wie man auf Findings reagiert).

- [ ] **Step 1: Doku-Seite anlegen**

Create `mkdocs/developer-guide/supply-chain-security.md` mit Überblick über: die CI-Security-Gates (Dependency-Review, npm audit, CodeQL, gitleaks, Trivy), das Gating (High/Critical blockiert), den nächtlichen Scan, Dependabot + Cooldown, SBOM/Provenance/cosign-Verifikation, Actions-SHA-Pinning, harden-runner, sowie Querverweise auf `SECURITY.md` und `docs/superpowers/supply-chain-policy.md`. Verlinke die Dependency-Checkliste in `ai-guide.md`.

- [ ] **Step 2: Nav-Eintrag ergänzen**

In `mkdocs.yml` unter dem Developer-Guide-Nav-Block einen Eintrag hinzufügen:

```yaml
      - Supply-Chain Security: developer-guide/supply-chain-security.md
```

- [ ] **Step 3: Build verifizieren**

Run: `cd "C:/Users/Admin/IdeaProjects/TaskWolf" && mkdocs build --strict`
Expected: Build erfolgreich, neue Seite in der Nav, keine broken links.

- [ ] **Step 4: Commit**

```bash
git add mkdocs/developer-guide/supply-chain-security.md mkdocs.yml
git commit -m "docs(wiki): document supply-chain security pipeline"
```

---

## Manuelle Schritte (Repo-Settings — nicht per Code, im PR-Abschluss dokumentieren)

- Settings → Security: **Dependabot Alerts**, **Dependabot security updates**, **Secret Scanning + Push Protection**, **Code Scanning (CodeQL)** aktivieren.
- Repo-Secret **`NVD_API_KEY`** anlegen (für Task 3, optional aber empfohlen).
- Branch-Protection auf `main`: die neuen Status-Checks (`dependency-review`, `npm audit`, `codeql`, `gitleaks`, Trivy-Jobs) als **required** markieren.

## Self-Review-Notizen

- **Spec-Coverage:** A→Tasks 1–3, B→Tasks 4–5 + Dependabot(13), C→Tasks 6–7, D→Tasks 8–10, E→Tasks 11–12 + manuelles Secret-Scanning-Enable, F→Task 15, G→Tasks 13–17. Alle sieben Stränge abgedeckt.
- **Gating-Konsistenz:** `fail-on-severity: high` / `--audit-level=high` / Trivy `severity: HIGH,CRITICAL exit-code:1` / `failBuildOnCVSS=7.0` — überall High/Critical-Schwelle, konsistent mit Global Constraints.
- **SHA-Pinning:** Tasks 2–3 fügen Actions zunächst mit `# vX`-Platzhalter-SHA hinzu; Task 6 löst alle auf und entfernt verbleibende Tag-Refs (Reihenfolge bewusst: erst Funktion, dann globales Pinning).
