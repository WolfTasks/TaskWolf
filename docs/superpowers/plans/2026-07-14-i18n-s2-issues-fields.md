# i18n Rollout — Session 2 (`issues-fields` namespace) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Localize the S2 `issues-fields` slice — the issue field selectors, pickers, chips and badges — into a new `issues-fields` namespace (`en`/`de`), including the scanner-blind **issue-type and priority enum labels** (also retro-fixing the `{issue.priority}` render that S1 deferred), and flip S2 to ✅ in the master-spec coverage matrix.

**Architecture:** Thin execution plan against the locked pattern in `docs/superpowers/specs/2026-07-13-i18n-full-rollout-design.md` (Anhang — Migrations-Checkliste). Add the namespace first, then localize components in batches, each batch removing its files from `frontend/scripts/i18n-allowlist.json`. Shared issue-domain enum labels (`type.*`, `priority.*`) live in this namespace and are referenced cross-namespace by later slices (board/backlog).

**Tech Stack:** react-i18next (`useTranslation`), `frontend/src/i18n/format.ts` (`formatDate`), the Session-0 scanner/parity scripts, Node 20, Vite/tsc build.

## Global Constraints

- **Namespace = `issues-fields`.** In components use `const { t } = useTranslation('issues-fields')`; reference shared terms with the `common:` prefix (`t('common:loading')`). Unprefixed keys resolve against `issues-fields`.
- **en/de stay key-identical** (parity check is authoritative).
- **Enum labels are the core of this slice.** `TypeSelector` and `PrioritySelector` render the raw enum values (`EPIC/STORY/BUG/TASK/SUBTASK`, `CRITICAL/HIGH/MEDIUM/LOW`) as JSX **variables** — the scanner does NOT flag them, but they are user-facing and DoD-required. Localize them via `t(\`type.${value}\`)` / `t(\`priority.${value}\`)`. Also retro-fix `IssueListPage.tsx` (S1 explicitly deferred its `{issue.priority}` to "ship with PrioritySelector in S2").
- **No string-concatenation of translated fragments.** Interpolate via variables (`t('label.create', { name })`). Decorative glyphs (`+`, `✕`, `📎`, `—`, `•`) stay as literal JSX/template text next to the `{t(...)}` call — they are icons/markers, not translated fragments.
- **Dates via `format.ts`** (DoD Abschnitt 5.3): `DueDatePicker` must use `formatDate(value)`, not raw `new Date(value).toLocaleDateString()`.
- **Remove each localized file from `frontend/scripts/i18n-allowlist.json`** in the same task that localizes it. The allowlist only shrinks. **Net for S2: 9 files removed (63 → 54).**
- **String-free, leave untouched (NOT in allowlist, no work):** `StatusBadge.tsx`, `LabelChip.tsx`, `VersionChip.tsx` — they render only data (`{name}`).
- **`VersionTag.tsx` is OUT OF SCOPE for S2** — it is the app-version footer (`v{__APP_VERSION__}`), shared app chrome, not an issue field. **Leave it in the allowlist**; it ships in S18 (shared/cleanup). Do not touch it, do not remove it from the allowlist.
- **Out of scope (other slices), leave unchanged:** `DraggableCard.tsx:56` `{issue.priority}` (S4 board); custom-field data labels `${definition.name} *` and `{opt.label}` (user data); attachment size units `B/KB/MB` in `formatBytes` (universal units); the RichTextEditor toolbar **button glyphs** `B`/`I`/`<>`/`• List`/`1. List` (typographic format markers — their localized meaning is carried by the `title` tooltips, which ARE localized).
- **Done-per-slice (master spec Abschnitt 5):** `npm run test:i18n && npm run lint:i18n && npm run build` all green; manual DE/EN browser check; matrix row flipped to ✅.
- All paths relative to repo root `C:\Users\Admin\IdeaProjects\TaskWolf`. Work in an isolated worktree branched from `origin/main` (which contains merged S0 tooling + S1 `issues`).

---

### Task 1: Create the `issues-fields` namespace + register it

