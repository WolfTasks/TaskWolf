# i18n Rollout — Session 13 (`automation` namespace) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Localize the S13 `automation` slice — `AutomationPage`, `AutomationRuleEditorPage`, `AdminAutomationPage`, `RuleEditor`, `TriggerSelector`, `ConditionGroupBuilder`, `ConditionRow`, `ActionList` (all currently **hard-coded German**), plus the scanner-blind `ActionRow` — into a new `automation` namespace (`en`/`de`), and flip S13 to ✅ in the master-spec coverage matrix.

**Architecture:** Thin execution plan against the locked pattern in `docs/superpowers/specs/2026-07-13-i18n-full-rollout-design.md` (Anhang — Migrations-Checkliste). One new `automation` namespace. These files are the "mixed DE/EN" the spec warned about — their current strings are German, so the **`de` JSON keeps the existing copy and the `en` JSON provides the English translation** (English is the fallback locale). Trigger/condition/operator/action-type labels live in module-level `const` arrays (scanner-blind) and are localized at the render site keyed on the raw enum (S2 precedent). `ActionRow` is not allowlisted (its labels/placeholder are in a const array, not JSX literals) but is user-facing, so it is localized here too for DoD completeness.

**Tech Stack:** react-i18next (`useTranslation`), the Session-0 scanner/parity scripts, Node 20, Vite/tsc build.

## Global Constraints

- **Namespace = `automation`.** In every file use `const { t } = useTranslation('automation')`; reference shared terms with the `common:` prefix (`t('common:cancel')`).
- **en/de stay key-identical** (parity check is authoritative). **en = English, de = the existing German copy.**
- **Variable-shadow trap (3 files).** `TRIGGERS.map(t => …)` (TriggerSelector), `TYPES.map(t => …)` (ConditionRow), `ACTION_TYPES.map(t => …)` (ActionRow) shadow the translator `t`. Rename each map parameter (`tr`, `ct`, `at`) when adding the hook.
- **Scanner-blind enum labels are DoD-required.** `TRIGGERS`/`TYPES`/`ACTION_TYPES` `label` fields, `ACTION_TYPES` `placeholder`, and the `{triggerType.replace(/_/g,' ')}` badges are user-facing; localize at render keyed on the raw enum. Drop the now-unused `label`/`placeholder` const fields (keep `value`/`paramKey`).
- **Logic keywords stay literal.** The `AND`/`OR` toggle buttons render `{l}` (a `GroupLogic` enum, universal) and the `· SYSTEM` suffix is a literal tag — leave both. The `⠿`/`✕`/`&#x270E;`/`&#x2715;`/`+` glyphs stay literal markers next to `{t(...)}`.
- **`ActionRow` is NOT in the allowlist** — do not add or remove it. Localizing it does not change the allowlist; the scanner stays green either way.
- **Remove the 8 allowlisted S13 files** (all except `ActionRow`). **Net for S13: 8 files removed (22 → 14).**
- **Done-per-slice (master spec Abschnitt 5):** `npm run test:i18n && npm run lint:i18n && npm run build` all green; manual DE/EN browser check; matrix row flipped to ✅.
- All paths relative to repo root `C:\Users\Admin\IdeaProjects\TaskWolf`. Work in an isolated worktree branched from `origin/main` (which must contain merged S0–S12; the allowlist starts at 22).

---

### Task 1: Create the `automation` namespace + register it

**Files:**
- Create: `frontend/src/i18n/locales/en/automation.json`
- Create: `frontend/src/i18n/locales/de/automation.json`
- Modify: `frontend/src/i18n/index.ts`

**Interfaces:**
- Produces: the `automation` namespace keys consumed by Tasks 2–5.

- [ ] **Step 1: Create `frontend/src/i18n/locales/en/automation.json`**

