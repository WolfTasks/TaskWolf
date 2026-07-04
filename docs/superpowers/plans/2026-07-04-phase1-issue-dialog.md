# Phase 1: Issue-Dialog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Issues können als Modal-Dialog über der aktuellen Seite (Board/Backlog/Liste) geöffnet werden, gesteuert über den URL-Parameter `?issue=KEY`; die bestehende Vollseite bleibt als Deep-Link-Fallback.

**Architecture:** Der Inhalt der heutigen `IssueDetailPage` wird in eine wiederverwendbare `IssueDetailContent`-Komponente extrahiert, die sowohl die Vollseite als auch ein neues hand-gerolltes `IssueDialog`-Modal rendert. Ein einmal in `AppLayout` gemounteter `IssueDialogHost` liest `?issue=KEY` und zeigt das Modal über der jeweils aktuellen Seite. Öffner (Board-Karte, Backlog-Zeile, Issue-Liste) setzen nur den Query-Parameter über einen gemeinsamen `useOpenIssue`-Hook.

**Tech Stack:** React 19, TypeScript 6, react-router-dom 7, @tanstack/react-query 5, Tailwind CSS 4, @dnd-kit/core (Board). Modal wird hand-gerollt nach dem bestehenden Muster in `components/sprint/CompleteSprintDialog.tsx` — **keine neue Dependency** (`@radix-ui/react-dialog` bleibt ungenutzt wie bisher).

## Global Constraints

- **Kein neues Test-Framework.** Das Frontend hat keins; automatisches Gate je Task ist `npm run build` (führt `tsc && vite build` aus, also vollständiger Typecheck). Verhalten wird manuell im Browser verifiziert.
- **Keine neuen npm-Dependencies.** Modal hand-rollen nach Vorbild `CompleteSprintDialog.tsx`.
- **Bestehende Query-Keys unverändert:** `useIssue` nutzt `['issues', projectKey, issueKey]`. Modal und Vollseite teilen sich denselben Cache-Eintrag — nicht duplizieren.
- **URL-Parameter beim Setzen/Löschen von `issue` müssen alle übrigen Query-Parameter erhalten** (Filter auf Backlog/Issue-Liste). Immer `new URLSearchParams(prev)` verwenden, nie `setSearchParams({ issue })`.
- **Alle Befehle laufen im Verzeichnis `frontend/`** des Worktrees `C:\Users\Admin\IdeaProjects\TaskWolf\.claude\worktrees\agile-ux-improvements`.

---

## File Structure

**Neu:**
- `frontend/src/components/issue/IssueDetailContent.tsx` — wiederverwendbarer Issue-Detail-Inhalt (Props: `projectKey`, `issueKey`).
- `frontend/src/hooks/useOpenIssue.ts` — Hook, der `?issue=KEY` setzt (übrige Params erhaltend).
- `frontend/src/components/issue/IssueDialog.tsx` — Modal, das `IssueDetailContent` umschließt.
- `frontend/src/components/issue/IssueDialogHost.tsx` — liest `?issue=`, rendert das Modal; in `AppLayout` gemountet.

**Geändert:**
- `frontend/src/pages/issues/IssueDetailPage.tsx` — auf `IssueDetailContent` reduziert.
- `frontend/src/layouts/AppLayout.tsx` — `IssueDialogHost` mounten.
- `frontend/src/pages/backlog/BacklogPage.tsx` — Zeilen öffnen Modal.
- `frontend/src/pages/issues/IssueListPage.tsx` — Zeilen öffnen Modal statt Navigation.
- `frontend/src/components/board/DraggableCard.tsx` — Klick öffnet Modal (mit Drag-Guard).

---

## Task 1: `IssueDetailContent` extrahieren, `IssueDetailPage` verschlanken

**Files:**
- Create: `frontend/src/components/issue/IssueDetailContent.tsx`
- Modify: `frontend/src/pages/issues/IssueDetailPage.tsx`

**Interfaces:**
- Produces: `IssueDetailContent(props: { projectKey: string; issueKey: string }): JSX.Element` — rendert Header + zweispaltiges Detail-Layout (ohne äußeren Breiten-Wrapper). Lädt selbst via `useIssue(projectKey, issueKey)`; zeigt eigene Loading-/Not-found-Zustände.

