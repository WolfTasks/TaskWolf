# i18n Rollout — Session 15 (`servicedesk` namespace) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Localize the S15 `servicedesk` slice — `ServiceDeskPage` and `IncidentDashboardPage` — into a new `servicedesk` namespace (`en`/`de`), including the scanner-blind SLA-status render, the incident resolve flow, and the resolved-timestamp via `format.ts`, then flip S15 to ✅ in the master-spec coverage matrix.

**Architecture:** Thin execution plan against the locked pattern in `docs/superpowers/specs/2026-07-13-i18n-full-rollout-design.md` (Anhang — Migrations-Checkliste). One new `servicedesk` namespace. The `computeSlaStatus` return `'N/A'` is renamed to `'NA'` so it can be a translation key segment; `SLA_COLOR`/`SEVERITY_COLOR` maps key off the raw enum and are left untouched. `{inc.severity}` (P1–P4 codes) and `{t.status}` (dynamic status name) stay as data.

**Tech Stack:** react-i18next (`useTranslation`), `frontend/src/i18n/format.ts` (`formatDateTime`), the Session-0 scanner/parity scripts, Node 20, Vite/tsc build.

## Global Constraints

- **Namespace = `servicedesk`.** In both files use `const { t } = useTranslation('servicedesk')`; reference shared terms with the `common:` prefix (`t('common:loading')`, `t('common:cancel')`).
- **en/de stay key-identical** (parity check is authoritative).
- **`t`-shadow trap in `ServiceDeskPage`.** The existing `tickets.map((t: any) => …)` names the ticket `t`, shadowing the translator. Rename that map parameter to `ticket` (and every `t.` inside → `ticket.`) when adding the hook.
- **SLA-status render is scanner-blind but user-facing.** Rename `computeSlaStatus`'s `return 'N/A'` to `return 'NA'` (safe — `SLA_COLOR` only special-cases BREACHED/WARNING, everything else is green) and render `t(\`sla.${slaStatus}\`)`. Keep `SLA_COLOR`/`SEVERITY_COLOR` keyed on the raw enum.
- **`{inc.severity}` (P1–P4) and `{t.status}`/`{ticket.status}` stay as data** — severity codes and dynamic status names, not translatable copy.
- **Resolved timestamp via `format.ts`.** Replace `new Date(inc.resolvedAt).toLocaleString()` with `formatDateTime(inc.resolvedAt)` (DoD point 3).
- **`Label: {data}` splits are not fragment-concat** — the label is a `{t(...)}` element and the value is a separate `{data}` expression (e.g. `{t('incidents.issueLabel')}: {inc.issueId}`).
- **Both files are `export default`** — keep that.
- **Remove both S15 files from `frontend/scripts/i18n-allowlist.json`.** **Net for S15: 2 files removed (11 → 9).**
- **Done-per-slice (master spec Abschnitt 5):** `npm run test:i18n && npm run lint:i18n && npm run build` all green; manual DE/EN browser check; matrix row flipped to ✅.
- All paths relative to repo root `C:\Users\Admin\IdeaProjects\TaskWolf`. Work in an isolated worktree branched from `origin/main` (which must contain merged S0–S14; the allowlist starts at 11).

---

### Task 1: Create the `servicedesk` namespace + register it

**Files:**
- Create: `frontend/src/i18n/locales/en/servicedesk.json`
- Create: `frontend/src/i18n/locales/de/servicedesk.json`
- Modify: `frontend/src/i18n/index.ts`

**Interfaces:**
- Produces: the `servicedesk` namespace keys consumed by Tasks 2–3.

- [ ] **Step 1: Create `frontend/src/i18n/locales/en/servicedesk.json`**

```json
{
  "desk": {
    "title": "Service Desk",
    "empty": "No tickets found."
  },
  "sla": {
    "NA": "N/A",
    "OK": "OK",
    "WARNING": "Warning",
    "BREACHED": "Breached"
  },
  "incidents": {
    "title": "Incidents",
    "empty": "No incidents found.",
    "issueLabel": "Issue",
    "resolvedLabel": "Resolved",
    "postmortemLabel": "Postmortem",
    "postmortemPlaceholder": "Postmortem notes (optional)...",
    "resolve": "Resolve",
    "resolving": "Resolving…",
    "confirmResolve": "Confirm Resolve"
  }
}
```

- [ ] **Step 2: Create `frontend/src/i18n/locales/de/servicedesk.json`** (same keys, German copy)

