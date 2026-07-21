# Backend-i18n Phase 3 — E-Mails + In-App-Notifications (Design)

**Datum:** 2026-07-21
**Backlog:** #16 (Backend-Text-Lokalisierung via Spring `MessageSource`)
**Status:** Design genehmigt (Brainstorming abgeschlossen); bereit für Implementierungsplan.

## Kontext

Backlog #16 lokalisiert backend-generierten Text über Spring `MessageSource`
(englische Base-Bundle `messages.properties` + `messages_de.properties`,
`\uXXXX`-escaped; Fallback EN). **Phase 1** (Fundament + `issues`-Pilot, PR #83)
und **Phase 2** (voller API-Fehler-Sweep, alle 5 Throw-Konstrukte, Tasks 0–9,
PRs #84/#85/#86/#87/#90) sind auf `main` gemergt (HEAD `e38e3f9`). Jeder
user-facing **API-Fehlertext** ist damit keyed/lokalisiert und durch drei
CI-Gates abgesichert (`MessagesParityTest`, `KeyedReferenceIntegrityTest`,
`NoUnkeyedUserFacingThrowTest`).

**Phase 3 ist das letzte offene Implementierungsstück von #16:** die
async/clientlosen Texte — **E-Mails** und **In-App-Notification-Titel** — werden
in der **Empfänger-Sprache** gerendert. (Der manuelle DE/EN-Browser-Smoke und ein
`v1.0.x`-Release bleiben separat und sind **nicht** Teil dieses Designs.)

Referenz: Ursprüngliche MessageSource-Spec
`docs/superpowers/specs/2026-07-18-backend-i18n-messagesource-design.md`
(Abschnitte 3–4, Phasierung Punkt 3). Dieses Dokument konkretisiert Phase 3 und
präzisiert **zwei Abweichungen**, die während des Brainstormings entschieden
wurden (siehe „Entscheidungen").

## Ziele

1. `EmailService` (Betreff + Body beider Templates) und `NotificationService`
   (Notification-**Titel**) rendern in der **Empfänger-Sprache**.
2. Konsistente Notifications: keine gemischtsprachigen Nachrichten (deutscher
   Titel + englischer Body).
3. Vorhandenes en/de-Paritäts-Gate deckt die neuen Keys automatisch ab.

**Außerhalb Scope:** manueller Smoke + Release (Wolfgang); weitere Sprachen als
en/de; „render-at-read-time" mit strukturierten DB-Params (bewusst YAGNI);
Übersetzung von user-generiertem Inhalt (Kommentar-/Issue-Text, Automations-
`message`).

## Betroffener Code (auf `main` verifiziert)

| Quelle | Datei | Empfänger-Herkunft |
|--------|-------|--------------------|
| E-Mail „Mention" | `notifications/application/EmailService.kt` `onMention` | `event.mentionedUser` (User im Event) |
| E-Mail „Assigned" | `EmailService.kt` `onAssigned` | `event.issue.assignee` (User im Event) |
| Notification „Mention" | `NotificationService.kt` `onMention` | `event.mentionedUser` (User im Event) |
| Notification „Assigned" | `NotificationService.kt` `onIssueFieldChanged` | `event.issue.assignee` (User im Event) |
| Notification „Incident" | `servicedesk/application/IncidentService.kt` `create` → `createDirect` | nur `userId: UUID` |
| Notification „SLA breached" | `servicedesk/application/SlaMonitorJob.kt` → `createDirect` | nur `userId: UUID` |
| Notification „Automation" | `automation/application/ActionExecutor.kt` `SEND_NOTIFICATION` → `createDirect` | nur `userId: UUID` |

## Entscheidungen (Brainstorming)

**E1 — Nicht-User-Content-Bodies mitlokalisieren.** Die Original-Spec sagt „Titel
lokalisieren, Body bleibt User-Content". Das gilt für 3 Quellen (Mention =
Kommentar-Auszug, Assign = Issue-Titel, Automation = frei konfigurierter
`message`). Aber **2 `createDirect`-Bodies sind hartkodierte englische Templates**,
kein User-Content:
- IncidentService: `"A {severity} incident has been declared for issue {…}."`
- SlaMonitorJob: `"Issue {key} has exceeded its SLA resolution time of {n} minutes."`

Diese beiden Template-Bodies werden **mitlokalisiert** (sonst deutscher Titel +
englischer Body). Echte User-Content-Bodies bleiben unübersetzt.

**E2 — Zentrales Rendering im `NotificationService` für den `createDirect`-Pfad.**
Die 3 `createDirect`-Aufrufer kennen nur `userId: UUID`. Statt in jedem Aufrufer
User zu laden + zu rendern (3× Duplikation), wird `createDirect` auf **keyed**
umgestellt: `NotificationService` bekommt `LocalizedMessages` **+ `UserRepository`**,
löst `user.language` per `userId` auf und rendert Titel (und ggf. Template-Body)
intern. Aufrufer bleiben dünn (nur Keys + Args).

**E3 — Incident-Notification zeigt Issue-Key statt roher UUID.** Titel, Template-
Body **und** Link nutzen aktuell die rohe `issueId`-UUID. `IncidentService` bekommt
`IssueRepository` (servicedesk hängt bereits am issues-Modul), lädt den Issue
**einmal** in `create()` und ersetzt die UUID **an allen drei Stellen** durch
`issue.key`. Deckt sich mit Mention-/SLA-Notifications, die bereits `issue.key`
verwenden. Fällt der Issue wider Erwarten weg → Fallback auf `issueId.toString()`
(Notification crasht nie).

## Design

### 1. Grundprinzip: explizite Empfänger-Locale

Alle Texte werden über die **explizite** Locale-Überladung gerendert:
`localizedMessages.get(key, localizedMessages.localeOf(user), args…)`. **Nicht**
über `LocaleContextHolder` — der auslösende Request kann eine andere Sprache haben
als der Empfänger. `LocalizedMessages` liefert bereits:
- `get(key, locale, vararg args)` — explizite Locale mit **EN-Fallback** bei
  `NoSuchMessageException`.
- `localeOf(user: User)` — `user.language` → `Locale`, sonst `Locale.ENGLISH`.

Diese API existiert seit Phase 1 und wird **unverändert** genutzt (keine Änderung
an `LocalizedMessages`).

### 2. `EmailService`

`LocalizedMessages` injizieren. Beide `@EventListener` haben das `User`-Objekt im
Event → Locale direkt auflösbar.
- `onMention` → `localeOf(event.mentionedUser)`; Keys `email.mention.subject`
  (Arg: `issue.key`) und `email.mention.body` (Args: `issue.key`, `issue.title`,
  Kommentar-Auszug `event.comment.body.take(500)`).
- `onAssigned` → `localeOf(assignee)`; Keys `email.assigned.subject` (Arg:
  `issue.key`) und `email.assigned.body` (Args: `issue.key`, `issue.title`).

Der mehrzeilige Body wird als **ein** Key mit `\n`-Platzhaltern gepflegt (Java-
`.properties` unterstützt Zeilenumbrüche via `\n`), Layout entspricht dem heutigen
`trimMargin`-Text.

### 3. `NotificationService`

**Listener (User im Event):** `onMention`, `onIssueFieldChanged` → **Titel** via
`localeOf(user)` gerendert (Keys `notification.mention.title` /
`notification.assigned.title`, Arg: `issue.key`); **Body bleibt** User-Content
(Kommentar-Auszug bzw. `issue.title`) **unverändert** gespeichert.

**`createDirect` → keyed (E2).** Neue Signatur, zentrales Rendering:

```kotlin
@Transactional
fun createDirect(
    userId: UUID,
    type: NotificationType,
    titleKey: String,
    titleArgs: Array<out Any?> = emptyArray(),
    link: String,
    bodyKey: String? = null,               // Template-Body (Incident/SLA)
    bodyArgs: Array<out Any?> = emptyArray(),
    rawBody: String? = null,               // User-Content-Body (Automation-message)
) {
    if (!preferences.isEnabled(userId, type, NotificationChannel.IN_APP)) return
    val locale = userRepository.findById(userId)
        .map(localizedMessages::localeOf).orElse(Locale.ENGLISH)   // Fallback: User weg → EN
    val title = localizedMessages.get(titleKey, locale, *titleArgs)
    val body = when {
        bodyKey != null -> localizedMessages.get(bodyKey, locale, *bodyArgs)
        else -> rawBody.orEmpty()
    }
    repository.save(Notification(userId = userId, type = type, title = title, body = body, link = link))
}
```

`NotificationService` bekommt zusätzlich `LocalizedMessages` + `UserRepository`
injiziert. (Modulgrenze unkritisch: `NotificationService` referenziert `auth`-
`User` bereits über die Events.) Es wird **genau eine** keyed `createDirect`-
Variante geben (die alte String-Signatur entfällt; alle 3 Aufrufer migrieren).
`bodyKey` und `rawBody` schließen sich gegenseitig aus; ist keiner gesetzt, ist
der Body leer.

### 4. `createDirect`-Aufrufer (dünn)

- **IncidentService.create** (E3): einmal `val issue = issueRepository.findById(issueId).orElse(null)`,
  `val issueKey = issue?.key ?: issueId.toString()`. Dann
  `titleKey = "notification.incident.title"`, `titleArgs = arrayOf(severity.name, issueKey)`,
  `bodyKey = "notification.incident.body"`, `bodyArgs = arrayOf(severity.name, issueKey)`,
  `link = "/issues/$issueKey"`.
- **SlaMonitorJob**: `titleKey = "notification.slaBreached.title"`, `titleArgs = arrayOf(issue.key)`,
  `bodyKey = "notification.slaBreached.body"`, `bodyArgs = arrayOf(issue.key, policy.resolutionMinutes.toString())`,
  `link = "/issues/${issue.key}"`.
- **ActionExecutor** (`SEND_NOTIFICATION`): `titleKey = "notification.automation.title"`,
  `titleArgs = arrayOf(issue.key)`, `rawBody = message` (User-Content bleibt),
  `link = "/p/${issue.project.key}/issues/${issue.key}"`.

### 5. Message-Katalog (~11 Keys, en Base + de `\uXXXX`)

```
email.mention.subject       = You were mentioned in {0}
email.mention.body          = {0}: {1}\n\n{2}
email.assigned.subject      = You were assigned to {0}
email.assigned.body         = You have been assigned to: {0}\n{1}
notification.mention.title  = You were mentioned in {0}
notification.assigned.title = You were assigned to {0}
notification.incident.title = Incident declared: {0} on issue {1}
notification.incident.body  = A {0} incident has been declared for issue {1}.
notification.slaBreached.title = SLA Breached: {0}
notification.slaBreached.body  = Issue {0} has exceeded its SLA resolution time of {1} minutes.
notification.automation.title  = Automation: {0}
```

Deutsche Werte werden mit rohen Umlauten geschrieben, dann per PowerShell-Pass
`\uXXXX`-escaped (0 Non-ASCII-Bytes danach); UTF-8-Tests asserten die dekodierten
Strings.

**MessageFormat-Details:**
- Kein literales `'` in irgendeinem Wert → **keine** `''`-Verdopplung nötig.
- **Numerische Args als String übergeben** (`resolutionMinutes.toString()`), damit
  `MessageFormat` keine locale-abhängige Tausendertrennung einfügt (`1440`, nicht
  `1.440`/`1,440`). `severity`/`issueKey` sind ohnehin Strings.

### 6. Tests (JUnit + MockK, explizite Empfänger-Locale)

- **Neu `EmailServiceTest`**: `JavaMailSender` gemockt, `SimpleMailMessage` per
  `slot`/`capture` abgegriffen; für `onMention` **und** `onAssigned` je Betreff +
  Body in **de**, **en** und `language = null`→**en** asserten. `mailHost` gesetzt
  (sonst `enabled == false` → früher `return`); `preferences.isEnabled` → true
  gemockt.
- **`NotificationServiceTest` erweitert**: Titel wird pro Empfänger-Sprache
  gerendert (de/en/null→en); User-Content-Body (`onMention`, `onIssueFieldChanged`)
  bleibt unverändert; `createDirect` mit `bodyKey` → Template-Body lokalisiert,
  mit `rawBody` → Body unverändert. `UserRepository.findById` gemockt (inkl.
  „User nicht gefunden → EN-Fallback").
- **`IncidentServiceTest`**: `IssueRepository` mit-stubben; asserten, dass der
  **Issue-Key** (nicht die UUID) in Titel/Body/Link erscheint; neue keyed
  `createDirect`-Args verifizieren.
- **`SlaMonitorJobTest` / `ActionExecutorTest`**: an die neue keyed
  `createDirect`-Signatur angepasst (Args statt fertiger Strings).
- **`MessagesParityTest`** (bestehendes Gate): erzwingt en/de-Parität der neuen
  Keys automatisch — kein neues Gate nötig. Hinweis: `KeyedReferenceIntegrityTest`
  scannt nur `.keyed(...)`/`message="{k}"`; Phase-3-Keys laufen über
  `LocalizedMessages.get(...)` und werden dort **nicht** erfasst — die Unit-Tests
  decken das Rendering ab.

### 7. Keine DB-Änderung

Titel wird **einmalig bei Erstellung** gerendert und als fertiger String
gespeichert (kein „render-at-read-time", YAGNI). Trade-off: Wechselt ein Nutzer
später die Sprache, bleiben **bereits erzeugte** Notifications in der alten
Sprache — für transiente Notifications akzeptabel. E-Mails sind ohnehin
verschickt. **Kein Migrationsbedarf.**

## Umfang / Session

Eine Session, ein SDD-Lauf (subagent-driven), ein Modul-Slice: 2 Services +
3 `createDirect`-Aufrufer + Katalog (en/de) + Tests. Frischer Worktree off `main`
(kein Stacking), PR → `main`, **stoppt vor Smoke + Release**. Damit ist #16
(bis auf das von Wolfgang durchzuführende Release) abgeschlossen.

## Risiken & Mitigation

- **Modulgrenze `notifications` → `auth.UserRepository` / `servicedesk` →
  `issues.IssueRepository`:** beide Abhängigkeitsrichtungen existieren bereits
  (Events tragen `auth.User`; `ServiceDeskController` nutzt `issues`), also keine
  neue Kopplung.
- **`createDirect`-Signaturbruch:** bewusst; alle 3 Produktiv-Aufrufer + ihre
  Tests migrieren im selben Commit-Slice. Compiler fängt vergessene Aufrufer.
- **Deutsche Mojibake:** roh-Umlaut→`\uXXXX`-Pass + UTF-8-Assertions sind
  selbstkorrigierend.
- **`MessageFormat`-Zahlenformat:** durch `.toString()`-Args entschärft.

## Deliverable

Ein PR nach `main`: ein Feature-Slice (E-Mail-/Notification-Rendering in
Empfänger-Sprache + `createDirect`-keyed-Umstellung + Incident-Key-Fix + Katalog-
Keys en/de + Tests), womit die #16-Phase-3 abgeschlossen ist. Manueller DE/EN-
Smoke + `v1.0.x`-Release folgen separat (Wolfgang).
