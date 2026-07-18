# Backend-Text-Lokalisierung (Spring `MessageSource`) — Design

> Backlog **#16** · Full-Stack · 2026-07-18
> Folge-Zyklus zu **#13/#15** (Frontend-i18n, ausgeliefert v1.0.13). Der
> Frontend-Scope endete bewusst an der Client-Präsentation; #16 lokalisiert die
> **serverseitig erzeugten** Texte.

## Ziel & Kontext

Heute erzeugt das Backend Text ausschließlich hart auf Englisch. Betroffen sind
drei Kategorien:

| Kategorie | Ort | Umfang | Sichtbarkeit |
|---|---|---|---|
| **API-Fehler / Validierung** | `GlobalExceptionHandler` + **84 throw-Sites** in 28 Dateien; Bean-Validation-Annotations | Fehler-/Validierungsmeldungen | Frontend zeigt `e.response?.data?.message` **direkt** an |
| **E-Mails** | `EmailService` | 2 Templates (Mention, Assigned): Betreff + Body | Direkt beim Nutzer (kein Client) |
| **In-App-Notifications** | `NotificationService` (+ 3 `createDirect`-Aufrufer) | Notification-**Titel**; als Text in DB persistiert | Frontend zeigt gespeicherten Titel |

**Frontend-Ist-Zustand (relevant):** Fehler werden als
`e.response?.data?.message || t('fallback')` angezeigt — das Frontend
**bevorzugt** also die Backend-Meldung. Deshalb muss die Backend-Meldung selbst
lokalisiert werden.

**Scope-Entscheid:** Voller Backend-Rollout — **alle drei Kategorien**,
durchgängig **serverseitig via Spring `MessageSource`** (nicht client-seitiges
Code→Text-Mapping). Sprachen **en/de**, Default/Fallback **en**.

### Verworfene Alternative (dokumentiert)

Für die API-Fehler wurde ein **client-seitiges Mapping** erwogen (Backend liefert
granulare `code`+Params, Frontend übersetzt via bestehender i18n). Verworfen
zugunsten der serverseitigen Lösung: eine einheitliche Mechanik über alle drei
Kategorien, funktioniert für **jeden** Client (auch PAT/REST-Consumer), und der
Item-Auftrag lautet explizit „Spring `MessageSource`". Preis: ein zweiter
Übersetzungskatalog im Backend parallel zum Frontend (durch Paritäts-Gate
abgesichert).

## Architektur

### Locale-Auflösung

Zwei Kontexte mit unterschiedlichen Locale-Quellen:

- **Request-gebunden** (API-Fehler, Validierung): `AcceptHeaderLocaleResolver`
  - `supportedLocales = [en, de]`, `defaultLocale = en`
  - Das **Frontend** setzt einen axios-**Request-Interceptor**, der
    `Accept-Language: <i18next.language>` (aktive UI-Sprache) mitschickt.
  - Anonyme Requests (Login/Register) → Browser-`Accept-Language` → Fallback en.
  - Auflösung zur Laufzeit über `LocaleContextHolder.getLocale()`.
- **Async / clientlos** (E-Mail, Notification-Titel): explizit die persistierte
  **`user.language` des Empfängers** → `Locale.forLanguageTag(lang)`, Fallback en
  wenn `null`/leer.

### `MessageSource`-Konfiguration

- Spring-Boot-`MessageSource` (`ResourceBundleMessageSource` bzw. Boot-Autoconfig
  über `spring.messages`), Basename **`messages`**.
- `messages_en.properties` (**Master/Default**) + `messages_de.properties` unter
  `backend/src/main/resources/`.
- `fallbackToSystemLocale = false`, damit unbekannte Locales deterministisch auf
  **en** fallen (nicht auf die Server-JVM-Locale).
- Encoding UTF-8.

### Zentraler Helper

Ein schmaler Service kapselt die Auflösung, damit Aufrufer nicht direkt mit
`MessageSource`/`Locale` hantieren:

```kotlin
@Component
class LocalizedMessages(private val messageSource: MessageSource) {
    /** Request-Locale via LocaleContextHolder (für Handler/Controller). */
    fun get(key: String, vararg args: Any?): String =
        messageSource.getMessage(key, args, LocaleContextHolder.getLocale())

    /** Explizite Locale (für async: E-Mail/Notification in Empfänger-Sprache). */
    fun get(key: String, locale: Locale, vararg args: Any?): String =
        messageSource.getMessage(key, args, locale)

    fun localeOf(user: User): Locale =
        user.language?.takeIf { it.isNotBlank() }?.let(Locale::forLanguageTag) ?: Locale.ENGLISH
}
```

