#!/usr/bin/env node
// npm-audit-Gate mit begründeter Allowlist.
//
// Ersetzt das nackte `npm audit --audit-level=high`: blockt weiterhin bei JEDEM
// HIGH/CRITICAL-Advisory, LÄSST aber explizit begründete Ausnahmen durch. Gleiche
// Policy wie .trivyignore (dort für den Trivy-FS-Scan). npm audit selbst kann keine
// einzelnen Advisories ausnehmen — daher dieser Filter.
//
// Ausführung: aus frontend/ heraus  ->  node ../.github/scripts/audit-gate.mjs
import { execSync } from 'node:child_process';

// Begründete Ausnahmen: GHSA-ID -> Grund + Datum. Jede Zeile muss auch in .trivyignore
// gespiegelt sein (Trivy-Gate) und via Dependabot-Alert-Dismiss abgedeckt werden.
const ALLOWLIST = {
  'GHSA-QWWW-VCR4-C8H2':
    'react-router RSC-only CSRF (CWE-352); TaskWolf ist Client-SPA (createBrowserRouter, kein RSC/SSR) -> nicht ausnutzbar. Fix nur in react-router 8.3.0 (Major); v8-Migration geplant, 2026-07-29',
};

const BLOCK = new Set(['high', 'critical']);
const GHSA_RE = /GHSA-[a-z0-9]{4,}-[a-z0-9]{4,}-[a-z0-9]{4,}/i;

function runAudit() {
  try {
    return execSync('npm audit --json', { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] });
  } catch (e) {
    // npm audit exit-code ist != 0, sobald Vulns existieren — das JSON liegt trotzdem auf stdout.
    if (e.stdout) return e.stdout.toString();
    throw e;
  }
}

let audit;
try {
  audit = JSON.parse(runAudit());
} catch (e) {
  console.error('❌ audit-gate: npm-audit-JSON nicht parsebar:', e.message);
  process.exit(1);
}

const vulns = audit.vulnerabilities || {};
const offending = new Map(); // GHSA-ID -> { pkg, severity, url }

for (const [pkg, info] of Object.entries(vulns)) {
  if (!BLOCK.has(info.severity)) continue;
  for (const via of info.via || []) {
    // String-Einträge = transitive Weiterreichung eines anderen Pakets, kein eigenes
    // Advisory -> überspringen (verhindert Doppelzählung/false-fail bei re-export-Paketen).
    if (typeof via !== 'object' || via === null) continue;
    const sev = String(via.severity || info.severity || '').toLowerCase();
    if (!BLOCK.has(sev)) continue;
    const m = String(via.url || '').match(GHSA_RE) || String(via.title || '').match(GHSA_RE);
    const id = m ? m[0].toUpperCase() : (via.source != null ? `SOURCE-${via.source}` : `${via.name || pkg}-UNKNOWN`);
    if (!(id in ALLOWLIST)) offending.set(id, { pkg: via.name || pkg, severity: sev, url: via.url });
  }
}

if (offending.size > 0) {
  console.error('❌ npm audit: nicht-allowlistete HIGH/CRITICAL-Advisories gefunden:');
  for (const [id, o] of offending) console.error(`   - ${id} (${o.severity}) in ${o.pkg}  ${o.url || ''}`);
  console.error('\nBehebe die Dependency ODER ergänze eine begründete Ausnahme in');
  console.error('.github/scripts/audit-gate.mjs (ALLOWLIST) + .trivyignore + Dependabot-Alert-Dismiss.');
  process.exit(1);
}

const allowed = Object.keys(ALLOWLIST);
console.log('✅ npm-audit-Gate ok: keine nicht-allowlisteten HIGH/CRITICAL-Advisories.');
if (allowed.length) console.log(`   Aktive begründete Ausnahmen: ${allowed.join(', ')}`);
