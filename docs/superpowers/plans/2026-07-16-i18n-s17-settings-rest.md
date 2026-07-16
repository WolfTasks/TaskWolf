# i18n Rollout — Session 17 (`settings` namespace — rest) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Localize the S17 settings-rest slice — `AccessTokensPage`, `ApiKeysPage`, `IntegrationsPage`, `WebhooksPage` — by **extending the existing `settings` namespace** (`en`/`de`) with new `shared`/`tokens`/`apiKeys`/`integrations`/`webhooks` groups, including DataTable columns, the secret-reveal blocks, and date columns via `format.ts`, then flip S17 to ✅ in the master-spec coverage matrix.

**Architecture:** Thin execution plan against the locked pattern in `docs/superpowers/specs/2026-07-13-i18n-full-rollout-design.md` (Anhang — Migrations-Checkliste). Unlike S1–S16, this slice **reuses the existing `settings` namespace** (matrix: "erweitert bestehenden settings-NS"), so there is **no new namespace file and no `index.ts` change** — only added keys in `en/settings.json` + `de/settings.json`. Repeated copy across the four pages (Copy/Copied/Dismiss/Never/Create/Creating/Revoke, table headers, "{{count}} days") lives in a shared `settings:shared` group.

**Tech Stack:** react-i18next (`useTranslation`), `frontend/src/i18n/format.ts` (`formatDate`, `formatDateTime`), the Session-0 scanner/parity scripts, Node 20, Vite/tsc build.

## Global Constraints

