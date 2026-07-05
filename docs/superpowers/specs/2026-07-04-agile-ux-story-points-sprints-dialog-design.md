# Agile UX: Story Points, Sprint-Übersicht & Issue-Dialog

**Datum:** 2026-07-04 (Phase-3-Addendum: 2026-07-05)
**Status:** Phase 1 & 2 ausgeliefert (PR #38, #39). Phase 3 + aufgeschobene Minors:
Design abgestimmt, bereit für Implementierungsplanung.
**Betroffene Bereiche:** Frontend (überwiegend), kleine Backend-Ergänzung

## Kontext & Problem

Drei zusammenhängende Lücken in der Agile-/Issue-UX von TaskWolf:

1. **Story Points sind nicht editierbar.** Das Backend unterstützt sie vollständig
   (`UpdateIssueRequest.storyPoints`, Activity-Log `STORY_POINTS_CHANGED`), aber das
   Frontend zeigt sie nur **lesend** an (Board-Karte, Backlog-Zeile, Issue-Detail-Sidebar)
   und **nur wenn `!= null`**. Es gibt kein Editier-Widget, und Punkte können nicht
   erstmalig gesetzt werden. Zusätzlich fehlt backendseitig ein Weg, Punkte wieder zu
   leeren (kein `clearStoryPoints`-Flag).
2. **Keine Sprint-Übersicht.** `GET /projects/{key}/sprints` liefert bereits alle Sprints
   mit Status (`PLANNED`/`ACTIVE`/`CLOSED`), Punkten und Daten, aber es gibt keine Seite,
   die laufende, geplante und abgeschlossene Sprints gesammelt zeigt. Die Backlog-Seite
   zeigt nur geplante Sprints + Backlog.
3. **Issues öffnen nur als Vollseite.** Ein Issue öffnet heute ausschließlich über die
   Route `/p/:key/issues/:issueKey`. Gewünscht ist ein Dialog/Modal, das sich über die
   aktuelle Seite (Board/Backlog/Liste) legt (abgedunkelter Hintergrund, nur das Popup
   klar sichtbar) und von verschiedenen Stellen geöffnet werden kann.

## Ziele

- Story Points im Issue-Detail setzen, ändern und leeren können (Fibonacci-Raster).
- Eine eigene Sprint-Übersichtsseite mit den Gruppen Laufend / Geplant / Abgeschlossen.
- Issues von verschiedenen Stellen als Modal öffnen, das die aktuelle Seite überlagert;
  die Vollseite bleibt als Deep-Link-/Reload-Fallback erhalten.

## Nicht-Ziele (YAGNI)

- Kein Inline-Editieren der Story Points direkt auf Board-Karte/Backlog-Zeile (nur Anzeige).
- Keine Sprint-Aktionen (Start/Abschließen/Bearbeiten) auf der neuen Übersichtsseite —
  Start bleibt im Backlog, Abschließen bleibt im Board.
- Keine erzwungene Fibonacci-Validierung im Backend (Raster wird frontendseitig angeboten;
  das Backend akzeptiert weiterhin beliebige `Int?`).
- Kein E2E-Test-Framework-Aufbau.

## Architektur & Phasen

Ein Spec, drei unabhängig auslieferbare Phasen. Phase 1 legt das Fundament (geteilte
Issue-Komponente + Modal), auf dem Phase 2 und 3 aufbauen.

---

### Phase 1 — Issue-Dialog (Fundament)

**Kernidee:** Der Modal-Zustand hängt am URL-Query-Parameter `?issue=KEY`, nicht an einer
eigenen Route. Dadurch legt sich das Modal über die aktuelle Seite (die im Hintergrund
abgedunkelt sichtbar bleibt), ist deep-linkbar und per Browser-Zurück schließbar.

**Komponenten:**

- **`IssueDetailContent`** (neu) — der komplette Inhalt der heutigen `IssueDetailPage`
  (Header, Beschreibung, Kommentare, Activity, Sidebar-Felder, Anhänge, References),
  extrahiert in eine wiederverwendbare Komponente mit Props `{ projectKey, issueKey }`.
  Nutzt unverändert die bestehenden Hooks (`useIssue`, `useUpdateIssue`, `useProjectMembers`,
  `useSprints`, `useLabels`, `useVersions`, `useCustomFields`).
- **`IssueDetailPage`** (bleibt, wird schlank) — rendert `IssueDetailContent` als Vollseite
  unter `/p/:key/issues/:issueKey`. Deep-Link-/Reload-Fallback.
- **`IssueDialog`** (neu) — Modal: abgedunkelter Backdrop, zentriertes scrollbares Panel,
  Schließen per ✕-Button, `Esc` und Backdrop-Klick. Rendert innen `IssueDetailContent`.
  Enthält oben einen „Vollansicht öffnen"-Link zur Vollseiten-Route.
- **`IssueDialogHost`** (neu) — einmal in `AppLayout` gemountet. Liest `?issue=KEY` via
  `useSearchParams`; ist der Parameter gesetzt und wir sind in einem Projektkontext,
  rendert er `IssueDialog`. Schließen entfernt den Query-Parameter (behält den restlichen
  Pfad/Query bei).

**Öffnen von verschiedenen Stellen** — alle setzen lediglich `?issue=KEY` (per
`setSearchParams` bzw. `Link`), ohne den Pfad zu wechseln:

- Board-Karte (`DraggableCard`)
- Backlog-Zeile (`IssueRow` in `BacklogPage`)
- Issue-Liste (`IssueListPage`)
- Sprint-Übersicht (Phase 3, sofern dort Issues gelistet werden — optional)

**dnd-kit-Wrinkle (Board-Karte):** `DraggableCard` nutzt Drag-Listener. Klick vs. Ziehen
wird über die bestehende 5px-Aktivierungsdistanz getrennt. Der Klick-Handler öffnet das
Modal nur, wenn kein Drag stattgefunden hat (Guard über den `isDragging`-Zustand bzw.
Unterdrücken des Klicks direkt nach einem Drag), damit ein Verschieben nicht versehentlich
das Issue öffnet.

**Datenfluss:** Modal und Vollseite laden dasselbe Issue über denselben React-Query-Key,
d. h. der Cache wird geteilt. Änderungen im Modal spiegeln sich sofort in der
Hintergrundseite (Board/Backlog) wider.

**Barrierefreiheit:** Fokus-Falle im offenen Modal, `Esc` schließt, Backdrop hat
`aria-hidden`-Hintergrund; der Öffner-Link bleibt ein echter Link (Mittelklick/Neuer Tab
funktioniert und öffnet die Vollseite bzw. die Seite mit Query-Parameter).

---

### Phase 2 — Story Points editierbar (Fibonacci)

**Frontend:**

- **`StoryPointsSelector`** (neu) — analog zu `PrioritySelector`/`TypeSelector`, platziert
  in der Sidebar von `IssueDetailContent`. Zeigt den aktuellen Wert als Chip; Klick öffnet
  ein Popover mit den festen Optionen **1, 2, 3, 5, 8, 13, 21** plus **„—" (leeren)**.
  - Wert wählen → `patch({ storyPoints: n })`
  - „leeren" wählen → `patch({ clearStoryPoints: true })`
  - Wird **immer** gerendert (nicht mehr nur bei `!= null`); ohne Wert zeigt der Chip einen
    dezenten Platzhalter („Set points").
- Der `updateIssue`-Payload-Typ im Frontend muss `clearStoryPoints?: boolean` zulassen.
- Die read-only Chips auf Board-Karte und Backlog-Zeile bleiben unverändert und zeigen
  dank geteiltem Cache automatisch den neuen Wert.

**Backend:**

- `UpdateIssueRequest` erhält `clearStoryPoints: Boolean = false`.
- `IssueService` wendet dieselbe Semantik wie bei `clearAssignee`/`clearSprint` an:
  `clearStoryPoints=true` → `storyPoints = null`; `storyPoints=n` → Wert setzen;
  beides ungesetzt → keine Änderung. Der bestehende `STORY_POINTS_CHANGED`-Activity-Eintrag
  wird wie bisher geschrieben.

---

### Phase 3 — Sprint-Übersicht

**Route & Navigation:**

- Neue Route `/p/:key/sprints` → `SprintsPage`.
- Neuer Menüpunkt **„Sprints"** in der Projekt-Navigation (`AppLayout`), zwischen
  „Backlog" und „Issues".

**Daten:** Der bestehende `useSprints(key)` liefert bereits alles Nötige
(`status`, `plannedPoints`, `completedPoints`, `startDate`, `endDate`). **Keine
Backend-Änderung.** Gruppierung erfolgt clientseitig nach Status:

- **Laufend** (`ACTIVE`) — oben, hervorgehoben.
- **Geplant / Backlog** (`PLANNED`).
- **Abgeschlossen** (`CLOSED`) — chronologisch absteigend.

**`SprintCard`** (neu) pro Sprint: Name, Ziel, Zeitraum, Punkte-Kennzahl
(geplant/erledigt); bei Laufend zusätzlich ein schlanker Fortschrittsbalken
(`completedPoints / plannedPoints`). Read-only.

**Klick-Navigation (kontextabhängig):**

- Laufend → `/p/:key/board`
- Geplant → `/p/:key/backlog`
- Abgeschlossen → `/p/:key/reports`

Leere Gruppen zeigen einen dezenten Hinweistext statt zu verschwinden.

---

### Phase 3 — Addendum: Aufgeschobene Phase-1-Minors

Drei kleine Interaktions-/Robustheitslücken aus Phase 1 (Issue-Dialog), die dort bewusst
zurückgestellt wurden, werden zusammen mit Phase 3 behoben. Alle drei betreffen nur
bestehende Frontend-Dateien, kein Backend, keine neue Abhängigkeit.

**Minor 1 — Board-Karte per Tastatur öffenbar (`DraggableCard.tsx`).**
Die Karte ist ein fokussierbares `role="button"`-Div (dnd-kit-`attributes`), aber
Enter/Space auf einem Div lösen kein `onClick` aus, und der bestehende `onClick` bricht
ohnehin ab, weil er eine pointer-basierte `downPos` erwartet. Da der Board-`DndContext`
**nur einen `PointerSensor`** (Distanz 5) nutzt und **keinen `KeyboardSensor`**, sind
Enter/Space frei. Lösung: ein `onKeyDown`-Handler öffnet das Issue bei `Enter`/`Space`
(`openIssue(issue.key)`), abgesichert mit `!isDragging` und `preventDefault()` bei Space
(kein Seiten-Scroll). Kein Konflikt mit Drag.

**Minor 2 — Drag-Guard härten (Netto-Verschiebungs-Bug) (`DraggableCard.tsx`).**
Der Guard prüft heute die Netto-Distanz `hypot(jetzt − down) < 5`; ein Hin-und-zurück-Drag
innerhalb von 5px kann dadurch gleichzeitig droppen **und** das Modal öffnen. Lösung: ein
`draggedRef` merkt sich, ob tatsächlich ein Drag stattfand — es wird `true`, sobald
`isDragging` `true` wird (via `useEffect` auf `isDragging`), und bei `pointerdown`
zurückgesetzt. `onClick` öffnet nur, wenn `!draggedRef.current`. Die Semantik wird damit
„öffnen genau dann, wenn kein Drag passiert ist" statt „wenn nah am Startpunkt geendet".

**Minor 3 — Kein Doppel-Editor über der Vollseite (`IssueDialogHost.tsx`).**
`IssueDialogHost` ist global in `AppLayout` gemountet; ein manuelles `?issue=KEY` während
man bereits auf `/p/:key/issues/:issueKey` ist, legt das Modal über den identischen
Vollseiten-Editor (zwei Editoren desselben Issues). Lösung: `IssueDialogHost` unterdrückt
das Rendern, wenn die aktuelle Route genau die Vollseiten-Route dieses Issues ist
(Abgleich via `useMatch('/p/:key/issues/:issueKey')` gegen den `?issue=`-Key); der
Query-Parameter wird dort schlicht ignoriert.

---

## Testing

**Backend:**

- Test in `IssueServiceTest` für `clearStoryPoints`: vorhandene Punkte werden auf `null`
  gesetzt, `STORY_POINTS_CHANGED`-Activity wird geschrieben. Analog zu bestehenden
  `clearAssignee`-Tests. Datenbank-neutral gehalten.

**Frontend (Vitest/RTL, bestehende Muster):**

- `StoryPointsSelector` — Wert setzen ruft `patch({ storyPoints: n })`, leeren ruft
  `patch({ clearStoryPoints: true })`.
- `IssueDialogHost` — öffnet Modal bei gesetztem `?issue=`, schließt bei Entfernen des
  Parameters; `Esc`/Backdrop schließen.
- `SprintsPage` — korrekte Gruppierung nach Status, kontextabhängige Links.

**Frontend hat kein Test-Framework** (kein Vitest/RTL) — Absicherung per `npm run build`
(Typecheck) + manuelle Verifikation, wie in allen bisherigen Phasen.

**Manuell verifiziert vor Abschluss jeder Phase:** Board-Klick → Modal → editieren →
Hintergrundseite aktualisiert sich. Für die Minors zusätzlich: (1) Board-Karte per Tab
fokussieren, Enter/Space öffnet das Issue; (2) Karte minimal hin-und-zurück ziehen öffnet
**nicht** das Modal; (3) auf der Vollseite `?issue=SELBER-KEY` anhängen zeigt **keinen**
zweiten Editor.

## Betroffene Dateien (Orientierung, nicht abschließend)

**Frontend — neu:**
`components/issue/IssueDetailContent.tsx`, `components/issue/IssueDialog.tsx`,
`components/issue/IssueDialogHost.tsx`, `components/issue/StoryPointsSelector.tsx`,
`pages/sprints/SprintsPage.tsx`, `components/sprint/SprintCard.tsx`.

**Frontend — geändert:**
`pages/issues/IssueDetailPage.tsx` (auf `IssueDetailContent` reduziert),
`layouts/AppLayout.tsx` (DialogHost mounten + „Sprints"-Nav), `app/router.tsx`
(Sprints-Route), `components/board/DraggableCard.tsx`, `pages/backlog/BacklogPage.tsx`,
`pages/issues/IssueListPage.tsx` (Öffnen via `?issue=`), `types/index.ts` /
`api/issues.ts` (Payload-Typ `clearStoryPoints`).

**Frontend — geändert (Phase-3-Addendum, Minors):**
`components/board/DraggableCard.tsx` (Tastatur-Öffnen + Drag-Guard-Härtung),
`components/issue/IssueDialogHost.tsx` (Doppel-Editor-Guard via `useMatch`).

**Backend — geändert:**
`issues/api/dto/UpdateIssueRequest.kt` (`clearStoryPoints`),
`issues/application/IssueService.kt` (Clear-Semantik).

## Offene Punkte / Risiken

- **dnd-kit Klick-vs-Drag** auf der Board-Karte ist der einzige nicht-triviale
  Interaktionspunkt; in Phase 1 abgesichert, mit dem Phase-3-Addendum (Minor 1 & 2)
  gehärtet (Tastatur-Öffnen + Drag-tatsächlich-passiert-Guard). Weiterhin manuell testen.
- **Wiki-Docs:** Als letzte Aufgabe je Phase die relevanten mkdocs-Seiten
  (frontend/components, frontend/overview, ggf. backend/issues) aktualisieren; `ai-guide.md`
  bei neuen Mustern (Modal-via-Query-Param) ergänzen.
