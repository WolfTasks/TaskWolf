# H3 — Blank-Passwort-Validierung (`@NotBlank`) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reine-Whitespace-Passwörter werden serverseitig abgelehnt — sowohl beim Passwort-Ändern (`ChangePasswordRequest.newPassword`) als auch bei der Registrierung (`RegisterRequest.password`).

**Architecture:** `@field:NotBlank` zusätzlich zu `@field:Size(min = 8)` an beiden Passwortfeldern. Beide Endpunkte setzen bereits `@Valid` (`AuthController.register:27`, `MeController.changePassword:36`), daher greift die Constraint direkt. Verifikation über direkte Bean-Validation-Unit-Tests (`jakarta.validation.Validator`) — kein Spring-Context/JWT nötig, testet exakt die Annotation.

**Tech Stack:** Kotlin, Jakarta Bean Validation (Hibernate Validator), JUnit 5.

## Global Constraints

- Nur die beiden DTOs ändern (`ChangePasswordRequest.kt`, `RegisterRequest.kt`); Controller unverändert (`@Valid` bereits vorhanden).
- `@NotBlank` ablehnt `null`/leer/reine-Whitespace; `@Size(min = 8)` bleibt für Mindestlänge.
- Keine zusätzlichen Passwort-Policy-/Komplexitätsregeln (YAGNI).
- Backend-Tests: JUnit 5. Für DTO-Constraints direkter `Validator` (kein MockK/Spring nötig).
- Referenz-Design: `docs/superpowers/specs/2026-07-10-h3-blank-password-validation-design.md`.
- Test-Command: `cd backend && ./gradlew test`.

---

### Task 1: `@NotBlank` an beiden Passwortfeldern + Validierungs-Unit-Tests

**Files:**
- Modify: `backend/src/main/kotlin/com/taskowolf/auth/api/dto/ChangePasswordRequest.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/auth/api/dto/RegisterRequest.kt`
- Create: `backend/src/test/kotlin/com/taskowolf/auth/PasswordValidationTest.kt`

**Interfaces:**
- Consumes: `jakarta.validation.Validation`, `jakarta.validation.Validator`.
- Produces: `ChangePasswordRequest.newPassword` und `RegisterRequest.password` tragen `@field:NotBlank @field:Size(min = 8)`; reine-Whitespace-Werte erzeugen eine Constraint-Violation auf dem jeweiligen Feld.

- [ ] **Step 1: Failing Test schreiben**

Erstelle `backend/src/test/kotlin/com/taskowolf/auth/PasswordValidationTest.kt`:

```kotlin
package com.taskowolf.auth

import com.taskowolf.auth.api.dto.ChangePasswordRequest
import com.taskowolf.auth.api.dto.RegisterRequest
import jakarta.validation.Validation
import jakarta.validation.Validator
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PasswordValidationTest {

    private val validator: Validator =
        Validation.buildDefaultValidatorFactory().validator

    @Test
    fun `changePassword rejects blank (whitespace-only) newPassword`() {
        val req = ChangePasswordRequest(currentPassword = "oldpassword", newPassword = "        ")
        val violations = validator.validate(req)
        assertTrue(violations.any { it.propertyPath.toString() == "newPassword" })
    }

    @Test
    fun `changePassword accepts a valid newPassword`() {
        val req = ChangePasswordRequest(currentPassword = "oldpassword", newPassword = "password123")
        val violations = validator.validate(req)
        assertFalse(violations.any { it.propertyPath.toString() == "newPassword" })
    }

    @Test
    fun `register rejects blank (whitespace-only) password`() {
        val req = RegisterRequest(email = "a@b.com", displayName = "A", password = "        ")
        val violations = validator.validate(req)
        assertTrue(violations.any { it.propertyPath.toString() == "password" })
    }

    @Test
    fun `register accepts a valid password`() {
        val req = RegisterRequest(email = "a@b.com", displayName = "A", password = "password123")
        val violations = validator.validate(req)
        assertFalse(violations.any { it.propertyPath.toString() == "password" })
    }
}
```

- [ ] **Step 2: Test laufen lassen → muss fehlschlagen**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.auth.PasswordValidationTest"`
Expected: FAIL — die beiden Blank-Tests finden aktuell keine Violation (nur `@Size(min=8)` greift, 8 Spaces sind ≥ 8 Zeichen).

- [ ] **Step 3: `@NotBlank` an `ChangePasswordRequest.newPassword` ergänzen**

Ersetze den Inhalt von `ChangePasswordRequest.kt`:

```kotlin
package com.taskowolf.auth.api.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class ChangePasswordRequest(
    @field:NotBlank val currentPassword: String,
    @field:NotBlank @field:Size(min = 8) val newPassword: String
)
```

- [ ] **Step 4: `@NotBlank` an `RegisterRequest.password` ergänzen**

Ersetze den Inhalt von `RegisterRequest.kt`:

```kotlin
package com.taskowolf.auth.api.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class RegisterRequest(
    @field:Email val email: String,
    @field:NotBlank val displayName: String,
    @field:NotBlank @field:Size(min = 8) val password: String
)
```

- [ ] **Step 5: Test laufen lassen → muss bestehen**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.auth.PasswordValidationTest"`
Expected: PASS (alle vier Tests).

- [ ] **Step 6: Regression — bestehende Auth-Tests laufen lassen**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.auth.*"`
Expected: PASS — `AuthServiceTest`, `AuthControllerIntegrationTest`, `UserAccountServiceTest` etc. unverändert grün (gültige Passwörter wie `"password123"` erfüllen `@NotBlank`).

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/auth/api/dto/ChangePasswordRequest.kt \
        backend/src/main/kotlin/com/taskowolf/auth/api/dto/RegisterRequest.kt \
        backend/src/test/kotlin/com/taskowolf/auth/PasswordValidationTest.kt
git commit -m "fix(auth): reject whitespace-only passwords via @NotBlank (H3)

Add @NotBlank alongside @Size(min=8) on ChangePasswordRequest.newPassword
and RegisterRequest.password so eight spaces no longer pass validation.
Both endpoints already carry @Valid, so the constraint takes effect
immediately. Covered by direct Bean-Validation unit tests.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Self-Review

**Spec coverage:**
- `@NotBlank` an `ChangePasswordRequest.newPassword` → Step 3 ✅
- `@NotBlank` an `RegisterRequest.password` (beide fixen) → Step 4 ✅
- `@Valid` bereits vorhanden, kein Controller-Change → Global Constraints ✅
- Whitespace-only abgelehnt, gültige akzeptiert, Regression → Steps 1/5/6 ✅
- Keine zusätzliche Policy → YAGNI eingehalten ✅

**Placeholder scan:** kein TODO/TBD; vollständige DTO- und Testdateien gezeigt. ✅

**Consistency:** `propertyPath`-Namen (`newPassword`, `password`) matchen die DTO-Felder; Testklasse/Package `com.taskowolf.auth.PasswordValidationTest` konsistent zwischen Create-Pfad und Test-Command. ✅
