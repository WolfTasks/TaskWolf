# Software-Supply-Chain-Härtung — Design Spec

**Datum:** 2026-06-28
**Status:** Approved (Design)
**Scope:** Umfassende DevSecOps-Pipeline für TaskWolf (Backend: Gradle/Kotlin, Frontend: npm/React, CI: GitHub Actions, Images: ghcr.io + Docker Hub)

## 1. Motivation & Ziele

TaskWolf ist ein Open-Source, self-hosted Projektmanagement-Tool. Als OSS-Projekt mit
veröffentlichten Docker-Images ist es zwei Klassen von Lieferketten-Risiken ausgesetzt:

1. **Bekannte Schwachstellen (CVEs)** in direkten und transitiven Abhängigkeiten —
   laufend und auch in zukünftiger Entwicklung zu berücksichtigen.
2. **Aktive Angriffe auf Libraries** vom Typ **Shai-Hulud-Wurm** (npm-Supply-Chain-Wurm,
   Sept. 2025): stiehlt npm-/GitHub-Tokens, injiziert bösartige `postinstall`-Skripte,
   exfiltriert Secrets (u.a. via `webhook.site` und `trufflehog`), republiziert
   infizierte Versionen von Paketen, die der kompromittierte Maintainer besitzt, und
   verbreitet sich so selbst.

**Ziele:**
- CVEs werden bei jedem PR und nächtlich automatisch erkannt; High/Critical blockieren das Mergen.
- Der Haupt-Verbreitungsweg von Wurm-Angriffen (`postinstall`-Skripte, sofortiges Ziehen
  frisch-publizierter Versionen) wird neutralisiert.
- Exfiltrationsversuche während CI-Builds werden erkennbar (Egress-Monitoring).
- Veröffentlichte Artefakte sind verifizierbar (SBOM, SLSA-Provenance, cosign-Signatur).
- CVE- und Lieferketten-Denken ist dauerhaft im Entwicklungs-Workflow verankert
  (Policy-Doc + Pflicht-Checkliste in `ai-guide.md`).

**Nicht-Ziele (YAGNI):** Eigene private Registry/Proxy (Artifactory/Nexus), kuratierte
Dependency-Allowlist, Runtime-Security (Falco o.ä.). TaskWolf publiziert **nicht** zu npm —
npm-Publish-Token-Hygiene wird daher nur dokumentiert, nicht erzwungen.

## 2. Entscheidungen (aus dem Brainstorming)

| Thema | Entscheidung |
|---|---|
| Umfang | Umfassende DevSecOps-Pipeline |
| CI-Gating | **Blockieren ab High/Critical**; Medium/Low = Annotation/Alert |
| Update-Bot | **Dependabot** (GitHub-nativ — keine Drittanbieter-App, minimale Trust-Surface) |
| Future-Proofing | `SECURITY.md` + internes Policy-Doc **und** `ai-guide.md`-Erweiterung |
| Frontend-Skripte | `ignore-scripts=true` mit gezieltem Allowlisting build-kritischer Skripte |
| Artefakt-Integrität | SBOM + SLSA-Provenance + cosign — **drin** (nicht aufgeschoben) |

## 3. Aktueller Stand (Ist-Analyse)

- **Backend** (`backend/build.gradle.kts`): Spring Boot 3.3.0; **kein** Dependency-Locking,
  **kein** SCA-Plugin. CI führt nur `./gradlew test` aus.
- **Frontend** (`frontend/package.json`): `package-lock.json` vorhanden, `npm ci` in Nutzung
  (gut); überall `^`-Ranges; **kein** `npm audit` in CI.
- **CI** (`.github/workflows/ci.yml`, `docker-publish.yml`, `docs.yml`): Actions per **Tag**
  gepinnt (`@v6`, `@v3`), nicht per SHA. Kein SBOM, kein Image-Scan, keine Signierung.
  `dependabot.yml` fehlt.
- **Images**: `backend/Dockerfile` → `eclipse-temurin:21-jre-alpine` (non-root ✓, kein Digest-Pin).
  `frontend/Dockerfile` → `node:20-alpine` + `nginx:alpine` (kein Digest-Pin). esbuild/Vite-Build
  benötigt native Postinstall-Schritte → relevant für `ignore-scripts`.

## 4. Architektur der Pipeline (Datenfluss)