```json
{
  "title": "Automation",
  "systemTitle": "System-wide Automation",
  "loading": "Loading rules…",
  "loadingSystem": "Loading system-wide rules…",
  "newRule": "New Rule",
  "newRuleTitle": "New Automation Rule",
  "empty": "No automation rules yet. Click \"+ New Rule\" to get started.",
  "active": "Active",
  "inactive": "Inactive",
  "trigger": {
    "ISSUE_CREATED": "Issue created",
    "STATUS_CHANGED": "Status changed",
    "PRIORITY_CHANGED": "Priority changed",
    "ASSIGNEE_CHANGED": "Assignee changed",
    "COMMENT_ADDED": "Comment added",
    "SPRINT_STARTED": "Sprint started",
    "SPRINT_COMPLETED": "Sprint completed"
  },
  "condition": {
    "ISSUE_TYPE": "Issue type",
    "PRIORITY": "Priority",
    "ASSIGNEE": "Assignee",
    "STATUS": "Status",
    "STORY_POINTS": "Story Points",
    "valuePlaceholder": "Value"
  },
  "operator": {
    "IS": "is",
    "IS_NOT": "is not",
    "CONTAINS": "contains",
    "GT": ">",
    "LT": "<"
  },
  "action": {
    "SET_STATUS": "Set status",
    "SET_ASSIGNEE": "Set assignee",
    "SET_PRIORITY": "Set priority",
    "SEND_NOTIFICATION": "Send notification",
    "CREATE_COMMENT": "Create comment",
    "CREATE_SUBTASK": "Create subtask"
  },
  "actionPlaceholder": {
    "SET_STATUS": "Status ID",
    "SET_ASSIGNEE": "User ID",
    "SET_PRIORITY": "CRITICAL | HIGH | MEDIUM | LOW",
    "SEND_NOTIFICATION": "Message",
    "CREATE_COMMENT": "Comment text",
    "CREATE_SUBTASK": "Subtask title"
  },
  "editor": {
    "nameLabel": "Rule name",
    "namePlaceholder": "e.g. auto-assign CRITICAL issues",
    "when": "WHEN",
    "triggerLabel": "Trigger",
    "if": "IF",
    "conditions": "Conditions",
    "then": "THEN",
    "actionsLabel": "Actions (in order)",
    "addCondition": "Condition",
    "addGroup": "Group",
    "addAction": "Add action",
    "save": "Save Rule"
  }
}
```

- [ ] **Step 2: Create `frontend/src/i18n/locales/de/automation.json`** (same keys, existing German copy)

```json
{
  "title": "Automation",
  "systemTitle": "Systemweite Automation",
  "loading": "Regeln werden geladen…",
  "loadingSystem": "Systemweite Regeln werden geladen…",
  "newRule": "Neue Regel",
  "newRuleTitle": "Neue Automation-Regel",
  "empty": "Noch keine Automation-Regeln. Klicke auf \"+ Neue Regel\" um zu starten.",
  "active": "Aktiv",
  "inactive": "Inaktiv",
  "trigger": {
    "ISSUE_CREATED": "Issue erstellt",
    "STATUS_CHANGED": "Status geändert",
    "PRIORITY_CHANGED": "Priorität geändert",
    "ASSIGNEE_CHANGED": "Assignee geändert",
    "COMMENT_ADDED": "Kommentar hinzugefügt",
    "SPRINT_STARTED": "Sprint gestartet",
    "SPRINT_COMPLETED": "Sprint abgeschlossen"
  },
  "condition": {
    "ISSUE_TYPE": "Issue-Typ",
    "PRIORITY": "Priorität",
    "ASSIGNEE": "Assignee",
    "STATUS": "Status",
    "STORY_POINTS": "Story Points",
    "valuePlaceholder": "Wert"
  },
  "operator": {
    "IS": "ist",
    "IS_NOT": "ist nicht",
    "CONTAINS": "enthält",
    "GT": ">",
    "LT": "<"
  },
  "action": {
    "SET_STATUS": "Status setzen",
    "SET_ASSIGNEE": "Assignee setzen",
    "SET_PRIORITY": "Priorität setzen",
    "SEND_NOTIFICATION": "Notification senden",
    "CREATE_COMMENT": "Kommentar erstellen",
    "CREATE_SUBTASK": "Subtask erstellen"
  },
  "actionPlaceholder": {
    "SET_STATUS": "Status-ID",
    "SET_ASSIGNEE": "User-ID",
    "SET_PRIORITY": "CRITICAL | HIGH | MEDIUM | LOW",
    "SEND_NOTIFICATION": "Nachricht",
    "CREATE_COMMENT": "Kommentar-Text",
    "CREATE_SUBTASK": "Subtask-Titel"
  },
  "editor": {
    "nameLabel": "Regelname",
    "namePlaceholder": "z. B. CRITICAL Issues auto-assign",
    "when": "WENN",
    "triggerLabel": "Trigger",
    "if": "FALLS",
    "conditions": "Bedingungen",
    "then": "DANN",
    "actionsLabel": "Actions (in Reihenfolge)",
    "addCondition": "Bedingung",
    "addGroup": "Gruppe",
    "addAction": "Action hinzufügen",
    "save": "Regel speichern"
  }
}
```