- [ ] **Step 1: `IssueDetailContent` anlegen**

Erstelle `frontend/src/components/issue/IssueDetailContent.tsx` mit dem aus der Seite übernommenen Inhalt, aber Props statt `useParams`. Der äußere `max-w-5xl`-Wrapper entfällt hier (den setzen Seite bzw. Dialog):

```tsx
import { useNavigate } from 'react-router-dom'
import { useIssue, useUpdateIssue } from '@/hooks/useIssues'
import { useMe } from '@/hooks/useAuth'
import { useSprints } from '@/hooks/useSprints'
import { useProjectMembers } from '@/hooks/useProjectMembers'
import { useLabels } from '@/hooks/useLabels'
import { useVersions } from '@/hooks/useVersions'
import { useCustomFields } from '@/hooks/useCustomFields'
import { CustomFieldInput } from '@/components/issue/CustomFieldInput'
import { StatusBadge } from '@/components/issue/StatusBadge'
import { InlineEditTitle } from '@/components/issue/InlineEditTitle'
import { PrioritySelector } from '@/components/issue/PrioritySelector'
import { TypeSelector } from '@/components/issue/TypeSelector'
import { AssigneeSelector } from '@/components/issue/AssigneeSelector'
import { SprintSelector } from '@/components/issue/SprintSelector'
import { DueDatePicker } from '@/components/issue/DueDatePicker'
import { LabelSelector } from '@/components/issue/LabelSelector'
import { VersionSelector } from '@/components/issue/VersionSelector'
import { RichTextEditor } from '@/components/issue/RichTextEditor'
import { CommentThread } from '@/components/comments/CommentThread'
import { ActivityFeed } from '@/components/comments/ActivityFeed'
import { AttachmentPanel } from '@/components/attachments/AttachmentPanel'

function SidebarField({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <label className="text-xs text-gray-500 uppercase tracking-wider mb-1 block">{label}</label>
      {children}
    </div>
  )
}

interface Props { projectKey: string; issueKey: string }

export function IssueDetailContent({ projectKey, issueKey }: Props) {
  const navigate = useNavigate()
  const { data: issue, isLoading } = useIssue(projectKey, issueKey)
  const { data: me } = useMe()
  const updateIssue = useUpdateIssue(projectKey)
  const { data: members = [] } = useProjectMembers(projectKey)
  const { data: sprints = [] } = useSprints(projectKey)
  const { data: allLabels = [] } = useLabels(projectKey)
  const { data: allVersions = [] } = useVersions(projectKey)
  const { data: customFieldDefs = [] } = useCustomFields(projectKey)

  if (isLoading) return <div className="text-gray-400">Loading...</div>
  if (!issue) return <div className="text-red-400">Issue not found</div>

  function patch(data: Record<string, unknown>) {
    updateIssue.mutate({ id: issue!.id, data })
  }

  return (
    <div>
      {/* Header */}
      <div className="flex items-center gap-3 mb-2">
        <span className="text-sm text-gray-500 font-mono">{issue.key}</span>
        <StatusBadge name={issue.statusName} category={issue.statusCategory} />
      </div>

      <InlineEditTitle value={issue.title} onSave={title => patch({ title })} />

      {/* Two-column layout */}
      <div className="grid grid-cols-3 gap-8">
        {/* Left: description + comments + activity */}
        <div className="col-span-2 space-y-8">
          <section>
            <h2 className="text-sm font-medium text-gray-400 mb-2">Description</h2>
            <RichTextEditor
              value={issue.description}
              onSave={description => patch({ description })}
            />
          </section>

          <section>
            <CommentThread projectKey={projectKey} issueKey={issueKey} currentUserId={me?.id} />
          </section>

          <section>
            <ActivityFeed projectKey={projectKey} issueKey={issueKey} />
          </section>

          {issue.refs && issue.refs.length > 0 && (
            <div className="mt-6">
              <h3 className="text-sm font-semibold text-gray-400 uppercase tracking-wider mb-3">References</h3>
              <div className="space-y-2">
                {issue.refs.map((ref) => (
                  <a key={ref.id} href={ref.url} target="_blank" rel="noopener noreferrer"
                    className="flex items-center gap-3 p-3 bg-gray-800 rounded hover:bg-gray-700 transition-colors">
                    <span className="text-xs font-bold px-2 py-0.5 rounded bg-gray-700 text-gray-300">{ref.provider}</span>
                    <span className="text-xs px-2 py-0.5 rounded bg-gray-700 text-gray-400">{ref.refType}</span>
                    <span className="text-sm text-blue-400 truncate">{ref.title || ref.externalId}</span>
                    <span className="text-xs text-gray-500 shrink-0">
                      {ref.createdAt ? new Date(ref.createdAt).toLocaleDateString() : ''}
                    </span>
                  </a>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* Right: metadata + attachments */}
        <div className="flex flex-col gap-4">
          <section className="space-y-4">
            <SidebarField label="Priority">
              <PrioritySelector value={issue.priority} onSave={priority => patch({ priority })} />
            </SidebarField>

            <SidebarField label="Type">
              <TypeSelector value={issue.type} onSave={type => patch({ type })} />
            </SidebarField>

            <SidebarField label="Assignee">
              <AssigneeSelector
                value={issue.assigneeName}
                assigneeId={issue.assigneeId}
                members={members}
                onSave={userId => userId ? patch({ assigneeId: userId }) : patch({ clearAssignee: true })}
              />
            </SidebarField>

            <SidebarField label="Reporter">
              <span className="text-sm text-gray-300">{issue.reporterName}</span>
            </SidebarField>

            <SidebarField label="Sprint">
              <SprintSelector
                value={issue.sprintName}
                sprintId={issue.sprintId}
                sprints={sprints}
                onSave={sprintId => sprintId ? patch({ sprintId }) : patch({ clearSprint: true })}
              />
            </SidebarField>

            <SidebarField label="Due Date">
              <DueDatePicker
                value={issue.dueDate}
                onSave={date => date ? patch({ dueDate: date }) : patch({ clearDueDate: true })}
              />
            </SidebarField>

            <SidebarField label="Labels">
              <LabelSelector
                projectKey={projectKey}
                value={issue.labels ?? []}
                allLabels={allLabels}
                onSave={labelIds => patch({ labelIds })}
                onChipClick={l => navigate(`/p/${projectKey}/issues?labelId=${l.id}`)}
              />
            </SidebarField>

            <SidebarField label="Fix Versions">
              <VersionSelector
                value={issue.fixVersions ?? []}
                allVersions={allVersions}
                onSave={fixVersionIds => patch({ fixVersionIds })}
                onChipClick={v => navigate(`/p/${projectKey}/issues?fixVersionId=${v.id}`)}
              />
            </SidebarField>

            <SidebarField label="Affects Versions">
              <VersionSelector
                value={issue.affectsVersions ?? []}
                allVersions={allVersions}
                onSave={affectsVersionIds => patch({ affectsVersionIds })}
                onChipClick={v => navigate(`/p/${projectKey}/issues?affectsVersionId=${v.id}`)}
              />
            </SidebarField>

            {customFieldDefs.map(def => {
              const cfValue = issue.customFields?.find(cf => cf.fieldId === def.id)
              return (
                <SidebarField key={def.id} label={def.required ? `${def.name} *` : def.name}>
                  <CustomFieldInput
                    definition={def}
                    value={cfValue}
                    onChange={val => patch({ customFieldValues: [{ fieldId: def.id, value: val }] })}
                  />
                </SidebarField>
              )
            })}

            {issue.storyPoints != null && (
              <SidebarField label="Story Points">
                <span className="text-sm text-white">{issue.storyPoints}</span>
              </SidebarField>
            )}

            <SidebarField label="Created">
              <span className="text-xs text-gray-500">{new Date(issue.createdAt).toLocaleDateString()}</span>
            </SidebarField>

            <SidebarField label="Updated">
              <span className="text-xs text-gray-500">{new Date(issue.updatedAt).toLocaleDateString()}</span>
            </SidebarField>
          </section>

          <section>
            <AttachmentPanel projectKey={projectKey} issueKey={issueKey} currentUserId={me?.id} />
          </section>
        </div>
      </div>
    </div>
  )
}
```

