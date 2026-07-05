# Phase 2: Editable Story Points (Fibonacci) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Story Points im Issue-Detail setzen, ändern und leeren können (festes Fibonacci-Raster), inklusive der fehlenden Backend-Fähigkeit, Punkte wieder auf `null` zu setzen.

**Architecture:** Backend erhält ein `clearStoryPoints`-Flag in `UpdateIssueRequest` und wendet in `IssueService.update` dieselbe Clear-Semantik an wie bei `clearAssignee`/`clearSprint`/`clearDueDate` (das heutige `?.let` wird zu einem `when`-Block umgebaut). Frontend bekommt einen `StoryPointsSelector` (analog zu `PrioritySelector`/`TypeSelector`), der in der Sidebar von `IssueDetailContent` **immer** gerendert wird und über den bestehenden `patch(...)`-Pfad speichert. Die read-only Story-Points-Chips auf Board-Karte und Backlog-Zeile bleiben unverändert und aktualisieren sich dank des geteilten React-Query-Caches automatisch.

**Tech Stack:** Backend Kotlin 2.4 / Spring Boot 3.5 / Gradle 9 (Test: JUnit 5 + MockK). Frontend React 19, TypeScript 6, @tanstack/react-query 5, Tailwind CSS 4.

## Global Constraints

- **Fibonacci-Raster ist fix:** genau die Optionen **1, 2, 3, 5, 8, 13, 21** plus eine **„— Clear"**-Option. Keine anderen Werte im Selector.
- **Keine Fibonacci-Validierung im Backend.** `storyPoints` bleibt `Int?`; das Backend akzeptiert weiterhin beliebige Ganzzahlen. Das Raster ist reine Frontend-Vorgabe.
- **Clear-Semantik exakt wie `clearAssignee`/`clearSprint`:** `clearStoryPoints=true` → `storyPoints = null`; `storyPoints=n` → Wert setzen; beides ungesetzt → keine Änderung. Der bestehende Activity-Eintrag (`IssueFieldChangedEvent` mit `field = "storyPoints"`) wird wie bisher geschrieben.
- **Kein Inline-Editieren** der Story Points auf Board-Karte/Backlog-Zeile — dort nur Anzeige (unverändert).
- **Keine neuen Dependencies** (weder npm noch Gradle).
- **Keine DB-Migration:** die Spalte `story_points` existiert bereits; Clear = `null` setzen, kein Schema-Change.
- **Test-Gates unterscheiden sich pro Task:**
  - Backend hat ein echtes Test-Framework (JUnit 5 + MockK) → **TDD**. Gate: `./gradlew test --tests "com.taskowolf.issues.IssueServiceTest"` aus dem Verzeichnis `backend/`.
  - Frontend hat **kein** Test-Framework → Gate ist `npm run build` (`tsc && vite build`, voller Typecheck) aus `frontend/`; Verhalten manuell im Browser verifiziert. (Konsistent mit allen bisherigen Phasen.)
- **Geteilter React-Query-Key** `['issues', projectKey, issueKey]` bleibt unverändert; Board/Backlog-Chips lesen aus demselben Cache und dürfen nicht angefasst werden.
- **Wiki-Docs sind laut Projektkonvention verpflichtende Abschlussaufgabe der Phase** (Task 3).

---

## File Structure

**Backend — geändert:**
- `backend/src/main/kotlin/com/taskowolf/issues/api/dto/UpdateIssueRequest.kt` — neues Feld `clearStoryPoints: Boolean = false`.
- `backend/src/main/kotlin/com/taskowolf/issues/application/IssueService.kt` — Story-Points-Handling von `?.let` auf `when`-Block (Set + Clear) umbauen.
- `backend/src/test/kotlin/com/taskowolf/issues/IssueServiceTest.kt` — zwei neue Tests (Set-Regression + Clear).

**Frontend — neu:**
- `frontend/src/components/issue/StoryPointsSelector.tsx` — Chip + Popover mit Fibonacci-Optionen + Clear.

**Frontend — geändert:**
- `frontend/src/components/issue/IssueDetailContent.tsx` — den read-only `{issue.storyPoints != null && ...}`-Block durch den immer sichtbaren `StoryPointsSelector` ersetzen; Import ergänzen.