**Files:**
- Create: `frontend/src/i18n/locales/en/issues-fields.json`
- Create: `frontend/src/i18n/locales/de/issues-fields.json`
- Modify: `frontend/src/i18n/index.ts`

**Interfaces:**
- Produces: the `issues-fields` namespace keys consumed verbatim by Tasks 2-5 (`type.*`, `priority.*`, `none`, `assignee.*`, `label.*`, `version.*`, `sprint.*`, `storyPoints.*`, `dueDate.*`, `customField.*`, `richText.*`, `attachment.*`).

- [ ] **Step 1: Create `frontend/src/i18n/locales/en/issues-fields.json`**

```json
{
  "type": {
    "EPIC": "Epic",
    "STORY": "Story",
    "BUG": "Bug",
    "TASK": "Task",
    "SUBTASK": "Subtask"
  },
  "priority": {
    "CRITICAL": "Critical",
    "HIGH": "High",
    "MEDIUM": "Medium",
    "LOW": "Low"
  },
  "none": "None",
  "assignee": {
    "unassigned": "Unassigned",
    "unassign": "Unassign",
    "searchPlaceholder": "Search members…"
  },
  "label": {
    "searchPlaceholder": "Search labels…",
    "create": "Create label \"{{name}}\""
  },
  "version": {
    "empty": "No versions. Create them in project settings."
  },
  "sprint": {
    "none": "No sprint",
    "empty": "No active sprints"
  },
  "storyPoints": {
    "set": "Set points",
    "clear": "Clear"
  },
  "dueDate": {
    "none": "No due date",
    "clear": "Clear due date"
  },
  "customField": {
    "none": "— None —"
  },
  "richText": {
    "placeholder": "Add a description…",
    "bold": "Bold",
    "italic": "Italic",
    "code": "Code",
    "bulletList": "Bullet list",
    "orderedList": "Ordered list"
  },
  "attachment": {
    "heading": "Attachments",
    "upload": "Upload",
    "uploading": "Uploading…",
    "empty": "No attachments",
    "confirmDelete": "Delete \"{{filename}}\"?"
  }
}
```

- [ ] **Step 2: Create `frontend/src/i18n/locales/de/issues-fields.json`** (same keys, German copy)

```json
{
  "type": {
    "EPIC": "Epic",
    "STORY": "Story",
    "BUG": "Bug",
    "TASK": "Aufgabe",
    "SUBTASK": "Teilaufgabe"
  },
  "priority": {
    "CRITICAL": "Kritisch",
    "HIGH": "Hoch",
    "MEDIUM": "Mittel",
    "LOW": "Niedrig"
  },
  "none": "Keine",
  "assignee": {
    "unassigned": "Nicht zugewiesen",
    "unassign": "Zuweisung entfernen",
    "searchPlaceholder": "Mitglieder suchen…"
  },
  "label": {
    "searchPlaceholder": "Labels suchen…",
    "create": "Label \"{{name}}\" erstellen"
  },
  "version": {
    "empty": "Keine Versionen. Erstelle sie in den Projekteinstellungen."
  },
  "sprint": {
    "none": "Kein Sprint",
    "empty": "Keine aktiven Sprints"
  },
  "storyPoints": {
    "set": "Punkte setzen",
    "clear": "Löschen"
  },
  "dueDate": {
    "none": "Kein Fälligkeitsdatum",
    "clear": "Fälligkeitsdatum löschen"
  },
  "customField": {
    "none": "— Keine —"
  },
  "richText": {
    "placeholder": "Beschreibung hinzufügen…",
    "bold": "Fett",
    "italic": "Kursiv",
    "code": "Code",
    "bulletList": "Aufzählung",
    "orderedList": "Nummerierte Liste"
  },
  "attachment": {
    "heading": "Anhänge",
    "upload": "Hochladen",
    "uploading": "Wird hochgeladen…",
    "empty": "Keine Anhänge",
    "confirmDelete": "\"{{filename}}\" löschen?"
  }
}
```

- [ ] **Step 3: Register the namespace in `frontend/src/i18n/index.ts`**

