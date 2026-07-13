# i18n Full-Rollout — Session 0 (Tooling + Doku) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the objective "100% localized" enforcement machinery — a dependency-free hardcoded-string scanner with a shrinking baseline allowlist, plus an en/de key-parity check — wire it into CI, and correct the backlog. No feature extraction in this session.

**Architecture:** Two small Node scripts under `frontend/scripts/`, each split into a pure, unit-tested core (`*-core.mjs`) and a thin CLI wrapper. The scanner parses `.tsx` via the already-installed TypeScript compiler API (no ESLint stack introduced) and skips files listed in `i18n-allowlist.json`; an `--init` mode auto-generates that allowlist from the current violation set. A CI gate runs both scripts on every push/PR. Follow-up feature sessions shrink the allowlist until it is empty.

**Tech Stack:** Node 20 (`node:test`, `node:fs`), `typescript` ^6 (compiler API, already a devDependency), no new dependencies.

## Global Constraints

- **Scope = frontend tooling + docs only.** No backend changes, no Flyway migration, no feature-string extraction this session.
- **No new heavy dependencies.** Do NOT introduce ESLint / typescript-eslint / eslint-plugin-i18next. Use the already-installed `typescript` compiler API. (Repo has an npm-audit-high gate + Trivy + dependency-review — keep the dep surface minimal.)
- **CI runtime is Node 20** (`.github/workflows/ci.yml`, `frontend-build`). Scripts must run on Node 20 with no build step.
- **Allowlist only ever shrinks.** Files are removed as they get localized; they are never re-added. New/un-listed `.tsx` files are scanner-obligated immediately.
- **en/de namespaces stay key-identical.** The parity check is authoritative.
- All script paths are relative to `frontend/`. Repo root is `C:\Users\Admin\IdeaProjects\TaskWolf`.

---

### Task 1: Scanner core (`findViolations`) + unit tests

Pure function that, given TSX source text, returns the list of hardcoded user-facing strings (JSX text children containing letters; string-literal values of `placeholder`/`title`/`aria-label`/`alt`/`label`). Honors an `i18n-ignore` marker on the comment line and the line immediately below it.

**Files:**
- Create: `frontend/scripts/i18n-scan-core.mjs`
- Test: `frontend/scripts/i18n-scan-core.test.mjs`

**Interfaces:**
- Produces: `findViolations(code: string, fileName?: string) => Array<{ line: number, text: string, kind: string }>` where `kind` is `'jsx-text'` or `'attr:<name>'`.

- [ ] **Step 1: Write the failing test**

Create `frontend/scripts/i18n-scan-core.test.mjs`:

```js
import test from 'node:test'
import assert from 'node:assert/strict'
import { findViolations } from './i18n-scan-core.mjs'

test('flags hardcoded JSX text', () => {
  const v = findViolations('const A = () => <button>Save changes</button>')
  assert.equal(v.length, 1)
  assert.equal(v[0].kind, 'jsx-text')
  assert.match(v[0].text, /Save changes/)
})

test('ignores translated t() calls', () => {
  const v = findViolations("const A = () => <button>{t('common:save')}</button>")
  assert.equal(v.length, 0)
})

test('flags hardcoded placeholder attribute', () => {
  const v = findViolations('const A = () => <input placeholder="Search issues" />')
  assert.equal(v.length, 1)
  assert.equal(v[0].kind, 'attr:placeholder')
})

test('ignores className and data-* attributes', () => {
  const v = findViolations('const A = () => <div className="flex gap-2" data-testid="Board" />')
  assert.equal(v.length, 0)
})

test('skips whitespace/punctuation-only JSX text', () => {
  const v = findViolations('const A = () => <div>  •  </div>')
  assert.equal(v.length, 0)
})

test('respects an i18n-ignore marker', () => {
  const code = [
    'const A = () => (',
    '  <div>',
    '    {/* i18n-ignore */}',
    '    <span>Raw literal</span>',
    '  </div>',
    ')',
  ].join('\n')
  assert.equal(findViolations(code).length, 0)
})
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd frontend && node --test scripts/i18n-scan-core.test.mjs`
Expected: FAIL — cannot find module `./i18n-scan-core.mjs` (or `findViolations is not a function`).

- [ ] **Step 3: Write the minimal implementation**

Create `frontend/scripts/i18n-scan-core.mjs`:

```js
import ts from 'typescript'

const CHECKED_ATTRS = new Set(['placeholder', 'title', 'aria-label', 'alt', 'label'])
const HAS_LETTER = /\p{L}/u

function collectIgnoredLines(code) {
  const ignored = new Set()
  code.split(/\r?\n/).forEach((line, i) => {
    if (line.includes('i18n-ignore')) {
      ignored.add(i + 1) // 1-based comment line
      ignored.add(i + 2) // line immediately below
    }
  })
  return ignored
}

export function findViolations(code, fileName = 'file.tsx') {
  const sf = ts.createSourceFile(fileName, code, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX)
  const ignoredLines = collectIgnoredLines(code)
  const violations = []
  const lineOf = (pos) => sf.getLineAndCharacterOfPosition(pos).line + 1

  function push(pos, text, kind) {
    const line = lineOf(pos)
    if (!ignoredLines.has(line)) violations.push({ line, text, kind })
  }

  function visit(node) {
    if (ts.isJsxText(node)) {
      if (HAS_LETTER.test(node.text)) push(node.getStart(sf), node.text.trim(), 'jsx-text')
    } else if (ts.isJsxAttribute(node) && node.name) {
      const name = node.name.getText(sf)
      if (CHECKED_ATTRS.has(name)) {
        const init = node.initializer
        let str = null
        if (init && ts.isStringLiteral(init)) str = init
        else if (init && ts.isJsxExpression(init) && init.expression && ts.isStringLiteral(init.expression)) str = init.expression
        if (str && HAS_LETTER.test(str.text)) push(str.getStart(sf), str.text, `attr:${name}`)
      }
    }
    ts.forEachChild(node, visit)
  }

  visit(sf)
  return violations
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd frontend && node --test scripts/i18n-scan-core.test.mjs`
Expected: PASS — all 6 subtests green.

- [ ] **Step 5: Commit**

```bash
cd frontend && git add scripts/i18n-scan-core.mjs scripts/i18n-scan-core.test.mjs
git commit -m "feat(i18n): hardcoded-string scanner core + tests"
```

---

### Task 2: Scanner CLI + allowlist + `--init`, plus npm scripts

Thin wrapper that walks `frontend/src/**/*.tsx`, skips allowlisted files, prints `file:line` violations and exits non-zero when any are found. `--init` rewrites the allowlist from all current offenders. Generates the real baseline allowlist for this repo.

**Files:**
- Create: `frontend/scripts/i18n-scan.mjs`
- Create: `frontend/scripts/i18n-allowlist.json` (starts as `[]`)
- Modify: `frontend/package.json` (scripts block)

**Interfaces:**
- Consumes: `findViolations` from Task 1.
- Produces: npm scripts `scan:i18n`, `scan:i18n:init`; allowlist file at `frontend/scripts/i18n-allowlist.json` (JSON array of `frontend/`-relative paths, e.g. `src/pages/board/BoardPage.tsx`).

- [ ] **Step 1: Create the empty allowlist**

Create `frontend/scripts/i18n-allowlist.json` with exactly:

```json
[]
```

- [ ] **Step 2: Write the CLI wrapper**

Create `frontend/scripts/i18n-scan.mjs`:

```js
import { readFileSync, writeFileSync, readdirSync, statSync } from 'node:fs'
import { join, relative, sep, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { findViolations } from './i18n-scan-core.mjs'

const HERE = dirname(fileURLToPath(import.meta.url))
const FRONTEND_ROOT = join(HERE, '..')
const SRC_DIR = join(FRONTEND_ROOT, 'src')
const ALLOWLIST_PATH = join(HERE, 'i18n-allowlist.json')
const isInit = process.argv.includes('--init')

function walk(dir, acc = []) {
  for (const entry of readdirSync(dir)) {
    const p = join(dir, entry)
    if (statSync(p).isDirectory()) walk(p, acc)
    else if (p.endsWith('.tsx')) acc.push(p)
  }
  return acc
}

const toRel = (abs) => relative(FRONTEND_ROOT, abs).split(sep).join('/')

const allowlist = new Set(JSON.parse(readFileSync(ALLOWLIST_PATH, 'utf8')))
const offenders = []
const report = []
let total = 0

for (const abs of walk(SRC_DIR)) {
  const rel = toRel(abs)
  if (!isInit && allowlist.has(rel)) continue
  const violations = findViolations(readFileSync(abs, 'utf8'), abs)
  if (!violations.length) continue
  offenders.push(rel)
  if (!isInit) {
    total += violations.length
    for (const v of violations) report.push(`${rel}:${v.line}  [${v.kind}]  ${v.text}`)
  }
}

if (isInit) {
  const sorted = [...new Set(offenders)].sort()
  writeFileSync(ALLOWLIST_PATH, JSON.stringify(sorted, null, 2) + '\n')
  console.log(`i18n-scan --init: wrote ${sorted.length} file(s) to the allowlist`)
  process.exit(0)
}

if (total) {
  console.error(`i18n-scan: ${total} hardcoded string(s) in ${offenders.length} non-allowlisted file(s):\n`)
  console.error(report.join('\n'))
  console.error('\nLocalize with t(), or add an `i18n-ignore` marker for genuine non-UI strings. Do NOT re-add files to the allowlist.')
  process.exit(1)
}
console.log(`i18n-scan: OK — 0 hardcoded strings outside the allowlist (${allowlist.size} file(s) still allowlisted).`)
```

