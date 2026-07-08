# Design: User-Profil-/Einstellungsseiten (gruppiert) — Backlog #3

> Status: Design abgestimmt (2026-07-08). Nächster Schritt: Implementierungsplan (writing-plans).

## Ziel

Eigene Profil-/Einstellungsseiten pro Nutzer, gruppiert nach Themengebieten,
unter einer dedizierten Settings-Shell mit eigener linker Sub-Navigation:
**Profil / Sicherheit / Benachrichtigungen / Access Tokens / Konto**.

Baut auf dem in #2 (PR #44) angelegten `/settings/*`-Bereich auf (Access Tokens,
Konto/Löschen existieren bereits) und ergänzt echte Profil-, Sicherheits- und
Benachrichtigungs-Funktion — inkl. der dafür nötigen neuen Backend-Endpunkte.

## Scope

**In Scope (vom Nutzer bestätigt):**
- Dedizierte Settings-Shell (verschachteltes Layout mit eigener Sub-Nav).
- **Profil:** Anzeigenamen ändern (neuer Endpunkt).
- **Sicherheit:** Passwort ändern (neuer Endpunkt) — widerruft die Sessions.
- **Benachrichtigungen:** per-User-Präferenzen (neue Entity + Migration +
  Dispatch-Gating); Matrix über **alle 4** Notification-Typen × 2 Kanäle.
- Verschieben der bestehenden Seiten (Access Tokens, Konto) unter die Shell —
  **Pfade bleiben gleich** (`/settings/tokens`, `/settings/account`).

**Bewusst ausgeschlossen:**
- E-Mail-Adresse ändern (hängt an der Login-Identität).
- Avatar/Bild-Upload.

## Frontend

### `SettingsLayout` — `frontend/src/layouts/SettingsLayout.tsx`

Verschachteltes Route-Layout: eigene linke Sub-Navigation (Profil, Sicherheit,
Benachrichtigungen, Access Tokens, Konto) + `<Outlet>` für den Inhalt.

### Router-Umbau (`app/router.tsx`)

`/settings` wird zum Layout mit Kind-Routen:

| Pfad | Seite | Status |
|------|-------|--------|
| `/settings` | Redirect → `/settings/profile` | neu |
| `/settings/profile` | `ProfilePage` | **neu** |
| `/settings/security` | `SecurityPage` | **neu** |
| `/settings/notifications` | `NotificationSettingsPage` | **neu** |
| `/settings/tokens` | `AccessTokensPage` | bestehend, verschoben |
| `/settings/account` | `AccountSettingsPage` | bestehend, verschoben |

Da die Pfade `/settings/tokens` und `/settings/account` gleich bleiben,
entstehen keine toten Links.

### Haupt-Sidebar (`layouts/AppLayout.tsx`)

Die „Account"-Sektion (heute 2 Links: Access Tokens, Account) wird zu **einem**
„Settings"-Eintrag → `/settings`. (Fügt sich in die Icon-Rail aus #8 ein — siehe
Reihenfolge-Hinweis im #8-Spec.)

### API-Client + Hooks

Neuer Client `frontend/src/api/me.ts` (oder Erweiterung eines bestehenden
`useAccount`-Hooks) mit React-Query-Hooks:
- `updateProfile(displayName)` → nach Erfolg `['me']` invalidieren.
- `changePassword(currentPassword, newPassword)` → nach Erfolg lokale Tokens
  löschen und auf `/login` navigieren (Sessions serverseitig widerrufen).
- `getNotificationPreferences()` / `putNotificationPreferences(matrix)`.

### Seiten

- **`ProfilePage`:** Formular mit Anzeigenamen (vorbefüllt aus `['me']`),
  Speichern-Button. E-Mail read-only angezeigt.
- **`SecurityPage`:** Passwort-ändern-Formular (aktuelles PW, neues PW,
  Bestätigung). Nach Erfolg: Hinweis „Bitte neu anmelden" + Redirect `/login`.
- **`NotificationSettingsPage`:** Toggle-Matrix (Zeilen = 4 Notification-Typen,
  Spalten = In-App / E-Mail). Lädt Prefs, speichert die geänderte Matrix.
  Für Typen ohne E-Mail-Dispatch (AUTOMATION, SLA_BREACHED) ist die
  E-Mail-Spalte anklickbar, hat aber noch keinen Effekt (future-proof) — als
  solche im UI kenntlich machen (z.B. Hinweis-Tooltip).

## Backend

### 1. Profil — `PATCH /api/v1/me`

- Controller: `MeController` (erweitern, `auth/api/MeController.kt`).
- DTO: `UpdateProfileRequest(displayName: String)` mit Validierung (nicht leer).
- `UserAccountService.updateProfile(userId, displayName)`.
- Audit-Event `USER_PROFILE_UPDATED`.

