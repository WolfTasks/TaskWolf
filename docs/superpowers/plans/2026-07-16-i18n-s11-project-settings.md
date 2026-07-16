# i18n Rollout — Session 11 (`project-settings` namespace) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Localize the S11 `project-settings` slice — `SettingsLayout`, `MembersPage`, `LabelsPage`, `VersionsPage`, `CustomFieldsPage`, `ProjectAuditPage`, `OrganizationSettingsPage` — into a new `project-settings` namespace (`en`/`de`), including all scanner-blind const-map labels (`ROLE_LABELS`, `FIELD_TYPES`, audit column headers, settings-nav item labels), `confirm()`/`alert()`-style strings, and the audit timestamp via `format.ts`, then flip S11 to ✅ in the master-spec coverage matrix.

**Architecture:** Thin execution plan against the locked pattern in `docs/superpowers/specs/2026-07-13-i18n-full-rollout-design.md` (Anhang — Migrations-Checkliste). One `project-settings` namespace with a shared `form`/`layout` group plus a sub-object per page. Scanner-blind labels held in module-level `const` maps/arrays are localized at their render site (S2 enum-label precedent), and module-level `columns` in `ProjectAuditPage` is moved inside the component so it can use `t`. The members org-ownership banner uses `<Trans>` (embedded bold `<span>`); it is one sentence with a variable, not a fragment concat.

**Tech Stack:** react-i18next (`useTranslation`, `<Trans>`), `frontend/src/i18n/format.ts` (`formatDateTime`), the Session-0 scanner/parity scripts, Node 20, Vite/tsc build.

## Global Constraints

- **Namespace = `project-settings`.** In each file use `const { t } = useTranslation('project-settings')`; reference shared terms with the `common:` prefix (`t('common:loading')`, `t('common:save')`, `t('common:cancel')`). Unprefixed keys resolve against `project-settings`.
- **en/de stay key-identical** (parity check is authoritative).
- **Scanner-blind const-map labels are DoD-required.** `ROLE_LABELS` (MembersPage), `FIELD_TYPES` option render + `{field.type}` badge (CustomFieldsPage), the `columns[].header` + `{e.level}` badge (ProjectAuditPage), and the settings-nav `items[].label` (SettingsLayout) are user-facing English not flagged by the scanner. Localize each at the render site keyed on the raw enum/id; leave the raw enum keys and any `*_COLOR` maps untouched.
- **`confirm()` / error strings are user-facing** — localize them via `t()` (the scanner does not check call arguments, but the DoD does). Interpolate names with variables (`t('members.removeConfirm', { name })`), never string-concat.
- **Audit timestamp via `format.ts`.** Replace `new Date(e.timestamp).toLocaleString()` with `formatDateTime(e.timestamp)` (DoD point 3).
- **`<Trans>` for the members org banner only.** Its bold `<span>` is embedded mid-sentence; use `<Trans ns="project-settings" i18nKey="members.orgBanner" components={{ b: <span className="font-medium" /> }} values={{ org }} />`. Every other string uses plain `t()`.
- **`ProjectAuditPage` is `export default`** — keep that. Move the module-level `columns` array **inside** the component (after the hook) so it can call `t`; keep `LEVEL_COLOR` module-level (classNames only).
- **CustomFieldsPage variable-shadow trap:** the existing `FIELD_TYPES.map(t => …)` shadows the translation `t`. Rename the map parameter to `ft` when you add the hook, so `t(...)` still resolves to the translator.
- **Data renders stay untouched** (`{user.displayName}`, `{u.email}`, `{label.color}`, `{version.name}`, `{field.name}`, `{opt.label}`, `{o.name}`, `{e.action}`, `{e.userEmail}`, server error `message`). The `⠿`/`▾`/`▸`/`✕`/`…`/`+` glyphs stay literal markers next to `{t(...)}`.
- **Remove all 7 S11 files from `frontend/scripts/i18n-allowlist.json`** as they are localized. **Net for S11: 7 files removed (31 → 24).**
- **Done-per-slice (master spec Abschnitt 5):** `npm run test:i18n && npm run lint:i18n && npm run build` all green; manual DE/EN browser check; matrix row flipped to ✅.
- All paths relative to repo root `C:\Users\Admin\IdeaProjects\TaskWolf`. Work in an isolated worktree branched from `origin/main` (which must contain merged S0–S10; the allowlist starts at 31).

---

### Task 1: Create the `project-settings` namespace + register it

**Files:**
- Create: `frontend/src/i18n/locales/en/project-settings.json`
- Create: `frontend/src/i18n/locales/de/project-settings.json`
- Modify: `frontend/src/i18n/index.ts`

**Interfaces:**
- Produces: the `project-settings` namespace keys consumed by Tasks 2–7.

- [ ] **Step 1: Create `frontend/src/i18n/locales/en/project-settings.json`**