- [ ] **Step 3: Add npm scripts**

In `frontend/package.json`, replace the `"scripts"` block:

```json
  "scripts": {
    "dev": "vite",
    "build": "tsc && vite build",
    "preview": "vite preview"
  },
```

with:

```json
  "scripts": {
    "dev": "vite",
    "build": "tsc && vite build",
    "preview": "vite preview",
    "scan:i18n": "node scripts/i18n-scan.mjs",
    "scan:i18n:init": "node scripts/i18n-scan.mjs --init",
    "check:i18n": "node scripts/i18n-parity.mjs",
    "test:i18n": "node --test scripts/i18n-scan-core.test.mjs scripts/i18n-parity-core.test.mjs",
    "lint:i18n": "npm run scan:i18n && npm run check:i18n"
  },
```

- [ ] **Step 4: Generate the baseline allowlist**

Run: `cd frontend && npm run scan:i18n:init`
Expected: prints `i18n-scan --init: wrote N file(s) to the allowlist` with N ≈ 80. `scripts/i18n-allowlist.json` now lists the un-localized `.tsx` files.

- [ ] **Step 5: Verify the scanner is green against the baseline**

Run: `cd frontend && npm run scan:i18n`
Expected: `i18n-scan: OK — 0 hardcoded strings outside the allowlist (N file(s) still allowlisted).` (exit 0)

- [ ] **Step 6: Verify the 8 pilot files are NOT in the allowlist**

Open `scripts/i18n-allowlist.json` and confirm none of these appear:
`src/layouts/AppLayout.tsx`, `src/pages/auth/LoginPage.tsx`, `src/pages/auth/RegisterPage.tsx`, `src/pages/settings/ProfilePage.tsx`, `src/pages/settings/SecurityPage.tsx`, `src/pages/settings/AccountSettingsPage.tsx`, `src/pages/settings/NotificationSettingsPage.tsx`, `src/components/LanguageSwitcher.tsx`.

If any DO appear, the scanner found a stray hardcoded string the pilot missed. For each such file: open it, localize the flagged string with `t()` (add the key to the matching `en`/`de` namespace) OR add an `i18n-ignore` marker if it is genuinely non-UI. Then re-run `npm run scan:i18n:init` and repeat this step until the 8 files are absent from the allowlist.

- [ ] **Step 7: Commit**

```bash
cd frontend && git add scripts/i18n-scan.mjs scripts/i18n-allowlist.json package.json
git commit -m "feat(i18n): scanner CLI + baseline allowlist + npm scripts"
```

---

### Task 3: Parity core (`flattenKeys` / `diffKeys`) + unit tests

Pure functions comparing two locale objects by their recursive key sets.

**Files:**
- Create: `frontend/scripts/i18n-parity-core.mjs`
- Test: `frontend/scripts/i18n-parity-core.test.mjs`

**Interfaces:**
- Produces: `flattenKeys(obj) => Set<string>` (dot-joined leaf paths); `diffKeys(a, b) => { missingInB: string[], missingInA: string[] }` (sorted).

- [ ] **Step 1: Write the failing test**

Create `frontend/scripts/i18n-parity-core.test.mjs`:

```js
import test from 'node:test'
import assert from 'node:assert/strict'
import { flattenKeys, diffKeys } from './i18n-parity-core.mjs'

test('flattenKeys flattens nested objects to leaf paths', () => {
  const keys = [...flattenKeys({ a: { b: 'x' }, c: 'y' })].sort()
  assert.deepEqual(keys, ['a.b', 'c'])
})

test('diffKeys reports keys missing on each side', () => {
  const { missingInB, missingInA } = diffKeys({ a: '1', b: '2' }, { a: '1', c: '3' })
  assert.deepEqual(missingInB, ['b'])
  assert.deepEqual(missingInA, ['c'])
})

test('diffKeys is empty when key sets match regardless of values', () => {
  const { missingInB, missingInA } = diffKeys({ a: { b: '1' } }, { a: { b: '2' } })
  assert.equal(missingInB.length, 0)
  assert.equal(missingInA.length, 0)
})
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd frontend && node --test scripts/i18n-parity-core.test.mjs`
Expected: FAIL — cannot find module `./i18n-parity-core.mjs`.

- [ ] **Step 3: Write the minimal implementation**

Create `frontend/scripts/i18n-parity-core.mjs`:

```js
export function flattenKeys(obj, prefix = '', acc = new Set()) {
  for (const [k, val] of Object.entries(obj)) {
    const key = prefix ? `${prefix}.${k}` : k
    if (val && typeof val === 'object' && !Array.isArray(val)) flattenKeys(val, key, acc)
    else acc.add(key)
  }
  return acc
}

export function diffKeys(a, b) {
  const ka = flattenKeys(a)
  const kb = flattenKeys(b)
  return {
    missingInB: [...ka].filter((k) => !kb.has(k)).sort(),
    missingInA: [...kb].filter((k) => !ka.has(k)).sort(),
  }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd frontend && node --test scripts/i18n-parity-core.test.mjs`
Expected: PASS — all 3 subtests green.

- [ ] **Step 5: Commit**

```bash
cd frontend && git add scripts/i18n-parity-core.mjs scripts/i18n-parity-core.test.mjs
git commit -m "feat(i18n): en/de key-parity core + tests"
```

---

### Task 4: Parity CLI + green baseline

Wrapper that loads every namespace JSON from `src/i18n/locales/en` and `.../de`, reports missing namespaces and missing keys, exits non-zero on any mismatch.

**Files:**
- Create: `frontend/scripts/i18n-parity.mjs`

**Interfaces:**
- Consumes: `diffKeys` from Task 3; the `check:i18n` npm script added in Task 2.

- [ ] **Step 1: Write the CLI wrapper**

Create `frontend/scripts/i18n-parity.mjs`:

```js
import { readFileSync, readdirSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { diffKeys } from './i18n-parity-core.mjs'

const HERE = dirname(fileURLToPath(import.meta.url))
const LOCALES = join(HERE, '..', 'src', 'i18n', 'locales')
const EN = join(LOCALES, 'en')
const DE = join(LOCALES, 'de')

const jsonFiles = (dir) => readdirSync(dir).filter((f) => f.endsWith('.json'))
const load = (dir, ns) => JSON.parse(readFileSync(join(dir, ns), 'utf8'))

const enFiles = jsonFiles(EN)
const deFiles = jsonFiles(DE)
let problems = 0

for (const f of enFiles.filter((f) => !deFiles.includes(f))) { console.error(`Namespace missing in de/: ${f}`); problems++ }
for (const f of deFiles.filter((f) => !enFiles.includes(f))) { console.error(`Namespace missing in en/: ${f}`); problems++ }

for (const ns of enFiles.filter((f) => deFiles.includes(f))) {
  const { missingInB, missingInA } = diffKeys(load(EN, ns), load(DE, ns))
  for (const k of missingInB) { console.error(`[${ns}] missing in de: ${k}`); problems++ }
  for (const k of missingInA) { console.error(`[${ns}] missing in en: ${k}`); problems++ }
}

if (problems) { console.error(`\ni18n-parity: ${problems} mismatch(es).`); process.exit(1) }
console.log('i18n-parity: OK — en/de namespaces and keys match.')
```

- [ ] **Step 2: Run the parity check against the current locales**

Run: `cd frontend && npm run check:i18n`
Expected: `i18n-parity: OK — en/de namespaces and keys match.` (exit 0)

If it reports mismatches, the pilot locales drifted. For each reported key, add the missing key (with a correct translation) to the deficient `src/i18n/locales/<lang>/<ns>.json` so both sides match, then re-run until green. Do not delete keys to force parity.

- [ ] **Step 3: Run the combined gate + core tests locally**