- [ ] **Step 3: Register the namespace in `frontend/src/i18n/index.ts`**

Add the imports after the last existing locale import:

```ts
import enAutomation from './locales/en/automation.json'
import deAutomation from './locales/de/automation.json'
```

In `resources`, append `automation` to both languages, and append `'automation'` to the `ns` array:

```ts
      en: { /* …existing… */ automation: enAutomation },
      de: { /* …existing… */ automation: deAutomation },
```
```ts
    ns: [/* …existing… */ 'automation'],
```

- [ ] **Step 4: Verify parity + build** (all 8 files still allowlisted, scanner stays green)

Run: `cd frontend && npm run check:i18n && npm run build`
Expected: `i18n-parity: OK — en/de namespaces and keys match.` then a successful build.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/i18n/locales/en/automation.json frontend/src/i18n/locales/de/automation.json frontend/src/i18n/index.ts
git commit -m "feat(i18n): add automation namespace (en/de)"
```

---

### Task 2: Localize the three automation pages

**Files:**
- Modify: `frontend/src/pages/automation/AutomationPage.tsx`
- Modify: `frontend/src/pages/automation/AutomationRuleEditorPage.tsx`
- Modify: `frontend/src/pages/admin/AdminAutomationPage.tsx`
- Modify: `frontend/scripts/i18n-allowlist.json`

**Interfaces:**
- Consumes: `automation:title/systemTitle/loading/loadingSystem/newRule/newRuleTitle/empty/active/inactive`, `automation:trigger.*` (Task 1).

- [ ] **Step 1: `AutomationPage.tsx` — hook + strings**

Add after line 2 (`import { useAutomationRules, … }`):

```ts
import { useTranslation } from 'react-i18next'
```

Add as the first line of the `AutomationPage` body (before `const { key } = useParams…`):

```ts
  const { t } = useTranslation('automation')
```

Replace:

```tsx
  if (isLoading) return <div className="p-6 text-zinc-400">Lade Regeln...</div>
```
→ `if (isLoading) return <div className="p-6 text-zinc-400">{t('loading')}</div>`

```tsx
        <h1 className="text-xl font-semibold text-zinc-100">Automation</h1>
```
→ `<h1 className="text-xl font-semibold text-zinc-100">{t('title')}</h1>`

```tsx
          + Neue Regel
        </button>
```
→ `+ {t('newRule')}` (inside the same `<button>`)

```tsx
          Noch keine Automation-Regeln. Klicke auf "+ Neue Regel" um zu starten.
```
→ `{t('empty')}`

```tsx
            <div className="text-xs text-zinc-400 mt-0.5">{rule.triggerType.replace(/_/g, ' ')}</div>