> Hinweis: Der `storyPoints`-Block bleibt vorerst read-only — das editierbare Widget kommt in Phase 2. In dieser Phase wird der Inhalt 1:1 übernommen.

- [ ] **Step 2: `IssueDetailPage` auf die neue Komponente reduzieren**

Ersetze den gesamten Inhalt von `frontend/src/pages/issues/IssueDetailPage.tsx` durch:

```tsx
import { useParams } from 'react-router-dom'
import { IssueDetailContent } from '@/components/issue/IssueDetailContent'

export function IssueDetailPage() {
  const { key, issueKey } = useParams<{ key: string; issueKey: string }>()
  return (
    <div className="max-w-5xl">
      <IssueDetailContent projectKey={key!} issueKey={issueKey!} />
    </div>
  )
}
```

- [ ] **Step 3: Typecheck**

Run: `npm run build`
Expected: PASS — kein TypeScript-Fehler, Build erfolgreich.

- [ ] **Step 4: Manuell verifizieren**

Run: `npm run dev` (Backend muss laufen). Öffne `/p/<KEY>/issues/<ISSUEKEY>`.
Expected: Die Vollseite sieht identisch aus wie vorher (Header, Beschreibung, Kommentare, Sidebar, Anhänge). Bearbeiten eines Felds (z. B. Priority) funktioniert weiterhin.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/issue/IssueDetailContent.tsx frontend/src/pages/issues/IssueDetailPage.tsx
git commit -m "refactor(issue): extract IssueDetailContent from IssueDetailPage"
```

---

## Task 2: `useOpenIssue`-Hook und `IssueDialog`-Modal

**Files:**
- Create: `frontend/src/hooks/useOpenIssue.ts`
- Create: `frontend/src/components/issue/IssueDialog.tsx`

**Interfaces:**
- Consumes: `IssueDetailContent` aus Task 1.
- Produces:
  - `useOpenIssue(): (issueKey: string) => void` — setzt `?issue=<issueKey>` und erhält alle übrigen Query-Parameter.
  - `IssueDialog(props: { projectKey: string; issueKey: string; onClose: () => void }): JSX.Element` — Modal-Overlay; schließt bei ✕, `Esc`, Backdrop-Klick.

- [ ] **Step 1: `useOpenIssue`-Hook anlegen**

Erstelle `frontend/src/hooks/useOpenIssue.ts`:

```ts
import { useCallback } from 'react'
import { useSearchParams } from 'react-router-dom'

