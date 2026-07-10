# H3 — Blank-Passwort-Validierung (`@NotBlank`) (Design)

> Kleiner Backend-Härtungs-Fix. Aus dem Final-Review von #3 (v1.0.09).

## Problem
`ChangePasswordRequest.newPassword` hat `@field:Size(min = 8)`, aber **kein**
`@NotBlank`:

```kotlin
data class ChangePasswordRequest(
    @field:NotBlank val currentPassword: String,
    @field:Size(min = 8) val newPassword: String   // 8 Leerzeichen sind valide
)
```

→ Acht Leerzeichen erfüllen `@Size(min = 8)` und werden als Passwort
akzeptiert. Das Frontend erzwingt zusätzlich die Länge (daher niedriges
Risiko), aber die serverseitige Validierung sollte reine Whitespace-Passwörter
ablehnen.

**Gleiche Lücke** besteht in `RegisterRequest.password`
(`@field:Size(min = 8)`, kein `@NotBlank`). Entscheidung getroffen: **beide**
Felder werden konsistent gehärtet.

## Ziel
Reine-Whitespace-Passwörter werden serverseitig mit **400** abgelehnt — sowohl
beim Passwort-Ändern als auch bei der Registrierung. Konsistente
Passwort-Validierung (`@NotBlank` + `@Size(min = 8)`) an beiden Stellen.

## Ansatz
`@field:NotBlank` ergänzen:

```kotlin
// ChangePasswordRequest.kt
data class ChangePasswordRequest(
    @field:NotBlank val currentPassword: String,
    @field:NotBlank @field:Size(min = 8) val newPassword: String
)

// RegisterRequest.kt
data class RegisterRequest(
    @field:Email val email: String,
    @field:NotBlank val displayName: String,
    @field:NotBlank @field:Size(min = 8) val password: String
)
```

`@NotBlank` lehnt `null`, leer und reine Whitespace-Strings ab; `@Size(min = 8)`
bleibt für die Mindestlänge. Beide Endpunkte setzen **bereits** `@Valid` auf dem
Request-Body (bestätigt: `AuthController.register:27`,
`MeController.changePassword:36`) → Bean-Validation greift, keine Controller-
Änderung nötig. `MethodArgumentNotValidException` → `GlobalExceptionHandler`
liefert `VALIDATION_ERROR`/400 mit Feld-Details.

## Abgrenzung
- Nur die beiden DTOs (`ChangePasswordRequest`, `RegisterRequest`).
- Keine Änderung an Passwort-Policy-Semantik über `@NotBlank` hinaus (keine
  Komplexitätsregeln in diesem Scope — YAGNI).
- Falls ein Endpunkt `@Valid` noch nicht setzt, wird das im Plan als
  Voraussetzung mit-adressiert.

## Betroffene Dateien
- `backend/.../auth/api/dto/ChangePasswordRequest.kt`
- `backend/.../auth/api/dto/RegisterRequest.kt`
- Tests (neu/erweitert) — Controller unverändert (`@Valid` bereits vorhanden)

## Verifikation (TDD, MockK/JUnit)
1. **Test changePassword:** `newPassword = "        "` (8 Spaces) → 400
   `VALIDATION_ERROR`; gültiges Passwort → 200.
2. **Test register:** `password = "        "` → 400; gültiges Passwort → 201/200
   wie bisher.
3. Regression: bestehende gültige Flows unverändert.
4. `./gradlew test` grün.

## Risiko / Größe
Sehr klein. `@Valid` ist an beiden Endpunkten bereits vorhanden, damit greift
die neue Annotation direkt — kein bekannter Fallstrick.