Add the imports after the existing `deIssues` import (line 14):

```ts
import enIssuesFields from './locales/en/issues-fields.json'
import deIssuesFields from './locales/de/issues-fields.json'
```

In the `resources` object, add `'issues-fields'` to both languages (note the quoted key — it contains a hyphen):

```ts
    resources: {
      en: { common: enCommon, settings: enSettings, nav: enNav, auth: enAuth, issues: enIssues, 'issues-fields': enIssuesFields },
      de: { common: deCommon, settings: deSettings, nav: deNav, auth: deAuth, issues: deIssues, 'issues-fields': deIssuesFields },
    },
```

And add `'issues-fields'` to the `ns` array:

```ts
    ns: ['common', 'settings', 'nav', 'auth', 'issues', 'issues-fields'],
```

- [ ] **Step 4: Verify parity + build** (no component change yet — the 9 target files are still allowlisted and the two enum selectors are scanner-blind, so the scanner stays green; parity + build is this task's gate)

Run: `cd frontend && npm run check:i18n && npm run build`
Expected: `i18n-parity: OK — en/de namespaces and keys match.` then a successful `tsc && vite build`.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/i18n/locales/en/issues-fields.json frontend/src/i18n/locales/de/issues-fields.json frontend/src/i18n/index.ts
git commit -m "feat(i18n): add issues-fields namespace (en/de)"
```

---

### Task 2: Localize the issue type & priority enum labels

**Files:**
- Modify: `frontend/src/components/issue/TypeSelector.tsx`
- Modify: `frontend/src/components/issue/PrioritySelector.tsx`
- Modify: `frontend/src/pages/issues/IssueListPage.tsx`

**Interfaces:**
- Consumes: `issues-fields:type.*`, `issues-fields:priority.*` (Task 1).

> No allowlist change in this task: `TypeSelector`/`PrioritySelector` are scanner-blind (enum vars) and `IssueListPage` was already removed from the allowlist in S1. The scanner must remain green.

- [ ] **Step 1: `TypeSelector.tsx` — add the hook and rename the shadowing map variable**

Add the import after `import { useState, useRef, useEffect } from 'react'`:

```ts
import { useTranslation } from 'react-i18next'
```

Add as the first line of the `TypeSelector` body (before `const [open, setOpen] = useState(false)`):

```ts
  const { t } = useTranslation('issues-fields')
```

**The dropdown maps with `TYPES.map(t => …)`, which would shadow the translation `t`.** Replace the entire button + dropdown-map block:

```tsx
      <button
        onClick={() => { if (!disabled) setOpen(o => !o) }}
        disabled={disabled}
        className={`text-sm text-gray-300 ${disabled ? '' : 'cursor-pointer hover:underline'}`}
      >
        {value}
      </button>
      {open && (
        <div className="absolute z-50 top-6 left-0 bg-gray-800 border border-gray-700 rounded shadow-lg py-1 min-w-32">
          {TYPES.map(t => (
            <button
              key={t}
              onClick={() => { onSave(t); setOpen(false) }}
              className={`w-full text-left px-3 py-1.5 text-sm text-gray-300 hover:bg-gray-700 ${t === value ? 'font-bold text-white' : ''}`}
            >
              {t}
            </button>
          ))}
        </div>
      )}
```

with:

```tsx
      <button
        onClick={() => { if (!disabled) setOpen(o => !o) }}
        disabled={disabled}
        className={`text-sm text-gray-300 ${disabled ? '' : 'cursor-pointer hover:underline'}`}
      >
        {t(`type.${value}`)}
      </button>
      {open && (
        <div className="absolute z-50 top-6 left-0 bg-gray-800 border border-gray-700 rounded shadow-lg py-1 min-w-32">
          {TYPES.map(ty => (
            <button
              key={ty}
              onClick={() => { onSave(ty); setOpen(false) }}
              className={`w-full text-left px-3 py-1.5 text-sm text-gray-300 hover:bg-gray-700 ${ty === value ? 'font-bold text-white' : ''}`}
            >
              {t(`type.${ty}`)}
            </button>
          ))}
        </div>
      )}
```

- [ ] **Step 2: `PrioritySelector.tsx` — add the hook and localize** (map var is `p`, no collision)

Add the import after `import { useState, useRef, useEffect } from 'react'`:

```ts
import { useTranslation } from 'react-i18next'
```

Add as the first line of the `PrioritySelector` body (before `const [open, setOpen] = useState(false)`):

```ts
  const { t } = useTranslation('issues-fields')
```

Replace the button label `{value}` (inside the trigger `<button>`) with:

```tsx
        {t(`priority.${value}`)}
```

Replace the dropdown item label `{p}` (inside `PRIORITIES.map(p => …)`) with:

```tsx
              {t(`priority.${p}`)}
```

- [ ] **Step 3: `IssueListPage.tsx` — retro-fix the deferred raw priority render**

This file already has `const { t } = useTranslation('issues')` (from S1); reference the new namespace with the full `issues-fields:` prefix. Replace (line ~235):

```tsx
            }`}>{issue.priority}</span>
```