**Docs — geändert:**
- `mkdocs/developer-guide/frontend/components.md` — `StoryPointsSelector` dokumentieren.
- `mkdocs/developer-guide/backend/issues.md` — `clearStoryPoints`-Flag dokumentieren.

---

## Task 1: Backend — `clearStoryPoints`-Flag + Clear-Semantik (TDD)

**Files:**
- Test: `backend/src/test/kotlin/com/taskowolf/issues/IssueServiceTest.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/issues/api/dto/UpdateIssueRequest.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/issues/application/IssueService.kt`

**Interfaces:**
- Produces: `UpdateIssueRequest.clearStoryPoints: Boolean` (default `false`). `IssueService.update` verarbeitet den Payload so, dass `clearStoryPoints=true` die Punkte auf `null` setzt und den `IssueFieldChangedEvent(field = "storyPoints", oldValue, newValue = null)` publiziert (nur wenn vorher ein Wert gesetzt war).

> **TDD-Hinweis:** In Kotlin ist die RED-Phase hier ein **Compile-Fehler** — der Clear-Test referenziert `clearStoryPoints = true`, das es noch nicht gibt. Das ist ein gültiges RED. Der Set-Test dient als Regressionsnetz für den `?.let`→`when`-Umbau.

- [ ] **Step 1: Beide Tests schreiben**

Füge in `IssueServiceTest.kt` am Ende der Klasse (vor der schließenden `}`) diese zwei Tests ein. Sie folgen exakt den bestehenden Mustern (`update sets dueDate when provided`, `update clears sprint when clearSprint is true`, `update publishes IssueFieldChangedEvent when title changes`):

```kotlin
    @Test
    fun `update sets storyPoints when provided`() {
        every { projectService.requireMember("WOLF", owner.id) } returns project
        every { issueRepository.findById(issue.id) } returns java.util.Optional.of(issue)
        every { issueRepository.save(any()) } returnsArgument 0

        val updated = service.update("WOLF", issue.id,
            UpdateIssueRequest(storyPoints = 8),
            owner)

        assertEquals(8, updated.storyPoints)
    }

    @Test
    fun `update clears storyPoints and logs activity when clearStoryPoints is true`() {
        issue.storyPoints = 5
        every { projectService.requireMember("WOLF", owner.id) } returns project
        every { issueRepository.findById(issue.id) } returns java.util.Optional.of(issue)
        every { issueRepository.save(any()) } returnsArgument 0

        val events = mutableListOf<Any>()
        every { eventPublisher.publish(capture(events)) } answers { Unit }

        val updated = service.update("WOLF", issue.id,
            UpdateIssueRequest(clearStoryPoints = true),
            owner)

        assertEquals(null, updated.storyPoints)
        val fieldEvent = events.filterIsInstance<IssueFieldChangedEvent>().first { it.field == "storyPoints" }
        assertEquals("5", fieldEvent.oldValue)
        assertEquals(null, fieldEvent.newValue)
    }
```

> `assertEquals`, `IssueFieldChangedEvent`, `UpdateIssueRequest`, MockK und das gemeinsame `issue`/`project`/`owner`-Fixture sind in dieser Testklasse bereits importiert bzw. definiert. JUnit erzeugt pro Testmethode eine neue Klasseninstanz, daher ist das Setzen von `issue.storyPoints = 5` isoliert (wie bei den bestehenden `issue.dueDate = ...`/`issue.sprint = ...`-Tests).

- [ ] **Step 2: Tests laufen lassen — RED erwarten**

Run (aus `backend/`): `./gradlew test --tests "com.taskowolf.issues.IssueServiceTest"`
Expected: **FAIL** — Kompilierfehler „no value passed for parameter" bzw. „unresolved reference: clearStoryPoints" für `UpdateIssueRequest(clearStoryPoints = true)`, weil das Feld noch nicht existiert.

- [ ] **Step 3: DTO-Feld ergänzen**

In `backend/src/main/kotlin/com/taskowolf/issues/api/dto/UpdateIssueRequest.kt` das Feld direkt nach `storyPoints` einfügen, damit die Clear-Flags nah bei ihren Feldern stehen (wie `clearAssignee` nach `assigneeId`, `clearDueDate` nach `dueDate`, `clearSprint` nach `sprintId`):