```json
{
  "form": {
    "name": "Name",
    "create": "Create",
    "edit": "Edit",
    "delete": "Delete",
    "nameRequired": "Name is required",
    "maxChars": "Max 50 characters"
  },
  "layout": {
    "title": "Settings",
    "profile": "Profile",
    "security": "Security",
    "notifications": "Notifications",
    "tokens": "Access Tokens",
    "account": "Account"
  },
  "members": {
    "title": "Members",
    "noPermission": "You don’t have permission to manage members for this project.",
    "orgBanner": "This project belongs to <b>{{org}}</b>. Its owners and admins are admins here; its members are viewers. Those people have access without appearing in the list below.",
    "orgFallback": "an organization",
    "add": "Add member",
    "searchPlaceholder": "Search by name or email…",
    "addButton": "Add",
    "remove": "Remove",
    "owner": "Owner",
    "you": "You",
    "removeConfirm": "Remove {{name}} from this project?",
    "alreadyMember": "This user is already a member.",
    "addFailed": "Could not add member.",
    "role": {
      "VIEWER": "Read-only",
      "MEMBER": "Read & Write",
      "ADMIN": "Admin"
    }
  },
  "labels": {
    "title": "Labels",
    "new": "New Label",
    "namePlaceholder": "Label name",
    "color": "Color",
    "preview": "Preview",
    "previewName": "Preview",
    "duplicate": "A label with that name already exists.",
    "deleteConfirm": "Delete this label? It will be removed from all issues.",
    "empty": "No labels yet. Create your first one!"
  },
  "versions": {
    "title": "Versions",
    "new": "New Version",
    "namePlaceholder": "e.g. v1.0.0",
    "duplicate": "A version with that name already exists.",
    "deleteConfirm": "Delete this version? It will be removed from all issues.",
    "empty": "No versions yet. Create your first one!"
  },
  "fields": {
    "title": "Custom Fields",
    "new": "New Field",
    "namePlaceholder": "Field name",
    "requiredLabel": "Required",
    "requiredBadge": "required",
    "duplicate": "A field with that name already exists.",
    "deleteConfirm": "Delete this field? All values will be lost.",
    "empty": "No custom fields yet.",
    "showOptions": "Options",
    "hideOptions": "Hide options",
    "optionPlaceholder": "New option label",
    "add": "Add",
    "type": {
      "TEXT": "Text",
      "NUMBER": "Number",
      "DATE": "Date",
      "DROPDOWN": "Dropdown",
      "CHECKBOX": "Checkbox"
    }
  },
  "audit": {
    "title": "Audit Log",
    "filterPlaceholder": "Filter action…",
    "empty": "No audit events",
    "col": {
      "time": "Time",
      "user": "User",
      "action": "Action",
      "level": "Level",
      "resource": "Resource"
    },
    "level": {
      "SECURITY": "Security",
      "WRITE": "Write",
      "ALL": "All"
    }
  },
  "org": {
    "title": "Organization",
    "noPermission": "You don't have permission to manage this project's organization.",
    "current": "Current organization",
    "notAssigned": "Not assigned",
    "assignLabel": "Assign to organization",
    "selectPlaceholder": "Select an organization…",
    "assign": "Assign",
    "removeFromOrg": "Remove from organization",
    "forbidden": "You must be an owner or admin of the target organization to assign this project to it.",
    "updateFailed": "Could not update the organization."
  }
}
```

- [ ] **Step 2: Create `frontend/src/i18n/locales/de/project-settings.json`** (same keys, German copy)

```json
{
  "form": {
    "name": "Name",
    "create": "Erstellen",
    "edit": "Bearbeiten",
    "delete": "Löschen",
    "nameRequired": "Name ist erforderlich",
    "maxChars": "Maximal 50 Zeichen"
  },
  "layout": {
    "title": "Einstellungen",
    "profile": "Profil",
    "security": "Sicherheit",
    "notifications": "Benachrichtigungen",
    "tokens": "Zugriffstokens",
    "account": "Konto"
  },
  "members": {
    "title": "Mitglieder",
    "noPermission": "Du hast keine Berechtigung, die Mitglieder dieses Projekts zu verwalten.",
    "orgBanner": "Dieses Projekt gehört zu <b>{{org}}</b>. Deren Eigentümer und Admins sind hier Admins; deren Mitglieder sind Betrachter. Diese Personen haben Zugriff, ohne in der Liste unten aufzutauchen.",
    "orgFallback": "einer Organisation",
    "add": "Mitglied hinzufügen",
    "searchPlaceholder": "Nach Name oder E-Mail suchen…",
    "addButton": "Hinzufügen",
    "remove": "Entfernen",
    "owner": "Eigentümer",
    "you": "Du",
    "removeConfirm": "{{name}} aus diesem Projekt entfernen?",
    "alreadyMember": "Dieser Benutzer ist bereits Mitglied.",
    "addFailed": "Mitglied konnte nicht hinzugefügt werden.",
    "role": {
      "VIEWER": "Nur Lesen",
      "MEMBER": "Lesen & Schreiben",
      "ADMIN": "Admin"
    }
  },
  "labels": {
    "title": "Labels",
    "new": "Neues Label",
    "namePlaceholder": "Label-Name",
    "color": "Farbe",
    "preview": "Vorschau",
    "previewName": "Vorschau",
    "duplicate": "Ein Label mit diesem Namen existiert bereits.",
    "deleteConfirm": "Dieses Label löschen? Es wird von allen Vorgängen entfernt.",
    "empty": "Noch keine Labels. Erstelle dein erstes!"
  },
  "versions": {
    "title": "Versionen",
    "new": "Neue Version",
    "namePlaceholder": "z. B. v1.0.0",
    "duplicate": "Eine Version mit diesem Namen existiert bereits.",
    "deleteConfirm": "Diese Version löschen? Sie wird von allen Vorgängen entfernt.",
    "empty": "Noch keine Versionen. Erstelle deine erste!"
  },
  "fields": {
    "title": "Benutzerdefinierte Felder",
    "new": "Neues Feld",
    "namePlaceholder": "Feldname",
    "requiredLabel": "Pflichtfeld",
    "requiredBadge": "Pflicht",
    "duplicate": "Ein Feld mit diesem Namen existiert bereits.",
    "deleteConfirm": "Dieses Feld löschen? Alle Werte gehen verloren.",
    "empty": "Noch keine benutzerdefinierten Felder.",
    "showOptions": "Optionen",
    "hideOptions": "Optionen ausblenden",
    "optionPlaceholder": "Neue Options-Bezeichnung",
    "add": "Hinzufügen",
    "type": {
      "TEXT": "Text",
      "NUMBER": "Zahl",
      "DATE": "Datum",
      "DROPDOWN": "Auswahlliste",
      "CHECKBOX": "Kontrollkästchen"
    }
  },
  "audit": {
    "title": "Audit-Protokoll",
    "filterPlaceholder": "Aktion filtern…",
    "empty": "Keine Audit-Ereignisse",
    "col": {
      "time": "Zeit",
      "user": "Benutzer",
      "action": "Aktion",
      "level": "Stufe",
      "resource": "Ressource"
    },
    "level": {
      "SECURITY": "Sicherheit",
      "WRITE": "Schreiben",
      "ALL": "Alle"
    }
  },
  "org": {
    "title": "Organisation",
    "noPermission": "Du hast keine Berechtigung, die Organisation dieses Projekts zu verwalten.",
    "current": "Aktuelle Organisation",
    "notAssigned": "Nicht zugewiesen",
    "assignLabel": "Einer Organisation zuweisen",
    "selectPlaceholder": "Organisation auswählen…",
    "assign": "Zuweisen",
    "removeFromOrg": "Aus Organisation entfernen",
    "forbidden": "Du musst Eigentümer oder Admin der Zielorganisation sein, um dieses Projekt zuzuweisen.",
    "updateFailed": "Organisation konnte nicht aktualisiert werden."
  }
}
```

