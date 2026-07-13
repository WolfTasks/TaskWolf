# #15 — Internationalisierung: Full-Rollout (Master-Spec, mehrere Sessions)

> Folge-Vorhaben zu **#13** (i18n-Fundament + Pilot-Slice, gemergt als `0b9b817`,
> PR #57). Das Fundament (`react-i18next`, `en`/`de`, Fallback `en`, `format.ts`,
> `LanguageSwitcher`, Backend `PATCH /me/language` + V30) steht bereits und liefert
> das kopierbare Muster. **Diese Master-Spec beschreibt die flächendeckende
> Extraktion aller verbleibenden Frontend-Komponenten über mehrere Sessions**, bis
> die komplette React-UI zu 100% lokalisiert ist.
>
> **Diese Spec ist ein lebendes Dokument:** die Coverage-Matrix in Abschnitt 4 wird
> pro abgeschlossener Session abgehakt und dient als Fortschrittsanzeige.

## Ziel & Scope-Entscheidungen

Nach dem Fundament sind nur **8** `.tsx`-Dateien lokalisiert (Pilot-Slice: `AppLayout`,
`LoginPage`, `RegisterPage`, `ProfilePage`, `SecurityPage`, `AccountSettingsPage`,
`NotificationSettingsPage`, `LanguageSwitcher` + Hook `useLanguageSync`). Die
restlichen **~87** Dateien in ~14 Feature-Bereichen sind weiter hart kodiert
(gemischt DE/EN). Ziel dieses Vorhabens: **jede nutzersichtbare UI-Zeichenkette im
Frontend läuft über `t()`**, in `en` und `de` vollständig gepflegt.

Im Brainstorming bestätigte Kern-Entscheidungen:

