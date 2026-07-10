# H2 — Notification-Prefs PUT: Enum-Leak beheben — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ein unbekannter `type` im PUT `/api/v1/me/notification-preferences` liefert weiterhin HTTP 400, aber mit einer sauberen Fehlermeldung ohne voll qualifizierten Enum-Namen.

**Architecture:** In `NotificationPreferenceController.update` die Enum-Auflösung von `NotificationType.valueOf(...)` auf eine kontrollierte Suche über `NotificationType.entries` umstellen; bei Miss eine `BadRequestException("Unknown notification type: <X>")` werfen. Der bestehende `GlobalExceptionHandler` mappt sie auf 400 mit der kontrollierten Message.

**Tech Stack:** Kotlin, Spring Boot, JUnit 5 + MockK.

## Global Constraints

- Backend-Tests: JUnit 5 + **MockK** (nicht Mockito). TDD: Test zuerst.
- `BadRequestException` existiert in `com.taskowolf.core.infrastructure` und wird via `GlobalExceptionHandler` auf 400 (`ErrorResponse("BAD_REQUEST", ex.message)`) gemappt.
- Verhalten für **gültige** Typen unverändert; kein DTO-Umbau (`NotificationPreferencesRequest.type` bleibt `String`).
- Globaler `IllegalArgumentException`-Handler **nicht** anfassen.
- Referenz-Design: `docs/superpowers/specs/2026-07-10-h2-notification-prefs-enum-leak-design.md`.
- Test-Command: `cd backend && ./gradlew test`.

---

### Task 1: Unbekannten Typ auf saubere 400-Message mappen

**Files:**
- Modify: `backend/src/main/kotlin/com/taskowolf/notifications/api/NotificationPreferenceController.kt`
- Test: `backend/src/test/kotlin/com/taskowolf/notifications/NotificationPreferenceControllerTest.kt`

**Interfaces:**
- Consumes: `NotificationType.entries` (Kotlin-Enum), `com.taskowolf.core.infrastructure.BadRequestException(message: String)`.
- Produces: `NotificationPreferenceController.update(request, user)` wirft bei unbekanntem Typ `BadRequestException` mit Message `"Unknown notification type: <X>"` (ohne FQCN); für gültige Typen unverändertes Verhalten.

- [ ] **Step 1: Failing Tests schreiben**

Ergänze in `NotificationPreferenceControllerTest.kt` zwei Tests. Passe die Imports oben an (`assertThrows`, `assertFalse`, `assertTrue`, `BadRequestException`):

```kotlin
// zusätzliche Imports:
// import com.taskowolf.core.infrastructure.BadRequestException
// import org.junit.jupiter.api.Assertions.assertFalse
// import org.junit.jupiter.api.Assertions.assertTrue
// import org.junit.jupiter.api.assertThrows

    @Test
    fun `put with unknown type throws BadRequest without leaking enum FQCN`() {
        val request = NotificationPreferencesRequest(listOf(
            NotificationPreferenceItem("NOT_A_REAL_TYPE", inApp = true, email = true)
        ))

        val ex = assertThrows<BadRequestException> {
            controller.update(request, user)
        }

        // Message darf den voll qualifizierten Enum-Namen nicht leaken
        assertFalse(ex.message!!.contains("com.taskowolf"))
        assertFalse(ex.message!!.contains("No enum constant"))
        // Message nennt den gemeldeten unbekannten Typ
        assertTrue(ex.message!!.contains("NOT_A_REAL_TYPE"))

        verify(exactly = 0) { service.update(any(), any()) }
    }

    @Test
    fun `put with only valid types still updates and returns matrix`() {
        every { service.getMatrix(user.id) } returns emptyList()
        val request = NotificationPreferencesRequest(listOf(
            NotificationPreferenceItem("ISSUE_ASSIGNED", inApp = true, email = false)
        ))

        controller.update(request, user)

        val captured = slot<Map<NotificationType, Pair<Boolean, Boolean>>>()
        verify { service.update(user.id, capture(captured)) }
        assertEquals(Pair(true, false), captured.captured[NotificationType.ISSUE_ASSIGNED])
    }
```

- [ ] **Step 2: Tests laufen lassen → müssen fehlschlagen**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.notifications.NotificationPreferenceControllerTest"`
Expected: FAIL — der Unknown-Type-Test bekommt aktuell `IllegalArgumentException` statt `BadRequestException` (bzw. die FQCN-Assertion schlägt an).

- [ ] **Step 3: Controller-Fix implementieren**

Ersetze in `NotificationPreferenceController.kt` den `update`-Rumpf. Ergänze den Import `import com.taskowolf.core.infrastructure.BadRequestException`:

```kotlin
    @PutMapping
    fun update(
        @RequestBody request: NotificationPreferencesRequest,
        @AuthenticationPrincipal user: User
    ): NotificationPreferencesResponse {
        val map = request.preferences.associate { item ->
            val type = NotificationType.entries.find { it.name == item.type }
                ?: throw BadRequestException("Unknown notification type: ${item.type}")
            type to Pair(item.inApp, item.email)
        }
        service.update(user.id, map)
        return NotificationPreferencesResponse.from(service.getMatrix(user.id))
    }
```

- [ ] **Step 4: Tests laufen lassen → müssen bestehen**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.notifications.NotificationPreferenceControllerTest"`
Expected: PASS (alle vier Tests: get, valid-put, unknown-type, valid-only).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/notifications/api/NotificationPreferenceController.kt \
        backend/src/test/kotlin/com/taskowolf/notifications/NotificationPreferenceControllerTest.kt
git commit -m "fix(notifications): don't leak enum FQCN on unknown pref type (H2)

Resolve the notification type against NotificationType.entries and throw a
controlled BadRequestException(\"Unknown notification type: <X>\") on miss,
instead of NotificationType.valueOf(...) whose IllegalArgumentException
message echoed the fully qualified enum name. Still HTTP 400; valid types
unchanged.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Self-Review

**Spec coverage:**
- Unbekannter Typ → 400 mit sauberer Message → Task 1, Step 3 ✅
- Kein FQCN / kein `No enum constant` in Response → Step 1 asserts ✅
- Gültige Typen unverändert → valid-only Test ✅
- Globaler Handler unangetastet → nur Controller geändert ✅
- MockK, kein DTO-Umbau → Steps ✅

**Placeholder scan:** kein TODO/TBD; vollständiger Code in jedem Step. ✅

**Consistency:** `BadRequestException`-Message-Format identisch in Test-Assertion und Implementierung (`Unknown notification type: <X>`). `NotificationType.entries`/`ISSUE_ASSIGNED` real (siehe bestehende Tests). ✅