- [ ] **Step 3: Register the namespace in `frontend/src/i18n/index.ts`**

Add the imports after the last existing locale import:

```ts
import enProjectSettings from './locales/en/project-settings.json'
import deProjectSettings from './locales/de/project-settings.json'
```

In `resources`, append `'project-settings'` to both languages (quoted key — it contains a hyphen):

```ts
      en: { /* …existing… */ 'project-settings': enProjectSettings },
      de: { /* …existing… */ 'project-settings': deProjectSettings },
```

And append `'project-settings'` to the `ns` array:

```ts
    ns: [/* …existing… */ 'project-settings'],
```

- [ ] **Step 4: Verify parity + build** (no component change yet — all 7 files still allowlisted, scanner stays green)

Run: `cd frontend && npm run check:i18n && npm run build`
Expected: `i18n-parity: OK — en/de namespaces and keys match.` then a successful build.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/i18n/locales/en/project-settings.json frontend/src/i18n/locales/de/project-settings.json frontend/src/i18n/index.ts
git commit -m "feat(i18n): add project-settings namespace (en/de)"
```

---

### Task 2: Localize SettingsLayout

**Files:**
- Modify: `frontend/src/layouts/SettingsLayout.tsx`
- Modify: `frontend/scripts/i18n-allowlist.json`

**Interfaces:**
- Consumes: `project-settings:layout.*` (Task 1).

- [ ] **Step 1: Swap the nav-item labels for keys + localize**

Replace the imports + `items` array:

```tsx
import { Outlet, NavLink } from 'react-router-dom'
import { User, Shield, Bell, KeyRound, UserX } from 'lucide-react'

const items = [
  { to: '/settings/profile', label: 'Profile', icon: User },
  { to: '/settings/security', label: 'Security', icon: Shield },
  { to: '/settings/notifications', label: 'Notifications', icon: Bell },
  { to: '/settings/tokens', label: 'Access Tokens', icon: KeyRound },
  { to: '/settings/account', label: 'Account', icon: UserX },
]
```

with:

```tsx
import { Outlet, NavLink } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { User, Shield, Bell, KeyRound, UserX } from 'lucide-react'