```kotlin
    val storyPoints: Int? = null,
    val clearStoryPoints: Boolean = false,
```

(Die übrigen Felder der Data Class bleiben unverändert.)

- [ ] **Step 4: Clear-Semantik in `IssueService.update` implementieren**

In `backend/src/main/kotlin/com/taskowolf/issues/application/IssueService.kt` den bestehenden Story-Points-Block ersetzen.

Ersetze diesen Block (aktuell direkt nach dem `priority`-Block):

```kotlin
        request.storyPoints?.let { newSP ->
            if (issue.storyPoints != newSP) {
                val old = issue.storyPoints?.toString()
                issue.storyPoints = newSP
                eventPublisher.publish(IssueFieldChangedEvent(issue, currentUser, "storyPoints", old, newSP.toString()))
            }
        }
```

durch (`when`-Muster identisch zum vorhandenen `clearDueDate`/`clearSprint`-Block weiter unten):

```kotlin
        when {
            request.clearStoryPoints -> {
                val old = issue.storyPoints?.toString()
                issue.storyPoints = null
                if (old != null) eventPublisher.publish(IssueFieldChangedEvent(issue, currentUser, "storyPoints", old, null))
            }
            request.storyPoints != null -> {
                val newSP = request.storyPoints
                if (issue.storyPoints != newSP) {
                    val old = issue.storyPoints?.toString()
                    issue.storyPoints = newSP
                    eventPublisher.publish(IssueFieldChangedEvent(issue, currentUser, "storyPoints", old, newSP.toString()))
                }
            }
        }
```

> `request.storyPoints` wird im zweiten Zweig auf `Int` smart-gecastet (data-class-`val`, gleiches Modul) — exakt wie `request.dueDate` im vorhandenen `dueDate`-`when`-Block. Falls der Compiler unerwartet meckert, `request.storyPoints!!` verwenden.

- [ ] **Step 5: Tests laufen lassen — GREEN erwarten**

Run (aus `backend/`): `./gradlew test --tests "com.taskowolf.issues.IssueServiceTest"`
Expected: **PASS** — beide neuen Tests grün, alle bestehenden `IssueServiceTest`-Tests weiterhin grün.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/issues/api/dto/UpdateIssueRequest.kt backend/src/main/kotlin/com/taskowolf/issues/application/IssueService.kt backend/src/test/kotlin/com/taskowolf/issues/IssueServiceTest.kt
git commit -m "feat(issues): support clearing story points via clearStoryPoints flag"
```

---

## Task 2: Frontend — `StoryPointsSelector` + Verdrahtung in `IssueDetailContent`

**Files:**
- Create: `frontend/src/components/issue/StoryPointsSelector.tsx`
- Modify: `frontend/src/components/issue/IssueDetailContent.tsx`

**Interfaces:**
- Consumes: den `patch(data: Record<string, unknown>)`-Helper in `IssueDetailContent` (bereits vorhanden) sowie `clearStoryPoints` aus Task 1.
- Produces: `StoryPointsSelector(props: { value: number | null | undefined; onSave: (value: number | null) => void }): JSX.Element` — Chip mit aktuellem Wert (oder Platzhalter „Set points"); Klick öffnet ein Popover mit den Fibonacci-Optionen und einer „— Clear"-Option. `onSave(n)` bei Auswahl einer Zahl, `onSave(null)` bei „Clear".

> **Kein Payload-Typ-Change nötig.** `patch` nimmt bereits `Record<string, unknown>`, und die bestehenden Clear-Flags (`clearAssignee`/`clearSprint`/`clearDueDate`) laufen unverändert über genau diesen Pfad. `patch({ storyPoints: n })` und `patch({ clearStoryPoints: true })` typprüfen daher ohne Änderung an `api/issues.ts`/`hooks/useIssues.ts`. Der Build in Step 3 bestätigt das.

- [ ] **Step 1: `StoryPointsSelector` anlegen**

Erstelle `frontend/src/components/issue/StoryPointsSelector.tsx` (Struktur 1:1 nach `PrioritySelector.tsx`/`TypeSelector.tsx` — `useState`-Open, Outside-Click-Effekt, `relative`-Container, Chip-Button, Popover):

```tsx
import { useState, useRef, useEffect } from 'react'

