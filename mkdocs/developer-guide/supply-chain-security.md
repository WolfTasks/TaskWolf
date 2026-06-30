# Supply-Chain Security

Diese Seite beschreibt die Security-Pipeline, die jede Änderung an TaskWolf
durchläuft. Ziel ist es, kompromittierte Abhängigkeiten, geleakte Secrets und
verwundbare Container-Images so früh wie möglich zu erkennen — und bei
hohem Schweregrad den Merge automatisch zu blockieren.

---

## Übersicht: Schutzebenen

| Ebene | Mechanismus | Wann |
|-------|-------------|------|
| Dependency-Review | GitHub Dependency Review Action | Jeder Pull Request |
| npm audit | `npm audit --audit-level=high` | Jeder Build (frontend) |
| OWASP Dependency-Check | Gradle-Plugin `dependencyCheckAnalyze` | Nächtlich 03:00 UTC |
| CodeQL | Statische Analyse (Java/Kotlin + JS/TS) | Push + PR + wöchentlich |
| Secret-Scanning (gitleaks) | `gitleaks-action` auf vollständiger Git-History | Jeder Build |
| Container-Scan (Trivy) | Trivy Image-Scan auf Backend- und Frontend-Image | Jeder Build |
| SBOM + Provenance | Docker BuildKit SBOM, `provenance: mode=max` | Jedes Release-Tag |
| Cosign-Signatur | Keyless-Signatur via Sigstore/OIDC | Jedes Release-Tag |
| Dependabot | Automatische Update-PRs (4 Ökosysteme) | Wöchentlich |
| Actions-SHA-Pinning | Alle Workflow-Schritte auf Commit-SHA gepinnt | Permanent |
| harden-runner | Egress-Audit auf jedem Runner | Jeder Job |

---

## Gating-Regel

Die Pipeline unterscheidet zwei Reaktionen:

- **High / Critical (CVSS ≥ 7,0) → CI bricht ab**, der PR kann nicht gemergt werden.
- **Medium / Low → Warnung** (Alert, SARIF-Upload), kein harter Block.

Diese Schwelle gilt konsistent in allen vier Scan-Werkzeugen:

| Werkzeug | Konfiguration |
|----------|---------------|
| Dependency Review | `fail-on-severity: high` |
| npm audit | `--audit-level=high` |
| OWASP Dependency-Check | `failBuildOnCVSS=7.0` |
| Trivy | `severity: HIGH,CRITICAL`, `exit-code: 1` |

---

## CI-Security-Gates (Workflow: `ci.yml`)

### Dependency-Review

Läuft ausschließlich auf Pull Requests, nachdem der Dependency-Graph des
Gradle-Projekts via `gradle/actions/dependency-submission` hochgeladen wurde.
Die `actions/dependency-review-action` vergleicht die neuen Abhängigkeiten mit
der GitHub Advisory Database und kommentiert bei Problemen direkt im PR
(`comment-summary-in-pr: on-failure`). Neue Abhängigkeiten mit bekannten
High/Critical-CVEs blockieren den Merge.

### npm audit

Im Job `frontend-build` wird nach `npm ci` sofort `npm audit --audit-level=high`
ausgeführt. Schlägt das Audit an, bricht der Build ab — das Frontend-Image wird
nicht gebaut und auch nicht gepusht. Damit ist sichergestellt, dass kein Build mit
verwundbaren npm-Paketen die nächste Stufe erreicht.

### CodeQL (Workflow: `codeql.yml`)

GitHub CodeQL analysiert **Java/Kotlin** (Autobuild) und **JavaScript/TypeScript**
(kein separater Build nötig). Der Workflow läuft bei jedem Push und PR auf `main`
sowie wöchentlich montags um 03:30 UTC. Findings werden als Code-Scanning-Alerts
im Security-Tab angezeigt. CodeQL blockt den Build nicht direkt, ist jedoch als
Required Status Check konfigurierbar.

### gitleaks