```
PR / push:
  [harden-runner egress-audit umschließt alle Jobs]
  backend-test (bestehend) ─┐
  frontend-build (bestehend)│
  + dependency-review ──────┤  Gate: High/Critical → roter Build
  + npm audit (--audit-level=high)
  + codeql (kotlin/java, js/ts)
  + gitleaks (PR-Diff)
  + trivy image scan (backend + frontend)

Tag-Release (v*.*.*):  publish-Workflow zusätzlich:
  + SBOM (buildx --sbom=true)
  + SLSA-Provenance (buildx --provenance=true)
  + cosign sign (keyless / OIDC)

Nightly (schedule):
  + OWASP Dependency-Check (Backend, NVD)
  + Trivy re-scan veröffentlichter Images   → fängt neue CVEs ohne Code-Änderung

Kontinuierlich:
  Dependabot (gradle, npm, github-actions, docker) — gruppiert, mit Cooldown
```

**Gating-Regel (durchgängig):** High/Critical ⇒ Job schlägt fehl, PR nicht mergebar.
Medium/Low ⇒ Annotation/Security-Tab-Alert, kein Blockieren. Begründete Ausnahmen nur über
Suppression-Dateien mit Pflicht-Kommentar (`dependency-check-suppressions.xml`, `.trivyignore`).

## 5. Komponenten (sieben Arbeitsstränge)

### A — Backend SCA (Gradle/Kotlin)

- **Dependency-Submission**: Job mit `gradle/actions/dependency-submission` speist GitHubs
  Dependency-Graph → aktiviert **Dependabot Alerts** + **Dependency Review** für transitive
  Java/Kotlin-Deps.
- **Gradle Dependency Locking**: `dependencyLocking { lockAllConfigurations() }` +
  `gradle.lockfile` committen → reproduzierbare Builds; unerwartete transitive Änderungen
  brechen den Build.
- **OWASP Dependency-Check**: `org.owasp.dependencycheck`-Plugin als **nächtlicher** Job
  (NVD-Download zu langsam für PRs), `failBuildOnCVSS = 7.0` (High), Suppression-XML mit Begründung.

### B — Frontend SCA (npm) + Shai-Hulud-Kern

- **`npm audit --audit-level=high`** als CI-Gate (blockt High/Critical).
- **`.npmrc` mit `ignore-scripts=true`** — neutralisiert den primären Wurm-Verbreitungsweg.
  - *Risiko/Mitigation:* Vite/esbuild benötigen native Postinstall-Schritte. Build muss nach
    Umstellung verifiziert werden. Strategie: globales `ignore-scripts=true`; build-kritische
    Pakete (z.B. esbuild) per `npm rebuild <pkg>` bzw. dediziertem, dokumentiertem Allowlist-Step
    explizit ausführen. Falls esbuild dadurch bricht, wird der erlaubte Schritt im Dockerfile
    und CI dokumentiert (Audit-Trail, welche Skripte bewusst laufen).
- **Update-Cooldown**: Dependabot `cooldown` (≈ 5–7 Tage) → frisch publizierte (potenziell
  kompromittierte) Versionen werden nicht sofort gezogen.
- **Lockfile-Integrität**: `npm ci` bleibt erzwungen (bricht bei Lockfile-Mismatch ab).

### C — CI/CD-Härtung (GitHub Actions)

- **Alle Actions auf vollen Commit-SHA pinnen** (statt `@v6`), mit `# v6.x.x`-Kommentar;
  Dependabot (`github-actions`-Ökosystem) hält die SHAs aktuell. Betrifft `ci.yml`,
  `docker-publish.yml`, `docs.yml`.
- **`step-security/harden-runner`** (SHA-gepinnt) als erster Step jedes Jobs, `egress-policy: audit`
  (später optional `block` mit Allowlist) → erkennt unerwartete ausgehende Verbindungen während
  Install/Build (direkte Erkennung von Worm-Exfiltration).
- **Minimale `permissions:` pro Job**; `GITHUB_TOKEN` nur so breit wie nötig
  (`contents: read` als Default, `packages: write` nur im Push-Job).

### D — Container-Image-Security

- **Trivy-Scan** der gebauten Backend-/Frontend-Images in CI (`aquasecurity/trivy-action`,
  SHA-gepinnt), `severity: HIGH,CRITICAL`, `exit-code: 1` → Gate. SARIF-Upload ins Security-Tab.
- **Base-Images per Digest pinnen** in beiden Dockerfiles
  (`FROM eclipse-temurin:21-jre-alpine@sha256:...`, analog `node:20-alpine`, `nginx:alpine`);
  Dependabot (`docker`-Ökosystem) aktualisiert die Digests.
- **SBOM + Provenance + Signierung** im `docker-publish.yml` (Tag-Release):
  `docker/build-push-action` mit `sbom: true`, `provenance: true`; anschließend
  **cosign** keyless (OIDC) Signatur der gepushten Images (ghcr.io + Docker Hub).