with:

```tsx
            }`}>{t(`issues-fields:priority.${issue.priority}`)}</span>
```

(Leave the `className` priority-color ternary above it unchanged — it keys off the raw enum, not display text.)

- [ ] **Step 4: Verify scanner + build**

Run: `cd frontend && npm run scan:i18n && npm run build`
Expected: `i18n-scan: OK — 0 hardcoded strings outside the allowlist (63 file(s) still allowlisted).` (unchanged count — no allowlist edits here) and a successful build.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/issue/TypeSelector.tsx frontend/src/components/issue/PrioritySelector.tsx frontend/src/pages/issues/IssueListPage.tsx
git commit -m "feat(i18n): localize issue type & priority enum labels"
```

---

### Task 3: Localize AssigneeSelector, SprintSelector, StoryPointsSelector, DueDatePicker

**Files:**
- Modify: `frontend/src/components/issue/AssigneeSelector.tsx`
- Modify: `frontend/src/components/issue/SprintSelector.tsx`
- Modify: `frontend/src/components/issue/StoryPointsSelector.tsx`
- Modify: `frontend/src/components/issue/DueDatePicker.tsx`
- Modify: `frontend/scripts/i18n-allowlist.json`

**Interfaces:**
- Consumes: `issues-fields:assignee.*`, `issues-fields:sprint.*`, `issues-fields:storyPoints.*`, `issues-fields:dueDate.*` (Task 1); `formatDate` from `@/i18n/format`.

- [ ] **Step 1: `AssigneeSelector.tsx`**

Add after `import type { User } from '@/types'`:

```ts
import { useTranslation } from 'react-i18next'
```

Add as the first line of the `AssigneeSelector` body (before `const [open, setOpen] = useState(false)`):

```ts
  const { t } = useTranslation('issues-fields')
```

Replace `{value ?? 'Unassigned'}` with `{value ?? t('assignee.unassigned')}`.
Replace `placeholder="Search members…"` with `placeholder={t('assignee.searchPlaceholder')}`.
Replace the "Unassign" button text — replace:

```tsx
              Unassign
```

with:

```tsx
              {t('assignee.unassign')}
```

- [ ] **Step 2: `SprintSelector.tsx`**

Add after `import type { Sprint } from '@/types'`:

```ts
import { useTranslation } from 'react-i18next'
```

Add as the first line of the `SprintSelector` body (before `const [open, setOpen] = useState(false)`):

```ts
  const { t } = useTranslation('issues-fields')
```

Replace `{value ?? 'No sprint'}` with `{value ?? t('sprint.none')}`.
Replace the clear-button text — replace:

```tsx
              No sprint
```

with:

```tsx
              {t('sprint.none')}
```

Replace `No active sprints` — replace:

```tsx
            <p className="px-3 py-2 text-xs text-gray-600">No active sprints</p>
```

with:

```tsx
            <p className="px-3 py-2 text-xs text-gray-600">{t('sprint.empty')}</p>
```

