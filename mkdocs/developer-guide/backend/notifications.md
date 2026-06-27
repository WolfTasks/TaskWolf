# Module: notifications

## Purpose

Delivers in-app, email, and WebSocket push notifications. Reacts to domain events from other modules — it has no public REST API for creating notifications. The only mutation exposed to clients is marking notifications as read.

---

## Entities Owned

| Entity | Table | Key Fields |
|---|---|---|
| `Notification` | `notifications` | `userId` UUID FK→users NOT NULL, `type: NotificationType` NOT NULL, `title` VARCHAR(255) NOT NULL, `body` TEXT?, `link` VARCHAR(500)?, `read` BOOLEAN NOT NULL DEFAULT false |

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

---

## API Endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/v1/notifications` | USER | List notifications for the authenticated user; paged, default `page=0, size=20`, sorted by `createdAt DESC` |
| `GET` | `/api/v1/notifications/unread-count` | USER | Returns `{ "count": <long> }` — count of unread notifications for the authenticated user |
| `PATCH` | `/api/v1/notifications/{id}/read` | USER | Mark a single notification as read; returns updated `NotificationResponse` |

There is no `POST` endpoint. Notifications are created exclusively by `@EventListener` methods in `NotificationService` reacting to domain events.

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
- `backend/src/main/kotlin/com/taskowolf/notifications/application/NotificationService.kt`
- `backend/src/main/kotlin/com/taskowolf/notifications/application/EmailService.kt`
- `backend/src/main/kotlin/com/taskowolf/notifications/api/NotificationController.kt`
- `backend/src/main/kotlin/com/taskowolf/notifications/api/dto/NotificationResponse.kt`
- `backend/src/main/resources/db/migration/V8__create_notifications.sql`

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

- **`NotificationServiceTest`** — pure unit test with MockK; mocks `NotificationRepository`. Verifies: `MentionEvent` saves a `COMMENT_MENTION` for `mentionedUser`; `IssueFieldChangedEvent(field="assignee")` saves an `ISSUE_ASSIGNED` for the assignee; `markRead` flips `read` and saves; `markRead` throws `NotFoundException` for an unknown notification id.
- **`EmailServiceTest`** — pure unit test; constructs `EmailService` with a mock `JavaMailSender`. Verifies: mention email sent when SMTP host is set; mention email skipped when SMTP host is blank; assignment email sent on assignee change; assignment email skipped when field is not "assignee".
