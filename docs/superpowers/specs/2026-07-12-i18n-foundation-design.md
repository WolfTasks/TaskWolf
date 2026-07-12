# #13 — Internationalisierung: Fundament + Pilot-Slice (Design)

> Backlog-Item **#13** aus `2026-07-07-backlog-overview.md` (Typ: Full-Stack/UI).
> Dieser Zyklus liefert das **i18n-Fundament** plus einen vollständig migrierten
> **Pilot-Slice** als kopierbares Muster. Die flächendeckende String-Extraktion
> über alle ~95 `.tsx`-Dateien erfolgt in eigenen Folge-Zyklen pro Feature.

## Ziel & Scope-Entscheidungen

Die Oberfläche soll in mehreren Sprachen nutzbar sein (Start: **Deutsch + Englisch**,
erweiterbar). Heute sind UI-Texte hart im Frontend verdrahtet und gemischt DE/EN;
es gibt keine i18n-Infrastruktur.

Vier Kern-Entscheidungen (im Brainstorming bestätigt):

1. **Scope = Fundament + Pilot-Slice.** Framework, Ressourcen-Struktur, Umschalter,
   Persistenz, Detection und Formatierungs-Fundament + genau **ein** vollständig
   migrierter Bereich. Kein Big-Bang über alle Seiten.