### E — Secret- & Code-Scanning

- **GitHub Secret Scanning + Push Protection** aktivieren (Repo-Setting, manueller Schritt → im Plan dokumentiert).
- **CodeQL** (`github/codeql-action`) für `kotlin`/`java` und `javascript-typescript`, als PR-Gate + nächtlich.
- **gitleaks** (`gitleaks/gitleaks-action`, SHA-gepinnt) auf PR-Diffs.

### F — Shai-Hulud-Playbook (im Policy-Doc, querschnittlich)

- Bekannte **IOCs** dokumentieren: `webhook.site`-Exfil-Endpunkte, verdächtige `trufflehog`-Nutzung
  in `postinstall`, bekannte bösartige Datei-/Hash-Signaturen, ungewöhnliche neue GitHub-Workflows
  in eigenen Repos. + **Reaktionsschritte** (Tokens rotieren, betroffene Versionen sperren, npm/GitHub melden).
- **npm-Token-Hygiene** (dokumentiert, da TaskWolf nicht publiziert → niedriges Risiko): granulare
  Tokens, 2FA, keine Publish-Tokens im Repo/CI.

### G — Prozess & Future-Proofing

- **`SECURITY.md`** (Repo-Root): Vulnerability-Disclosure für OSS-Nutzer — Reporting-Weg
  (private GitHub Security Advisory), Supported Versions, Reaktions-SLA.
- **`docs/superpowers/.../supply-chain-policy.md`**: interner Incident-Response-Plan
  (CVE gemeldet → Triage → Patch → Release), Shai-Hulud-Reaktion (siehe F), Update-Kadenz,
  Suppression-Review-Prozess.
- **`mkdocs/developer-guide/ai-guide.md` erweitern** — neue Pflicht-Sektion „Neue Dependency
  hinzufügen?" mit Vetting-Checkliste (vor jedem neuen Package zu prüfen):
  - Paketalter & letzte Aktivität, Maintainer-Reputation, wöchentliche Downloads
  - Offene/bekannte CVEs (npm audit / OSV)
  - Führt das Paket Install-Skripte aus? Werden sie benötigt?
  - Aktiv gewartet? Lizenz kompatibel?
  - Cooldown beachten — keine < 5 Tage alten Versionen pinnen
- **`.github/dependabot.yml`**: vier Ökosysteme (`gradle`, `npm`, `github-actions`, `docker`),
  Update-Gruppierung, `cooldown`, Ziel-Branch `main`.

## 6. Fehlerbehandlung & Ausnahmen

- **Suppression-Dateien** mit Pflicht-Begründung: `backend/dependency-check-suppressions.xml`
  (CVE-ID + Grund + Datum), `.trivyignore` (CVE-ID + Grund). Review-Prozess im Policy-Doc.
- **False-Positive-Strategie:** Lieber gezielt suppressen als Gate global lockern.
- **Nightly-Scan-Failures** öffnen automatisch ein Issue (nicht nur roter Build), damit neue
  CVEs in unveränderten Deps nicht übersehen werden.

## 7. Teststrategie / Verifikation

Jedes Gate wird gegen einen bekannten Fehlerfall verifiziert:
- Eine bewusst verwundbare Dep (Test-Branch) → Dependency-Review/npm-audit/Trivy schlagen fehl.
- Suppression-Eintrag → Gate wird grün, Begründung sichtbar.
- `ignore-scripts=true` → Frontend-Build (`npm run build`) läuft weiterhin durch (esbuild-Allowlist verifiziert).
- Tag-Release → SBOM-Attachment, Provenance-Attestation und cosign-Signatur sind am Image vorhanden
  (`cosign verify`, `docker buildx imagetools inspect`).
- harden-runner → Egress-Report im Job-Summary sichtbar.

## 8. Manuelle Schritte (außerhalb Code, im Plan zu dokumentieren)

- Repo-Settings: Dependabot Alerts, Secret Scanning + Push Protection, Code Scanning aktivieren.
- ggf. `DOCKERHUB_*`-Secrets (bereits aus Phase 8) — keine neuen Secrets nötig (cosign keyless via OIDC).

## 9. Offene Punkte

- esbuild-Verhalten unter `ignore-scripts=true` muss empirisch in Task B verifiziert werden;
  Fallback-Allowlist-Mechanismus wird dort final festgelegt.
- harden-runner `egress-policy: audit` zunächst; Umstellung auf `block` mit Allowlist als
  optionaler Folgeschritt nach Beobachtung des Audit-Reports.
