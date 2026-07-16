# i18n Rollout — Session 9 (`notifications` namespace) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Localize the S9 `notifications` slice — the header `NotificationBell` and the `NotificationsPage` list — into a new `notifications` namespace (`en`/`de`), including the scanner-blind `typeLabel` switch and replacing the hand-rolled `formatTime` with `formatRelativeTime` (the S3 relative-time rollout precedent), then flip S9 to ✅ in the master-spec coverage matrix.

**Architecture:** Thin execution plan against the locked pattern in `docs/superpowers/specs/2026-07-13-i18n-full-rollout-design.md` (Anhang — Migrations-Checkliste). One new `notifications` namespace. The `typeLabel` helper (a `switch` returning string literals, scanner-blind but user-facing) is deleted and replaced by keyed `t()` at the render site (S2 enum-label precedent). Timestamps move to `format.ts` per DoD point 3.

**Tech Stack:** react-i18next (`useTranslation`), `frontend/src/i18n/format.ts` (`formatRelativeTime`), the Session-0 scanner/parity scripts, Node 20, Vite/tsc build.

## Global Constraints

- **Namespace = `notifications`.** In both files use `const { t } = useTranslation('notifications')`; reference shared terms with the `common:` prefix (`t('common:loading')`).
- **en/de stay key-identical** (parity check is authoritative).
- **`typeLabel` is DoD-required (scanner-blind).** It returns `'💬 Mention'`/`'📋 Assigned'` from a `switch` — user-facing but not flagged by the scanner. Delete it and render `t(\`type.${n.type}\`, { defaultValue: n.type })`. The emoji stays inside the translation value (part of the label), not concatenated in JSX.
- **Replace the hand-rolled `formatTime` with `formatRelativeTime`** from `@/i18n/format` (DoD point 3 — no raw `toLocaleDateString`/`toLocaleTimeString`). Delete the local `formatTime` function.
- **The `99+` cap in `NotificationBell` stays literal** — it is a numeric overflow marker, not translatable text. The `{count}` / `{n.title}` / `{n.body}` renders are data.
- **Remove both S9 files from `frontend/scripts/i18n-allowlist.json`.** **Net for S9: 2 files removed (34 → 32).**
- **Done-per-slice (master spec Abschnitt 5):** `npm run test:i18n && npm run lint:i18n && npm run build` all green; manual DE/EN browser check; matrix row flipped to ✅.
- All paths relative to repo root `C:\Users\Admin\IdeaProjects\TaskWolf`. Work in an isolated worktree branched from `origin/main` (which must contain merged S0–S8; the allowlist starts at 34).

---

### Task 1: Create the `notifications` namespace + register it

**Files:**
- Create: `frontend/src/i18n/locales/en/notifications.json`
- Create: `frontend/src/i18n/locales/de/notifications.json`
- Modify: `frontend/src/i18n/index.ts`

**Interfaces:**
- Produces: the `notifications` namespace keys consumed by Task 2.

- [ ] **Step 1: Create `frontend/src/i18n/locales/en/notifications.json`**

```json
{
  "title": "Notifications",
  "empty": "No notifications yet",
  "bell": {
    "aria": "Notifications"
  },
  "type": {
    "COMMENT_MENTION": "💬 Mention",
    "ISSUE_ASSIGNED": "📋 Assigned"
  }
}
```

- [ ] **Step 2: Create `frontend/src/i18n/locales/de/notifications.json`** (same keys, German copy)

```json
{
  "title": "Benachrichtigungen",
  "empty": "Noch keine Benachrichtigungen",
  "bell": {
    "aria": "Benachrichtigungen"
  },
  "type": {
    "COMMENT_MENTION": "💬 Erwähnung",
    "ISSUE_ASSIGNED": "📋 Zugewiesen"
  }
}
```

- [ ] **Step 3: Register the namespace in `frontend/src/i18n/index.ts`**

Add the imports after the last existing locale import:

```ts
import enNotifications from './locales/en/notifications.json'
import deNotifications from './locales/de/notifications.json'
```

In `resources`, append `notifications` to both languages:

```ts
      en: { /* …existing… */ notifications: enNotifications },
      de: { /* …existing… */ notifications: deNotifications },
```

