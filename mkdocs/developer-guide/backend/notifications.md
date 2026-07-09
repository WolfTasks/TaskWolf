# Module: notifications

## Purpose

Delivers in-app, email, and WebSocket push notifications. Reacts to domain events from other modules — it has no public REST API for creating notifications. The only mutation exposed to clients is marking notifications as read.

---

## Entities Owned

| Entity | Table | Key Fields |
|---|---|---|
| `Notification` | `notifications` | `userId` UUID FK→users NOT NULL, `type: NotificationType` NOT NULL, `title` VARCHAR(255) NOT NULL, `body` TEXT?, `link` VARCHAR(500)?, `read` BOOLEAN NOT NULL DEFAULT false |
| `NotificationPreference` | `notification_preferences` | `userId` UUID FK→users NOT NULL, `type: NotificationType` NOT NULL, `inAppEnabled` BOOLEAN NOT NULL DEFAULT true, `emailEnabled` BOOLEAN NOT NULL DEFAULT true; UNIQUE `(user_id, type)` |

`NotificationType` enum values: `COMMENT_MENTION`, `ISSUE_ASSIGNED`, `AUTOMATION`, `SLA_BREACHED`.

Notifications are append-only from the outside. The only allowed mutation via the API is flipping `read` to `true`.

---

## DB Schema

### `notifications` (V8)

```sql
CREATE TABLE notifications (
    id         UUID         NOT NULL PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type       VARCHAR(30)  NOT NULL,
    title      VARCHAR(255) NOT NULL,
    body       TEXT,
    link       VARCHAR(500),
    read       BOOLEAN      NOT NULL DEFAULT false,
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL
);
```

Index: `idx_notifications_user` on `(user_id, read, created_at DESC)` — supports the common query pattern of fetching unread notifications for a user in reverse-chronological order.

### `notification_preferences` (V29)

```sql
CREATE TABLE notification_preferences (
    id             UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id        UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type           VARCHAR(30) NOT NULL,
    in_app_enabled BOOLEAN     NOT NULL DEFAULT TRUE,
    email_enabled  BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, type)
);
```

Index: `idx_notification_preferences_user` on `user_id`.

---

## API Endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/v1/notifications` | USER | List notifications for the authenticated user; paged, default `page=0, size=20`, sorted by `createdAt DESC` |
| `GET` | `/api/v1/notifications/unread-count` | USER | Returns `{ "count": <long> }` — count of unread notifications for the authenticated user |
| `PATCH` | `/api/v1/notifications/{id}/read` | USER | Mark a single notification as read; returns updated `NotificationResponse` |
| `GET` | `/api/v1/me/notification-preferences` | USER | Returns `NotificationPreferencesResponse` — one `{type, inApp, email}` item per `NotificationType`, synthesizing a default (both `true`) for any type with no row |
| `PUT` | `/api/v1/me/notification-preferences` | USER | Upserts `{type, inApp, email}` items for the authenticated user; returns the updated matrix |

There is no `POST` endpoint on `/api/v1/notifications`. Notifications are created exclusively by `@EventListener` methods in `NotificationService` reacting to domain events.

### Notification preferences (opt-out model)

`NotificationPreference` rows are per `(userId, type)`. A **missing row means the type is enabled** on both channels — `NotificationPreferenceService.isEnabled(userId, type, channel)` returns `true` when no row exists, so new notification types are enabled by default until a user explicitly opts out via `PUT /api/v1/me/notification-preferences`. `getMatrix()` follows the same convention: it always returns one item per `NotificationType`, filling in `inApp=true, email=true` defaults for types the user has never configured.

`isEnabled()` gates both delivery paths:

- **`NotificationService`** — checked before saving a `Notification` row, for all four types: `onMention` (`COMMENT_MENTION`), `onIssueFieldChanged` (`ISSUE_ASSIGNED`), and `createDirect` (used by `ActionExecutor` for `AUTOMATION` and by `SlaMonitorJob`/`IncidentService` for `SLA_BREACHED`).
- **`EmailService`** — checked before sending mail, currently only for `onMention` (`COMMENT_MENTION`) and `onAssigned` (`ISSUE_ASSIGNED`); `AUTOMATION`/`SLA_BREACHED` have no email listener today.

> **DO NOT** treat a missing `NotificationPreference` row as "disabled" — the opt-out model requires the opposite default. Always route the check through `NotificationPreferenceService.isEnabled()` rather than querying the repository directly.

---

## Events Emitted

None. The notifications module does not publish domain events.

---

## Events Consumed

### `NotificationService`

| Event | Handler | Action |
|---|---|---|
| `MentionEvent` | `onMention()` | Saves `Notification(type=COMMENT_MENTION)` for `mentionedUser.id`; title = `"You were mentioned in {issue.key}"`; body = first 200 chars of comment |
| `IssueFieldChangedEvent` (field = "assignee") | `onIssueFieldChanged()` | Saves `Notification(type=ISSUE_ASSIGNED)` for the new assignee; no-ops when field is not "assignee" or when `issue.assignee` is null |

### `EmailService`

| Event | Handler | Action |
|---|---|---|
| `MentionEvent` | `onMention()` | Sends `SimpleMailMessage` to `mentionedUser.email`; no-ops when `spring.mail.host` is blank |
| `IssueFieldChangedEvent` (field = "assignee") | `onAssigned()` | Sends assignment email to `assignee.email`; no-ops when field is not "assignee", when `issue.assignee` is null, or when SMTP is not configured |