/** Öffnet ein Issue als Modal, indem `?issue=KEY` gesetzt wird (übrige Query-Parameter bleiben erhalten). */
export function useOpenIssue() {
  const [, setSearchParams] = useSearchParams()
  return useCallback(
    (issueKey: string) => {
      setSearchParams(prev => {
        const next = new URLSearchParams(prev)
        next.set('issue', issueKey)
        return next
      })
    },
    [setSearchParams],
  )
}
```

- [ ] **Step 2: `IssueDialog` anlegen**

Erstelle `frontend/src/components/issue/IssueDialog.tsx` (Modal-Muster wie `CompleteSprintDialog`, ergänzt um Esc-Handler, Backdrop-Klick und „Vollansicht"-Link):

```tsx
import { useEffect } from 'react'
import { Link } from 'react-router-dom'
import { IssueDetailContent } from '@/components/issue/IssueDetailContent'

interface Props {
  projectKey: string
  issueKey: string
  onClose: () => void
}

export function IssueDialog({ projectKey, issueKey, onClose }: Props) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  return (
    <div
      className="fixed inset-0 bg-black/60 flex items-start justify-center z-50 p-4 overflow-y-auto"
      onClick={onClose}
      role="dialog"
      aria-modal="true"
    >
      <div
        className="bg-gray-950 border border-gray-800 rounded-xl w-full max-w-5xl my-8 p-6 relative"
        onClick={e => e.stopPropagation()}
      >
        <div className="flex items-center justify-end gap-3 mb-2">
          <Link
            to={`/p/${projectKey}/issues/${issueKey}`}
            className="text-xs text-gray-400 hover:text-white"
          >
            ⤢ Full view
          </Link>
          <button
            onClick={onClose}
            aria-label="Close"
            className="text-gray-400 hover:text-white text-lg leading-none"
          >
            ✕
          </button>
        </div>
        <IssueDetailContent projectKey={projectKey} issueKey={issueKey} />
      </div>
    </div>
  )
}
```

> Der „Full view"-`Link` navigiert zur Vollseiten-Route; da er auf einen reinen Pfad ohne Query zeigt, wird `?issue=` dabei automatisch verworfen und das Modal verschwindet.

- [ ] **Step 3: Typecheck**

Run: `npm run build`
Expected: PASS. (Beide neuen Dateien werden noch nirgends importiert — das ist in diesem Task ok; die Verdrahtung folgt in Task 3.)

- [ ] **Step 4: Commit**

```bash
git add frontend/src/hooks/useOpenIssue.ts frontend/src/components/issue/IssueDialog.tsx
git commit -m "feat(issue): add useOpenIssue hook and IssueDialog modal"
```

---

## Task 3: `IssueDialogHost` und Einbindung in `AppLayout`

**Files:**
- Create: `frontend/src/components/issue/IssueDialogHost.tsx`
- Modify: `frontend/src/layouts/AppLayout.tsx`

**Interfaces:**
- Consumes: `IssueDialog` aus Task 2.
- Produces: `IssueDialogHost(props: { projectKey: string }): JSX.Element | null` — liest `?issue=`; rendert bei gesetztem Wert das `IssueDialog`, dessen `onClose` den Parameter entfernt.

- [ ] **Step 1: `IssueDialogHost` anlegen**

Erstelle `frontend/src/components/issue/IssueDialogHost.tsx`:

```tsx
import { useCallback } from 'react'
import { useSearchParams } from 'react-router-dom'
import { IssueDialog } from '@/components/issue/IssueDialog'