- [ ] **Step 3: `StoryPointsSelector.tsx`**

Add after `import { useState, useRef, useEffect } from 'react'`:

```ts
import { useTranslation } from 'react-i18next'
```

Add as the first line of the `StoryPointsSelector` body (before `const [open, setOpen] = useState(false)`):

```ts
  const { t } = useTranslation('issues-fields')
```

Replace `{value != null ? value : 'Set points'}` with `{value != null ? value : t('storyPoints.set')}`.
Replace the clear button (the `—` glyph stays literal) — replace:

```tsx
            — Clear
```

with:

```tsx
            — {t('storyPoints.clear')}
```

- [ ] **Step 4: `DueDatePicker.tsx` (localize + `format.ts` date)**

Add after `import { useState, useRef, useEffect } from 'react'`:

```ts
import { useTranslation } from 'react-i18next'
import { formatDate } from '@/i18n/format'
```

Add as the first line of the `DueDatePicker` body (before `const [open, setOpen] = useState(false)`):

```ts
  const { t } = useTranslation('issues-fields')
```

Replace:

```tsx
  const display = value ? new Date(value).toLocaleDateString() : 'No due date'
```

with:

```tsx
  const display = value ? formatDate(value) : t('dueDate.none')
```

Replace the clear button — replace:

```tsx
              Clear due date
```

with:

```tsx
              {t('dueDate.clear')}
```

- [ ] **Step 5: Remove the four files from the allowlist**

In `frontend/scripts/i18n-allowlist.json`, delete these lines:

```json
  "src/components/issue/AssigneeSelector.tsx",
  "src/components/issue/DueDatePicker.tsx",
  "src/components/issue/SprintSelector.tsx",
  "src/components/issue/StoryPointsSelector.tsx",
```

- [ ] **Step 6: Verify scanner + build**

Run: `cd frontend && npm run scan:i18n && npm run build`
Expected: `i18n-scan: OK — 0 hardcoded strings outside the allowlist (59 file(s) still allowlisted).` and a successful build. If the scanner reports a leftover string in any of these four files, localize it (reuse an existing key if one fits) before proceeding.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/components/issue/AssigneeSelector.tsx frontend/src/components/issue/SprintSelector.tsx frontend/src/components/issue/StoryPointsSelector.tsx frontend/src/components/issue/DueDatePicker.tsx frontend/scripts/i18n-allowlist.json
git commit -m "feat(i18n): localize assignee/sprint/story-points/due-date selectors"
```

---

### Task 4: Localize LabelSelector & VersionSelector

**Files:**
- Modify: `frontend/src/components/issue/LabelSelector.tsx`
- Modify: `frontend/src/components/issue/VersionSelector.tsx`
- Modify: `frontend/scripts/i18n-allowlist.json`

**Interfaces:**
- Consumes: `issues-fields:none`, `issues-fields:label.*`, `issues-fields:version.*` (Task 1).

- [ ] **Step 1: `LabelSelector.tsx`**

Add after `import { useCreateLabel } from '@/hooks/useLabels'`:

```ts
import { useTranslation } from 'react-i18next'
```

Add as the first line of the `LabelSelector` body (before `const [open, setOpen] = useState(false)`):

```ts
  const { t } = useTranslation('issues-fields')
```

Replace the empty-state span — replace:

```tsx
          ? <span className="text-sm text-gray-500 hover:text-gray-300">None</span>
```

with:

```tsx
          ? <span className="text-sm text-gray-500 hover:text-gray-300">{t('none')}</span>
```

Replace `placeholder="Search labels…"` with `placeholder={t('label.searchPlaceholder')}`.

Replace the create-label button (interpolation; `+` glyph stays literal) — replace:

```tsx
              + Create label "{search.trim()}"
```

with:

```tsx
              + {t('label.create', { name: search.trim() })}
```

- [ ] **Step 2: `VersionSelector.tsx`**

Add after `import { VersionChip } from './VersionChip'`:

```ts
import { useTranslation } from 'react-i18next'
```

Add as the first line of the `VersionSelector` body (before `const [open, setOpen] = useState(false)`):

```ts
  const { t } = useTranslation('issues-fields')