Email delivery is entirely optional: `EmailService.enabled` is false when `spring.mail.host` is blank, so no `JavaMailSender` calls are made.

---

## Key Files

- `backend/src/main/kotlin/com/taskowolf/notifications/domain/Notification.kt`
- `backend/src/main/kotlin/com/taskowolf/notifications/domain/NotificationType.kt`
- `backend/src/main/kotlin/com/taskowolf/notifications/domain/NotificationChannel.kt`
- `backend/src/main/kotlin/com/taskowolf/notifications/domain/NotificationPreference.kt`
- `backend/src/main/kotlin/com/taskowolf/notifications/application/NotificationService.kt`
- `backend/src/main/kotlin/com/taskowolf/notifications/application/NotificationPreferenceService.kt`
- `backend/src/main/kotlin/com/taskowolf/notifications/application/EmailService.kt`
- `backend/src/main/kotlin/com/taskowolf/notifications/api/NotificationController.kt`
- `backend/src/main/kotlin/com/taskowolf/notifications/api/NotificationPreferenceController.kt`
- `backend/src/main/kotlin/com/taskowolf/notifications/api/dto/NotificationResponse.kt`
- `backend/src/main/kotlin/com/taskowolf/notifications/api/dto/NotificationPreferencesResponse.kt`
- `backend/src/main/kotlin/com/taskowolf/notifications/api/dto/NotificationPreferencesRequest.kt`
- `backend/src/main/kotlin/com/taskowolf/notifications/infrastructure/NotificationPreferenceRepository.kt`
- `backend/src/main/resources/db/migration/V8__create_notifications.sql`
- `backend/src/main/resources/db/migration/V29__notification_preferences.sql`

---

## Extension Points

- **New notification type:** Add the value to `NotificationType`. Publish a domain event from the originating module (do not call `NotificationService` directly). Add an `@EventListener` method in `NotificationService` that maps the event to a `Notification` entity and calls `notificationRepository.save()`. Push the saved notification to the WebSocket destination `/user/{userId}/queue/notifications` so the frontend receives it in real time.
- **Email for new events:** Add a corresponding `@EventListener` in `EmailService` following the existing pattern: check `enabled`, build a `SimpleMailMessage`, call `mailSender.send()`.

---

## Common Pitfalls

- DO NOT call `NotificationService` directly from other modules — publish a domain event and let `NotificationService` react via `@EventListener`. Direct calls create hidden coupling and bypass the event contract. The internal `createDirect()` method exists for the notification module's own wiring only. Never call it from code outside the `notifications` package — publish an event instead.
- WebSocket push uses STOMP destination `/user/{userId}/queue/notifications` — do not change this path without updating the frontend subscription.
- `EmailService` is a no-op when `spring.mail.host` is blank; tests must construct `EmailService` with an explicit non-blank `mailHost` to verify that `mailSender.send()` is called.
- `IssueFieldChangedEvent` is emitted for all field changes; `NotificationService.onIssueFieldChanged()` explicitly guards `if (event.field != "assignee") return` — copy this pattern for any listener that handles only specific fields.

---

## Example

`NotificationService.onMention` is the canonical pattern for consuming a domain event and persisting a notification:

```kotlin
@EventListener
@Transactional
fun onMention(event: MentionEvent) {
    repository.save(
        Notification(
            userId = event.mentionedUser.id,
            type = NotificationType.COMMENT_MENTION,
            title = "You were mentioned in ${event.issue.key}",
            body = event.comment.body.take(200),
            link = "/issues/${event.issue.key}"
        )
    )
}
```

The `@EventListener` + `@Transactional` pair ensures the notification is saved in the same transaction as the originating event. If the outer transaction rolls back, the notification is not persisted.

---

## Test Patterns

- **`NotificationServiceTest`** — pure unit test with MockK; mocks `NotificationRepository` and `NotificationPreferenceService`. Verifies: `MentionEvent` saves a `COMMENT_MENTION` for `mentionedUser`; `IssueFieldChangedEvent(field="assignee")` saves an `ISSUE_ASSIGNED` for the assignee; `markRead` flips `read` and saves; `markRead` throws `NotFoundException` for an unknown notification id; a disabled preference (`isEnabled` returns `false`) suppresses the save.
- **`EmailServiceTest`** — pure unit test; constructs `EmailService` with a mock `JavaMailSender` and a mock `NotificationPreferenceService`. Verifies: mention email sent when SMTP host is set; mention email skipped when SMTP host is blank; assignment email sent on assignee change; assignment email skipped when field is not "assignee"; email skipped when the preference is disabled even if SMTP is configured.
- **`NotificationPreferenceServiceTest`** — pure unit test with MockK; mocks `NotificationPreferenceRepository`. Verifies: `isEnabled` returns `true` when no row exists (opt-out default); `isEnabled` reflects the stored `inAppEnabled`/`emailEnabled` flags per channel when a row exists; `getMatrix` returns one item per `NotificationType`, defaulting unconfigured types to enabled; `update` upserts rows for each type in the request map.
- **`NotificationPreferenceControllerTest`** — pure unit test with MockK; mocks `NotificationPreferenceService`. Verifies `get()` maps the matrix to `NotificationPreferencesResponse`, and `update()` converts request items into the typed `Map<NotificationType, Pair<Boolean, Boolean>>` passed to `service.update()`.
- **`NotificationPreferenceRepositoryIntegrationTest`** — extends `IntegrationTestBase` (real Postgres, not H2); persists a `NotificationPreference` against a real `users` FK and verifies `findByUserIdAndType`/`findByUserId` lookups.
