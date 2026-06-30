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