Run: `cd frontend && npm run test:i18n && npm run lint:i18n`
Expected: core tests PASS, then `i18n-scan: OK …` and `i18n-parity: OK …`.

- [ ] **Step 4: Commit**

```bash
cd frontend && git add scripts/i18n-parity.mjs
git commit -m "feat(i18n): en/de key-parity CLI gate"
```

---

### Task 5: Wire the i18n gate into CI

Add a gating step to the existing `frontend-build` job so every push/PR fails on new hardcoded strings or en/de key drift.

**Files:**
- Modify: `.github/workflows/ci.yml` (`frontend-build` job, between the npm-audit step and the build step)

- [ ] **Step 1: Add the CI step**

In `.github/workflows/ci.yml`, replace:

```yaml
      - name: npm audit (block on high/critical)
        working-directory: frontend
        run: npm audit --audit-level=high

      - name: Build frontend
        working-directory: frontend
        run: npm run build
```

with:

```yaml
      - name: npm audit (block on high/critical)
        working-directory: frontend
        run: npm audit --audit-level=high

      - name: i18n scanner self-tests
        working-directory: frontend
        run: npm run test:i18n

      - name: i18n coverage + parity gate
        working-directory: frontend
        run: npm run lint:i18n

      - name: Build frontend
        working-directory: frontend
        run: npm run build
```

- [ ] **Step 2: Validate the workflow YAML**

Run: `cd frontend && node -e "const s=require('node:fs').readFileSync('../.github/workflows/ci.yml','utf8'); if(!s.includes('i18n coverage + parity gate')) throw new Error('gate step missing'); console.log('ci.yml gate step present')"`
Expected: `ci.yml gate step present`.

- [ ] **Step 3: Re-run the gate locally to confirm the exact CI command passes**