### 2. Sicherheit — `POST /api/v1/me/password`

- Controller: `MeController`.
- DTO: `ChangePasswordRequest(currentPassword, newPassword)`; `newPassword`
  mit gleicher Mindest-Validierung wie bei der Registrierung.
- Ablauf: `currentPassword` via `PasswordEncoder.matches` gegen den Hash
  prüfen (bei Fehlschlag 400/401), neues Passwort hashen und speichern.
- **Session-Widerruf:** `refreshTokenService.revokeAllForUser(userId)` (wie
  `AuthService.logout`, `auth/application/AuthService.kt:73`). Der aktuell
  ausgestellte Access-Token läuft kurzlebig aus; das Frontend erzwingt nach
  Erfolg direkt einen Neu-Login.
- **PATs bleiben unangetastet:** Personal Access Tokens (`twk_`,
  `AccessTokenService`) werden bei Passwortwechsel **nicht** widerrufen.
- Audit-Event `USER_PASSWORD_CHANGED`.

### 3. Benachrichtigungen — Präferenzen

**Entity + Migration**
- Neue Entity `NotificationPreference` im `notifications`-Modul:
  `userId: UUID`, `type: NotificationType`, `inAppEnabled: Boolean`,
  `emailEnabled: Boolean`. Eine Zeile pro (User, Typ).
- Flyway-Migration **V29** `notification_preferences` (unique auf
  `(user_id, type)`). Aktuelle Flyway-Konvention einhalten (siehe `ai-guide.md`).
- Repository `NotificationPreferenceRepository` (findByUserId,
  findByUserIdAndType).

**Service**
- `NotificationPreferenceService`:
  - `getMatrix(userId)` → alle 4 Typen; fehlende Zeilen mit Default
    (alles aktiviert) auffüllen.
  - `update(userId, prefs)` → upsert der Zeilen.
  - `isEnabled(userId, type, channel)` → Default `true`, falls keine Zeile
    existiert (opt-out-Modell).

**Dispatch-Gating**
- **In-App (alle 4 Typen):** zentral in `NotificationService` vor
  `repository.save` prüfen (`isEnabled(userId, type, IN_APP)`). Betrifft
  `onMention`, `onIssueFieldChanged` **und** `createDirect`
  (`notifications/application/NotificationService.kt`). Damit sind auch
  AUTOMATION (`automation/application/ActionExecutor.kt:55`,
  `servicedesk/application/IncidentService.kt:30`) und SLA_BREACHED
  (`servicedesk/application/SlaMonitorJob.kt:50`) abgedeckt, da diese über
  `createDirect` laufen.
- **E-Mail:** in `EmailService.onMention` / `onAssigned`
  (`notifications/application/EmailService.kt`) `isEnabled(userId, type, EMAIL)`
  prüfen. Nur Mention/Assigned haben E-Mail-Dispatch; für AUTOMATION/SLA gibt es
  heute keinen E-Mail-Versand → die E-Mail-Präferenz wird gespeichert, hat aber
  noch keinen Effekt (future-proof).

**Endpunkte**
- `GET /api/v1/me/notification-preferences` → Matrix (4 Typen × 2 Kanäle).
- `PUT /api/v1/me/notification-preferences` → Matrix speichern.
- Untergebracht in `MeController` oder einem eigenen
  `NotificationPreferenceController` unter `/api/v1/me/...`.

## Testing

- **Backend (TDD, JUnit + MockK):**
  - `updateProfile` (Name gesetzt, Validierung).
  - `changePassword`: korrektes/falsches aktuelles Passwort, neues Hash gesetzt,
    `revokeAllForUser` aufgerufen, PATs unangetastet.
  - `NotificationPreferenceService`: Default-Matrix, Upsert, `isEnabled`-Logik.
  - Dispatch-Gating: In-App wird bei deaktivierter Pref **nicht** gespeichert
    (alle 4 Typen); E-Mail bei deaktivierter Pref **nicht** gesendet.
  - Migration/Repo gegen echtes Postgres (Testcontainers-Muster; H2 weicht bei
    einigen Typen ab — vgl. Lessons aus dem Audit-Null-Param-Bug).
- **Frontend:** Typecheck + manuell (Profil speichern spiegelt sich in `me`;
  Passwortwechsel erzwingt Neu-Login; Notification-Matrix lädt/speichert;
  deaktivierte Pref unterdrückt tatsächlich die jeweilige Benachrichtigung).

## Wiki-Docs (Pflicht-Abschlussaufgabe)

Vor Abschluss die Wiki-Docs ergänzen (neue `/me`-Endpunkte, Notification-
Präferenzen) und `ai-guide.md` aktualisieren, falls sich Flyway-Version oder
Muster ändern.