interface Props { projectKey: string }

export function IssueDialogHost({ projectKey }: Props) {
  const [searchParams, setSearchParams] = useSearchParams()
  const issueKey = searchParams.get('issue')

  const close = useCallback(() => {
    setSearchParams(prev => {
      const next = new URLSearchParams(prev)
      next.delete('issue')
      return next
    })
  }, [setSearchParams])

  if (!issueKey) return null
  return <IssueDialog projectKey={projectKey} issueKey={issueKey} onClose={close} />
}
```

- [ ] **Step 2: Host in `AppLayout` mounten**

In `frontend/src/layouts/AppLayout.tsx`:

Import ergänzen (bei den übrigen Imports oben):

```tsx
import { IssueDialogHost } from '@/components/issue/IssueDialogHost'
```

Im `<main>`-Block den Host nach dem `<Outlet />` einfügen (er braucht `projectKey`, das in `AppLayout` bereits berechnet wird):

```tsx
      <main className="flex-1 overflow-auto p-8">
        <Outlet />
        {projectKey && <IssueDialogHost projectKey={projectKey} />}
      </main>
```

- [ ] **Step 3: Typecheck**

Run: `npm run build`
Expected: PASS.

- [ ] **Step 4: Manuell verifizieren (Deep-Link)**

Run: `npm run dev`. Öffne eine Projektseite, z. B. `/p/<KEY>/board`, und hänge manuell `?issue=<ISSUEKEY>` an die URL.
Expected: Das Modal erscheint über dem Board (abgedunkelter Hintergrund, Board bleibt im Hintergrund sichtbar). ✕, `Esc` und Klick auf den abgedunkelten Rand schließen das Modal und entfernen `?issue=` aus der URL. „Full view" navigiert zur Vollseite.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/issue/IssueDialogHost.tsx frontend/src/layouts/AppLayout.tsx
git commit -m "feat(issue): mount IssueDialogHost driven by ?issue= param"
```

---