```
→ `<div className="text-xs text-zinc-400 mt-0.5">{t(\`trigger.${rule.triggerType}\`)}</div>`

```tsx
            {rule.enabled ? 'Aktiv' : 'Inaktiv'}
```
→ `{rule.enabled ? t('active') : t('inactive')}`

(Leave `{rule.name}`, the pencil `&#x270E;` and cross `&#x2715;` glyphs.)

- [ ] **Step 2: `AutomationRuleEditorPage.tsx` — hook + title**

Add after line 4 (`import type { TriggerType, … }`):

```ts
import { useTranslation } from 'react-i18next'
```

Add as the first line of the `AutomationRuleEditorPage` body (before `const { key } = useParams…`):

```ts
  const { t } = useTranslation('automation')
```

Replace:

```tsx
      <h1 className="text-xl font-semibold text-zinc-100 mb-6">Neue Automation-Regel</h1>
```
→ `<h1 className="text-xl font-semibold text-zinc-100 mb-6">{t('newRuleTitle')}</h1>`

- [ ] **Step 3: `AdminAutomationPage.tsx` — hook + strings**

Add after line 2 (`import { useSystemRules, … }`):

```ts
import { useTranslation } from 'react-i18next'
```

Add as the first line of the `AdminAutomationPage` body (before `const { data, isLoading } = useSystemRules()`):

```ts
  const { t } = useTranslation('automation')
```

Replace:

```tsx
  if (isLoading) return <div className="p-6 text-zinc-400">Lade systemweite Regeln...</div>
```
→ `if (isLoading) return <div className="p-6 text-zinc-400">{t('loadingSystem')}</div>`

```tsx
        <h1 className="text-xl font-semibold text-zinc-100">Systemweite Automation</h1>
```
→ `<h1 className="text-xl font-semibold text-zinc-100">{t('systemTitle')}</h1>`

```tsx
          + Neue Regel
        </button>
```
→ `+ {t('newRule')}` (inside the same `<button>`)

```tsx
            <div className="text-xs text-zinc-400">{rule.triggerType.replace(/_/g, ' ')} · SYSTEM</div>
```
→ `<div className="text-xs text-zinc-400">{t(\`trigger.${rule.triggerType}\`)} · SYSTEM</div>` (`· SYSTEM` stays literal)

```tsx
            {rule.enabled ? 'Aktiv' : 'Inaktiv'}
```
→ `{rule.enabled ? t('active') : t('inactive')}`

- [ ] **Step 4: Remove the three page files from the allowlist**

In `frontend/scripts/i18n-allowlist.json`, delete these lines:

```json
  "src/pages/admin/AdminAutomationPage.tsx",
  "src/pages/automation/AutomationPage.tsx",
  "src/pages/automation/AutomationRuleEditorPage.tsx",
```

- [ ] **Step 5: Verify scanner + build**

Run: `cd frontend && npm run scan:i18n && npm run build`
Expected: `i18n-scan: OK — 0 hardcoded strings outside the allowlist (19 file(s) still allowlisted).` and a successful build.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/pages/automation/AutomationPage.tsx frontend/src/pages/automation/AutomationRuleEditorPage.tsx frontend/src/pages/admin/AdminAutomationPage.tsx frontend/scripts/i18n-allowlist.json
git commit -m "feat(i18n): localize automation pages (project + system-wide)"
```

---

### Task 3: Localize RuleEditor + TriggerSelector

**Files:**
- Modify: `frontend/src/components/automation/RuleEditor.tsx`
- Modify: `frontend/src/components/automation/TriggerSelector.tsx`
- Modify: `frontend/scripts/i18n-allowlist.json`

**Interfaces:**
- Consumes: `automation:editor.*`, `automation:trigger.*` (Task 1); `common:cancel` (existing).

- [ ] **Step 1: `RuleEditor.tsx` — hook + editor strings**

Add after line 5 (`import { ActionList } from './ActionList'`):

```ts
import { useTranslation } from 'react-i18next'
```

Add as the first line of the `RuleEditor` body (before `const [name, setName] = useState(initialName)`):

```ts
  const { t } = useTranslation('automation')
```

Replace:

```tsx
        <label className="text-xs uppercase text-zinc-400 mb-1 block">Regelname</label>
```
→ `<label className="text-xs uppercase text-zinc-400 mb-1 block">{t('editor.nameLabel')}</label>`

```tsx
          placeholder="z.B. CRITICAL Issues auto-assign"
```
→ `placeholder={t('editor.namePlaceholder')}`

```tsx
          <span className="bg-amber-500 text-zinc-900 text-xs font-bold px-2 py-0.5 rounded-full">IF</span>
          <span className="text-zinc-400 text-sm">Bedingungen</span>
```

with:

```tsx
          <span className="bg-amber-500 text-zinc-900 text-xs font-bold px-2 py-0.5 rounded-full">{t('editor.if')}</span>
          <span className="text-zinc-400 text-sm">{t('editor.conditions')}</span>
```

```tsx
          <span className="bg-emerald-500 text-zinc-900 text-xs font-bold px-2 py-0.5 rounded-full">THEN</span>
          <span className="text-zinc-400 text-sm">Actions (in Reihenfolge)</span>
```

with:

```tsx
          <span className="bg-emerald-500 text-zinc-900 text-xs font-bold px-2 py-0.5 rounded-full">{t('editor.then')}</span>
          <span className="text-zinc-400 text-sm">{t('editor.actionsLabel')}</span>
```

```tsx
        <button onClick={onCancel} className="bg-zinc-700 text-zinc-300 text-sm rounded-lg px-4 py-2 hover:bg-zinc-600">Abbrechen</button>
```
→ `<button onClick={onCancel} className="bg-zinc-700 text-zinc-300 text-sm rounded-lg px-4 py-2 hover:bg-zinc-600">{t('common:cancel')}</button>`

```tsx
          Regel speichern
        </button>
```
→ `{t('editor.save')}` (inside the same `<button>`)

- [ ] **Step 2: `TriggerSelector.tsx` — drop const labels + hook (mind the `t` shadow)**

Replace the imports + `TRIGGERS` array:

```tsx
import type { TriggerType } from '../../types'

const TRIGGERS: { value: TriggerType; label: string }[] = [
  { value: 'ISSUE_CREATED', label: 'Issue erstellt' },
  { value: 'STATUS_CHANGED', label: 'Status geändert' },
  { value: 'PRIORITY_CHANGED', label: 'Priorität geändert' },
  { value: 'ASSIGNEE_CHANGED', label: 'Assignee geändert' },
  { value: 'COMMENT_ADDED', label: 'Kommentar hinzugefügt' },
  { value: 'SPRINT_STARTED', label: 'Sprint gestartet' },
  { value: 'SPRINT_COMPLETED', label: 'Sprint abgeschlossen' },
]
```

with:

```tsx
import { useTranslation } from 'react-i18next'
import type { TriggerType } from '../../types'

const TRIGGERS: TriggerType[] = [
  'ISSUE_CREATED', 'STATUS_CHANGED', 'PRIORITY_CHANGED', 'ASSIGNEE_CHANGED',
  'COMMENT_ADDED', 'SPRINT_STARTED', 'SPRINT_COMPLETED',
]
```

Add the hook as the first line of the `TriggerSelector` body (before `return (`):

```ts
  const { t } = useTranslation('automation')
```

Replace the WHEN badge + Trigger label:

```tsx
        <span className="bg-indigo-500 text-white text-xs font-bold px-2 py-0.5 rounded-full">WHEN</span>
        <span className="text-zinc-400 text-sm">Trigger</span>
```

with:

```tsx
        <span className="bg-indigo-500 text-white text-xs font-bold px-2 py-0.5 rounded-full">{t('editor.when')}</span>
        <span className="text-zinc-400 text-sm">{t('editor.triggerLabel')}</span>
```

Replace the options (rename the shadowing map param `t` → `tr`):

```tsx
        {TRIGGERS.map(t => <option key={t.value} value={t.value}>{t.label}</option>)}
```
→ `{TRIGGERS.map(tr => <option key={tr} value={tr}>{t(\`trigger.${tr}\`)}</option>)}`

- [ ] **Step 3: Remove both files from the allowlist**

In `frontend/scripts/i18n-allowlist.json`, delete these lines:

```json
  "src/components/automation/RuleEditor.tsx",
  "src/components/automation/TriggerSelector.tsx",
```

- [ ] **Step 4: Verify scanner + build**

Run: `cd frontend && npm run scan:i18n && npm run build`
Expected: `i18n-scan: OK — 0 hardcoded strings outside the allowlist (17 file(s) still allowlisted).` and a successful build. Confirm tsc did not error on a shadowed `t` (Step 2 rename to `tr`).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/automation/RuleEditor.tsx frontend/src/components/automation/TriggerSelector.tsx frontend/scripts/i18n-allowlist.json
git commit -m "feat(i18n): localize automation rule editor + trigger selector"
```

---

### Task 4: Localize ConditionGroupBuilder + ConditionRow

**Files:**
- Modify: `frontend/src/components/automation/ConditionGroupBuilder.tsx`
- Modify: `frontend/src/components/automation/ConditionRow.tsx`
- Modify: `frontend/scripts/i18n-allowlist.json`

**Interfaces:**
- Consumes: `automation:editor.addCondition/addGroup`, `automation:condition.*`, `automation:operator.*` (Task 1).

- [ ] **Step 1: `ConditionGroupBuilder.tsx` — hook + add buttons**

Add after line 2 (`import { ConditionRow } from './ConditionRow'`):

```ts
import { useTranslation } from 'react-i18next'
```

Add as the first line of the `ConditionGroupBuilder` body (before `const setLogic = …`):

```ts
  const { t } = useTranslation('automation')
```

Replace:

```tsx
        <button onClick={addCondition} className="text-xs text-zinc-400 hover:text-zinc-200">+ Bedingung</button>
        {depth < 2 && (
          <button onClick={addChildGroup} className="text-xs text-zinc-400 hover:text-zinc-200">+ Gruppe</button>
        )}
```

with:

```tsx
        <button onClick={addCondition} className="text-xs text-zinc-400 hover:text-zinc-200">+ {t('editor.addCondition')}</button>
        {depth < 2 && (
          <button onClick={addChildGroup} className="text-xs text-zinc-400 hover:text-zinc-200">+ {t('editor.addGroup')}</button>
        )}
```

(Leave the `AND`/`OR` `{l}` toggle buttons and the `✕` glyph.)

- [ ] **Step 2: `ConditionRow.tsx` — drop const labels + hook (mind the `t` shadow)**

Replace the imports + `TYPES` array (keep `OPERATORS`):

```tsx
import type { RuleCondition, ConditionType } from '../../types'

const TYPES: { value: ConditionType; label: string }[] = [
  { value: 'ISSUE_TYPE', label: 'Issue-Typ' },
  { value: 'PRIORITY', label: 'Priorität' },
  { value: 'ASSIGNEE', label: 'Assignee' },
  { value: 'STATUS', label: 'Status' },
  { value: 'STORY_POINTS', label: 'Story Points' },
]

const OPERATORS = ['IS', 'IS_NOT', 'CONTAINS', 'GT', 'LT']
```

with:

```tsx
import { useTranslation } from 'react-i18next'
import type { RuleCondition, ConditionType } from '../../types'

const TYPES: ConditionType[] = ['ISSUE_TYPE', 'PRIORITY', 'ASSIGNEE', 'STATUS', 'STORY_POINTS']

const OPERATORS = ['IS', 'IS_NOT', 'CONTAINS', 'GT', 'LT']
```

Add the hook as the first line of the `ConditionRow` body (before `return (`):

```ts
  const { t } = useTranslation('automation')
```

Replace the type options (rename the shadowing map param `t` → `ct`):

```tsx
        {TYPES.map(t => <option key={t.value} value={t.value}>{t.label}</option>)}
```
→ `{TYPES.map(ct => <option key={ct} value={ct}>{t(\`condition.${ct}\`)}</option>)}`

Replace the operator options:

```tsx
        {OPERATORS.map(op => <option key={op} value={op}>{op}</option>)}
```
→ `{OPERATORS.map(op => <option key={op} value={op}>{t(\`operator.${op}\`)}</option>)}`

Replace the value placeholder:

```tsx
        placeholder="Wert"
```
→ `placeholder={t('condition.valuePlaceholder')}`

- [ ] **Step 3: Remove both files from the allowlist**

In `frontend/scripts/i18n-allowlist.json`, delete these lines:

```json
  "src/components/automation/ConditionGroupBuilder.tsx",
  "src/components/automation/ConditionRow.tsx",
```

- [ ] **Step 4: Verify scanner + build**

Run: `cd frontend && npm run scan:i18n && npm run build`
Expected: `i18n-scan: OK — 0 hardcoded strings outside the allowlist (15 file(s) still allowlisted).` and a successful build. Confirm tsc did not error on a shadowed `t` (Step 2 rename to `ct`).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/automation/ConditionGroupBuilder.tsx frontend/src/components/automation/ConditionRow.tsx frontend/scripts/i18n-allowlist.json
git commit -m "feat(i18n): localize automation condition builder + row"
```

---

### Task 5: Localize ActionList + ActionRow

**Files:**
- Modify: `frontend/src/components/automation/ActionList.tsx`
- Modify: `frontend/src/components/automation/ActionRow.tsx`
- Modify: `frontend/scripts/i18n-allowlist.json`

**Interfaces:**
- Consumes: `automation:editor.addAction`, `automation:action.*`, `automation:actionPlaceholder.*` (Task 1).

- [ ] **Step 1: `ActionList.tsx` — hook + add button**

Add after line 4 (`import { ActionRow } from './ActionRow'`):

```ts
import { useTranslation } from 'react-i18next'
```

Add as the first line of the `ActionList` body (before `function handleDragEnd…`):

```ts
  const { t } = useTranslation('automation')
```

Replace:

```tsx
        + Action hinzufügen
      </button>
```
→ `+ {t('editor.addAction')}` (inside the same `<button>`)

- [ ] **Step 2: `ActionRow.tsx` — drop const labels/placeholders + hook (mind the `t` shadow)**

Replace the imports + `ACTION_TYPES` array:

```tsx
import type { RuleAction, ActionType } from '../../types'
import { useSortable } from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'

const ACTION_TYPES: { value: ActionType; label: string; paramKey: string; placeholder: string }[] = [
  { value: 'SET_STATUS', label: 'Status setzen', paramKey: 'statusId', placeholder: 'Status-ID' },
  { value: 'SET_ASSIGNEE', label: 'Assignee setzen', paramKey: 'assigneeId', placeholder: 'User-ID' },
  { value: 'SET_PRIORITY', label: 'Priorität setzen', paramKey: 'priority', placeholder: 'CRITICAL | HIGH | MEDIUM | LOW' },
  { value: 'SEND_NOTIFICATION', label: 'Notification senden', paramKey: 'message', placeholder: 'Nachricht' },
  { value: 'CREATE_COMMENT', label: 'Kommentar erstellen', paramKey: 'body', placeholder: 'Kommentar-Text' },
  { value: 'CREATE_SUBTASK', label: 'Subtask erstellen', paramKey: 'title', placeholder: 'Subtask-Titel' },
]
```

with:

```tsx
import { useTranslation } from 'react-i18next'
import type { RuleAction, ActionType } from '../../types'
import { useSortable } from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'

const ACTION_TYPES: { value: ActionType; paramKey: string }[] = [
  { value: 'SET_STATUS', paramKey: 'statusId' },
  { value: 'SET_ASSIGNEE', paramKey: 'assigneeId' },
  { value: 'SET_PRIORITY', paramKey: 'priority' },
  { value: 'SEND_NOTIFICATION', paramKey: 'message' },
  { value: 'CREATE_COMMENT', paramKey: 'body' },
  { value: 'CREATE_SUBTASK', paramKey: 'title' },
]
```

Add the hook as the first line of the `ActionRow` body (before `const { attributes, … } = useSortable…`):

```ts
  const { t } = useTranslation('automation')
```

Replace the type options (rename the shadowing map param `t` → `at`):

```tsx
        {ACTION_TYPES.map(t => <option key={t.value} value={t.value}>{t.label}</option>)}
```
→ `{ACTION_TYPES.map(at => <option key={at.value} value={at.value}>{t(\`action.${at.value}\`)}</option>)}`

Replace the param placeholder (it previously used `meta.placeholder`; `meta` still supplies `paramKey`):

```tsx
        placeholder={meta.placeholder}
```
→ `placeholder={t(\`actionPlaceholder.${action.type}\`)}`

(Leave `{action.position + 1}` and the `⠿`/`✕` glyphs. `meta.paramKey` stays unchanged.)

- [ ] **Step 3: Remove ActionList from the allowlist**

`ActionRow.tsx` is **not** in the allowlist — do not touch it there. In `frontend/scripts/i18n-allowlist.json`, delete only this line:

```json
  "src/components/automation/ActionList.tsx",
```

- [ ] **Step 4: Verify scanner + build**

Run: `cd frontend && npm run scan:i18n && npm run build`
Expected: `i18n-scan: OK — 0 hardcoded strings outside the allowlist (14 file(s) still allowlisted).` and a successful build. Confirm tsc did not error on a shadowed `t` (Step 2 rename to `at`).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/automation/ActionList.tsx frontend/src/components/automation/ActionRow.tsx frontend/scripts/i18n-allowlist.json
git commit -m "feat(i18n): localize automation action list + row"
```

---

### Task 6: Finalize S13 — full gate + flip coverage matrix

**Files:**
- Modify: `docs/superpowers/specs/2026-07-13-i18n-full-rollout-design.md`

- [ ] **Step 1: Run the full slice gate**

Run: `cd frontend && npm run test:i18n && npm run lint:i18n && npm run build`
Expected: scanner self-tests pass, `i18n-scan: OK — 0 hardcoded strings outside the allowlist (14 file(s) still allowlisted).`, `i18n-parity: OK — en/de namespaces and keys match.`, and a successful build. All 8 allowlisted S13 files must be absent from the allowlist (22 baseline − 8 = 14 entries).

- [ ] **Step 2: Manual DE/EN browser check**

Start the dev server (`cd frontend && npm run dev`), switch language via Settings → Profile, and confirm across automation: the project + system-wide rule lists (loading, title, "+ New Rule", empty state, trigger label per rule, Active/Inactive), and the rule editor (name field, WHEN/IF/THEN badges + labels, trigger dropdown, condition type + operator + value dropdowns, add-condition/group/action buttons, action-type dropdown + its placeholder, Cancel/Save) all switch between English and German with no raw keys. **Watch the operator dropdown width (`w-24`)** — confirm "is not"/"ist nicht"/"contains"/"enthält" are not clipped.

- [ ] **Step 3: Flip S13 to ✅ in the coverage matrix**

In `docs/superpowers/specs/2026-07-13-i18n-full-rollout-design.md`, replace the S13 matrix row:

```
| S13 | `automation` | ⬜ | AutomationPage, AutomationRuleEditorPage, AdminAutomationPage, RuleEditor, ActionList, ActionRow, ConditionGroupBuilder, ConditionRow, TriggerSelector |
```

with:

```
| S13 | `automation` | ✅ | All automation files localized (new `automation` ns; de = former hard-coded German, en = translation); scanner-blind TRIGGERS/TYPES/ACTION_TYPES/OPERATORS labels + placeholders localized at render site; ActionRow localized (not allowlisted); three `t`-shadow renames (tr/ct/at) |
```

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/specs/2026-07-13-i18n-full-rollout-design.md
git commit -m "docs(i18n): mark S13 automation slice complete in coverage matrix"
```

---

## Self-Review

**Spec coverage:**
- New `automation` namespace en+de (de = existing German, en = translation), registered in `index.ts` → Task 1. ✅
- Every user-facing string across all 9 files via `t()` → Tasks 2–5. ✅
- Scanner-blind const-array labels/placeholders (TRIGGERS, TYPES, OPERATORS, ACTION_TYPES) + `{triggerType.replace(...)}` badges localized at render site → Tasks 2–5. ✅
- `ActionRow` localized despite not being allowlisted (DoD completeness) → Task 5. ✅
- All three `t`-shadow traps handled (map params → `tr`/`ct`/`at`) → Tasks 3, 4, 5. ✅
- `AND`/`OR` `{l}` and `· SYSTEM` correctly left literal; `common:cancel` reused → Global Constraints, Task 3. ✅
- 8 allowlisted files removed (22 → 14) → Tasks 2–5. ✅
- Full gate + manual DE/EN + matrix flip → Task 6. ✅

**Placeholder scan:** No TBD/TODO; every step has exact old/new code and exact commands with expected output.

**Type/key consistency:** All keys used in Tasks 2–5 are defined in Task 1's en+de JSON (`title`, `systemTitle`, `loading`, `loadingSystem`, `newRule`, `newRuleTitle`, `empty`, `active`, `inactive`, `trigger.*`, `condition.*`, `operator.*`, `action.*`, `actionPlaceholder.*`, `editor.*`). Dynamic keys cover exactly the enum members present: `trigger.${triggerType}` = 7 TriggerType values; `condition.${ct}` over `TYPES` = 5 ConditionType values; `operator.${op}` over `OPERATORS` = IS/IS_NOT/CONTAINS/GT/LT; `action.${at.value}`/`actionPlaceholder.${action.type}` over `ACTION_TYPES` = 6 ActionType values. `common:cancel` is pre-existing. Allowlist arithmetic: 22 → 19 (T2) → 17 (T3) → 15 (T4) → 14 (T5).