1. **Scope = Frontend-UI zu 100%.** Serverseitig gerenderte Texte (Spring
   `MessageSource`: Validierungs-/Fehler-/Notification-/E-Mail-Texte) sind **nicht**
   Teil dieses Vorhabens, sondern ein bewusst benannter, separater Folge-Zyklus
   (Backlog **#16**), damit er nicht untergeht.
2. **„100%" wird objektiv erzwungen** durch einen automatischen
   Hardcoded-String-Scanner mit schrumpfender Baseline-Allowlist (Abschnitt 2). CI
   ist grün, solange keine *neuen* harten Strings dazukommen; Allowlist leer = fertig.
3. **Session-Schnitt = ein Feature-Namespace pro Session** (~18 Slices, Abschnitt 3),
   nach Nutzer-Sichtbarkeit geordnet. Passt zum Fundament-Muster („ein Namespace pro
   Folge-Zyklus") und zur Arbeitsweise „eine Phase pro Session".
4. **Kein neuer Brainstorm/Spec je Slice.** Das Muster ist gelockt; jede Folge-Session
   erhält nur einen **dünnen Ausführungsplan** gegen diese Master-Spec.

**Ausdrücklich NICHT im Scope:** Backend-`MessageSource` (#16); weitere Sprachen über
`en`/`de` hinaus; Einführung eines Frontend-Test-Frameworks (#5, orthogonal — der
Scanner ist Lint, kein Test-Runner).

## Rollout-Strategie (Ansatz A: Scanner-first)

Abgewogene Ansätze:

- **A — Scanner-first, dann Namespace für Namespace (gewählt).** Ein einmaliger
  Setup-Zyklus („Session 0") führt den Scanner + Baseline-Allowlist + Key-Paritäts-
  Check ein und schreibt diese Master-Spec. Danach lokalisiert jede Session genau
  einen Namespace **und streicht ihre Dateien aus der Allowlist**. Der Verstoß-Zähler
  sinkt monoton auf 0.
- **B — erst alles extrahieren, Scanner zuletzt.** Kein objektives Fortschrittssignal
  unterwegs, Regressionen schleichen unbemerkt ein. Verworfen.
- **C — Big-Bang in einer Session.** Explizit ausgeschlossen (Vorhaben ist bewusst
  mehr-Session). Verworfen.

## Abschnitt 2 — „100%"-Mechanik: Scanner + Baseline-Allowlist

Zwei Prüfungen, als `npm`-Scripts und als CI-Schritt (im bestehenden Frontend-Job):

1. **Hardcoded-String-Scanner.** Meldet nutzersichtbare, hart kodierte JSX-Strings
   (JSX-Text-Kinder mit Buchstaben sowie die String-Literal-Werte der Attribute
   `placeholder`, `title`, `aria-label`, `alt`, `label`). **Umsetzung = schlankes
   eigenes Node-Skript über die TypeScript-Compiler-API** (`typescript` ist bereits
   Dependency) — **bewusst kein ESLint-Stack**: das Frontend hat heute *kein* ESLint,
   und der strenge Supply-Chain-Posture des Repos (npm-audit-Gate, Trivy,
   dependency-review) spricht gegen einen großen neuen Dep-Baum (eslint +
   typescript-eslint + Plugin) für eine einzige Regel. Das Skript ignoriert Nicht-UI-
   Strings strukturell (nur JSX-Text/-Attribute werden geprüft; `className`, `data-*`,
   `key`, Routen-Pfade, Icon-Namen sind entweder keine geprüften Attribute oder als
   reine Symbole/URLs von der Buchstaben-Heuristik ausgenommen). Einzelfälle über einen
   `// i18n-ignore`-Zeilenkommentar entschärfbar (sparsam).
2. **Baseline-Allowlist** (`frontend/scripts/i18n-allowlist.json`): Liste aller heute
   noch unlokalisierten Dateien (repo-relative Pfade). Das Skript überspringt gelistete
   Dateien. Dadurch ist CI **ab Session 0 grün**, ohne dass alle ~87 Dateien sofort
   fehlschlagen. Jede Feature-Session **entfernt ihre Dateien aus der Allowlist**; ab
   dann sind genau diese Dateien scanner-pflichtig, und neu eingeschleuste harte Strings
   brechen den Build (Regressionsschutz). Leere Allowlist + Scanner grün = 100%.
3. **en/de-Key-Paritäts-Check.** Kleines Node-Skript, das für jeden Namespace prüft,
   dass die Key-Mengen in `locales/en/<ns>.json` und `locales/de/<ns>.json` identisch
   sind (rekursiv). Fehlender Key in einer Sprache → Fehler. Läuft im selben CI-Schritt.

**Fertig-Kriterium des Gesamt-Vorhabens:** Allowlist leer **und** Scanner grün **und**
Key-Parität grün über alle Namespaces.

## Abschnitt 3 — Namespace-/Session-Aufteilung

Ein Namespace = ein Bereich = eine Session, geordnet nach Nutzer-Sichtbarkeit
(häufigste Flächen zuerst, Admin/Config zuletzt). Namespaces spiegeln **Bereiche**,
nicht einzelne Komponenten (Fundament-Konvention). Sehr kleine Slices (`backlog`,
`reports`) darf der Executor bei freiem Budget zusammenlegen; die Liste bleibt die
kanonische Ziel-Checkliste. Keys hierarchisch/semantisch (`issue.detail.title`,
nicht der englische Text als Key). Geteilte Begriffe wandern nach `common`.

## Abschnitt 4 — Coverage-Matrix (lebende Checkliste)

Legende: ⬜ offen · 🔧 in Arbeit · ✅ fertig (Dateien aus Allowlist entfernt, Scanner
grün, en/de gepflegt, Build grün, Browser-Check ok).

> **Session-Nummerierung `S#` ist eigenständig** und hat nichts mit den Backlog-Item-
> Nummern (#13/#15/#16) zu tun.

| S# | Namespace | Status | Kern-Dateien |
|----|-----------|--------|--------------|
| S0 | **setup** | ⬜ | Scanner + Allowlist + Key-Parität + Master-Spec + Backlog |
| — | *(Fundament/Pilot)* | ✅ | `common`,`nav`,`auth`,`settings`(Teil): AppLayout, Login/Register, Profile/Security/Account/Notification, LanguageSwitcher |
| S1 | `issues` (Detail) | ⬜ | IssueDetailPage, IssueDetailContent, IssueDialog, IssueDialogHost, InlineEditTitle, IssueListPage |
| S2 | `issues-fields` | ⬜ | StatusBadge, TypeSelector, PrioritySelector, AssigneeSelector, LabelSelector, LabelChip, VersionSelector, VersionChip, VersionTag, SprintSelector, StoryPointsSelector, DueDatePicker, CustomFieldInput, RichTextEditor, AttachmentPanel |
| S3 | `comments` | ⬜ | CommentsActivityTabs, CommentThread, ActivityFeed **+ relative-Zeit-Rollout via `format.ts`** |
| S4 | `board` | ⬜ | BoardPage, BoardColumn, DraggableCard |
| S5 | `backlog` | ⬜ | BacklogPage |
| S6 | `sprints` | ⬜ | SprintsPage, SprintCard, SprintHeader, CreateSprintForm, CompleteSprintDialog |
| S7 | `dashboard` | ⬜ | DashboardPage, ProjectDashboardPage, DashboardCanvas, WidgetPalette, WidgetWrapper, Burndown/IssueCount/IssueList/IssuesByStatus/Velocity/CycleTime-Widget |
| S8 | `reports` | ⬜ | ReportsPage |
| S9 | `notifications` | ⬜ | NotificationBell, NotificationsPage |
| S10 | `projects` | ⬜ | ProjectListPage, ProjectCreatePage |
| S11 | `project-settings` | ⬜ | MembersPage, LabelsPage, VersionsPage, CustomFieldsPage, ProjectAuditPage, OrganizationSettingsPage, SettingsLayout (Rest) |
| S12 | `workflow` | ⬜ | WorkflowEditorPage, WorkflowCanvas, StatusNode, TransitionArrow, TransitionGuardPanel |
| S13 | `automation` | ⬜ | AutomationPage, AutomationRuleEditorPage, AdminAutomationPage, RuleEditor, ActionList, ActionRow, ConditionGroupBuilder, ConditionRow, TriggerSelector |
| S14 | `admin` | ⬜ | AdminUsersPage, AuditLogPage, SsoSettingsPage |
| S15 | `servicedesk` | ⬜ | ServiceDeskPage, IncidentDashboardPage |
| S16 | `orgs` | ⬜ | OrgsPage, OrgSettingsPage, OrgSwitcher |
| S17 | `settings` (Rest) | ⬜ | AccessTokensPage, ApiKeysPage, IntegrationsPage, WebhooksPage (erweitert bestehenden `settings`-NS) |
| S18 | `shared`/Cleanup | ⬜ | DataTable, Table-Komponenten, NavItem, SidebarSection, VersionTag/StatusBadge falls geteilt, Rest-`common`; **finaler Scanner-auf-Null-Sweep** (Allowlist muss danach leer sein) |

> Hinweis: Diese Liste basiert auf dem Datei-Bestand vom 2026-07-13. Kommen bis zum
> Abschluss neue Seiten/Komponenten hinzu, fängt der Scanner sie automatisch (neue
> Datei ohne Allowlist-Eintrag ist sofort scanner-pflichtig) — die Matrix ist Leitfaden,
> der Scanner ist die harte Ziellinie.

## Abschnitt 5 — Definition of Done pro Slice (identisch für jede Session)

Eine Feature-Session ist abgeschlossen, wenn **alle** gelten:

1. **Extraktion vollständig:** jede nutzersichtbare Zeichenkette der Slice-Dateien läuft
   über `t('<ns>:…')`. **Keine** String-Concatenation aus übersetzten Fragmenten;
   Interpolation ausschließlich über Variablen (`t('key', { name })`); Plurale über
   i18next-Plural-Keys.
2. **Ressourcen:** neuer Namespace-JSON in **`en/` und `de/`**, schlüsselgleich; in
   `i18n/index.ts` registriert (`resources` + `ns`-Liste). Key-Paritäts-Check grün.
3. **Formatierung:** Datum/Zahl/relative Zeit über `format.ts` (kein rohes
   `toLocaleString`, keine hart kodierten „vor X Minuten").
4. **Allowlist:** Slice-Dateien aus der Baseline-Allowlist entfernt; Scanner grün
   (0 Verstöße in den nun geprüften Dateien).
5. **Build:** `npm run build` (tsc + Vite) grün.
6. **Manuelle Browser-Prüfung:** DE/EN-Umschalten in genau diesem Bereich sichtbar
   korrekt, kein Roh-Key, keine Layout-Brüche durch längere DE-Strings.

## Abschnitt 6 — Deliverables dieses Zyklus (Session 0)

Session 0 liefert **nur Werkzeuge + Doku**, noch keine Feature-Extraktion:

1. **Scanner + Allowlist + Key-Paritäts-Check** als `npm`-Scripts und CI-Schritt
   (Abschnitt 2). Baseline-Allowlist enthält alle ~87 heute unlokalisierten Dateien →
   CI bleibt grün.
2. **Diese Master-Spec** (`2026-07-13-i18n-full-rollout-design.md`) mit der lebenden
   Coverage-Matrix.
3. **Backlog-Korrekturen** in `docs/superpowers/specs/2026-07-07-backlog-overview.md`:
   - **#13** von „🔀 PR #57 offen, nicht gemergt" auf „✅ Fundament+Pilot **gemergt**
     (`0b9b817`, PR #57; noch nicht released)" korrigieren.
   - Neuer Sammel-Eintrag **#15 — i18n Full-Rollout** (dieses Vorhaben), verlinkt auf
     diese Master-Spec, Status „in Arbeit (mehrere Sessions)".
   - Neuer Eintrag **#16 — Backend-Text-Lokalisierung (Spring `MessageSource`)** als
     bewusst separater Folge-Zyklus (Frontend-Scope endet an der Client-Präsentation).
4. **Kurz-Doku „Wie migriere ich einen Bereich"** (im `ai-guide`/Wiki bzw. am Ende
   dieser Spec verlinkt): Namespace anlegen (`en`+`de`), Keys hierarchisch,
   `useTranslation('<ns>')`, keine String-Concat, `format.ts` für Datum/Zahl, Datei aus
   Allowlist streichen. (Das Fundament-Spec enthält dieses Muster bereits; hier nur der
   Verweis + der Allowlist-Schritt.)

**Nicht in Session 0:** erste Feature-Extraktion (startet Session 1 mit `issues`);
Backend-`MessageSource` (#16); weitere Sprachen.

## Abschnitt 7 — Verifikation

- **Automatisch (CI + lokal):** Scanner (0 neue Verstöße), en/de-Key-Parität, `npm run
  build`. Nach jedem Slice sinkt die Allowlist; am Ende ist sie leer.
- **Manuell:** pro Slice DE/EN-Umschalten im betroffenen Bereich (Fundament-Muster).
- **Backend:** in diesem Vorhaben **unverändert** — keine Migration, keine Endpoints,
  keine Backend-Tests (Client-only-Übersetzung; Backend-Texte = #16).

## Abschnitt 8 — Risiken & Edge Cases

- **Scanner-False-Positives** (technische Strings als „Text" erkannt): über die
  Regel-Options / gezielte `eslint-disable-next-line`-Kommentare mit Begründung
  entschärfen — sparsam, damit die Ziellinie hart bleibt.
- **Längere DE-Strings** brechen Layouts (Buttons/Badges): pro Slice im Browser prüfen;
  betrifft v.a. `issues-fields` (Selektoren/Badges) und `board`.
- **Relative Zeiten** (`comments`/Activity): erst hier wird das `format.ts`-Fundament
  real ausgerollt — auf konsistente Locale-Nutzung achten (kein Rest von hart kodierten
  Zeit-Strings).
- **Geteilte Komponenten** (`StatusBadge`, `DataTable`, `VersionTag`) werden evtl. von
  mehreren Bereichen genutzt → gehören in `common` bzw. den `shared`-Cleanup-Slice, um
  Doppel-Keys zu vermeiden.
- **Neue Features während des Rollouts** (parallele Zyklen) landen automatisch als
  scanner-pflichtige neue Dateien; Reihenfolge in der Matrix bleibt Leitfaden.

## Berührungspunkte

- **#13** (Fundament/Pilot): liefert Framework, `format.ts`, `LanguageSwitcher`, Muster.
- **#16** (neu): Backend-`MessageSource` — separater Folge-Zyklus, hier nur verlinkt.
- **#5** (UI-Tests): künftige UI-Tests gegen i18n-Keys/`data-*` statt feste Strings; der
  hier eingeführte Scanner ist Lint, kein Test-Runner (orthogonal).

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