## Task 4: Öffner in Backlog-Zeile und Issue-Liste verdrahten

**Files:**
- Modify: `frontend/src/pages/backlog/BacklogPage.tsx`
- Modify: `frontend/src/pages/issues/IssueListPage.tsx`

**Interfaces:**
- Consumes: `useOpenIssue` aus Task 2.

- [ ] **Step 1: Backlog-Zeile klickbar machen**

In `frontend/src/pages/backlog/BacklogPage.tsx`:

Import ergänzen:

```tsx
import { useOpenIssue } from '@/hooks/useOpenIssue'
```

`IssueRow` um einen Klick-Handler erweitern. Ersetze die bestehende `IssueRow`-Funktion durch:

```tsx
function IssueRow({ issue, action, onOpen }: { issue: Issue; action: React.ReactNode; onOpen: (issueKey: string) => void }) {
  return (
    <div className="flex items-center gap-3 px-4 py-2.5 bg-gray-900/50 rounded border border-gray-800/50 hover:border-gray-700">
      <span className="text-xs text-gray-500 font-mono w-20 shrink-0">{issue.key}</span>
      <button
        onClick={() => onOpen(issue.key)}
        className="flex-1 text-left text-sm text-white truncate hover:text-blue-400"
      >
        {issue.title}
      </button>
      <StatusBadge name={issue.statusName} category={issue.statusCategory} />
      {issue.storyPoints != null && (
        <span className="text-xs bg-gray-800 text-gray-400 px-1.5 py-0.5 rounded font-mono">{issue.storyPoints}</span>
      )}
      {action}
    </div>
  )
}
```

In der `BacklogPage`-Funktion den Hook holen:

```tsx
  const openIssue = useOpenIssue()
```

und beide `<IssueRow ... />`-Verwendungen um `onOpen={openIssue}` ergänzen (die im Sprint-Block und die im Backlog-Block). Beispiel Sprint-Block:

```tsx
                <IssueRow
                  key={issue.id}
                  issue={issue}
                  onOpen={openIssue}
                  action={
                    <button
                      onClick={() => unassignIssue.mutate({ sprintId: entry.sprint.id, issueId: issue.id })}
                      className="text-xs text-gray-500 hover:text-red-400 shrink-0"
                    >
                      ✕
                    </button>
                  }
                />
```

Und im Backlog-Block analog `onOpen={openIssue}` zur `<IssueRow>` ergänzen.

- [ ] **Step 2: Issue-Liste auf Modal umstellen**

In `frontend/src/pages/issues/IssueListPage.tsx`:

Import ergänzen:

```tsx
import { useOpenIssue } from '@/hooks/useOpenIssue'
```

In der `IssueListPage`-Funktion (bei den anderen Hooks) ergänzen:

```tsx
  const openIssue = useOpenIssue()
```

Die Ergebnisliste von `<Link>` auf einen klickbaren `<button>` umstellen. Ersetze den `page?.content.map(...)`-Block (der aktuell ein `<Link ... to={/p/${key}/issues/${issue.key}}>` rendert) durch:

```tsx
        {page?.content.map(issue => (
          <button key={issue.id} onClick={() => openIssue(issue.key)}
            className="w-full text-left bg-gray-900 border border-gray-800 hover:border-gray-600 rounded-lg px-4 py-3 flex items-center gap-4">
            <span className="text-xs text-gray-500 font-mono w-20">{issue.key}</span>
            <span className="flex-1 text-sm text-white">{issue.title}</span>
            <StatusBadge name={issue.statusName} category={issue.statusCategory} />
            <span className={`text-xs px-2 py-0.5 rounded font-medium ${
              issue.priority === 'CRITICAL' ? 'text-red-400' :
              issue.priority === 'HIGH' ? 'text-orange-400' :
              issue.priority === 'MEDIUM' ? 'text-yellow-400' : 'text-green-400'
            }`}>{issue.priority}</span>
          </button>
        ))}
```

Falls `Link` danach nicht mehr verwendet wird, den Import `Link` aus der `react-router-dom`-Importzeile entfernen, um einen `noUnusedLocals`-Fehler zu vermeiden. (`useSearchParams` und `useParams` bleiben.)