- **Namespace = `settings` (existing).** In each file use `const { t } = useTranslation('settings')`; reference shared terms with the `common:` prefix (`t('common:loading')`, `t('common:cancel')`).
- **en/de stay key-identical** (parity check is authoritative). Add the new groups to **both** `en/settings.json` and `de/settings.json`; do not remove or reorder existing keys (`language`/`profile`/`security`/`account`/`notifications`).
- **`t`-shadow trap in `AccessTokensPage`.** Its `columns` cells are written `cell: t => …` (row named `t`) and `rowKey={t => t.id}`. When you add the translator `t`, rename every column row parameter to `tok` (and `t.` → `tok.`) and `rowKey={tok => tok.id}`. `ApiKeysPage` uses `k` and `IntegrationsPage`/`WebhooksPage` use `provider`/`wh`/`ev`/`d` — no shadow there.
- **DataTable `columns[].header` + `empty` prop, scope badge, `alert()` strings, "Never" cells are user-facing** (scanner-blind / not-checked, but DoD-required). Localize via `t()`. `columns` are already defined inside each component, so `t` is in scope after the hook is added.
- **Date columns via `format.ts`.** Replace `new Date(x).toLocaleDateString()` with `formatDate(x)` (tokens/apiKeys last-used/expires) and `new Date(x).toLocaleString()` with `formatDateTime(x)` (webhook delivery log) — DoD point 3.
- **Technical enums stay as data.** Webhook event codes `{ev}`/`{d.eventType}`, the `GitHub`/`GitLab` brand names + `GH`/`GL` badges, and all `{…plaintext…}`/`{…url…}`/`{…prefix…}` renders are left untouched. The `⚠`/`●`/`—`/`…` glyphs stay literal markers next to `{t(...)}`.
- **`alert` server `message` is shown when present** (backend #16 scope); only the client fallback is localized.
- **Remove all 4 S17 files from `frontend/scripts/i18n-allowlist.json`.** **Net for S17: 4 files removed (6 → 2).**
- **Done-per-slice (master spec Abschnitt 5):** `npm run test:i18n && npm run lint:i18n && npm run build` all green; manual DE/EN browser check; matrix row flipped to ✅.
- All paths relative to repo root `C:\Users\Admin\IdeaProjects\TaskWolf`. Work in an isolated worktree branched from `origin/main` (which must contain merged S0–S16; the allowlist starts at 6).

---

### Task 1: Extend the `settings` namespace (en + de)

**Files:**
- Modify: `frontend/src/i18n/locales/en/settings.json`
- Modify: `frontend/src/i18n/locales/de/settings.json`

**Interfaces:**
- Produces: `settings:shared.*`, `settings:tokens.*`, `settings:apiKeys.*`, `settings:integrations.*`, `settings:webhooks.*` consumed by Tasks 2–5.

- [ ] **Step 1: Add the new groups to `frontend/src/i18n/locales/en/settings.json`**

Insert these keys inside the top-level object (e.g. after the existing `"notifications": { … }` block — remember to add a comma after the `notifications` closing brace):

```json
  "shared": {
    "never": "Never",
    "copy": "Copy",
    "copied": "Copied!",
    "dismiss": "Dismiss",
    "create": "Create",
    "creating": "Creating…",
    "revoke": "Revoke",
    "days": "{{count}} days",
    "col": {
      "prefix": "Prefix",
      "name": "Name",
      "lastUsed": "Last Used",
      "expires": "Expires"
    }
  },
  "tokens": {
    "title": "Personal Access Tokens",
    "create": "Create Token",
    "newTitle": "New Token",
    "namePlaceholder": "Token name (e.g. My CLI)",
    "scopeLabel": "Scope",
    "expiresLabel": "Expires",
    "empty": "No tokens yet.",
    "copyWarning": "Copy your token now — it will not be shown again.",
    "createFailed": "Failed to create token",
    "revokeFailed": "Failed to revoke token",
    "col": { "scope": "Scope" },
    "scope": {
      "READ_ONLY": "Read-only",
      "READ_WRITE": "Read & Write"
    }
  },
  "apiKeys": {
    "title": "API Keys",
    "create": "Create API Key",
    "newTitle": "New API Key",
    "namePlaceholder": "Key name (e.g. CI Pipeline)",
    "empty": "No API keys yet.",
    "copyWarning": "Copy your key now — it will not be shown again.",
    "createFailed": "Failed to create API key",
    "revokeFailed": "Failed to revoke key"
  },
  "integrations": {
    "title": "Integrations",
    "saveWarning": "Save these values now — the secret will not be shown again.",
    "webhookUrlLabel": "Webhook URL (paste into {{provider}}):",
    "webhookSecretLabel": "Webhook Secret:",
    "active": "Active",
    "remove": "Remove",
    "connect": "Connect",
    "repoUrlLabel": "Repository URL (optional, for display only):",
    "repoUrlPlaceholder": "https://github.com/org/repo",
    "connecting": "Connecting…",
    "generateUrl": "Generate Webhook URL",
    "connectFailed": "Failed to connect integration"
  },
  "webhooks": {
    "title": "Webhooks",
    "add": "Add Webhook",
    "newTitle": "New Webhook",
    "urlPlaceholder": "https://hooks.example.com/payload",
    "eventsLabel": "Events:",
    "empty": "No webhooks configured.",
    "copyWarning": "Copy your webhook secret now — it will not be shown again.",
    "createFailed": "Failed to create webhook",
    "ping": "Ping",
    "log": "Log",
    "hideLog": "Hide Log",
    "delete": "Delete",
    "deliveryLog": "Delivery Log",
    "noDeliveries": "No deliveries yet.",
    "attempt": "attempt {{count}}"
  }
```

- [ ] **Step 2: Add the mirror groups to `frontend/src/i18n/locales/de/settings.json`** (same keys, German copy)

```json
  "shared": {
    "never": "Nie",
    "copy": "Kopieren",
    "copied": "Kopiert!",
    "dismiss": "Schließen",
    "create": "Erstellen",
    "creating": "Wird erstellt…",
    "revoke": "Widerrufen",
    "days": "{{count}} Tage",
    "col": {
      "prefix": "Präfix",
      "name": "Name",
      "lastUsed": "Zuletzt verwendet",
      "expires": "Läuft ab"
    }
  },
  "tokens": {
    "title": "Persönliche Zugriffstokens",
    "create": "Token erstellen",
    "newTitle": "Neues Token",
    "namePlaceholder": "Token-Name (z. B. My CLI)",
    "scopeLabel": "Bereich",
    "expiresLabel": "Läuft ab",
    "empty": "Noch keine Tokens.",
    "copyWarning": "Kopiere dein Token jetzt — es wird nicht erneut angezeigt.",
    "createFailed": "Token konnte nicht erstellt werden",
    "revokeFailed": "Token konnte nicht widerrufen werden",
    "col": { "scope": "Bereich" },
    "scope": {
      "READ_ONLY": "Nur Lesen",
      "READ_WRITE": "Lesen & Schreiben"
    }
  },
  "apiKeys": {
    "title": "API-Schlüssel",
    "create": "API-Schlüssel erstellen",
    "newTitle": "Neuer API-Schlüssel",
    "namePlaceholder": "Schlüsselname (z. B. CI Pipeline)",
    "empty": "Noch keine API-Schlüssel.",
    "copyWarning": "Kopiere deinen Schlüssel jetzt — er wird nicht erneut angezeigt.",
    "createFailed": "API-Schlüssel konnte nicht erstellt werden",
    "revokeFailed": "Schlüssel konnte nicht widerrufen werden"
  },
  "integrations": {
    "title": "Integrationen",
    "saveWarning": "Speichere diese Werte jetzt — das Secret wird nicht erneut angezeigt.",
    "webhookUrlLabel": "Webhook-URL (in {{provider}} einfügen):",
    "webhookSecretLabel": "Webhook-Secret:",
    "active": "Aktiv",
    "remove": "Entfernen",
    "connect": "Verbinden",
    "repoUrlLabel": "Repository-URL (optional, nur zur Anzeige):",
    "repoUrlPlaceholder": "https://github.com/org/repo",
    "connecting": "Wird verbunden…",
    "generateUrl": "Webhook-URL generieren",
    "connectFailed": "Integration konnte nicht verbunden werden"
  },
  "webhooks": {
    "title": "Webhooks",
    "add": "Webhook hinzufügen",
    "newTitle": "Neuer Webhook",
    "urlPlaceholder": "https://hooks.example.com/payload",
    "eventsLabel": "Ereignisse:",
    "empty": "Keine Webhooks konfiguriert.",
    "copyWarning": "Kopiere dein Webhook-Secret jetzt — es wird nicht erneut angezeigt.",
    "createFailed": "Webhook konnte nicht erstellt werden",
    "ping": "Ping",
    "log": "Log",
    "hideLog": "Log ausblenden",
    "delete": "Löschen",
    "deliveryLog": "Zustellungs-Log",
    "noDeliveries": "Noch keine Zustellungen.",
    "attempt": "Versuch {{count}}"
  }
```

- [ ] **Step 3: Verify parity + build** (no component change yet — all 4 files still allowlisted, scanner stays green)

Run: `cd frontend && npm run check:i18n && npm run build`
Expected: `i18n-parity: OK — en/de namespaces and keys match.` then a successful build. (No `index.ts` change — `settings` is already registered.)

- [ ] **Step 4: Commit**

```bash
git add frontend/src/i18n/locales/en/settings.json frontend/src/i18n/locales/de/settings.json
git commit -m "feat(i18n): extend settings namespace for tokens/api-keys/integrations/webhooks (en/de)"
```

---

### Task 2: Localize AccessTokensPage

**Files:**
- Modify: `frontend/src/pages/settings/AccessTokensPage.tsx`
- Modify: `frontend/scripts/i18n-allowlist.json`

**Interfaces:**
- Consumes: `settings:shared.*`, `settings:tokens.*` (Task 1); `common:loading`, `common:cancel` (existing); `formatDate` from `@/i18n/format` (existing).

- [ ] **Step 1: Add imports**

Add after `import { DataTable, type Column } from '@/components/table/DataTable'` (line 6):

```ts
import { useTranslation } from 'react-i18next'
import { formatDate } from '@/i18n/format'
```

- [ ] **Step 2: Add the hook + localize the create alert**

Add as the first line of the `AccessTokensPage` body (before `const { data: tokens = [], isLoading } = useAccessTokens()`):

```ts
  const { t } = useTranslation('settings')
```

Replace:

```tsx
      alert(e.response?.data?.message || 'Failed to create token')
```
→ `alert(e.response?.data?.message || t('tokens.createFailed'))`

- [ ] **Step 3: Localize the columns (rename the shadowing `t` row param → `tok`)**

Replace the whole `columns` array:

```tsx
  const columns: Column<(typeof tokens)[number]>[] = [
    { key: 'prefix', header: 'Prefix', width: '140px', cell: t => <code className="text-green-400">{t.tokenPrefix}…</code> },
    { key: 'name', header: 'Name', cell: t => t.name },
    {
      key: 'scope',
      header: 'Scope',
      width: '140px',
      cell: t => (
        <span className={`px-2 py-0.5 rounded text-xs ${
          t.scope === 'READ_ONLY' ? 'bg-gray-700 text-gray-300' : 'bg-indigo-900/50 text-indigo-300'
        }`}>
          {t.scope === 'READ_ONLY' ? 'Read-only' : 'Read & Write'}
        </span>
      ),
    },
    { key: 'lastUsed', header: 'Last Used', width: '120px', cell: t => <span className="text-gray-400">{t.lastUsedAt ? new Date(t.lastUsedAt).toLocaleDateString() : 'Never'}</span> },
    { key: 'expires', header: 'Expires', width: '120px', cell: t => <span className="text-gray-400">{t.expiresAt ? new Date(t.expiresAt).toLocaleDateString() : 'Never'}</span> },
    {
      key: 'actions',
      header: '',
      width: '100px',
      align: 'right',
      cell: t => (
        <button
          onClick={() => revokeToken.mutate(t.id, {
            onError: (e: any) => alert(e.response?.data?.message || 'Failed to revoke token'),
          })}
          className="px-3 py-1 bg-red-900/40 hover:bg-red-800 text-red-400 hover:text-red-300 rounded text-xs"
        >
          Revoke
        </button>
      ),
    },
  ]
```

with:

```tsx
  const columns: Column<(typeof tokens)[number]>[] = [
    { key: 'prefix', header: t('shared.col.prefix'), width: '140px', cell: tok => <code className="text-green-400">{tok.tokenPrefix}…</code> },
    { key: 'name', header: t('shared.col.name'), cell: tok => tok.name },
    {
      key: 'scope',
      header: t('tokens.col.scope'),
      width: '140px',
      cell: tok => (
        <span className={`px-2 py-0.5 rounded text-xs ${
          tok.scope === 'READ_ONLY' ? 'bg-gray-700 text-gray-300' : 'bg-indigo-900/50 text-indigo-300'
        }`}>
          {tok.scope === 'READ_ONLY' ? t('tokens.scope.READ_ONLY') : t('tokens.scope.READ_WRITE')}
        </span>
      ),
    },
    { key: 'lastUsed', header: t('shared.col.lastUsed'), width: '120px', cell: tok => <span className="text-gray-400">{tok.lastUsedAt ? formatDate(tok.lastUsedAt) : t('shared.never')}</span> },
    { key: 'expires', header: t('shared.col.expires'), width: '120px', cell: tok => <span className="text-gray-400">{tok.expiresAt ? formatDate(tok.expiresAt) : t('shared.never')}</span> },
    {
      key: 'actions',
      header: '',
      width: '100px',
      align: 'right',
      cell: tok => (
        <button
          onClick={() => revokeToken.mutate(tok.id, {
            onError: (e: any) => alert(e.response?.data?.message || t('tokens.revokeFailed')),
          })}
          className="px-3 py-1 bg-red-900/40 hover:bg-red-800 text-red-400 hover:text-red-300 rounded text-xs"
        >
          {t('shared.revoke')}
        </button>
      ),
    },
  ]
```

- [ ] **Step 4: Localize loading, header, secret block, create form, table empty**

Replace:

```tsx
  if (isLoading) return <div className="text-gray-400">Loading…</div>
```
→ `if (isLoading) return <div className="text-gray-400">{t('common:loading')}</div>`

```tsx
        <h1 className="text-2xl font-bold">Personal Access Tokens</h1>
```
→ `<h1 className="text-2xl font-bold">{t('tokens.title')}</h1>`

```tsx
          Create Token
        </button>
```
→ `{t('tokens.create')}`

```tsx
            ⚠ Copy your token now — it will not be shown again.
```
→ `⚠ {t('tokens.copyWarning')}`

```tsx
              {copied ? 'Copied!' : 'Copy'}
```
→ `{copied ? t('shared.copied') : t('shared.copy')}`

```tsx
            Dismiss
          </button>
```
→ `{t('shared.dismiss')}`

```tsx
          <h2 className="text-sm font-semibold">New Token</h2>
          <input
            type="text"
            placeholder="Token name (e.g. My CLI)"
```

with:

```tsx
          <h2 className="text-sm font-semibold">{t('tokens.newTitle')}</h2>
          <input
            type="text"
            placeholder={t('tokens.namePlaceholder')}
```

```tsx
          <label className="text-sm text-gray-300">
            Scope
```
→ (keep the `<label>` markup) `{t('tokens.scopeLabel')}` in place of the `Scope` text

```tsx
              <option value="READ_WRITE">Read &amp; Write</option>
              <option value="READ_ONLY">Read-only</option>
```

with:

```tsx
              <option value="READ_WRITE">{t('tokens.scope.READ_WRITE')}</option>
              <option value="READ_ONLY">{t('tokens.scope.READ_ONLY')}</option>
```

```tsx
          <label className="text-sm text-gray-300">
            Expires
```
→ `{t('tokens.expiresLabel')}` in place of the `Expires` text

```tsx
              <option value="">Never</option>
              <option value="30">30 days</option>
              <option value="60">60 days</option>
              <option value="90">90 days</option>
```

with:

```tsx
              <option value="">{t('shared.never')}</option>
              <option value="30">{t('shared.days', { count: 30 })}</option>
              <option value="60">{t('shared.days', { count: 60 })}</option>
              <option value="90">{t('shared.days', { count: 90 })}</option>
```

```tsx
              {createToken.isPending ? 'Creating…' : 'Create'}
```
→ `{createToken.isPending ? t('shared.creating') : t('shared.create')}`

```tsx
              Cancel
            </button>
```
→ `{t('common:cancel')}`

```tsx
        empty="No tokens yet."
```
→ `empty={t('tokens.empty')}`

And update `rowKey={t => t.id}` → `rowKey={tok => tok.id}`.

- [ ] **Step 5: Remove AccessTokensPage from the allowlist**

In `frontend/scripts/i18n-allowlist.json`, delete this line:

```json
  "src/pages/settings/AccessTokensPage.tsx",
```

- [ ] **Step 6: Verify scanner + build**

Run: `cd frontend && npm run scan:i18n && npm run build`
Expected: `i18n-scan: OK — 0 hardcoded strings outside the allowlist (5 file(s) still allowlisted).` and a successful build. Confirm tsc did not error on a shadowed `t` (Step 3 rename to `tok`).

- [ ] **Step 7: Commit**

```bash
git add frontend/src/pages/settings/AccessTokensPage.tsx frontend/scripts/i18n-allowlist.json
git commit -m "feat(i18n): localize personal access tokens page"
```

---

### Task 3: Localize ApiKeysPage

**Files:**
- Modify: `frontend/src/pages/settings/ApiKeysPage.tsx`
- Modify: `frontend/scripts/i18n-allowlist.json`

**Interfaces:**
- Consumes: `settings:shared.*`, `settings:apiKeys.*` (Task 1); `common:loading`, `common:cancel` (existing); `formatDate` from `@/i18n/format` (existing).

- [ ] **Step 1: Add imports + hook**

Add after `import { DataTable, type Column } from '@/components/table/DataTable'` (line 5):

```ts
import { useTranslation } from 'react-i18next'
import { formatDate } from '@/i18n/format'
```

Add as the first line of the `ApiKeysPage` body (before `const { key } = useParams…`):

```ts
  const { t } = useTranslation('settings')
```

- [ ] **Step 2: Localize the create alert**

```tsx
      alert(e.response?.data?.message || 'Failed to create API key')
```
→ `alert(e.response?.data?.message || t('apiKeys.createFailed'))`

- [ ] **Step 3: Localize the columns** (row param is `k` — no shadow)

Replace:

```tsx
    { key: 'prefix', header: 'Prefix', width: '140px', cell: k => <code className="text-green-400">{k.keyPrefix}…</code> },
    { key: 'name', header: 'Name', cell: k => k.name },
    { key: 'lastUsed', header: 'Last Used', width: '120px', cell: k => <span className="text-gray-400">{k.lastUsedAt ? new Date(k.lastUsedAt).toLocaleDateString() : 'Never'}</span> },
    { key: 'expires', header: 'Expires', width: '120px', cell: k => <span className="text-gray-400">{k.expiresAt ? new Date(k.expiresAt).toLocaleDateString() : 'Never'}</span> },
```

with:

```tsx
    { key: 'prefix', header: t('shared.col.prefix'), width: '140px', cell: k => <code className="text-green-400">{k.keyPrefix}…</code> },
    { key: 'name', header: t('shared.col.name'), cell: k => k.name },
    { key: 'lastUsed', header: t('shared.col.lastUsed'), width: '120px', cell: k => <span className="text-gray-400">{k.lastUsedAt ? formatDate(k.lastUsedAt) : t('shared.never')}</span> },
    { key: 'expires', header: t('shared.col.expires'), width: '120px', cell: k => <span className="text-gray-400">{k.expiresAt ? formatDate(k.expiresAt) : t('shared.never')}</span> },
```

Replace the revoke button:

```tsx
            onError: (e: any) => alert(e.response?.data?.message || 'Failed to revoke key'),
```
→ `onError: (e: any) => alert(e.response?.data?.message || t('apiKeys.revokeFailed')),`

```tsx
          Revoke
        </button>
```
→ `{t('shared.revoke')}`

- [ ] **Step 4: Localize loading, header, secret block, create form, empty**

```tsx
  if (isLoading) return <div className="text-gray-400">Loading…</div>
```
→ `{t('common:loading')}`

```tsx
        <h1 className="text-2xl font-bold">API Keys</h1>
```
→ `{t('apiKeys.title')}`

```tsx
          Create API Key
        </button>
```
→ `{t('apiKeys.create')}`

```tsx
            ⚠ Copy your key now — it will not be shown again.
```
→ `⚠ {t('apiKeys.copyWarning')}`

```tsx
              {copied ? 'Copied!' : 'Copy'}
```
→ `{copied ? t('shared.copied') : t('shared.copy')}`

```tsx
            Dismiss
          </button>
```
→ `{t('shared.dismiss')}`

```tsx
          <h2 className="text-sm font-semibold mb-3">New API Key</h2>
          <input
            type="text"
            placeholder="Key name (e.g. CI Pipeline)"
```

with:

```tsx
          <h2 className="text-sm font-semibold mb-3">{t('apiKeys.newTitle')}</h2>
          <input
            type="text"
            placeholder={t('apiKeys.namePlaceholder')}
```

```tsx
              {createKey.isPending ? 'Creating…' : 'Create'}
```
→ `{createKey.isPending ? t('shared.creating') : t('shared.create')}`

```tsx
              Cancel
            </button>
```
→ `{t('common:cancel')}`

```tsx
        empty="No API keys yet."
```
→ `empty={t('apiKeys.empty')}`

- [ ] **Step 5: Remove ApiKeysPage from the allowlist**

In `frontend/scripts/i18n-allowlist.json`, delete this line:

```json
  "src/pages/settings/ApiKeysPage.tsx",
```

- [ ] **Step 6: Verify scanner + build**

Run: `cd frontend && npm run scan:i18n && npm run build`
Expected: `i18n-scan: OK — 0 hardcoded strings outside the allowlist (4 file(s) still allowlisted).` and a successful build.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/pages/settings/ApiKeysPage.tsx frontend/scripts/i18n-allowlist.json
git commit -m "feat(i18n): localize project API keys page"
```

---

### Task 4: Localize IntegrationsPage

**Files:**
- Modify: `frontend/src/pages/settings/IntegrationsPage.tsx`
- Modify: `frontend/scripts/i18n-allowlist.json`

**Interfaces:**
- Consumes: `settings:shared.*`, `settings:integrations.*` (Task 1); `common:loading`, `common:cancel` (existing).

- [ ] **Step 1: Add the hook import + hook**

Add after `import type { CreateIntegrationResponse } from '@/hooks/useProjectIntegrations'` (line 6):

```ts
import { useTranslation } from 'react-i18next'
```

Add as the first line of the `IntegrationsPage` body (before `const { key } = useParams…`):

```ts
  const { t } = useTranslation('settings')
```

- [ ] **Step 2: Localize the connect alert + loading + heading**

```tsx
      alert(e.response?.data?.message || 'Failed to connect integration')
```
→ `alert(e.response?.data?.message || t('integrations.connectFailed'))`

```tsx
  if (isLoading) return <div className="text-gray-400">Loading…</div>
```
→ `{t('common:loading')}`

```tsx
      <h1 className="text-2xl font-bold mb-6">Integrations</h1>
```
→ `{t('integrations.title')}`

- [ ] **Step 3: Localize the secret-reveal block**

```tsx
            ⚠ Save these values now — the secret will not be shown again.
```
→ `⚠ {t('integrations.saveWarning')}`

```tsx
              <p className="text-xs text-gray-400 mb-1">Webhook URL (paste into {newIntegration.provider}):</p>
```
→ `<p className="text-xs text-gray-400 mb-1">{t('integrations.webhookUrlLabel', { provider: newIntegration.provider })}</p>`

```tsx
                  {copiedUrl ? 'Copied!' : 'Copy'}
```
→ `{copiedUrl ? t('shared.copied') : t('shared.copy')}`

```tsx
              <p className="text-xs text-gray-400 mb-1">Webhook Secret:</p>
```
→ `<p className="text-xs text-gray-400 mb-1">{t('integrations.webhookSecretLabel')}</p>`

```tsx
                  {copiedSecret ? 'Copied!' : 'Copy'}
```
→ `{copiedSecret ? t('shared.copied') : t('shared.copy')}`

```tsx
          <button onClick={() => setNewIntegration(null)} className="mt-3 text-xs text-gray-400 hover:text-white">
            Dismiss
          </button>
```
→ `{t('shared.dismiss')}` (inside the same `<button>`)

- [ ] **Step 4: Localize the provider cards**

(Leave `{provider === 'GITHUB' ? 'GH' : 'GL'}` badge and `{provider === 'GITHUB' ? 'GitHub' : 'GitLab'}` brand name — proper nouns.)

```tsx
                      <span className="text-xs text-green-400 font-medium">● Active</span>
```
→ `<span className="text-xs text-green-400 font-medium">● {t('integrations.active')}</span>`

```tsx
                        Remove
                      </button>
```
→ `{t('integrations.remove')}`

```tsx
                      Connect
                    </button>
```
→ `{t('integrations.connect')}`

```tsx
                  <p className="text-xs text-gray-400 mb-2">
                    Repository URL (optional, for display only):
                  </p>
                  <input
                    type="text"
                    placeholder="https://github.com/org/repo"
```

with:

```tsx
                  <p className="text-xs text-gray-400 mb-2">
                    {t('integrations.repoUrlLabel')}
                  </p>
                  <input
                    type="text"
                    placeholder={t('integrations.repoUrlPlaceholder')}
```

```tsx
                      {createIntegration.isPending ? 'Connecting…' : 'Generate Webhook URL'}
```
→ `{createIntegration.isPending ? t('integrations.connecting') : t('integrations.generateUrl')}`

```tsx
                      Cancel
                    </button>
```
→ `{t('common:cancel')}`

- [ ] **Step 5: Remove IntegrationsPage from the allowlist**

In `frontend/scripts/i18n-allowlist.json`, delete this line:

```json
  "src/pages/settings/IntegrationsPage.tsx",
```

- [ ] **Step 6: Verify scanner + build**

Run: `cd frontend && npm run scan:i18n && npm run build`
Expected: `i18n-scan: OK — 0 hardcoded strings outside the allowlist (3 file(s) still allowlisted).` and a successful build.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/pages/settings/IntegrationsPage.tsx frontend/scripts/i18n-allowlist.json
git commit -m "feat(i18n): localize integrations page"
```

---

### Task 5: Localize WebhooksPage

**Files:**
- Modify: `frontend/src/pages/settings/WebhooksPage.tsx`
- Modify: `frontend/scripts/i18n-allowlist.json`

**Interfaces:**
- Consumes: `settings:shared.*`, `settings:webhooks.*` (Task 1); `common:loading`, `common:cancel` (existing); `formatDateTime` from `@/i18n/format` (existing).

- [ ] **Step 1: Add imports + hook**

Add after `import type { CreateWebhookResult } from '@/hooks/useWebhooks'` (line 7):

```ts
import { useTranslation } from 'react-i18next'
import { formatDateTime } from '@/i18n/format'
```

Add as the first line of the `WebhooksPage` body (before `const { key } = useParams…`):

```ts
  const { t } = useTranslation('settings')
```

- [ ] **Step 2: Localize the create alert + loading + header**

```tsx
      alert(e.response?.data?.message || 'Failed to create webhook')
```
→ `alert(e.response?.data?.message || t('webhooks.createFailed'))`

```tsx
  if (isLoading) return <div className="text-gray-400">Loading…</div>
```
→ `{t('common:loading')}`

```tsx
        <h1 className="text-2xl font-bold">Webhooks</h1>
```
→ `{t('webhooks.title')}`

```tsx
          Add Webhook
        </button>
```
→ `{t('webhooks.add')}`

- [ ] **Step 3: Localize the secret block + create form**

```tsx
            ⚠ Copy your webhook secret now — it will not be shown again.
```
→ `⚠ {t('webhooks.copyWarning')}`

```tsx
              {copiedSecret ? 'Copied!' : 'Copy'}
```
→ `{copiedSecret ? t('shared.copied') : t('shared.copy')}`

```tsx
          <button onClick={() => setNewSecret(null)} className="mt-2 text-xs text-gray-400 hover:text-white">Dismiss</button>
```
→ `{t('shared.dismiss')}` (inside the same `<button>`)

```tsx
          <h2 className="text-sm font-semibold mb-3">New Webhook</h2>
          <input type="text" placeholder="https://hooks.example.com/payload"
```

with:

```tsx
          <h2 className="text-sm font-semibold mb-3">{t('webhooks.newTitle')}</h2>
          <input type="text" placeholder={t('webhooks.urlPlaceholder')}
```

```tsx
          <p className="text-xs text-gray-400 mb-2">Events:</p>
```
→ `<p className="text-xs text-gray-400 mb-2">{t('webhooks.eventsLabel')}</p>`

(Leave the `{ev}` event-code checkbox labels — technical enum data.)

```tsx
              {createWebhook.isPending ? 'Creating…' : 'Create'}
```
→ `{createWebhook.isPending ? t('shared.creating') : t('shared.create')}`

```tsx
              className="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded text-sm">Cancel</button>
```
→ `…rounded text-sm">{t('common:cancel')}</button>`

- [ ] **Step 4: Localize the webhook list + delivery log**

(Leave `{wh.url}`, the `{ev}` event chips, `{d.responseStatus ?? '—'}`, `{d.eventType}` — data.)

```tsx
                  className="px-3 py-1 bg-gray-700 hover:bg-gray-600 rounded text-xs">Ping</button>
```
→ `…rounded text-xs">{t('webhooks.ping')}</button>`

```tsx
                  {selectedWebhookId === wh.id ? 'Hide Log' : 'Log'}
```
→ `{selectedWebhookId === wh.id ? t('webhooks.hideLog') : t('webhooks.log')}`

```tsx
                  className="px-3 py-1 bg-red-900/40 hover:bg-red-800 text-red-400 rounded text-xs">Delete</button>
```
→ `…rounded text-xs">{t('webhooks.delete')}</button>`

```tsx
                <p className="text-xs text-gray-400 mb-2">Delivery Log</p>
                {deliveries.length === 0 ? (
                  <p className="text-xs text-gray-500">No deliveries yet.</p>
```

with:

```tsx
                <p className="text-xs text-gray-400 mb-2">{t('webhooks.deliveryLog')}</p>
                {deliveries.length === 0 ? (
                  <p className="text-xs text-gray-500">{t('webhooks.noDeliveries')}</p>
```

```tsx
                        <span className="text-gray-500">
                          {d.createdAt ? new Date(d.createdAt).toLocaleString() : ''}
                        </span>
                        <span className="text-gray-500">attempt {d.attemptCount}</span>
```

with:

```tsx
                        <span className="text-gray-500">
                          {d.createdAt ? formatDateTime(d.createdAt) : ''}
                        </span>
                        <span className="text-gray-500">{t('webhooks.attempt', { count: d.attemptCount })}</span>
```

```tsx
        {webhooks.length === 0 && <p className="text-gray-400 text-sm">No webhooks configured.</p>}
```
→ `{webhooks.length === 0 && <p className="text-gray-400 text-sm">{t('webhooks.empty')}</p>}`

- [ ] **Step 5: Remove WebhooksPage from the allowlist**

In `frontend/scripts/i18n-allowlist.json`, delete this line:

```json
  "src/pages/settings/WebhooksPage.tsx",
```

- [ ] **Step 6: Verify scanner + build**

Run: `cd frontend && npm run scan:i18n && npm run build`
Expected: `i18n-scan: OK — 0 hardcoded strings outside the allowlist (2 file(s) still allowlisted).` and a successful build.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/pages/settings/WebhooksPage.tsx frontend/scripts/i18n-allowlist.json
git commit -m "feat(i18n): localize webhooks page"
```

---

### Task 6: Finalize S17 — full gate + flip coverage matrix

**Files:**
- Modify: `docs/superpowers/specs/2026-07-13-i18n-full-rollout-design.md`

- [ ] **Step 1: Run the full slice gate**

Run: `cd frontend && npm run test:i18n && npm run lint:i18n && npm run build`
Expected: scanner self-tests pass, `i18n-scan: OK — 0 hardcoded strings outside the allowlist (2 file(s) still allowlisted).`, `i18n-parity: OK — en/de namespaces and keys match.`, and a successful build. All 4 S17 files must be absent from the allowlist (6 baseline − 4 = 2 entries — the remaining two are the S18 cleanup targets `VersionTag`, `AuthLayout`).

- [ ] **Step 2: Manual DE/EN browser check**

Start the dev server (`cd frontend && npm run dev`), switch language via Settings → Profile, and confirm: Personal Access Tokens (table headers, scope badge, Never cells, revoke, create form incl. scope + expiry dropdowns, secret reveal); API Keys (headers, create form, secret reveal); Integrations (provider cards, connect flow, secret reveal with `{{provider}}` interpolation); Webhooks (create form, event checkboxes as codes, per-hook Ping/Log/Delete, delivery log with localized timestamps + "attempt N") all switch between English and German with no raw keys.

- [ ] **Step 3: Flip S17 to ✅ in the coverage matrix**

In `docs/superpowers/specs/2026-07-13-i18n-full-rollout-design.md`, replace the S17 matrix row:

```
| S17 | `settings` (Rest) | ⬜ | AccessTokensPage, ApiKeysPage, IntegrationsPage, WebhooksPage (erweitert bestehenden `settings`-NS) |
```

with:

```
| S17 | `settings` (Rest) | ✅ | All 4 files localized by extending the existing `settings` ns (shared/tokens/apiKeys/integrations/webhooks groups; no new ns); DataTable headers + secret-reveal blocks + `{{provider}}`/`{{count}}` interpolation localized; dates via `formatDate`/`formatDateTime`; AccessTokens `cell: t`→`tok` shadow handled; event codes left as data |
```

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/specs/2026-07-13-i18n-full-rollout-design.md
git commit -m "docs(i18n): mark S17 settings-rest slice complete in coverage matrix"
```

---

## Self-Review

**Spec coverage:**
- Existing `settings` namespace extended (en+de) — no new ns, no `index.ts` change → Task 1. ✅
- Every user-facing string across all 4 files via `t()` → Tasks 2–5. ✅
- DataTable headers/`empty`/scope badge/`alert()`/Never localized → Tasks 2–5. ✅
- Interpolation via variables (`shared.days` {{count}}, `integrations.webhookUrlLabel` {{provider}}, `webhooks.attempt` {{count}}) — no fragment concat. ✅
- Dates via `formatDate` (tokens/apiKeys) + `formatDateTime` (webhooks) → Tasks 2, 3, 5. ✅
- `AccessTokensPage` `t`-shadow handled (row param → `tok`, `rowKey`) → Task 2. ✅
- Technical enums (event codes, GitHub/GitLab brand, prefixes) + server `message` left as data/backend-scope → Global Constraints. ✅
- Shared repeated copy consolidated in `settings:shared` (DRY). ✅
- All 4 files removed from allowlist (6 → 2) → Tasks 2–5. ✅
- Full gate + manual DE/EN + matrix flip → Task 6. ✅

**Placeholder scan:** No TBD/TODO; every step has exact old/new code and exact commands with expected output.

**Type/key consistency:** All keys used in Tasks 2–5 are defined in Task 1's en+de additions (`shared.*`+`shared.col.*`, `tokens.*`+`tokens.scope.*`+`tokens.col.scope`, `apiKeys.*`, `integrations.*`, `webhooks.*`). `common:loading`/`common:cancel` and `formatDate`/`formatDateTime` are pre-existing. Allowlist arithmetic: 6 → 5 (T2) → 4 (T3) → 3 (T4) → 2 (T5). Remaining 2 entries (`VersionTag`, `AuthLayout`) are the S18 final-sweep targets.