```

Replace the empty-state span — replace:

```tsx
          ? <span className="text-sm text-gray-500 hover:text-gray-300">None</span>
```

with:

```tsx
          ? <span className="text-sm text-gray-500 hover:text-gray-300">{t('none')}</span>
```

Replace the no-versions hint — replace:

```tsx
            <p className="px-3 py-2 text-sm text-gray-500">No versions. Create them in project settings.</p>
```

with:

```tsx
            <p className="px-3 py-2 text-sm text-gray-500">{t('version.empty')}</p>
```

- [ ] **Step 3: Remove the two files from the allowlist**

In `frontend/scripts/i18n-allowlist.json`, delete these lines:

```json
  "src/components/issue/LabelSelector.tsx",
  "src/components/issue/VersionSelector.tsx",
```

- [ ] **Step 4: Verify scanner + build**

Run: `cd frontend && npm run scan:i18n && npm run build`
Expected: `i18n-scan: OK — 0 hardcoded strings outside the allowlist (57 file(s) still allowlisted).` and a successful build.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/issue/LabelSelector.tsx frontend/src/components/issue/VersionSelector.tsx frontend/scripts/i18n-allowlist.json
git commit -m "feat(i18n): localize label & version selectors"
```

---

### Task 5: Localize CustomFieldInput, RichTextEditor, AttachmentPanel

**Files:**
- Modify: `frontend/src/components/issue/CustomFieldInput.tsx`
- Modify: `frontend/src/components/issue/RichTextEditor.tsx`
- Modify: `frontend/src/components/attachments/AttachmentPanel.tsx`
- Modify: `frontend/scripts/i18n-allowlist.json`

**Interfaces:**
- Consumes: `issues-fields:customField.none`, `issues-fields:richText.*`, `issues-fields:attachment.*` (Task 1); `common:loading` (existing).

- [ ] **Step 1: `CustomFieldInput.tsx`**

Add after `import type { CustomFieldDefinition, CustomFieldValue } from '@/types'`:

```ts
import { useTranslation } from 'react-i18next'
```

Add as the first line of the `CustomFieldInput` body (before `const label = …`):

```ts
  const { t } = useTranslation('issues-fields')
```

Replace the DROPDOWN placeholder option — replace:

```tsx
          <option value="">— None —</option>
```

with:

```tsx
          <option value="">{t('customField.none')}</option>
```

(Leave `placeholder={label}` and `{opt.label}` unchanged — they render user data.)

- [ ] **Step 2: `RichTextEditor.tsx`**

Add after `import { useState, useEffect, useRef } from 'react'`:

```ts
import { useTranslation } from 'react-i18next'
```

Add as the first line of the `RichTextEditor` body (before `const [editing, setEditing] = useState(false)`):

```ts
  const { t } = useTranslation('issues-fields')
```

Replace the tiptap placeholder config — replace:

```ts
      Placeholder.configure({ placeholder: 'Add a description…' }),
```

with:

```ts
      Placeholder.configure({ placeholder: t('richText.placeholder') }),
```

Replace the empty-state span — replace:

```tsx
          : <span className="text-gray-600 italic">Add a description…</span>}
```

with:

```tsx
          : <span className="text-gray-600 italic">{t('richText.placeholder')}</span>}
```

Localize the toolbar `title` tooltips (the terse `label` glyphs stay as typographic markers per the Global Constraints). Replace the toolbar button descriptor array:

```tsx
          { label: 'B', title: 'Bold', action: () => editor?.chain().focus().toggleBold().run(), active: () => editor?.isActive('bold') },
          { label: 'I', title: 'Italic', action: () => editor?.chain().focus().toggleItalic().run(), active: () => editor?.isActive('italic') },
          { label: '<>', title: 'Code', action: () => editor?.chain().focus().toggleCode().run(), active: () => editor?.isActive('code') },
          { label: '• List', title: 'Bullet list', action: () => editor?.chain().focus().toggleBulletList().run(), active: () => editor?.isActive('bulletList') },
          { label: '1. List', title: 'Ordered list', action: () => editor?.chain().focus().toggleOrderedList().run(), active: () => editor?.isActive('orderedList') },
```