Run: `cd frontend && npm run test:i18n && npm run lint:i18n`
Expected: all green (same as Task 4 Step 3).

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "ci(i18n): gate frontend build on i18n coverage + key parity"
```

---

### Task 6: Backlog corrections + per-session migration checklist

Fix the stale #13 status, register the two follow-up backlog items (#15 rollout, #16 backend MessageSource), and append a reusable per-session migration checklist to the master spec so every future session follows the locked pattern.

**Files:**
- Modify: `docs/superpowers/specs/2026-07-07-backlog-overview.md`
- Modify: `docs/superpowers/specs/2026-07-13-i18n-full-rollout-design.md` (append appendix)

- [ ] **Step 1: Fix the #13 table row**

In `docs/superpowers/specs/2026-07-07-backlog-overview.md`, replace:

```
| 13 | Internationalisierung (UI in mehreren Sprachen) | Full-Stack/UI | 🔀 **PR #57 offen** (Fundament + Pilot-Slice, nicht gemergt) |
| 14 | Organisationen als Oberkategorie (Projekt-/Member-Zuordnung + Rechte-Vererbung) | Full-Stack | ✅ **AUSGELIEFERT** (Backend PR #55, Frontend PR #56, Release v1.0.12) |
```

with:

```
| 13 | Internationalisierung — Fundament + Pilot-Slice | Full-Stack/UI | ✅ **GEMERGT** (PR #57, squash `0b9b817`; noch nicht released) |
| 14 | Organisationen als Oberkategorie (Projekt-/Member-Zuordnung + Rechte-Vererbung) | Full-Stack | ✅ **AUSGELIEFERT** (Backend PR #55, Frontend PR #56, Release v1.0.12) |
| 15 | Internationalisierung — Full-Rollout (alle Komponenten, mehrere Sessions) | UI | 🔧 **IN ARBEIT** (Master-Spec `2026-07-13-i18n-full-rollout-design.md`; Session 0 = Tooling) |
| 16 | Backend-Text-Lokalisierung (Spring `MessageSource`) | Full-Stack | ⬜ Backlog (bewusst separater Folge-Zyklus zu #15; Frontend-Scope endet an der Client-Präsentation) |
```

- [ ] **Step 2: Fix the detailed #13 status block**

In the same file, replace the blockquote under `## #13 — Internationalisierung (UI in mehreren Sprachen)` that begins:

```
> 🔀 **PR #57 offen** (2026-07-12, Branch `worktree-worktree-i18n-foundation`, nicht
> gemergt). Scope-Entscheid: **Fundament + Pilot-Slice** (nicht flächendeckend).
```

with:

```
> ✅ **Fundament + Pilot-Slice GEMERGT** (PR #57, squash `0b9b817`; noch nicht
> released). Scope-Entscheid: **Fundament + Pilot-Slice** (nicht flächendeckend).
> **Folge-Vorhaben #15** (Full-Rollout aller Komponenten, mehrere Sessions) ist
> gestartet — Master-Spec `2026-07-13-i18n-full-rollout-design.md`.
```

- [ ] **Step 3: Append the per-session migration checklist to the master spec**

Append to the end of `docs/superpowers/specs/2026-07-13-i18n-full-rollout-design.md`:

```markdown

## Anhang — Migrations-Checkliste pro Folge-Session (Muster gelockt)

Jede Feature-Session (S1…S18) folgt exakt diesen Schritten — kein neuer Brainstorm/Spec:

1. Namespace `<ns>` festlegen (siehe Matrix). Neue Dateien anlegen:
   `frontend/src/i18n/locales/en/<ns>.json` und `.../de/<ns>.json`.
2. In `frontend/src/i18n/index.ts` den Namespace importieren, in `resources`
   (en+de) und in die `ns`-Liste eintragen.
3. In den Slice-Dateien jede nutzersichtbare Zeichenkette durch
   `t('<ns>:hierarchischer.key')` ersetzen (`useTranslation('<ns>')`). **Keine**
   String-Concat aus Fragmenten; Interpolation über Variablen; Plurale über
   i18next-Plural-Keys. Datum/Zahl/relative Zeit über `frontend/src/i18n/format.ts`.
4. Die Slice-Dateien aus `frontend/scripts/i18n-allowlist.json` entfernen.
5. Grün ziehen: `npm run test:i18n && npm run lint:i18n && npm run build`
   (Scanner 0 Verstöße in den nun geprüften Dateien, en/de-Parität, Build).
6. Manuell im Browser DE/EN in diesem Bereich prüfen (kein Roh-Key, keine
   Layout-Brüche durch längere DE-Strings).
7. In der Coverage-Matrix (Abschnitt 4) die Zeile auf ✅ setzen. Commit.

**Fertig-Kriterium des Gesamt-Vorhabens:** `i18n-allowlist.json` = `[]`, Scanner grün,
Parität grün.
```

- [ ] **Step 4: Verify the docs render and cross-reference correctly**

Run: `cd frontend && node -e "const fs=require('node:fs'); const b=fs.readFileSync('../docs/superpowers/specs/2026-07-07-backlog-overview.md','utf8'); if(b.includes('PR #57 offen')) throw new Error('stale #13 text remains'); if(!b.includes('| 15 |')||!b.includes('| 16 |')) throw new Error('#15/#16 rows missing'); console.log('backlog updated')"`
Expected: `backlog updated`.

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/specs/2026-07-07-backlog-overview.md docs/superpowers/specs/2026-07-13-i18n-full-rollout-design.md
git commit -m "docs(i18n): correct #13 status, register #15/#16, add per-session migration checklist"
```

---

## Self-Review

**Spec coverage** (against `2026-07-13-i18n-full-rollout-design.md` Abschnitt 6 — Session-0 deliverables):
- Scanner (Abschnitt 2.1) → Tasks 1–2. ✅
- Baseline allowlist (Abschnitt 2.2) → Task 2 (`--init`). ✅
- en/de key-parity check (Abschnitt 2.3) → Tasks 3–4. ✅
- npm scripts + CI gate → Task 2 (scripts) + Task 5 (CI). ✅
- Master spec exists (prior commit) + per-session checklist → Task 6 Step 3. ✅
- Backlog corrections #13/#15/#16 → Task 6 Steps 1–2. ✅
- Explicitly out of scope this session: feature extraction, backend MessageSource — no task touches them. ✅

**Placeholder scan:** No TBD/TODO; every code step contains complete runnable code and exact commands with expected output. ✅

**Type consistency:** `findViolations(code, fileName)` defined in Task 1, consumed unchanged in Task 2. `diffKeys`/`flattenKeys` defined in Task 3, consumed unchanged in Task 4. npm script names (`scan:i18n`, `scan:i18n:init`, `check:i18n`, `test:i18n`, `lint:i18n`) added in Task 2 and referenced consistently in Tasks 4–5. Allowlist path `frontend/scripts/i18n-allowlist.json` consistent across Tasks 2 and 6. ✅