`gitleaks-action` scannt die **vollständige Git-History** (`fetch-depth: 0`) auf
geleakte Secrets (API-Keys, Tokens, Passwörter). Der Scan läuft auf jedem Push
und PR. Bei einem Fund bricht der Job sofort ab. Damit werden sowohl neu
eingeführte als auch historisch begrabene Secrets erkannt.

### Trivy (Container-Scan)

Trivy scannt die Docker-Images für Backend und Frontend **vor dem Push** auf
bekannte CVEs. Der Workflow baut dazu zunächst ein lokales Image (ohne Push),
führt dann den Trivy-Scan durch und lädt das SARIF-Ergebnis in den GitHub
Security-Tab hoch. Erst wenn der Scan bestanden hat, wird das Image gepusht.

```
severity: HIGH,CRITICAL
exit-code: 1
trivyignores: .trivyignore
```

Begründete Ausnahmen (nicht ausnutzbare CVEs) werden in `.trivyignore`
eingetragen — mit Begründung und Datum. Die Datei wird quartalsweise reviewt.

---

## Nächtlicher OWASP-Scan (Workflow: `nightly-security.yml`)

Täglich um 03:00 UTC führt `./gradlew dependencyCheckAnalyze` einen
OWASP-Dependency-Check auf dem Backend durch. Die Schwelle liegt bei
`failBuildOnCVSS=7.0`. Das SARIF-Ergebnis wird in den GitHub Security-Tab
hochgeladen.

**Besonderheit:** Schlägt der nächtliche Scan an, öffnet der Workflow automatisch
ein GitHub-Issue mit dem Label `security` und verweist auf den Workflow-Run. So
gehen Findings nicht unbemerkt unter. Für schnellere NVD-API-Abfragen kann der
Repo-Secret `NVD_API_KEY` gesetzt werden.

---

## Dependabot

Dependabot überwacht vier Ökosysteme und öffnet wöchentlich Update-PRs:

| Ökosystem | Verzeichnis |
|-----------|-------------|
| Gradle (Backend-Deps) | `/backend` |
| npm (Frontend-Deps) | `/frontend` |
| GitHub Actions | `/` |
| Docker (Basis-Images) | `/backend`, `/frontend` |

Gradle-, npm- und GitHub-Actions-Updates sind in Gruppen zusammengefasst
(`backend-deps`, `frontend-deps`, `actions`), damit verwandte Updates gebündelt
ankommen statt als Einzelflut. Docker-Image-Updates werden nicht gruppiert und
treffen pro Image einzeln ein.

### 5-Tage-Cooldown (Gradle und npm)