with:

```tsx
          { label: 'B', title: t('richText.bold'), action: () => editor?.chain().focus().toggleBold().run(), active: () => editor?.isActive('bold') },
          { label: 'I', title: t('richText.italic'), action: () => editor?.chain().focus().toggleItalic().run(), active: () => editor?.isActive('italic') },
          { label: '<>', title: t('richText.code'), action: () => editor?.chain().focus().toggleCode().run(), active: () => editor?.isActive('code') },
          { label: '• List', title: t('richText.bulletList'), action: () => editor?.chain().focus().toggleBulletList().run(), active: () => editor?.isActive('bulletList') },
          { label: '1. List', title: t('richText.orderedList'), action: () => editor?.chain().focus().toggleOrderedList().run(), active: () => editor?.isActive('orderedList') },
```

> Note: the tiptap `Placeholder` is read at editor-init, so a mid-edit language switch won't live-update that in-editor placeholder until re-mount; the visible empty-state span updates immediately. Acceptable known-minor.

- [ ] **Step 3: `AttachmentPanel.tsx`**

Add after `import { attachmentsApi } from '@/api/attachments'`:

```ts
import { useTranslation } from 'react-i18next'
```

Add as the first line of the `AttachmentPanel` body (before `const { data: attachments = [], isLoading } = …`):

```ts
  const { t } = useTranslation('issues-fields')
```

Localize the confirm (scanner-blind, DoD-required) — replace:

```tsx
    if (confirm(`Delete "${filename}"?`)) {
```

with:

```tsx
    if (confirm(t('attachment.confirmDelete', { filename }))) {
```

Replace the heading — replace:

```tsx
        <h3 className="text-sm font-semibold text-gray-400 uppercase tracking-wide">Attachments</h3>
```

with:

```tsx
        <h3 className="text-sm font-semibold text-gray-400 uppercase tracking-wide">{t('attachment.heading')}</h3>
```

Replace the upload-button label (the `+` glyph stays as a template-literal prefix, not a translated fragment) — replace:

```tsx
              {upload.isPending ? 'Uploading...' : '+ Upload'}
```

with:

```tsx
              {upload.isPending ? t('attachment.uploading') : `+ ${t('attachment.upload')}`}
```

Replace the loading line (reuse shared `common:loading`) — replace:

```tsx
      {isLoading && <div className="text-gray-500 text-sm">Loading...</div>}
```

with:

```tsx
      {isLoading && <div className="text-gray-500 text-sm">{t('common:loading')}</div>}
```

Replace the empty state — replace:

```tsx
        <p className="text-gray-600 text-sm italic">No attachments</p>
```

with:

```tsx
        <p className="text-gray-600 text-sm italic">{t('attachment.empty')}</p>
```

(Leave `📎`, `✕`, and `formatBytes` `B`/`KB`/`MB` units unchanged.)

- [ ] **Step 4: Remove the three files from the allowlist**

In `frontend/scripts/i18n-allowlist.json`, delete these lines:

```json
  "src/components/attachments/AttachmentPanel.tsx",
  "src/components/issue/CustomFieldInput.tsx",
  "src/components/issue/RichTextEditor.tsx",
```

- [ ] **Step 5: Verify scanner + build**