const items = [
  { to: '/settings/profile', labelKey: 'profile', icon: User },
  { to: '/settings/security', labelKey: 'security', icon: Shield },
  { to: '/settings/notifications', labelKey: 'notifications', icon: Bell },
  { to: '/settings/tokens', labelKey: 'tokens', icon: KeyRound },
  { to: '/settings/account', labelKey: 'account', icon: UserX },
]
```

- [ ] **Step 2: Add the hook + localize heading + item labels**

Replace the component body:

```tsx
export function SettingsLayout() {
  return (
    <div className="flex gap-8 h-full min-h-0">
      <nav className="w-48 shrink-0 flex flex-col gap-1">
        <h2 className="px-3 text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">Settings</h2>
        {items.map(({ to, label, icon: Icon }) => (
```

with:

```tsx
export function SettingsLayout() {
  const { t } = useTranslation('project-settings')
  return (
    <div className="flex gap-8 h-full min-h-0">
      <nav className="w-48 shrink-0 flex flex-col gap-1">
        <h2 className="px-3 text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">{t('layout.title')}</h2>
        {items.map(({ to, labelKey, icon: Icon }) => (
```

And replace the label render:

```tsx
            <span className="truncate">{label}</span>
```
→ `<span className="truncate">{t(\`layout.${labelKey}\`)}</span>`

- [ ] **Step 3: Remove SettingsLayout from the allowlist**

In `frontend/scripts/i18n-allowlist.json`, delete this line:

```json
  "src/layouts/SettingsLayout.tsx",
```

- [ ] **Step 4: Verify scanner + build**

Run: `cd frontend && npm run scan:i18n && npm run build`
Expected: `i18n-scan: OK — 0 hardcoded strings outside the allowlist (30 file(s) still allowlisted).` and a successful build.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/layouts/SettingsLayout.tsx frontend/scripts/i18n-allowlist.json
git commit -m "feat(i18n): localize settings layout nav"
```

---

### Task 3: Localize MembersPage

**Files:**
- Modify: `frontend/src/pages/projects/settings/MembersPage.tsx`
- Modify: `frontend/scripts/i18n-allowlist.json`

**Interfaces:**
- Consumes: `project-settings:members.*`, `project-settings:members.role.*` (Task 1); `common:loading` (existing).

- [ ] **Step 1: Add imports + delete the `ROLE_LABELS` map**

Replace:

```tsx
import type { ProjectRole, UserSearchResult } from '@/types'

const ROLE_LABELS: Record<ProjectRole, string> = {
  VIEWER: 'Read-only',
  MEMBER: 'Read & Write',
  ADMIN: 'Admin',
}
const ROLE_OPTIONS: ProjectRole[] = ['VIEWER', 'MEMBER', 'ADMIN']
```

with:

```tsx
import { Trans, useTranslation } from 'react-i18next'
import type { ProjectRole, UserSearchResult } from '@/types'

const ROLE_OPTIONS: ProjectRole[] = ['VIEWER', 'MEMBER', 'ADMIN']
```

- [ ] **Step 2: `AddMemberForm` — hook + error strings + labels**

Add as the first line of the `AddMemberForm` body (before `const [input, setInput] = useState('')`):

```ts
  const { t } = useTranslation('project-settings')
```

Replace the catch error:

```tsx
      setError(status === 409 ? 'This user is already a member.' : 'Could not add member.')
```
→ `setError(status === 409 ? t('members.alreadyMember') : t('members.addFailed'))`

Replace the label, placeholder, role options, and Add button:

```tsx
          <label className="block text-xs text-gray-400 mb-1">Add member</label>
```
→ `<label className="block text-xs text-gray-400 mb-1">{t('members.add')}</label>`

```tsx
          placeholder="Search by name or email…"
```
→ `placeholder={t('members.searchPlaceholder')}`

```tsx
          {ROLE_OPTIONS.map(r => <option key={r} value={r}>{ROLE_LABELS[r]}</option>)}
```
→ `{ROLE_OPTIONS.map(r => <option key={r} value={r}>{t(\`members.role.${r}\`)}</option>)}`

```tsx
          Add
        </button>
```
→ `{t('members.addButton')}` (inside the same `<button>`)

- [ ] **Step 3: `MembersPage` — hook + loading/permission + confirm**

Add as the first line of the `MembersPage` body (before `const { key } = useParams…`):

```ts
  const { t } = useTranslation('project-settings')
```

Replace the loading + permission guards:

```tsx
  if (isLoading || !project) return <div className="text-gray-400 p-6">Loading…</div>

  if (project.myRole !== 'ADMIN') {
    return <div className="p-6 text-gray-400">You don’t have permission to manage members for this project.</div>
  }
```

with:

```tsx
  if (isLoading || !project) return <div className="text-gray-400 p-6">{t('common:loading')}</div>

  if (project.myRole !== 'ADMIN') {
    return <div className="p-6 text-gray-400">{t('members.noPermission')}</div>
  }
```

Replace the remove confirm:

```tsx
    if (!confirm(`Remove ${name} from this project?`)) return
```
→ `if (!confirm(t('members.removeConfirm', { name }))) return`

- [ ] **Step 4: `MembersPage` — heading, org banner, badges, second role select, remove**

Replace the heading:

```tsx
      <h1 className="text-2xl font-semibold">Members</h1>
```
→ `<h1 className="text-2xl font-semibold">{t('members.title')}</h1>`

Replace the org banner block:

```tsx
        <div className="p-4 bg-blue-950/40 border border-blue-900 rounded-lg text-sm text-blue-200">
          This project belongs to <span className="font-medium">{org?.name ?? 'an organization'}</span>.
          Its owners and admins are admins here; its members are viewers. Those people have access
          without appearing in the list below.
        </div>
```

with:

```tsx
        <div className="p-4 bg-blue-950/40 border border-blue-900 rounded-lg text-sm text-blue-200">
          <Trans
            ns="project-settings"
            i18nKey="members.orgBanner"
            values={{ org: org?.name ?? t('members.orgFallback') }}
            components={{ b: <span className="font-medium" /> }}
          />
        </div>
```

Replace the Owner/You badges:

```tsx
                <span className="text-xs bg-gray-700 text-gray-300 px-2 py-0.5 rounded">Owner</span>
```
→ `<span className="text-xs bg-gray-700 text-gray-300 px-2 py-0.5 rounded">{t('members.owner')}</span>`

```tsx
                <span className="text-xs bg-gray-700 text-gray-300 px-2 py-0.5 rounded">You</span>
```
→ `<span className="text-xs bg-gray-700 text-gray-300 px-2 py-0.5 rounded">{t('members.you')}</span>`

Replace the second role select options + Remove button:

```tsx
                  {ROLE_OPTIONS.map(r => <option key={r} value={r}>{ROLE_LABELS[r]}</option>)}
```
→ `{ROLE_OPTIONS.map(r => <option key={r} value={r}>{t(\`members.role.${r}\`)}</option>)}`

```tsx
                  Remove
                </button>
```
→ `{t('members.remove')}` (inside the same `<button>`)

- [ ] **Step 5: Remove MembersPage from the allowlist**

In `frontend/scripts/i18n-allowlist.json`, delete this line:

```json
  "src/pages/projects/settings/MembersPage.tsx",
```

- [ ] **Step 6: Verify scanner + build**

Run: `cd frontend && npm run scan:i18n && npm run build`
Expected: `i18n-scan: OK — 0 hardcoded strings outside the allowlist (29 file(s) still allowlisted).` and a successful build.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/pages/projects/settings/MembersPage.tsx frontend/scripts/i18n-allowlist.json
git commit -m "feat(i18n): localize project members page"
```

---

### Task 4: Localize LabelsPage + VersionsPage

**Files:**
- Modify: `frontend/src/pages/projects/settings/LabelsPage.tsx`
- Modify: `frontend/src/pages/projects/settings/VersionsPage.tsx`
- Modify: `frontend/scripts/i18n-allowlist.json`

**Interfaces:**
- Consumes: `project-settings:form.*`, `project-settings:labels.*`, `project-settings:versions.*` (Task 1); `common:save`, `common:cancel`, `common:loading` (existing).

- [ ] **Step 1: `LabelsPage.tsx` — add hook import**

Add after `import type { Label } from '@/types'` (line 6):

```ts
import { useTranslation } from 'react-i18next'
```

- [ ] **Step 2: `LabelForm` (in LabelsPage) — hook + validation + fields + buttons**

Add as the first line of the `LabelForm` body (before `const [name, setName] = useState…`):

```ts
  const { t } = useTranslation('project-settings')
```

Replace the validation messages:

```tsx
    if (!name.trim()) { setError('Name is required'); return }
    if (name.trim().length > 50) { setError('Max 50 characters'); return }
```

with:

```tsx
    if (!name.trim()) { setError(t('form.nameRequired')); return }
    if (name.trim().length > 50) { setError(t('form.maxChars')); return }
```

Replace the field labels, placeholder, preview fallback name, and buttons:

```tsx
        <label className="block text-xs text-gray-400 mb-1">Name</label>
```
→ `<label className="block text-xs text-gray-400 mb-1">{t('form.name')}</label>`

```tsx
          placeholder="Label name"
```
→ `placeholder={t('labels.namePlaceholder')}`

```tsx
        <label className="block text-xs text-gray-400 mb-1">Color</label>
```
→ `<label className="block text-xs text-gray-400 mb-1">{t('labels.color')}</label>`

```tsx
        <label className="block text-xs text-gray-400 mb-1">Preview</label>
        <LabelChip label={{ id: '', name: name || 'Preview', color }} />
```

with:

```tsx
        <label className="block text-xs text-gray-400 mb-1">{t('labels.preview')}</label>
        <LabelChip label={{ id: '', name: name || t('labels.previewName'), color }} />
```

```tsx
          {initial ? 'Save' : 'Create'}
```
→ `{initial ? t('common:save') : t('form.create')}`

```tsx
          Cancel
        </button>
```
→ `{t('common:cancel')}` (inside the same `<button>`)

- [ ] **Step 3: `LabelsPage` component — hook + strings**

Add as the first line of the `LabelsPage` body (before `const { key } = useParams…`):

```ts
  const { t } = useTranslation('project-settings')
```

Replace the two duplicate-error catches (both `setApiError('A label with that name already exists.')`) with `setApiError(t('labels.duplicate'))`.

Replace the delete confirm:

```tsx
    if (!confirm('Delete this label? It will be removed from all issues.')) return
```
→ `if (!confirm(t('labels.deleteConfirm'))) return`

Replace the loading, heading, New button, Edit/Delete buttons, and empty state:

```tsx
  if (isLoading) return <div className="text-gray-400 p-6">Loading…</div>
```
→ `if (isLoading) return <div className="text-gray-400 p-6">{t('common:loading')}</div>`

```tsx
        <h1 className="text-2xl font-semibold">Labels</h1>
```
→ `<h1 className="text-2xl font-semibold">{t('labels.title')}</h1>`

```tsx
            + New Label
          </button>
```
→ `+ {t('labels.new')}` (inside the same `<button>`)

```tsx
                      Edit
                    </button>
```
→ `{t('form.edit')}`

```tsx
                      Delete
                    </button>
```
→ `{t('form.delete')}`

```tsx
          <p className="text-sm text-gray-500 py-8 text-center">No labels yet. Create your first one!</p>
```
→ `<p className="text-sm text-gray-500 py-8 text-center">{t('labels.empty')}</p>`

- [ ] **Step 4: `VersionsPage.tsx` — mirror the same edits**

Add after `import type { Version } from '@/types'` (line 5):

```ts
import { useTranslation } from 'react-i18next'
```

In `VersionForm`, add `const { t } = useTranslation('project-settings')` as the first body line, then:
- `setError('Name is required')` → `setError(t('form.nameRequired'))`
- `setError('Max 50 characters')` → `setError(t('form.maxChars'))`
- `<label …>Name</label>` → `{t('form.name')}`
- `placeholder="e.g. v1.0.0"` → `placeholder={t('versions.namePlaceholder')}`
- `{initial ? 'Save' : 'Create'}` → `{initial ? t('common:save') : t('form.create')}`
- button `Cancel` → `{t('common:cancel')}`

In `VersionsPage`, add `const { t } = useTranslation('project-settings')` as the first body line, then:
- both `setApiError('A version with that name already exists.')` → `setApiError(t('versions.duplicate'))`
- `confirm('Delete this version? It will be removed from all issues.')` → `confirm(t('versions.deleteConfirm'))`
- `Loading…` → `{t('common:loading')}`
- `<h1 …>Versions</h1>` → `{t('versions.title')}`
- `+ New Version` → `+ {t('versions.new')}`
- `Edit` → `{t('form.edit')}`, `Delete` → `{t('form.delete')}`
- `No versions yet. Create your first one!` → `{t('versions.empty')}`

- [ ] **Step 5: Remove both files from the allowlist**

In `frontend/scripts/i18n-allowlist.json`, delete these lines:

```json
  "src/pages/projects/settings/LabelsPage.tsx",
  "src/pages/projects/settings/VersionsPage.tsx",
```

- [ ] **Step 6: Verify scanner + build**

Run: `cd frontend && npm run scan:i18n && npm run build`
Expected: `i18n-scan: OK — 0 hardcoded strings outside the allowlist (27 file(s) still allowlisted).` and a successful build.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/pages/projects/settings/LabelsPage.tsx frontend/src/pages/projects/settings/VersionsPage.tsx frontend/scripts/i18n-allowlist.json
git commit -m "feat(i18n): localize labels + versions settings pages"
```

---

### Task 5: Localize CustomFieldsPage

**Files:**
- Modify: `frontend/src/pages/projects/settings/CustomFieldsPage.tsx`
- Modify: `frontend/scripts/i18n-allowlist.json`

**Interfaces:**
- Consumes: `project-settings:form.*`, `project-settings:fields.*`, `project-settings:fields.type.*` (Task 1); `common:save`, `common:cancel`, `common:loading` (existing).

- [ ] **Step 1: Add the hook import**

Add after `import type { CustomFieldDefinition } from '@/types'` (line 13):

```ts
import { useTranslation } from 'react-i18next'
```

- [ ] **Step 2: `SortableField` — hook + type badge + required badge + buttons**

Add as the first line of the `SortableField` body (before `const { attributes, … } = useSortable…`):

```ts
  const { t } = useTranslation('project-settings')
```

Replace:

```tsx
      <span className="text-xs text-gray-500 bg-gray-800 px-2 py-0.5 rounded">{field.type}</span>
      {field.required && <span className="text-xs text-red-400">required</span>}
```

with:

```tsx
      <span className="text-xs text-gray-500 bg-gray-800 px-2 py-0.5 rounded">{t(\`fields.type.${field.type}\`)}</span>
      {field.required && <span className="text-xs text-red-400">{t('fields.requiredBadge')}</span>}
```

```tsx
          <button onClick={onEdit} className="text-xs text-gray-400 hover:text-white px-2 py-1 rounded hover:bg-gray-700">Edit</button>
          <button onClick={onDelete} className="text-xs text-red-400 hover:text-red-300 px-2 py-1 rounded hover:bg-gray-700">Delete</button>
```

with:

```tsx
          <button onClick={onEdit} className="text-xs text-gray-400 hover:text-white px-2 py-1 rounded hover:bg-gray-700">{t('form.edit')}</button>
          <button onClick={onDelete} className="text-xs text-red-400 hover:text-red-300 px-2 py-1 rounded hover:bg-gray-700">{t('form.delete')}</button>
```

- [ ] **Step 3: `CustomFieldsPage` — hook + errors + confirm**

Add as the first line of the `CustomFieldsPage` body (before `const { key } = useParams…`):

```ts
  const { t } = useTranslation('project-settings')
```

Replace both `setApiError('A field with that name already exists.')` with `setApiError(t('fields.duplicate'))`.

Replace:

```tsx
    if (!confirm('Delete this field? All values will be lost.')) return
```
→ `if (!confirm(t('fields.deleteConfirm'))) return`

Replace:

```tsx
  if (isLoading) return <div className="text-gray-400 p-6">Loading…</div>
```
→ `if (isLoading) return <div className="text-gray-400 p-6">{t('common:loading')}</div>`

- [ ] **Step 4: `CustomFieldsPage` — heading, create form (mind the `t` shadow), buttons, empty**

Replace the heading + New button:

```tsx
        <h1 className="text-2xl font-semibold">Custom Fields</h1>
```
→ `<h1 className="text-2xl font-semibold">{t('fields.title')}</h1>`

```tsx
            + New Field
          </button>
```
→ `+ {t('fields.new')}` (inside the same `<button>`)

Replace the create-form name placeholder, the **type select (rename the shadowing map param `t` → `ft`)**, the Required label, and the Create/Cancel buttons:

```tsx
            <input value={newName} onChange={e => setNewName(e.target.value)} placeholder="Field name" autoFocus
              className="flex-1 bg-gray-700 border border-gray-600 rounded px-3 py-1.5 text-sm text-white outline-none focus:border-blue-500" />
            <select value={newType} onChange={e => setNewType(e.target.value)}
              className="bg-gray-700 border border-gray-600 rounded px-3 py-1.5 text-sm text-white outline-none">
              {FIELD_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
            </select>
          </div>
          <label className="flex items-center gap-2 text-sm text-gray-300 cursor-pointer">
            <input type="checkbox" checked={newRequired} onChange={e => setNewRequired(e.target.checked)} />
            Required
          </label>
          <div className="flex gap-2">
            <button type="submit" className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-1.5 rounded text-sm font-medium">Create</button>
            <button type="button" onClick={() => { setShowCreate(false); setApiError('') }} className="text-gray-400 hover:text-white px-3 py-1.5 text-sm">Cancel</button>
          </div>
```

with:

```tsx
            <input value={newName} onChange={e => setNewName(e.target.value)} placeholder={t('fields.namePlaceholder')} autoFocus
              className="flex-1 bg-gray-700 border border-gray-600 rounded px-3 py-1.5 text-sm text-white outline-none focus:border-blue-500" />
            <select value={newType} onChange={e => setNewType(e.target.value)}
              className="bg-gray-700 border border-gray-600 rounded px-3 py-1.5 text-sm text-white outline-none">
              {FIELD_TYPES.map(ft => <option key={ft} value={ft}>{t(\`fields.type.${ft}\`)}</option>)}
            </select>
          </div>
          <label className="flex items-center gap-2 text-sm text-gray-300 cursor-pointer">
            <input type="checkbox" checked={newRequired} onChange={e => setNewRequired(e.target.checked)} />
            {t('fields.requiredLabel')}
          </label>
          <div className="flex gap-2">
            <button type="submit" className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-1.5 rounded text-sm font-medium">{t('form.create')}</button>
            <button type="button" onClick={() => { setShowCreate(false); setApiError('') }} className="text-gray-400 hover:text-white px-3 py-1.5 text-sm">{t('common:cancel')}</button>
          </div>
```

Replace the edit-form Required label + Save/Cancel:

```tsx
                      Required
                    </label>
                    <div className="flex gap-2">
                      <button type="submit" className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-1.5 rounded text-sm font-medium">Save</button>
                      <button type="button" onClick={() => { setEditingField(null); setApiError('') }} className="text-gray-400 hover:text-white px-3 py-1.5 text-sm">Cancel</button>
```

with:

```tsx
                      {t('fields.requiredLabel')}
                    </label>
                    <div className="flex gap-2">
                      <button type="submit" className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-1.5 rounded text-sm font-medium">{t('common:save')}</button>
                      <button type="button" onClick={() => { setEditingField(null); setApiError('') }} className="text-gray-400 hover:text-white px-3 py-1.5 text-sm">{t('common:cancel')}</button>
```

Replace the empty state:

```tsx
              <p className="text-sm text-gray-500 py-8 text-center">No custom fields yet.</p>
```
→ `<p className="text-sm text-gray-500 py-8 text-center">{t('fields.empty')}</p>`

- [ ] **Step 5: `DropdownOptionsPanel` — hook + toggle + option placeholder + Add**

Add as the first line of the `DropdownOptionsPanel` body (before `const createOption = useCreateOption…`):

```ts
  const { t } = useTranslation('project-settings')
```

Replace the toggle button text (glyph stays literal):

```tsx
        {expanded ? '▾ Hide options' : '▸ Options'} ({field.options?.length ?? 0})
```
→ `{expanded ? '▾' : '▸'} {t(expanded ? 'fields.hideOptions' : 'fields.showOptions')} ({field.options?.length ?? 0})`

Replace the new-option placeholder + Add button:

```tsx
              <input value={newOptionLabel} onChange={e => onNewOptionLabelChange(e.target.value)}
                placeholder="New option label" className="flex-1 bg-gray-700 border border-gray-600 rounded px-2 py-1 text-xs text-white outline-none focus:border-blue-500" />
              <button type="submit" className="bg-blue-600 hover:bg-blue-700 text-white px-3 py-1 rounded text-xs">Add</button>
```

with:

```tsx
              <input value={newOptionLabel} onChange={e => onNewOptionLabelChange(e.target.value)}
                placeholder={t('fields.optionPlaceholder')} className="flex-1 bg-gray-700 border border-gray-600 rounded px-2 py-1 text-xs text-white outline-none focus:border-blue-500" />
              <button type="submit" className="bg-blue-600 hover:bg-blue-700 text-white px-3 py-1 rounded text-xs">{t('fields.add')}</button>
```

(Leave `{opt.label}` and the `✕` glyph.)

- [ ] **Step 6: Remove CustomFieldsPage from the allowlist**

In `frontend/scripts/i18n-allowlist.json`, delete this line:

```json
  "src/pages/projects/settings/CustomFieldsPage.tsx",
```

- [ ] **Step 7: Verify scanner + build**

Run: `cd frontend && npm run scan:i18n && npm run build`
Expected: `i18n-scan: OK — 0 hardcoded strings outside the allowlist (26 file(s) still allowlisted).` and a successful build. Confirm tsc did not error on a shadowed `t` (Step 4 rename to `ft`).

- [ ] **Step 8: Commit**

```bash
git add frontend/src/pages/projects/settings/CustomFieldsPage.tsx frontend/scripts/i18n-allowlist.json
git commit -m "feat(i18n): localize custom fields settings page"
```

---

### Task 6: Localize ProjectAuditPage

**Files:**
- Modify: `frontend/src/pages/projects/settings/ProjectAuditPage.tsx`
- Modify: `frontend/scripts/i18n-allowlist.json`

**Interfaces:**
- Consumes: `project-settings:audit.*`, `project-settings:audit.col.*`, `project-settings:audit.level.*` (Task 1); `formatDateTime` from `@/i18n/format` (existing).

- [ ] **Step 1: Swap imports + drop the module-level `columns`**

Replace the top imports + `LEVEL_COLOR` + `columns` block (lines 1–41):

```tsx
import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { auditApi, AuditEvent } from '../../../api/audit'
import { DataTable, type Column } from '@/components/table/DataTable'

const LEVEL_COLOR: Record<string, string> = {
  SECURITY: 'bg-red-900/40 text-red-400',
  WRITE: 'bg-blue-900/40 text-blue-400',
  ALL: 'bg-gray-700 text-gray-300',
}

const columns: Column<AuditEvent>[] = [
  {
    key: 'time',
    header: 'Time',
    width: '180px',
    cell: e => <span className="text-gray-400">{new Date(e.timestamp).toLocaleString()}</span>,
  },
  { key: 'user', header: 'User', cell: e => e.userEmail },
  {
    key: 'action',
    header: 'Action',
    cell: e => <span className="font-mono text-xs">{e.action}</span>,
  },
  {
    key: 'level',
    header: 'Level',
    width: '120px',
    cell: e => (
      <span className={`px-2 py-1 rounded text-xs font-medium ${LEVEL_COLOR[e.level]}`}>
        {e.level}
      </span>
    ),
  },
  {
    key: 'resource',
    header: 'Resource',
    cell: e => <span className="text-gray-400">{e.resourceType} {e.resourceId}</span>,
  },
]
```

with:

```tsx
import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { auditApi, AuditEvent } from '../../../api/audit'
import { DataTable, type Column } from '@/components/table/DataTable'
import { formatDateTime } from '@/i18n/format'

const LEVEL_COLOR: Record<string, string> = {
  SECURITY: 'bg-red-900/40 text-red-400',
  WRITE: 'bg-blue-900/40 text-blue-400',
  ALL: 'bg-gray-700 text-gray-300',
}
```

- [ ] **Step 2: Build `columns` inside the component with `t` + localize the shell**

Replace the component body:

```tsx
export default function ProjectAuditPage() {
  const { key } = useParams<{ key: string }>()
  const projectKey = key!

  const [action, setAction] = useState('')
```

with:

```tsx
export default function ProjectAuditPage() {
  const { t } = useTranslation('project-settings')
  const { key } = useParams<{ key: string }>()
  const projectKey = key!

  const columns: Column<AuditEvent>[] = [
    {
      key: 'time',
      header: t('audit.col.time'),
      width: '180px',
      cell: e => <span className="text-gray-400">{formatDateTime(e.timestamp)}</span>,
    },
    { key: 'user', header: t('audit.col.user'), cell: e => e.userEmail },
    {
      key: 'action',
      header: t('audit.col.action'),
      cell: e => <span className="font-mono text-xs">{e.action}</span>,
    },
    {
      key: 'level',
      header: t('audit.col.level'),
      width: '120px',
      cell: e => (
        <span className={`px-2 py-1 rounded text-xs font-medium ${LEVEL_COLOR[e.level]}`}>
          {t(`audit.level.${e.level}`, { defaultValue: e.level })}
        </span>
      ),
    },
    {
      key: 'resource',
      header: t('audit.col.resource'),
      cell: e => <span className="text-gray-400">{e.resourceType} {e.resourceId}</span>,
    },
  ]

  const [action, setAction] = useState('')
```

Then localize the heading, filter placeholder, and empty prop:

```tsx
      <h1 className="text-2xl font-semibold">Audit Log</h1>
```
→ `<h1 className="text-2xl font-semibold">{t('audit.title')}</h1>`

```tsx
          placeholder="Filter action…"
```
→ `placeholder={t('audit.filterPlaceholder')}`

```tsx
        empty="No audit events"
```
→ `empty={t('audit.empty')}`

(Leave `{e.userEmail}`, `{e.action}`, `{e.resourceType} {e.resourceId}` — data.)

- [ ] **Step 3: Remove ProjectAuditPage from the allowlist**

In `frontend/scripts/i18n-allowlist.json`, delete this line:

```json
  "src/pages/projects/settings/ProjectAuditPage.tsx",
```

- [ ] **Step 4: Verify scanner + build**

Run: `cd frontend && npm run scan:i18n && npm run build`
Expected: `i18n-scan: OK — 0 hardcoded strings outside the allowlist (25 file(s) still allowlisted).` and a successful build.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/projects/settings/ProjectAuditPage.tsx frontend/scripts/i18n-allowlist.json
git commit -m "feat(i18n): localize project audit page (columns + relative dates)"
```

---

### Task 7: Localize OrganizationSettingsPage

**Files:**
- Modify: `frontend/src/pages/projects/settings/OrganizationSettingsPage.tsx`
- Modify: `frontend/scripts/i18n-allowlist.json`

**Interfaces:**
- Consumes: `project-settings:org.*` (Task 1); `common:loading` (existing).

- [ ] **Step 1: Add the hook import**

Add after `import { projectsApi } from '@/api/projects'` (line 6):

```ts
import { useTranslation } from 'react-i18next'
```

- [ ] **Step 2: Add the hook + localize errors + guards**

Add as the first line of the `OrganizationSettingsPage` body (before `const { key } = useParams…`):

```ts
  const { t } = useTranslation('project-settings')
```

Replace the mutation error:

```tsx
      setError(status === 403
        ? 'You must be an owner or admin of the target organization to assign this project to it.'
        : 'Could not update the organization.')
```

with:

```tsx
      setError(status === 403 ? t('org.forbidden') : t('org.updateFailed'))
```

Replace the loading + permission guards:

```tsx
  if (isLoading || !project) return <div className="p-6 text-gray-400">Loading…</div>
  if (project.myRole !== 'ADMIN') {
    return <div className="p-6 text-gray-400">You don't have permission to manage this project's organization.</div>
  }
```

with:

```tsx
  if (isLoading || !project) return <div className="p-6 text-gray-400">{t('common:loading')}</div>
  if (project.myRole !== 'ADMIN') {
    return <div className="p-6 text-gray-400">{t('org.noPermission')}</div>
  }
```

- [ ] **Step 3: Localize the body**

Replace:

```tsx
      <h1 className="text-2xl font-semibold">Organization</h1>

      <div className="p-4 bg-gray-900 border border-gray-800 rounded-lg space-y-1">
        <div className="text-xs text-gray-400">Current organization</div>
        <div className="text-sm text-white">
          {project.orgId ? (currentOrg?.name ?? '…') : 'Not assigned'}
        </div>
      </div>
```

with:

```tsx
      <h1 className="text-2xl font-semibold">{t('org.title')}</h1>

      <div className="p-4 bg-gray-900 border border-gray-800 rounded-lg space-y-1">
        <div className="text-xs text-gray-400">{t('org.current')}</div>
        <div className="text-sm text-white">
          {project.orgId ? (currentOrg?.name ?? '…') : t('org.notAssigned')}
        </div>
      </div>
```

Replace the assign label, select placeholder, Assign button, and Remove link:

```tsx
        <label className="block text-xs text-gray-400">Assign to organization</label>
```
→ `<label className="block text-xs text-gray-400">{t('org.assignLabel')}</label>`

```tsx
            <option value="">Select an organization…</option>
```
→ `<option value="">{t('org.selectPlaceholder')}</option>`

```tsx
            Assign
          </button>
```
→ `{t('org.assign')}` (inside the same `<button>`)

```tsx
            Remove from organization
          </button>
```
→ `{t('org.removeFromOrg')}` (inside the same `<button>`)

(Leave `{currentOrg?.name}`, `{o.name}`, and the `…` marker.)

- [ ] **Step 4: Remove OrganizationSettingsPage from the allowlist**

In `frontend/scripts/i18n-allowlist.json`, delete this line:

```json
  "src/pages/projects/settings/OrganizationSettingsPage.tsx",
```

- [ ] **Step 5: Verify scanner + build**

Run: `cd frontend && npm run scan:i18n && npm run build`
Expected: `i18n-scan: OK — 0 hardcoded strings outside the allowlist (24 file(s) still allowlisted).` and a successful build.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/pages/projects/settings/OrganizationSettingsPage.tsx frontend/scripts/i18n-allowlist.json
git commit -m "feat(i18n): localize project organization settings page"
```

---

### Task 8: Finalize S11 — full gate + flip coverage matrix

**Files:**
- Modify: `docs/superpowers/specs/2026-07-13-i18n-full-rollout-design.md`

- [ ] **Step 1: Run the full slice gate**

Run: `cd frontend && npm run test:i18n && npm run lint:i18n && npm run build`
Expected: scanner self-tests pass, `i18n-scan: OK — 0 hardcoded strings outside the allowlist (24 file(s) still allowlisted).`, `i18n-parity: OK — en/de namespaces and keys match.`, and a successful build. All 7 S11 files must be absent from the allowlist (31 baseline − 7 = 24 entries).

- [ ] **Step 2: Manual DE/EN browser check**

Start the dev server (`cd frontend && npm run dev`), switch language via Settings → Profile, and confirm across the project settings area: the settings sidebar labels; Members (roles, add form, org banner, Owner/You badges, remove confirm); Labels + Versions (create/edit forms, validation, duplicate errors, empty states); Custom Fields (type dropdown + badge, Required, options panel toggle); Audit Log (column headers, level badge, filter placeholder, empty, localized timestamps); Organization (current/assign/remove, permission text) all switch between English and German with no raw keys and no layout breakage.

- [ ] **Step 3: Flip S11 to ✅ in the coverage matrix**

In `docs/superpowers/specs/2026-07-13-i18n-full-rollout-design.md`, replace the S11 matrix row:

```
| S11 | `project-settings` | ⬜ | MembersPage, LabelsPage, VersionsPage, CustomFieldsPage, ProjectAuditPage, OrganizationSettingsPage, SettingsLayout (Rest) |
```

with:

```
| S11 | `project-settings` | ✅ | All 7 files localized (new `project-settings` ns); scanner-blind ROLE_LABELS/FIELD_TYPES/audit column headers/settings-nav labels localized at render site; members org banner via `<Trans>`; audit timestamp via `formatDateTime`; CustomFields `t`-shadow (`ft`) handled |
```

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/specs/2026-07-13-i18n-full-rollout-design.md
git commit -m "docs(i18n): mark S11 project-settings slice complete in coverage matrix"
```

---

## Self-Review

**Spec coverage:**
- New `project-settings` namespace en+de, registered in `index.ts` → Task 1. ✅
- Every user-facing string across all 7 files via `t()`/`<Trans>` → Tasks 2–7. ✅
- Scanner-blind const-map labels (ROLE_LABELS, FIELD_TYPES, audit `columns[].header`, `{e.level}`, `{field.type}`, settings-nav labels) localized at render site → Tasks 2, 3, 5, 6. ✅
- `confirm()` + error strings localized with variable interpolation (`members.removeConfirm` {{name}}) — no fragment concat → Tasks 3, 4, 5. ✅
- Members org banner via `<Trans>` with `{{org}}` + bold `<span>` component → Task 3. ✅
- Audit timestamp via `formatDateTime` (DoD point 3) → Task 6. ✅
- CustomFields `t`-shadow trap handled (map param → `ft`) → Task 5. ✅
- `common:*` reused for save/cancel/loading (no duplicate keys). ✅
- All 7 files removed from allowlist (31 → 24) → Tasks 2–7. ✅
- Full gate + manual DE/EN + matrix flip → Task 8. ✅

**Placeholder scan:** No TBD/TODO; every step has exact old/new code and exact commands with expected output.

**Type/key consistency:** All keys used in Tasks 2–7 are defined in Task 1's en+de JSON (`form.*`, `layout.*`, `members.*`+`members.role.*`, `labels.*`, `versions.*`, `fields.*`+`fields.type.*`, `audit.*`+`audit.col.*`+`audit.level.*`, `org.*`). Dynamic keys cover exactly the enum members present: `members.role.${r}` over `ROLE_OPTIONS` = VIEWER/MEMBER/ADMIN; `fields.type.${field.type}`/`${ft}` over `FIELD_TYPES` = TEXT/NUMBER/DATE/DROPDOWN/CHECKBOX; `audit.level.${e.level}` over SECURITY/WRITE/ALL (with `defaultValue` fallback). `common:loading/save/cancel` and `formatDateTime` are pre-existing. Allowlist arithmetic: 31 → 30 (T2) → 29 (T3) → 27 (T4) → 26 (T5) → 25 (T6) → 24 (T7).