```json
{
  "desk": {
    "title": "Service Desk",
    "empty": "Keine Tickets gefunden."
  },
  "sla": {
    "NA": "N/V",
    "OK": "OK",
    "WARNING": "Warnung",
    "BREACHED": "Verletzt"
  },
  "incidents": {
    "title": "Incidents",
    "empty": "Keine Incidents gefunden.",
    "issueLabel": "Vorgang",
    "resolvedLabel": "Gelöst",
    "postmortemLabel": "Postmortem",
    "postmortemPlaceholder": "Postmortem-Notizen (optional)...",
    "resolve": "Lösen",
    "resolving": "Wird gelöst…",
    "confirmResolve": "Lösung bestätigen"
  }
}
```

- [ ] **Step 3: Register the namespace in `frontend/src/i18n/index.ts`**

Add the imports after the last existing locale import:

```ts
import enServicedesk from './locales/en/servicedesk.json'
import deServicedesk from './locales/de/servicedesk.json'
```

In `resources`, append `servicedesk` to both languages, and append `'servicedesk'` to the `ns` array:

```ts
      en: { /* …existing… */ servicedesk: enServicedesk },
      de: { /* …existing… */ servicedesk: deServicedesk },
```
```ts
    ns: [/* …existing… */ 'servicedesk'],
```

- [ ] **Step 4: Verify parity + build** (both files still allowlisted, scanner stays green)

Run: `cd frontend && npm run check:i18n && npm run build`
Expected: `i18n-parity: OK — en/de namespaces and keys match.` then a successful build.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/i18n/locales/en/servicedesk.json frontend/src/i18n/locales/de/servicedesk.json frontend/src/i18n/index.ts
git commit -m "feat(i18n): add servicedesk namespace (en/de)"
```

---

### Task 2: Localize ServiceDeskPage

**Files:**
- Modify: `frontend/src/pages/projects/servicedesk/ServiceDeskPage.tsx`
- Modify: `frontend/scripts/i18n-allowlist.json`

**Interfaces:**
- Consumes: `servicedesk:desk.*`, `servicedesk:sla.*` (Task 1); `common:loading` (existing).

- [ ] **Step 1: Add the hook import + rename the SLA sentinel**

Add after `import { serviceDeskApi } from '@/api/servicedesk'` (line 3):

```ts
import { useTranslation } from 'react-i18next'
```

In `computeSlaStatus`, replace:

```tsx
  if (!slaStartTime || !slaPolicy) return 'N/A'
```
→ `if (!slaStartTime || !slaPolicy) return 'NA'`

- [ ] **Step 2: Add the hook + localize loading/heading/empty**

Add as the first line of the `ServiceDeskPage` body (before `const { key } = useParams…`):

```ts
  const { t } = useTranslation('servicedesk')
```

Replace:

```tsx
  if (ticketsLoading) return <div className="p-6 text-gray-400">Loading...</div>