Run: `cd frontend && npm run scan:i18n && npm run build`
Expected: `i18n-scan: OK — 0 hardcoded strings outside the allowlist (54 file(s) still allowlisted).` and a successful build. If the scanner reports a leftover string, localize it before proceeding.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/issue/CustomFieldInput.tsx frontend/src/components/issue/RichTextEditor.tsx frontend/src/components/attachments/AttachmentPanel.tsx frontend/scripts/i18n-allowlist.json
git commit -m "feat(i18n): localize custom-field/rich-text/attachment components"
```

---

### Task 6: Finalize S2 — full gate + flip coverage matrix

**Files:**
- Modify: `docs/superpowers/specs/2026-07-13-i18n-full-rollout-design.md`

- [ ] **Step 1: Run the full slice gate**

Run: `cd frontend && npm run test:i18n && npm run lint:i18n && npm run build`
Expected: scanner self-tests pass (17/17), `i18n-scan: OK — 0 hardcoded strings outside the allowlist (54 file(s) still allowlisted).`, `i18n-parity: OK …`, and a successful build. The nine S2 files must be absent from `frontend/scripts/i18n-allowlist.json` (63 baseline − 9 = 54 entries); `VersionTag.tsx` must still be present.

- [ ] **Step 2: Manual DE/EN browser check**

Start the dev server (`cd frontend && npm run dev`), switch language via Settings → Profile, and confirm on an issue detail (open the sidebar selectors) and the issue list: type/priority labels, assignee/sprint/story-points/due-date pickers, label & version selectors (incl. "Create label" and empty states), the custom-field dropdown "None", the rich-text placeholder + toolbar tooltips, and the attachment panel (heading, upload button, empty state, delete confirm) all switch between English and German with no raw keys and no layout breakage from longer German strings (watch the selector buttons/badges).

- [ ] **Step 3: Flip S2 to ✅ in the coverage matrix**

In `docs/superpowers/specs/2026-07-13-i18n-full-rollout-design.md`, replace the S2 matrix row:

```
| S2 | `issues-fields` | ⬜ | StatusBadge, TypeSelector, PrioritySelector, AssigneeSelector, LabelSelector, LabelChip, VersionSelector, VersionChip, VersionTag, SprintSelector, StoryPointsSelector, DueDatePicker, CustomFieldInput, RichTextEditor, AttachmentPanel |
```

with:

```
| S2 | `issues-fields` | ✅ | type/priority enum labels + Assignee/Label/Version/Sprint/StoryPoints/DueDate/CustomField selectors, RichTextEditor, AttachmentPanel localized (+ IssueListPage priority retrofit); StatusBadge/LabelChip/VersionChip already string-free; **VersionTag deferred to S18** (app-version chrome, not an issue field) |
```

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/specs/2026-07-13-i18n-full-rollout-design.md
git commit -m "docs(i18n): mark S2 issues-fields slice complete in coverage matrix"
```

---

## Self-Review

**Spec coverage** (against master-spec Abschnitt 5 DoD + Anhang checklist):
- New `issues-fields` namespace en+de, registered in `index.ts` → Task 1. ✅
- Every user-facing string in the 11 in-scope component files via `t()`, incl. the scanner-blind enum labels, RichTextEditor tooltips/placeholder, and the AttachmentPanel `confirm` → Tasks 2-5. ✅
- Enum labels shared in `issues-fields` (`type.*`/`priority.*`) + IssueListPage retrofit of the S1-deferred `{issue.priority}` → Task 2. ✅
- Dates via `format.ts` (`DueDatePicker`) → Task 3. ✅
- Files removed from allowlist as localized (9 files, 63 → 54) → Tasks 3-5. ✅
- `VersionTag` correctly excluded (stays allowlisted → S18); `StatusBadge`/`LabelChip`/`VersionChip` string-free, untouched → Global Constraints. ✅
- Full gate (`test:i18n`/`lint:i18n`/`build`) + manual DE/EN + matrix flip → Task 6. ✅

**Placeholder scan:** No TBD/TODO; every step has exact old/new code and exact commands with expected output.

**Type/key consistency:** Every `t(...)` key used in Tasks 2-5 is defined in Task 1's en+de JSON. Dynamic keys `type.${value}`/`priority.${value}` cover the exact enum members defined under `type`/`priority`. Interpolation variables match: `label.create` uses `{{name}}` ↔ `{ name }`; `attachment.confirmDelete` uses `{{filename}}` ↔ `{ filename }`. Allowlist arithmetic is consistent across tasks: 63 (start) → 59 (Task 3, −4) → 57 (Task 4, −2) → 54 (Task 5, −3).