const POINTS = [1, 2, 3, 5, 8, 13, 21] as const

interface Props {
  value: number | null | undefined
  onSave: (value: number | null) => void
}

export function StoryPointsSelector({ value, onSave }: Props) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', handleClick)
    return () => document.removeEventListener('mousedown', handleClick)
  }, [])

  return (
    <div ref={ref} className="relative">
      <button
        onClick={() => setOpen(o => !o)}
        className={`text-sm cursor-pointer hover:underline ${value != null ? 'text-white' : 'text-gray-500'}`}
      >
        {value != null ? value : 'Set points'}
      </button>
      {open && (
        <div className="absolute z-50 top-6 left-0 bg-gray-800 border border-gray-700 rounded shadow-lg py-1 min-w-32">
          {POINTS.map(p => (
            <button
              key={p}
              onClick={() => { onSave(p); setOpen(false) }}
              className={`w-full text-left px-3 py-1.5 text-sm text-gray-300 hover:bg-gray-700 ${p === value ? 'font-bold text-white' : ''}`}
            >
              {p}
            </button>
          ))}
          <button
            onClick={() => { onSave(null); setOpen(false) }}
            className="w-full text-left px-3 py-1.5 text-sm text-gray-500 hover:bg-gray-700 border-t border-gray-700"
          >
            — Clear
          </button>
        </div>
      )}
    </div>
  )
}
```

- [ ] **Step 2: In `IssueDetailContent` verdrahten**

In `frontend/src/components/issue/IssueDetailContent.tsx`:

(a) Import bei den anderen Selector-Imports (neben `PrioritySelector`/`TypeSelector`) ergänzen:

```tsx
import { StoryPointsSelector } from '@/components/issue/StoryPointsSelector'
```

(b) Den bestehenden read-only Story-Points-Block ersetzen. Ersetze:

```tsx
            {issue.storyPoints != null && (
              <SidebarField label="Story Points">
                <span className="text-sm text-white">{issue.storyPoints}</span>
              </SidebarField>
            )}
```

durch (immer gerendert; `onSave` entscheidet zwischen Setzen und Clearen — gleiches Muster wie Assignee/Sprint/DueDate direkt darüber):

```tsx
            <SidebarField label="Story Points">
              <StoryPointsSelector
                value={issue.storyPoints}
                onSave={sp => sp != null ? patch({ storyPoints: sp }) : patch({ clearStoryPoints: true })}
              />
            </SidebarField>
```

- [ ] **Step 3: Typecheck / Build**

Run (aus `frontend/`): `npm run build`
Expected: **PASS** — `tsc && vite build` ohne TypeScript-Fehler. (Bestätigt insbesondere, dass `patch({ storyPoints })`/`patch({ clearStoryPoints: true })` ohne Payload-Typ-Change typprüfen.)

- [ ] **Step 4: Manuell verifizieren**

Run (aus `frontend/`): `npm run dev` (Backend muss laufen, Task 1 gemerged/gebaut).
- Öffne ein Issue **ohne** Story Points (Vollseite oder Modal). Der Chip zeigt „Set points".
- Wähle **8** → Chip zeigt `8`; die read-only Chips auf Board-Karte/Backlog-Zeile für dasselbe Issue zeigen dank geteiltem Cache ebenfalls `8`.
- Öffne erneut, wähle **„— Clear"** → Chip zeigt wieder „Set points"; der read-only Chip auf Board/Backlog verschwindet (da `storyPoints` wieder `null`).
- Ein anderer Wert (z. B. 13) ersetzt den vorigen korrekt.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/issue/StoryPointsSelector.tsx frontend/src/components/issue/IssueDetailContent.tsx
git commit -m "feat(issue): editable story points via Fibonacci StoryPointsSelector"
```

---

## Task 3: Dokumentation aktualisieren

**Files:**
- Modify: `mkdocs/developer-guide/frontend/components.md`
- Modify: `mkdocs/developer-guide/backend/issues.md`

> Wiki-Docs sind laut Projektkonvention verpflichtende Abschlussaufgabe jeder Phase. Vor dem Schreiben jede Datei lesen und den vorhandenen Stil/Aufbau übernehmen.

- [ ] **Step 1: Frontend-Komponenten-Doku ergänzen**