And append `'notifications'` to the `ns` array:

```ts
    ns: [/* …existing… */ 'notifications'],
```

- [ ] **Step 4: Verify parity + build** (no component change yet — both files still allowlisted, scanner stays green)

Run: `cd frontend && npm run check:i18n && npm run build`
Expected: `i18n-parity: OK — en/de namespaces and keys match.` then a successful build.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/i18n/locales/en/notifications.json frontend/src/i18n/locales/de/notifications.json frontend/src/i18n/index.ts
git commit -m "feat(i18n): add notifications namespace (en/de)"
```

---

### Task 2: Localize NotificationBell + NotificationsPage

**Files:**
- Modify: `frontend/src/components/notifications/NotificationBell.tsx`
- Modify: `frontend/src/pages/notifications/NotificationsPage.tsx`
- Modify: `frontend/scripts/i18n-allowlist.json`

**Interfaces:**
- Consumes: `notifications:title`, `notifications:empty`, `notifications:bell.aria`, `notifications:type.*` (Task 1); `common:loading` (existing); `formatRelativeTime` from `@/i18n/format` (existing).

- [ ] **Step 1: `NotificationBell.tsx` — hook + aria-label**

Add after `import { useUnreadCount } from '@/hooks/useNotifications'` (line 2):

```ts
import { useTranslation } from 'react-i18next'
```

Add as the first line of the `NotificationBell` body (before `const navigate = useNavigate()`):

```ts
  const { t } = useTranslation('notifications')
```

Replace the aria-label:

```tsx
      aria-label="Notifications"
```
→ `aria-label={t('bell.aria')}`

(Leave `{count > 99 ? '99+' : count}` — numeric marker + data.)

- [ ] **Step 2: `NotificationsPage.tsx` — swap imports (add hook + relative time, drop local helpers)**

Replace the top imports + the two local helper functions:

```tsx
import { useNotifications, useMarkRead } from '@/hooks/useNotifications'
import type { Notification } from '@/types'

function formatTime(iso: string): string {
  const d = new Date(iso)
  return d.toLocaleDateString() + ' ' + d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}

function typeLabel(type: Notification['type']): string {
  switch (type) {
    case 'COMMENT_MENTION': return '💬 Mention'
    case 'ISSUE_ASSIGNED': return '📋 Assigned'
    default: return type
  }
}
```

with:

```tsx
import { useTranslation } from 'react-i18next'
import { useNotifications, useMarkRead } from '@/hooks/useNotifications'
import { formatRelativeTime } from '@/i18n/format'
```

(The `Notification` type import is only used by the deleted `typeLabel` signature — removing it avoids an unused-import build error. If any other reference to `Notification` remains, keep the type import instead.)

- [ ] **Step 3: `NotificationsPage.tsx` — add the hook**

Add as the first line of the `NotificationsPage` body (before `const { data, isLoading } = useNotifications()`):

```ts
  const { t } = useTranslation('notifications')
```

- [ ] **Step 4: `NotificationsPage.tsx` — localize heading, loading, empty**

Replace:

```tsx
      <h1 className="text-2xl font-bold text-white mb-6">Notifications</h1>

      {isLoading && <div className="text-gray-500">Loading...</div>}

      {!isLoading && notifications.length === 0 && (
        <p className="text-gray-500 italic">No notifications yet</p>
      )}
```

with:

```tsx
      <h1 className="text-2xl font-bold text-white mb-6">{t('title')}</h1>

      {isLoading && <div className="text-gray-500">{t('common:loading')}</div>}

      {!isLoading && notifications.length === 0 && (
        <p className="text-gray-500 italic">{t('empty')}</p>
      )}
```

- [ ] **Step 5: `NotificationsPage.tsx` — localize the type label + relative timestamp**

Replace:

```tsx
                  <span className="text-xs text-gray-500">{typeLabel(n.type)}</span>