Für die Package-Registries Gradle und npm ist ein Cooldown (`cooldown.default-days: 5`)
konfiguriert. Dieser verhindert, dass Dependabot eine frisch veröffentlichte —
und potenziell noch nicht gecheckte oder kompromittierte — Version sofort als PR
öffnet. GitHub Actions und Docker haben keinen Cooldown. Dasselbe Prinzip gilt
beim manuellen Pinnen: Niemals eine Version fixieren, die weniger als 5 Tage alt
ist. Details dazu in der [Dependency-Checkliste im AI-Guide](ai-guide.md#adding-a-new-dependency).

---

## SBOM, Provenance und Cosign-Signatur (Workflow: `docker-publish.yml`)

Jeder Release-Tag (`v*.*.*`) durchläuft einen erweiterten Publish-Workflow:

1. **SBOM:** Docker BuildKit generiert automatisch eine
   Software-Bill-of-Materials (`sbom: true`) — eine maschinenlesbare Liste
   aller Schichten und Pakete im Image.

2. **Provenance:** Mit `provenance: mode=max` wird ein SLSA-Provenance-Attestat
   eingebettet, das den vollständigen Build-Kontext (Quelle, Commit-SHA,
   Build-Umgebung) beschreibt.

3. **Cosign-Signatur (keyless):** Nach dem Push wird das Image mit `cosign sign`
   signiert. Da der Workflow `id-token: write` hat, authentifiziert sich cosign
   via GitHub-OIDC bei Sigstore/Fulcio — es wird kein langlebiger privater Key
   benötigt. Das Zertifikat ist an den OIDC-Claim (`repo`, `workflow`, `ref`)
   gebunden und in Rekor transparent geloggt.

**Signatur verifizieren:**

```bash
cosign verify \
  --certificate-identity-regexp "https://github.com/taskowolf/TaskWolf" \
  --certificate-oidc-issuer "https://token.actions.githubusercontent.com" \
  kwolfgang/taskowolf-backend:<digest>
```

---

## Actions-SHA-Pinning

Alle `uses:`-Einträge in Workflows sind auf einen vollständigen Commit-SHA
(40 Zeichen) gepinnt, nicht auf einen mutable Tag wie `@v3`. Damit ist
sichergestellt, dass ein kompromittierter Tag-Push die CI-Schritte nicht
austauschen kann. Der kommentierte Versionsname (z. B. `# v2`) dient nur der
Lesbarkeit; verbindlich ist ausschließlich der SHA.

Beispiel aus `ci.yml`:

```yaml
uses: step-security/harden-runner@9af89fc71515a100421586dfdb3dc9c984fbf411   # v2
```

Dependabot aktualisiert die SHAs automatisch, sobald eine neue Action-Version
erscheint.

---

## harden-runner

Jeder Job in jedem Workflow startet mit `step-security/harden-runner`.
Im aktuellen Modus (`egress-policy: audit`) werden alle ausgehenden
Netzwerkverbindungen des Runners geloggt, ohne sie zu blockieren. Die
Egress-Reports erscheinen im Workflow-Summary und ermöglichen es,
unerwartete Verbindungen (Indikatoren für einen kompromittierten Build-Schritt
oder ein bösartiges Skript) frühzeitig zu erkennen.

---

## Auf Findings reagieren

### High/Critical-CVE blockiert CI

1. Dependabot-PR prüfen — oft ist bereits ein Update verfügbar.
2. Wenn kein Update verfügbar: Ausnutzbarkeit im TaskWolf-Kontext prüfen.
3. Wenn nicht ausnutzbar: Suppression in `.trivyignore` (Container) oder
   `dependency-check-suppressions.xml` (Backend) eintragen — mit Begründung
   und Datum.
4. Den vollständigen Reaktionsprozess beschreibt
   `docs/superpowers/supply-chain-policy.md` (interne Repo-Datei,
   nicht im veröffentlichten Docs-Build).

### Geleaktes Secret

1. CI sofort anhalten; betroffenen PR **nicht** mergen.
2. Alle betroffenen Tokens und Secrets **rotieren** (Annahme: exfiltriert).
3. gitleaks-Befund in der Git-History adressieren (Commit reschreiben oder
   Token per se ungültig machen).

### Verdächtiges Paket / Lifecycle-Skript

Indikatoren und Gegenmaßnahmen sind in
`docs/superpowers/supply-chain-policy.md` beschrieben
(Abschnitt *Indicators of Compromise* und *Reaktion bei Verdacht auf
Kompromittierung*).

---

## Schwachstellen melden

Sicherheitslücken in TaskWolf bitte **nicht** als öffentliches GitHub-Issue
melden. Stattdessen das Private Vulnerability Reporting nutzen:
*Security → Report a vulnerability* im Repository. Details zur Reaktionszeit
und zum Prozess stehen in der
[SECURITY.md](https://github.com/taskowolf/TaskWolf/blob/main/SECURITY.md)
im Repository-Root.

---

## Weiterführende Inhalte

- [AI Implementation Guide → Adding a New Dependency](ai-guide.md#adding-a-new-dependency) —
  Checkliste für neue Abhängigkeiten (Reife, CVEs, Cooldown, Lizenz).
- `docs/superpowers/supply-chain-policy.md` — internes Dokument mit
  CVE-Reaktionsprozess, Update-Kadenz, IOC-Liste und Kompromittierungs-Reaktion.
- [SECURITY.md](https://github.com/taskowolf/TaskWolf/blob/main/SECURITY.md) —
  Supported Versions und Responsible-Disclosure-Prozess.