2. **Persistenz = localStorage + Backend-User-Preference.** localStorage als
   sofortiger/optimistischer Cache; zusätzlich ein Sprach-Feld am User, damit die
   Wahl dem Nutzer geräteübergreifend folgt (Berührungspunkt #3).
3. **Backend-Texte: nur Client übersetzen.** Serverseitige Validierungs-/Fehler-/
   Notification-Texte bleiben unverändert; Spring `MessageSource` ist ein
   abgegrenzter Folge-Zyklus.
4. **Pilot-Slice = Nav/Chrome + Auth + Settings** (inkl. dem neuen Umschalter).

**Framework-Wahl: `react-i18next` + `i18next` + `i18next-browser-languagedetector`**
(Ansatz A). Deckt jeden in #13 gelisteten Punkt ab — Namespaces, Interpolation,
Pluralisierung, Detection, Intl-Formatierung, Persistenz — mit minimalem Eigen-Code
und fügt sich über `useTranslation()` in den bestehenden Hook-Stil ein.

**Ausdrücklich NICHT im Scope:** flächendeckende Extraktion aller Seiten;
serverseitige Text-Lokalisierung (`MessageSource`); Übersetzung der bestehenden
relativen Zeiten in Feeds/Activity (nur das Formatierungs-Fundament wird gelegt);
weitere Sprachen über `en`/`de` hinaus.

## Architektur & Initialisierung

- **Neue Frontend-Deps:** `i18next`, `react-i18next`, `i18next-browser-languagedetector`.
- **Neues Modul `frontend/src/i18n/`:**
  - `index.ts` — i18next-Init: `initReactI18next`, `LanguageDetector`, statisch
    importierte Ressourcen, registrierte Formatter, `fallbackLng: 'en'`,
    `supportedLngs: ['en','de']`, `nonExplicitSupportedLngs: true` (mappt `de-DE`→`de`),
    `interpolation.escapeValue: false` (React escaped selbst), `debug` nur in Dev.
  - `locales/` — Übersetzungs-JSON (s.u.).
  - `format.ts` — Intl-Helfer (s. „Formatierung").
- **Einbindung:** `import './i18n'` in `main.tsx` **vor** dem `createRoot`-Render.
  Kein zusätzlicher Provider nötig (react-i18next liefert Context implizit).
- Komponenten konsumieren via `const { t } = useTranslation('<ns>')`.

## Ressourcen-Struktur

```
frontend/src/i18n/locales/
  en/  common.json  nav.json  auth.json  settings.json
  de/  common.json  nav.json  auth.json  settings.json
```

- **Namespaces nach Bereich** (nicht pro Komponente). `common` = geteilte Begriffe
  (Save/Cancel/Loading/Error…). Der Pilot legt `nav`, `auth`, `settings` an;
  jeder Folge-Zyklus fügt genau **einen** neuen Feature-Namespace hinzu.
- **Keys semantisch/hierarchisch**, nicht der englische Text als Key:
  `profile.displayName`, `common.save`, `auth.login.submit`. So bleiben Keys stabil,
  wenn sich Copy ändert.
- **Statischer Import** der JSON in `index.ts` (bei 2 Sprachen kein Lazy-Loading nötig).
- **Beide Locales bleiben schlüsselgleich.** Fehlt ein Key in `de`, greift der
  `en`-Fallback automatisch (kein Crash) — für den Pilot müssen aber beide Sprachen
  vollständig gepflegt sein.

## Sprachen, Fallback & Detection

- Unterstützt: **`en`, `de`**. **Fallback-/Default-Sprache: `en`** (breitester
  OSS-Default).
- **Detection-Reihenfolge** (`LanguageDetector.order`):
  1. **Backend-Preference** — sobald `/me` geladen ist und `language != null`, wird
     sie angewandt (Backend gewinnt geräteübergreifend, s. „Persistenz").
  2. **localStorage** (`taskowolf.lang`).
  3. **`navigator.language`** (Browser `Accept-Language`).
- Bei Sprachwechsel wird **`document.documentElement.lang`** aktualisiert (a11y/SEO).

## Sprachumschalter & Persistenz

- **Komponente `LanguageSwitcher`** (`frontend/src/components/`): ein `Select`
  (`en`/`de`), zeigt die aktuelle `i18n.language`. Platzierung: **`ProfilePage`**
  (Settings). (Weitere Platzierung z.B. im Account-Menü ist bewusst außerhalb des
  Scopes.)
- **Wechsel-Flow:**
  1. `i18n.changeLanguage(lng)` → `LanguageDetector` schreibt sofort `localStorage`
     (optimistisch, kein Warten auf Netz), `<html lang>` wird aktualisiert.
  2. Feuert **`PATCH /api/v1/me/language`** über neuen Hook `useUpdateLanguage`
     (analog `useUpdateProfile`), invalidiert `['me']`.
- **App-Start-Abgleich:** Nach dem ersten erfolgreichen `/me`-Load wird, falls
  `me.language` gesetzt ist und von der aktiven Sprache abweicht,
  `i18n.changeLanguage(me.language)` aufgerufen (ein kleiner Effekt, z.B. in
  `AppLayout` oder einem `useLanguageSync`-Hook). So folgt die Wahl dem Nutzer auf
  einem neuen Gerät, auch wenn dort noch nichts im localStorage steht.

## Backend-Änderungen (minimal)

- **Flyway-Migration `V30`** (aktuell höchste = V29):
  `ALTER TABLE users ADD COLUMN language varchar(8);`
  Nullable — `null` bedeutet „keine explizite Wahl", der Client detektiert dann selbst.
- **`User.language: String? = null`** (`auth/domain/User.kt`).
- **`UserResponse`** und die `/me`-Shape um `language` erweitern; im Frontend den
  `me`-Typ (`types`/`api`) mitziehen.
- **Neuer Endpoint `PATCH /api/v1/me/language`** in `MeController` (analog zum
  bestehenden `PATCH /api/v1/me/password`):
  - Body `{ "language": "de" }`.
  - **Validierung gegen erlaubte Werte** (`en`, `de`). Unbekannter/leerer Wert →
    **400** mit sauberer, generischer Meldung — **kein** Enum-/Interna-Leak
    (H2-Lektion aus dem Backlog beachten: keine `valueOf`-Exception durchreichen).
  - Logik in `UserAccountService` (setzt `user.language`, speichert).
- **Backend-Tests:** MockK-Unit (`UserAccountService.updateLanguage`) +
  Integrationstest (setzen → `/me` liefert Wert; ungültiger Wert → 400).

## Formatierung (Intl-Fundament)

- **`frontend/src/i18n/format.ts`** — dünne Helfer, Locale aus `i18n.language`:
  - `formatDate(date)`, `formatDateTime(date)` via `Intl.DateTimeFormat`.
  - `formatNumber(n)` via `Intl.NumberFormat`.
  - `formatRelativeTime(date)` via `Intl.RelativeTimeFormat` (für „vor 3 Minuten"-Muster).
- Zusätzlich als **i18next-Formatter** registriert (`{{count, number}}`,
  `{{d, datetime}}`), damit Interpolation lokalisiert formatiert.
- **Voller Rollout** auf bestehende relative Zeiten (Feeds/Activity) ist Folge-Zyklus;
  hier wird das Fundament gelegt und im Pilot dort genutzt, wo Datum/Zahl anfällt.

## Pilot-Slice (genaue Dateien)

Vollständig extrahiert + in `en`/`de` gepflegt:

- **Nav/Chrome — `layouts/AppLayout.tsx`:** Sidebar-Labels (inkl. `sectionLabel`
  je Gruppe), Header, Logout, User-/Account-Menü.
- **Auth — `pages/auth/*`:** Login, Register — Labels, Buttons, Platzhalter und die
  Fehlermeldungen, die die UI selbst rendert (keine Backend-Strings).
- **Settings — `pages/settings/*`:** mindestens `ProfilePage` (inkl. `LanguageSwitcher`),
  `SecurityPage`, `AccountSettingsPage`, `NotificationSettingsPage` sicher
  vollständig; `AccessTokensPage`/`IntegrationsPage`/`WebhooksPage`/`ApiKeysPage`
  nach Aufwand. **Ziel = repräsentatives Muster** (Nav + Formular + Buttons +
  Fehleranzeige + Umschalter), nicht 100% jeder Settings-Unterseite.

Alles außerhalb des Slices bleibt unverändert (weiter hart kodiert) → kein
Regressionsrisiko für nicht angefasste Seiten.

## Fehlerbehandlung & Edge Cases

- **Fehlender Key:** i18next fällt auf `en` zurück, dann auf den Key selbst — nie ein
  Crash. `saveMissing`/`debug` nur in Dev, um Lücken zu finden.
- **`PATCH /me/language` schlägt fehl:** die localStorage-Wahl bleibt aktiv und die
  UI blockiert nicht; stiller Retry beim nächsten Wechsel. Sprachwechsel ist rein
  clientseitig sofort wirksam, unabhängig vom Netz.
- **Interpolation immer über Variablen** (`t('greeting', { name })`), niemals
  String-Concat aus übersetzten Fragmenten — vermeidet Grammatik-Fallen in anderen
  Sprachen. Pluralisierung über i18next-`_plural`-Keys wo nötig.
- **Ungültige gespeicherte Sprache** (z.B. manipulierter localStorage / entfernte
  Sprache): `supportedLngs` filtert, `fallbackLng` greift.

## Testing / Verifikation

- **Frontend** hat kein Test-Framework (#5 offen) → Verifikation via
  `npm run build` (`tsc` + Vite-Build) **und** manuelle Browser-Prüfung:
  - Umschalter wechselt Nav/Auth/Settings sichtbar zwischen DE/EN.
  - Reload behält die Wahl (localStorage).
  - Geräteübergreifend: Wahl auf Gerät A → frischer Login (leerer localStorage) auf
    Gerät B übernimmt sie aus `/me`.
  - Fehlender Key fällt sauber auf `en` zurück (kein Crash / kein Roh-Key im Kern-Pilot).
- **Backend:** MockK-Unit + Integrationstests (setzen/lesen, 400 bei ungültigem Wert).

## Muster-/Konventions-Doku (Teil des Deliverables)

Kurzer Abschnitt im Wiki/`ai-guide` bzw. am Ende dieses Specs: **„Wie füge ich in
einem Folge-Zyklus Übersetzungen hinzu"** — neuen Namespace anlegen (`en`+`de`),
Keys hierarchisch benennen, `useTranslation('<ns>')`, keine String-Concats,
Datums-/Zahl-Formatierung über `format.ts`. Damit dient der Pilot als reproduzierbares
Muster für die schrittweise Migration der übrigen Seiten.

## Berührungspunkte

- **#3 (User-Profil/Settings):** Umschalter lebt im Settings-Bereich; Backend-Pref
  reiht sich in das Per-User-Preference-Muster ein (analog `notification-preferences`).
- **#5 (UI-Tests):** künftige UI-Tests sollten gegen i18n-Keys/`data-*` prüfen statt
  gegen feste Strings.
- **Folge-Zyklen:** (a) restliche Seiten schrittweise migrieren; (b) serverseitige
  Lokalisierung via `MessageSource`; (c) voller Rollout der Intl-Zeitformatierung in
  Feeds/Activity; (d) weitere Sprachen.