## Komponenten im Detail

### 1. API-Fehler

Die vier Domain-Exceptions (`NotFoundException`, `ForbiddenException`,
`ConflictException`, `BadRequestException`) erhalten **zusätzlich** einen
Key-basierten Pfad. Alle vier implementieren ein gemeinsames Interface
`LocalizedException`, das `messageKey` + `args` freilegt:

```kotlin
interface LocalizedException {
    val messageKey: String?
    val args: Array<out Any?>
}

class NotFoundException : RuntimeException, LocalizedException {
    override val messageKey: String?
    override val args: Array<out Any?>
    // bestehend (Freitext, bleibt rückwärtskompatibel):
    constructor(message: String) : super(message) { messageKey = null; args = emptyArray() }
    // neu (Key + Args):
    constructor(messageKey: String, vararg args: Any?) : super(messageKey) {
        this.messageKey = messageKey; this.args = args
    }
}
```

Der `GlobalExceptionHandler` löst auf:

```kotlin
// ex ist z.B. NotFoundException (RuntimeException + LocalizedException):
private fun resolve(ex: RuntimeException, fallback: String): String {
    val loc = ex as? LocalizedException
    return loc?.messageKey?.let { messages.get(it, *loc.args) } ?: ex.message ?: fallback
}
```

- Der **`code`** in `ErrorResponse` (`NOT_FOUND`, `FORBIDDEN`, `CONFLICT`,
  `BAD_REQUEST`, `VALIDATION_ERROR`, …) bleibt **unverändert und stabil**
  (maschinenlesbar).
- **Rückwärtskompatibilität:** Nicht migrierte throw-Sites nutzen weiter den
  Freitext-Konstruktor und liefern ihren (englischen) Text — nichts bricht,
  phasenweise Migration möglich.
- `INTERNAL_ERROR` und die Handler-Fallbacks werden ebenfalls verkeyt
  (`common.internalError`, `common.notFound`, …); generische letzte Fallback-
  Strings bleiben als Sicherheitsnetz im Code.

### 2. Bean-Validation

- `LocalValidatorFactoryBean` an den `MessageSource` binden
  (`setValidationMessageSource(messageSource)`).
- Annotation-Messages als Keys: `@NotBlank(message = "{issue.title.required}")`,
  `@Size(...)` etc. Der `MethodArgumentNotValidException`-Handler baut `details`
  aus `fieldError.defaultMessage` — das ist dann **bereits in Request-Sprache
  aufgelöst**, keine weitere Änderung im Handler nötig.
- Hibernate-Default-Messages (heute implizit EN) werden bei Bedarf über eigene
  Keys überschrieben; die 2 bestehenden Custom-Messages (Slug-/Key-Pattern) auf
  Keys umstellen.

### 3. E-Mails (`EmailService`)

- Betreff + Body pro Template via `LocalizedMessages.get(key, recipientLocale, args…)`.
- Empfänger-Locale = `localeOf(event.mentionedUser)` bzw. `localeOf(assignee)`.
- Mehrzeiliger Body über eigene Keys (z.B. `email.mention.body`), Args = Issue-Key,
  Titel, gekürzter Kommentar.

### 4. In-App-Notifications (`NotificationService`)

- Der **`title`** wird **zum Erstellungszeitpunkt** in der Empfänger-Sprache
  (`user.language`) gerendert und als fertiger String gespeichert.
- Der **`body`** bleibt **user-generierter Inhalt** (Kommentar-Auszug,
  Issue-Titel) und wird **nicht** übersetzt — unverändert gespeichert.
- Betrifft `onMention`, `onIssueFieldChanged` sowie die 3 `createDirect`-Aufrufer:
  Automation-`ActionExecutor`, Servicedesk-`IncidentService`, `SlaMonitorJob`.
- **Automation-Sonderfall:** Sollte `ActionExecutor` frei konfigurierbaren
  Nutzer-Text als Notification-Titel senden (aus einer Automations-Regel), bleibt
  dieser als user-content **unübersetzt**. Bei der Umsetzung verifizieren und im
  Ergebnis dokumentieren.