```
→ `<span className="text-xs text-gray-500">{t(\`type.${n.type}\`, { defaultValue: n.type })}</span>`

Replace:

```tsx
              <span className="text-xs text-gray-600 flex-shrink-0">{formatTime(n.createdAt)}</span>
```
→ `<span className="text-xs text-gray-600 flex-shrink-0">{formatRelativeTime(n.createdAt)}</span>`

(Leave `{n.title}` and `{n.body}` — data.)

- [ ] **Step 6: Remove both files from the allowlist**

In `frontend/scripts/i18n-allowlist.json`, delete these lines:

```json
  "src/components/notifications/NotificationBell.tsx",
  "src/pages/notifications/NotificationsPage.tsx",
```

- [ ] **Step 7: Verify scanner + build**

Run: `cd frontend && npm run scan:i18n && npm run build`
Expected: `i18n-scan: OK — 0 hardcoded strings outside the allowlist (32 file(s) still allowlisted).` and a successful build. If tsc flags an unused `Notification` import, remove it (Step 2 note).

- [ ] **Step 8: Commit**

```bash
git add frontend/src/components/notifications/NotificationBell.tsx frontend/src/pages/notifications/NotificationsPage.tsx frontend/scripts/i18n-allowlist.json
git commit -m "feat(i18n): localize notification bell + notifications page (relative timestamps)"
```

---

### Task 3: Finalize S9 — full gate + flip coverage matrix

**Files:**
- Modify: `docs/superpowers/specs/2026-07-13-i18n-full-rollout-design.md`

- [ ] **Step 1: Run the full slice gate**

Run: `cd frontend && npm run test:i18n && npm run lint:i18n && npm run build`
Expected: scanner self-tests pass, `i18n-scan: OK — 0 hardcoded strings outside the allowlist (32 file(s) still allowlisted).`, `i18n-parity: OK — en/de namespaces and keys match.`, and a successful build. Both S9 files must be absent from the allowlist (34 − 2 = 32 entries).

- [ ] **Step 2: Manual DE/EN browser check**

Start the dev server (`cd frontend && npm run dev`), switch language via Settings → Profile, and confirm: the header bell's tooltip/`aria-label`, the Notifications page heading, the loading + empty states, each notification's type label (Mention/Assigned with emoji), and the relative timestamps ("2 hours ago" / "vor 2 Stunden") switch between English and German with no raw keys.

- [ ] **Step 3: Flip S9 to ✅ in the coverage matrix**

In `docs/superpowers/specs/2026-07-13-i18n-full-rollout-design.md`, replace the S9 matrix row:

```
| S9 | `notifications` | ⬜ | NotificationBell, NotificationsPage |
```

with:

```
| S9 | `notifications` | ✅ | NotificationBell (aria) + NotificationsPage localized (new `notifications` ns); scanner-blind `typeLabel` switch → keyed `t()`; hand-rolled `formatTime` → `formatRelativeTime` (relative-time rollout) |
```

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/specs/2026-07-13-i18n-full-rollout-design.md
git commit -m "docs(i18n): mark S9 notifications slice complete in coverage matrix"
```

---

## Self-Review

**Spec coverage:**
- New `notifications` namespace en+de, registered in `index.ts` → Task 1. ✅
- Every user-facing string in both files via `t()` (bell aria, page title, loading→`common:loading`, empty, type labels) → Task 2. ✅
- Scanner-blind `typeLabel` switch deleted, localized at render site with keyed `t()` → Task 2. ✅
- Hand-rolled `formatTime` replaced with `formatRelativeTime` (DoD point 3) → Task 2. ✅
- `99+` cap + `{count}`/`{n.title}`/`{n.body}` correctly left as markers/data → Global Constraints. ✅
- Both files removed from allowlist (34 → 32) → Task 2. ✅
- Full gate + manual DE/EN + matrix flip → Task 3. ✅

**Placeholder scan:** No TBD/TODO; every step has exact old/new code and exact commands with expected output.

**Type/key consistency:** Keys used in Task 2 (`title`, `empty`, `bell.aria`, `type.COMMENT_MENTION`/`type.ISSUE_ASSIGNED`) are defined in Task 1's en+de JSON. The dynamic `type.${n.type}` covers exactly the two `Notification['type']` members that had non-default labels (the `default` branch is preserved by `{ defaultValue: n.type }`). `common:loading` and `formatRelativeTime` are pre-existing. Allowlist arithmetic: 34 → 32 (Task 2, −2).