```
→ `if (ticketsLoading) return <div className="p-6 text-gray-400">{t('common:loading')}</div>`

```tsx
      <h1 className="text-2xl font-semibold">Service Desk</h1>

      {tickets.length === 0 ? (
        <p className="text-gray-500 text-sm">No tickets found.</p>
```

with:

```tsx
      <h1 className="text-2xl font-semibold">{t('desk.title')}</h1>

      {tickets.length === 0 ? (
        <p className="text-gray-500 text-sm">{t('desk.empty')}</p>
```

- [ ] **Step 3: Rename the shadowing map param + localize the SLA badge**

Replace the ticket map block:

```tsx
          {tickets.map((t: any) => {
            const matchedPolicy = slaPolicies.find(
              (p: any) => p.priority === t.priority
            ) ?? null
            const slaStatus = computeSlaStatus(t.slaStartTime, matchedPolicy)
            return (
              <div
                key={t.id}
                className="flex items-center gap-3 bg-gray-900 border border-gray-800 rounded-lg p-3"
              >
                <span className="font-mono text-sm text-gray-400">{t.key}</span>
                <span className="flex-1 text-sm">{t.title}</span>
                <span className="text-xs text-gray-500">{t.status}</span>
                <span
                  className={`text-xs px-2 py-0.5 rounded font-medium ${SLA_COLOR(slaStatus)}`}
                >
                  {slaStatus}
                </span>
              </div>
            )
          })}
```

with:

```tsx
          {tickets.map((ticket: any) => {
            const matchedPolicy = slaPolicies.find(
              (p: any) => p.priority === ticket.priority
            ) ?? null
            const slaStatus = computeSlaStatus(ticket.slaStartTime, matchedPolicy)
            return (
              <div
                key={ticket.id}
                className="flex items-center gap-3 bg-gray-900 border border-gray-800 rounded-lg p-3"
              >
                <span className="font-mono text-sm text-gray-400">{ticket.key}</span>
                <span className="flex-1 text-sm">{ticket.title}</span>
                <span className="text-xs text-gray-500">{ticket.status}</span>
                <span
                  className={`text-xs px-2 py-0.5 rounded font-medium ${SLA_COLOR(slaStatus)}`}
                >
                  {t(`sla.${slaStatus}`)}
                </span>
              </div>
            )
          })}
```

(`{ticket.key}`, `{ticket.title}`, `{ticket.status}` stay as data.)

- [ ] **Step 4: Remove ServiceDeskPage from the allowlist**

In `frontend/scripts/i18n-allowlist.json`, delete this line:

```json
  "src/pages/projects/servicedesk/ServiceDeskPage.tsx",
```

- [ ] **Step 5: Verify scanner + build**

Run: `cd frontend && npm run scan:i18n && npm run build`
Expected: `i18n-scan: OK — 0 hardcoded strings outside the allowlist (10 file(s) still allowlisted).` and a successful build. Confirm tsc did not error on a shadowed `t` (Step 3 rename to `ticket`).

- [ ] **Step 6: Commit**

```bash
git add frontend/src/pages/projects/servicedesk/ServiceDeskPage.tsx frontend/scripts/i18n-allowlist.json
git commit -m "feat(i18n): localize service desk page"
```

---

### Task 3: Localize IncidentDashboardPage

**Files:**
- Modify: `frontend/src/pages/projects/servicedesk/IncidentDashboardPage.tsx`
- Modify: `frontend/scripts/i18n-allowlist.json`

**Interfaces:**
- Consumes: `servicedesk:incidents.*` (Task 1); `common:loading`, `common:cancel` (existing); `formatDateTime` from `@/i18n/format` (existing).

- [ ] **Step 1: Add imports**

Add after `import { serviceDeskApi } from '@/api/servicedesk'` (line 4):

```ts
import { useTranslation } from 'react-i18next'
import { formatDateTime } from '@/i18n/format'
```

- [ ] **Step 2: Add the hook + localize loading/heading/empty**

Add as the first line of the `IncidentDashboardPage` body (before `const { key } = useParams…`):

```ts
  const { t } = useTranslation('servicedesk')
```

Replace:

```tsx
  if (isLoading) return <div className="p-6 text-gray-400">Loading...</div>
```
→ `if (isLoading) return <div className="p-6 text-gray-400">{t('common:loading')}</div>`

```tsx
      <h1 className="text-2xl font-semibold">Incidents</h1>

      {incidents.length === 0 ? (
        <p className="text-gray-500 text-sm">No incidents found.</p>
```

with:

```tsx
      <h1 className="text-2xl font-semibold">{t('incidents.title')}</h1>

      {incidents.length === 0 ? (
        <p className="text-gray-500 text-sm">{t('incidents.empty')}</p>
```

- [ ] **Step 3: Localize the incident detail labels + resolved timestamp**

Replace:

```tsx
                <p className="font-medium text-sm">Issue: {inc.issueId}</p>
                {inc.resolvedAt && (
                  <p className="text-xs text-gray-400">
                    Resolved: {new Date(inc.resolvedAt).toLocaleString()}
                  </p>
                )}
                {inc.postmortemBody && (
                  <p className="text-xs text-gray-500">
                    Postmortem: {inc.postmortemBody}
                  </p>
                )}
```

with:

```tsx
                <p className="font-medium text-sm">{t('incidents.issueLabel')}: {inc.issueId}</p>
                {inc.resolvedAt && (
                  <p className="text-xs text-gray-400">
                    {t('incidents.resolvedLabel')}: {formatDateTime(inc.resolvedAt)}
                  </p>
                )}
                {inc.postmortemBody && (
                  <p className="text-xs text-gray-500">
                    {t('incidents.postmortemLabel')}: {inc.postmortemBody}
                  </p>
                )}
```

(`{inc.issueId}`, `{inc.postmortemBody}`, `{inc.severity}` stay as data.)

- [ ] **Step 4: Localize the resolve flow**

Replace:

```tsx
                      placeholder="Postmortem notes (optional)..."
```
→ `placeholder={t('incidents.postmortemPlaceholder')}`

```tsx
                        {resolveMutation.isPending ? 'Resolving...' : 'Confirm Resolve'}
```
→ `{resolveMutation.isPending ? t('incidents.resolving') : t('incidents.confirmResolve')}`

```tsx
                        Cancel
                      </button>
```
→ `{t('common:cancel')}`

```tsx
                  Resolve
                </button>
```
→ `{t('incidents.resolve')}`

- [ ] **Step 5: Remove IncidentDashboardPage from the allowlist**

In `frontend/scripts/i18n-allowlist.json`, delete this line:

```json
  "src/pages/projects/servicedesk/IncidentDashboardPage.tsx",
```

- [ ] **Step 6: Verify scanner + build**

Run: `cd frontend && npm run scan:i18n && npm run build`
Expected: `i18n-scan: OK — 0 hardcoded strings outside the allowlist (9 file(s) still allowlisted).` and a successful build.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/pages/projects/servicedesk/IncidentDashboardPage.tsx frontend/scripts/i18n-allowlist.json
git commit -m "feat(i18n): localize incident dashboard page"
```

---

### Task 4: Finalize S15 — full gate + flip coverage matrix

**Files:**
- Modify: `docs/superpowers/specs/2026-07-13-i18n-full-rollout-design.md`

- [ ] **Step 1: Run the full slice gate**

Run: `cd frontend && npm run test:i18n && npm run lint:i18n && npm run build`
Expected: scanner self-tests pass, `i18n-scan: OK — 0 hardcoded strings outside the allowlist (9 file(s) still allowlisted).`, `i18n-parity: OK — en/de namespaces and keys match.`, and a successful build. Both S15 files must be absent from the allowlist (11 − 2 = 9 entries).

- [ ] **Step 2: Manual DE/EN browser check**

Start the dev server (`cd frontend && npm run dev`), switch language via Settings → Profile, and confirm: Service Desk (title, empty state, SLA status badge — N/A/OK/Warning/Breached); Incidents (title, empty, Issue/Resolved/Postmortem labels, localized resolved timestamp, resolve flow: Resolve → postmortem placeholder → Resolving/Confirm Resolve/Cancel) all switch between English and German with no raw keys.

- [ ] **Step 3: Flip S15 to ✅ in the coverage matrix**

In `docs/superpowers/specs/2026-07-13-i18n-full-rollout-design.md`, replace the S15 matrix row:

```
| S15 | `servicedesk` | ⬜ | ServiceDeskPage, IncidentDashboardPage |
```

with:

```
| S15 | `servicedesk` | ✅ | Both files localized (new `servicedesk` ns); scanner-blind SLA status localized (`'N/A'`→`'NA'` sentinel); resolved timestamp via `formatDateTime`; `t`-shadow (ticket) handled; P1–P4 severity + status left as data |
```

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/specs/2026-07-13-i18n-full-rollout-design.md
git commit -m "docs(i18n): mark S15 servicedesk slice complete in coverage matrix"
```

---

## Self-Review

**Spec coverage:**
- New `servicedesk` namespace en+de, registered in `index.ts` → Task 1. ✅
- Every user-facing string in both files via `t()` → Tasks 2–3. ✅
- Scanner-blind SLA-status render localized (with `'N/A'`→`'NA'` sentinel rename) → Task 2. ✅
- `t`-shadow trap handled (map param → `ticket`) → Task 2. ✅
- Resolved timestamp via `formatDateTime` (DoD point 3) → Task 3. ✅
- `{inc.severity}`/`{ticket.status}` correctly left as data → Global Constraints. ✅
- `common:loading`/`common:cancel` reused. ✅
- Both files removed from allowlist (11 → 9) → Tasks 2–3. ✅
- Full gate + manual DE/EN + matrix flip → Task 4. ✅

**Placeholder scan:** No TBD/TODO; every step has exact old/new code and exact commands with expected output.

**Type/key consistency:** Keys used in Tasks 2–3 (`desk.title/empty`, `sla.NA/OK/WARNING/BREACHED`, `incidents.title/empty/issueLabel/resolvedLabel/postmortemLabel/postmortemPlaceholder/resolve/resolving/confirmResolve`) are defined in Task 1's en+de JSON. `sla.${slaStatus}` covers exactly NA/OK/WARNING/BREACHED (the four `computeSlaStatus` returns after the rename). `common:loading`/`common:cancel` and `formatDateTime` are pre-existing. Allowlist arithmetic: 11 → 10 (T2) → 9 (T3).