In `mkdocs/developer-guide/frontend/components.md` einen Eintrag zu `StoryPointsSelector` ergänzen, im Stil der bestehenden Selector-Einträge (`PrioritySelector`/`TypeSelector`/`AssigneeSelector`). Inhalt:
- Sidebar-Selector in `IssueDetailContent`; Fibonacci-Optionen **1, 2, 3, 5, 8, 13, 21** + „— Clear".
- Props `{ value: number | null | undefined; onSave: (value: number | null) => void }`.
- Wird **immer** gerendert (auch ohne Wert → Platzhalter „Set points"); speichert über `patch({ storyPoints })` bzw. `patch({ clearStoryPoints: true })`.

- [ ] **Step 2: Backend-Issues-Doku ergänzen**

In `mkdocs/developer-guide/backend/issues.md` beim `UpdateIssueRequest`/Update-Semantik-Abschnitt das neue Flag `clearStoryPoints` dokumentieren, im Stil der bestehenden Beschreibung von `clearAssignee`/`clearSprint`/`clearDueDate`: `clearStoryPoints=true` → `storyPoints=null`; `storyPoints=n` → setzen; beides ungesetzt → keine Änderung; Activity `IssueFieldChangedEvent(field="storyPoints")` wird wie gehabt geschrieben. Kein Schema-Change, keine Fibonacci-Validierung im Backend.

> **AI-Guide:** `mkdocs/developer-guide/ai-guide.md` benötigt **kein** neues Pattern — die Clear-Flag-Semantik ist bereits als Muster dokumentiert (`clearAssignee`/`clearSprint`); `clearStoryPoints` ist nur eine weitere Anwendung. Nur ergänzen, falls beim Umsetzen wider Erwarten ein neues, wiederverwendbares Muster entstanden ist.

- [ ] **Step 3: Commit**

```bash
git add mkdocs/
git commit -m "docs(issues): document editable story points and clearStoryPoints flag"
```

---

## Self-Review (vom Plan-Autor durchgeführt)

- **Spec-Abdeckung (Phase 2):**
  - Frontend `StoryPointsSelector` (Fibonacci + Clear, immer gerendert, Platzhalter) → Task 2. ✔
  - Payload lässt `clearStoryPoints` zu → bereits durch `patch(Record<string, unknown>)` erfüllt; in Task 2 per Build bestätigt, kein Typ-Change nötig (Spec-Annahme eines strikteren Typs trifft auf den realen Code nicht zu). ✔
  - Read-only Chips auf Board/Backlog unverändert, Update via geteiltem Cache → explizit als Constraint + manueller Verifikationsschritt (Task 2, Step 4). ✔
  - Backend `UpdateIssueRequest.clearStoryPoints: Boolean = false` → Task 1, Step 3. ✔
  - Backend Clear-Semantik wie `clearAssignee`/`clearSprint`, bestehende Activity erhalten → Task 1, Step 4. ✔
  - Backend-Test für Clear (null + Activity), datenbank-neutral (MockK) → Task 1, Step 1/5. ✔
  - Wiki-Docs (frontend/components, backend/issues; ai-guide nur bei neuem Muster) → Task 3. ✔
- **Bewusste Abweichung vom Spec-Testabschnitt:** Der Spec nennt Vitest/RTL für das Frontend; das Frontend hat weiterhin kein Test-Framework, daher Frontend-Gate `npm run build` + manuell (wie in Phase 1 und allen bisherigen Phasen). Das Backend nutzt hingegen sein echtes JUnit/MockK-Suite (TDD).
- **Typkonsistenz:** `clearStoryPoints: Boolean`/`clearStoryPoints?: boolean` durchgängig; `StoryPointsSelector`-Props `{ value: number | null | undefined; onSave: (value: number | null) => void }` in Definition und Verwendung identisch; `onSave(null)` ↔ `patch({ clearStoryPoints: true })`, `onSave(n)` ↔ `patch({ storyPoints: n })`.
- **Keine Platzhalter:** jeder Code-Step enthält vollständigen Code; jeder Test-Step nennt Befehl + erwartetes Ergebnis.
- **Nicht enthalten (YAGNI / andere Phasen):** Sprint-Übersicht = Phase 3; keine Board-/Backlog-Inline-Edits; keine Fibonacci-Validierung im Backend.
