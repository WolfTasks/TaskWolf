# H2 — Notification-Prefs PUT: unbekannter Typ leakt Enum-Namen (Design)

> Kleiner Backend-Härtungs-Fix. Aus dem Final-Review von #3 (v1.0.09).

## Problem
`NotificationPreferenceController.update` (`PUT
/api/v1/me/notification-preferences`) baut die Preference-Map so:

```kotlin
val map = request.preferences.associate {
    NotificationType.valueOf(it.type) to Pair(it.inApp, it.email)
}
```

Bei unbekanntem `it.type` wirft `NotificationType.valueOf(...)` eine
`IllegalArgumentException`. Der `GlobalExceptionHandler` mappt diese sauber auf
**400** (nicht 500), echot aber `ex.message`:

```
No enum constant com.taskowolf.notifications.domain.NotificationType.<X>
```

→ Die Response leakt den **voll qualifizierten Enum-Namen** (interne
Package-Struktur). Nicht-blockierend, niedrige Prio (die vertrauenswürdige UI
schickt heute nur gültige Typen), aber unnötige Informationspreisgabe.

## Ziel
Ein unbekannter `type` führt weiterhin zu **HTTP 400**, aber mit einer
**sauberen Fehlermeldung ohne FQCN** (z.B. `Unknown notification type: <X>`).

## Ansatz (empfohlen: Validierung im Controller)
Enum-Auflösung im Controller explizit machen und bei Miss eine kontrollierte
`BadRequestException` mit sauberer Nachricht werfen:

```kotlin
val map = request.preferences.associate { item ->
    val type = NotificationType.entries.find { it.name == item.type }
        ?: throw BadRequestException("Unknown notification type: ${item.type}")
    type to Pair(item.inApp, item.email)
}
```

`BadRequestException` existiert bereits in
`core/infrastructure/GlobalExceptionHandler.kt` und wird auf 400 mit
`ErrorResponse("BAD_REQUEST", ex.message)` gemappt — die Message ist jetzt
unsere kontrollierte, FQCN-freie Zeichenkette.

## Verworfene Alternativen
- **Globalen `IllegalArgumentException`-Handler sanitizen** (Message
  unterdrücken): zu breit — andere Aufrufer verlassen sich auf die
  `ex.message`-Weitergabe; würde andere 400-Antworten verschlechtern.
- **Unbekannte Typen still überspringen** (`mapNotNull`): versteckt echte
  Client-Fehler; ein Tippfehler würde stillschweigend ignoriert.

## Abgrenzung
- Nur `NotificationPreferenceController.update` betroffen.
- Kein Schema-/DTO-Umbau (`NotificationPreferencesRequest` bleibt `String`-Typ);
  bewusst kein Enum-Binding im DTO, damit die Fehlermeldung kontrolliert bleibt
  statt über Jacksons Deserialisierungsfehler zu laufen.
- Verhalten für **gültige** Typen unverändert.

## Betroffene Dateien
- `backend/.../notifications/api/NotificationPreferenceController.kt` (Fix)
- Test (neu/erweitert), siehe unten.

## Verifikation (TDD, MockK)
Backend hat JUnit/MockK — Test-first:
1. **Test:** PUT mit einem unbekannten `type` →
   - Status **400**,
   - Response-Message **enthält NICHT** `com.taskowolf` (kein FQCN),
   - Response-Message enthält den gemeldeten unbekannten Typ-String.
2. **Test:** PUT mit ausschließlich gültigen Typen → 200, Matrix aktualisiert
   (Regression: bestehendes Verhalten unverändert).
3. `./gradlew test` grün; Security-/Docker-Gates unberührt.

## Risiko / Größe
Sehr klein, lokal begrenzt auf eine Controller-Methode.