- [ ] **Step 3: Typecheck**

Run: `npm run build`
Expected: PASS. Falls Fehler „`Link` is declared but never read": den `Link`-Import wie in Step 2 beschrieben entfernen.

- [ ] **Step 4: Manuell verifizieren**

Run: `npm run dev`.
- Auf `/p/<KEY>/backlog`: Klick auf einen Issue-Titel öffnet das Modal über der Backlog-Seite; aktive Sprint-Filter/Zustände bleiben erhalten; Schließen bringt die Backlog-Seite unverändert zurück.
- Auf `/p/<KEY>/issues`: Setze einen Filter (z. B. Label), klicke dann eine Zeile. Das Modal öffnet, und der Filter-Query-Parameter bleibt in der URL erhalten (neben `issue=`).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/backlog/BacklogPage.tsx frontend/src/pages/issues/IssueListPage.tsx
git commit -m "feat(issue): open issue modal from backlog rows and issue list"
```

---

## Task 5: Board-Karte als Öffner (mit Drag-Guard)

**Files:**
- Modify: `frontend/src/components/board/DraggableCard.tsx`

**Interfaces:**
- Consumes: `useOpenIssue` aus Task 2.

**Warum ein Guard:** Die Karte nutzt `@dnd-kit`-Drag-Listener. Ein reiner Klick (ohne Bewegung) darf das Modal öffnen, ein Ziehen darf es **nicht**. Wir merken uns die Pointer-Position beim Drücken (Capture-Phase, damit die dnd-Listener nicht überschrieben werden) und öffnen nur, wenn sich der Pointer um weniger als 5px bewegt hat — passend zur bestehenden 5px-Aktivierungsdistanz des `PointerSensor`.

- [ ] **Step 1: Klick-Handler mit Drag-Guard einbauen**

Ersetze den gesamten Inhalt von `frontend/src/components/board/DraggableCard.tsx` durch:

```tsx
import { useRef } from 'react'
import { useDraggable } from '@dnd-kit/core'
import { CSS } from '@dnd-kit/utilities'
import { cn } from '@/lib/utils'
import { useOpenIssue } from '@/hooks/useOpenIssue'
import type { Issue } from '@/types'

const priorityColor: Record<string, string> = {
  CRITICAL: 'text-red-400',
  HIGH: 'text-orange-400',
  MEDIUM: 'text-yellow-400',
  LOW: 'text-green-400',
}

interface Props { issue: Issue }