**Bewusste Entscheidung (YAGNI):** Kein „render-at-read-time" mit strukturierten
Params in der DB. Titel wird einmalig bei Erstellung gerendert. Trade-off:
Wechselt ein Nutzer später die Sprache, bleiben **bereits erzeugte**
Notifications in der alten Sprache — für transiente Notifications akzeptabel und
deutlich einfacher (kein Schema-/JSON-Param-Umbau). E-Mails sind ohnehin
verschickt. Kein DB-Migrationsbedarf.

## Message-Katalog

- **Namespacing nach Domäne** (analog Frontend-i18n): `issue.*`, `project.*`,
  `org.*`, `auth.*`, `comment.*`, `notification.*`, `email.*`, `validation.*`,
  `common.*` (generische Fallbacks wie `common.notFound`).
- `messages_en.properties` = **Master**, `messages_de.properties` = Übersetzung.
- Platzhalter positional (`{0}`, `{1}`) im `java.text.MessageFormat`-Stil.

### Paritäts-Gate (CI)

Analog zum Frontend-Scanner/Parity-Gate:

- **`MessagesParityTest`** (JUnit, läuft in `backend-test`): stellt sicher, dass
  `messages_en.properties` und `messages_de.properties` **denselben Key-Satz**
  haben (kein fehlender/verwaister Key). Schlägt CI rot bei Drift.
- Optionaler zweiter Test (best effort): prüft, dass in Validierungs-Annotations
  referenzierte Keys existieren. throw-Site-Keys sind Strings → nicht statisch
  vollständig prüfbar; Integrationstests decken die migrierten Pfade ab.

## Phasierung

Ein Spec, mehrere Sessions (wie beim Frontend-i18n). Jede Phase eigener Plan,
grün + merge-ready abgeschlossen.

1. **Phase 1 — Fundament + Pilot**
   - `MessageSource`-/`AcceptHeaderLocaleResolver`-Config, `LocalizedMessages`-Helper.
   - `LocalValidatorFactoryBean` an MessageSource binden.
   - Frontend: axios-`Accept-Language`-Request-Interceptor (aktive i18next-Sprache).
   - `messages_en/de.properties` Grundgerüst + `common.*`-Fallbacks.
   - `MessagesParityTest` (CI-Gate).
   - **Pilot:** `GlobalExceptionHandler`-Fallbacks + **ein Modul (`issues`)**
     end-to-end lokalisiert inkl. Integrationstest
     (`Accept-Language: de` → deutsche Meldung; en/fallback).
2. **Phase 2 — API-Fehler-Sweep**
   - Restliche throw-Sites modulweise auf Keys; Validierungs-Messages auf Keys;
     2 Custom-Validation-Messages migrieren.
3. **Phase 3 — E-Mails + Notifications**
   - `EmailService` + `NotificationService` (inkl. `createDirect`-Aufrufer) auf
     Empfänger-Sprachen-Rendering.

## Tests

Backend nutzt **JUnit + MockK** (TDD).

- **Paritäts-Test** grün in jeder Phase.
- **Integrationstests** für Locale-Auflösung: `Accept-Language: de` → deutsche
  Fehlermeldung, `en` → englisch, unbekannt/fehlend → Fallback en.
- **Unit-Tests** für E-Mail-/Notification-Rendering mit **expliziter
  Empfänger-Locale** (de/en/null→en).

## Bewusst außerhalb des Scopes

- Weitere Sprachen als en/de.
- „Render-at-read-time"-Notifications mit strukturierten DB-Params.
- Lokalisierung von Log-Ausgaben, OpenAPI/Swagger, sowie `INTERNAL_ERROR`
  (bleibt generisch en — keine nutzerspezifische Info).
- Übersetzung von user-generiertem Inhalt (Kommentar-/Issue-Texte).

## Berührungspunkte / Risiken

- **Doppelter Katalog** (Backend + Frontend): durch Paritäts-Gate und
  domänen-gleiches Namespacing gemildert; bewusst akzeptiert.
- **Accept-Language-Konsistenz:** setzt voraus, dass das Frontend seine aktive
  i18next-Sprache im Header schickt (Teil von Phase 1). Ohne Header → Fallback en.
- **Rückwärtskompatibilität** der Exceptions verhindert Big-Bang: gemischter
  Zustand (teils lokalisiert, teils EN-Freitext) ist zwischen den Phasen gültig.