export function DraggableCard({ issue }: Props) {
  const { attributes, listeners, setNodeRef, transform, isDragging } = useDraggable({ id: issue.id })
  const openIssue = useOpenIssue()
  const downPos = useRef<{ x: number; y: number } | null>(null)

  return (
    <div
      ref={setNodeRef}
      style={{ transform: CSS.Translate.toString(transform) }}
      {...attributes}
      {...listeners}
      onPointerDownCapture={e => { downPos.current = { x: e.clientX, y: e.clientY } }}
      onClick={e => {
        const start = downPos.current
        downPos.current = null
        if (!start) return
        const moved = Math.hypot(e.clientX - start.x, e.clientY - start.y)
        if (moved < 5) openIssue(issue.key)
      }}
      className={cn(
        'bg-gray-900 border border-gray-800 rounded-lg p-3 cursor-grab active:cursor-grabbing select-none',
        isDragging && 'opacity-50 border-blue-500 z-50'
      )}
    >
      <div className="text-xs text-gray-500 font-mono mb-1">{issue.key}</div>
      <div className="text-sm text-white mb-2 line-clamp-2">{issue.title}</div>
      <div className="flex items-center gap-2">
        <span className={cn('text-xs font-medium', priorityColor[issue.priority] ?? 'text-gray-400')}>
          {issue.priority}
        </span>
        {issue.storyPoints != null && (
          <span className="ml-auto text-xs bg-gray-800 text-gray-400 px-1.5 py-0.5 rounded font-mono">
            {issue.storyPoints}
          </span>
        )}
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Typecheck**

Run: `npm run build`
Expected: PASS.

- [ ] **Step 3: Manuell verifizieren (Klick vs. Ziehen)**

Run: `npm run dev`. Öffne `/p/<KEY>/board` (aktiver Sprint nötig).
Expected:
- **Klick** auf eine Karte (ohne Ziehen) öffnet das Issue-Modal über dem Board.
- **Ziehen** einer Karte in eine andere Spalte verschiebt das Issue und öffnet **kein** Modal.
- Nach dem Schließen des Modals ist das Board unverändert.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/board/DraggableCard.tsx
git commit -m "feat(board): open issue modal on card click with drag guard"
```

---

## Task 6: Dokumentation aktualisieren

**Files:**
- Modify: `mkdocs/developer-guide/frontend/components.md`
- Modify: `mkdocs/developer-guide/frontend/overview.md`
- Modify: `mkdocs/developer-guide/ai-guide.md`

> Wiki-Docs sind laut Projektkonvention verpflichtende Abschlussaufgabe jeder Phase.

- [ ] **Step 1: Komponenten-Doku ergänzen**

In `mkdocs/developer-guide/frontend/components.md` einen Abschnitt zum Issue-Dialog ergänzen. Beschreibe:
- `IssueDetailContent` — geteilter Detail-Inhalt (Props `projectKey`, `issueKey`), genutzt von Vollseite und Modal.
- `IssueDialog` / `IssueDialogHost` — Modal-Overlay, gesteuert über den URL-Parameter `?issue=KEY`; `IssueDialogHost` ist einmal in `AppLayout` gemountet.
- `useOpenIssue()` — Hook zum Öffnen (`?issue=KEY` setzen, übrige Query-Parameter erhalten).

Formuliere im Stil der bestehenden Einträge dieser Datei (Tabelle bzw. Absätze — vorhandenes Muster übernehmen).

- [ ] **Step 2: Overview/Routing-Notiz ergänzen**

In `mkdocs/developer-guide/frontend/overview.md` das Muster „Issue als Modal über der aktuellen Seite via `?issue=`-Query-Parameter, Vollseite `/p/:key/issues/:issueKey` als Deep-Link-Fallback" kurz dokumentieren.

- [ ] **Step 3: AI-Guide ergänzen**

In `mkdocs/developer-guide/ai-guide.md` das neue Muster „Modal-via-Query-Param (`useOpenIssue` + `IssueDialogHost`, Vollseite als Deep-Link-Fallback)" als wiederverwendbares Frontend-Pattern ergänzen, im Stil der bestehenden Pattern-Einträge dieser Datei.

- [ ] **Step 4: Commit**

```bash
git add mkdocs/ docs/
git commit -m "docs(frontend): document issue dialog (modal via ?issue= param)"
```

---

## Self-Review (vom Plan-Autor durchgeführt)

- **Spec-Abdeckung:** Phase-1-Anforderungen des Specs abgedeckt — geteilte `IssueDetailContent` (Task 1), Modal via `?issue=` überlagert aktuelle Seite (Tasks 2–3), Öffnen von verschiedenen Stellen inkl. dnd-Guard (Tasks 4–5), Vollseite als Deep-Link-Fallback (Task 1/2), Docs (Task 6). Story-Points-Editor und Sprint-Übersicht sind bewusst **nicht** hier (Phase 2/3).
- **Testabweichung vom Spec (bewusst):** Das Spec nannte Vitest/RTL-Tests; das Frontend hat kein Test-Framework, daher — mit dem Nutzer abgestimmt — Absicherung per `npm run build` (Typecheck) + manueller Verifikation, konsistent mit den bisherigen 9 Phasen.
- **Typkonsistenz:** `useOpenIssue(): (issueKey: string) => void`, `IssueDialog`-Props `{ projectKey, issueKey, onClose }`, `IssueDialogHost`-Props `{ projectKey }`, `IssueDetailContent`-Props `{ projectKey, issueKey }` — durchgängig gleich verwendet.
- **Keine Platzhalter:** Jeder Code-Step enthält vollständigen Code; Befehle mit erwartetem Ergebnis.
```
