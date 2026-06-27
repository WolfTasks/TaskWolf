# Module: comments

## Purpose

Manages comments on issues, @mention parsing, and the activity feed. Owns the `Comment` and `IssueActivity` entities and provides the unified timeline of changes visible on the issue detail view.

---

## Entities Owned

| Entity | Table | Key Fields |
|---|---|---|
| `Comment` | `comments` | `issueId` UUID FK→issues NOT NULL, `authorId` UUID FK→users nullable (null = system comment), `body` TEXT NOT NULL, `editedAt` Instant?, `deletedAt` Instant? (soft-delete) |
| `IssueActivity` | `issue_activities` | `issueId` UUID FK→issues NOT NULL, `actorId` UUID FK→users NOT NULL, `type: ActivityType` NOT NULL, `commentId` UUID? FK→comments (set to null on comment delete), `oldValue` TEXT?, `newValue` TEXT? |

`ActivityType` enum values: `COMMENT`, `STATUS_CHANGED`, `ASSIGNED`, `UNASSIGNED`, `PRIORITY_CHANGED`, `TITLE_CHANGED`, `DESCRIPTION_CHANGED`, `STORY_POINTS_CHANGED`, `DUE_DATE_CHANGED`, `SPRINT_CHANGED`, `ATTACHMENT_ADDED`, `ATTACHMENT_REMOVED`.

---

## DB Schema

### `comments` (V7)

```sql
CREATE TABLE comments (
    id           UUID         NOT NULL PRIMARY KEY,
    body         TEXT         NOT NULL,
    issue_id     UUID         NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    author_id    UUID         NOT NULL REFERENCES users(id),
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP    NOT NULL,
    edited_at    TIMESTAMP,
    deleted_at   TIMESTAMP
);
```

`deleted_at` is set on soft-delete; `listComments` filters out rows where `deleted_at IS NOT NULL`. `edited_at` is set on every `editComment` call.

### `issue_activities` (V7)

```sql
CREATE TABLE issue_activities (
    id          UUID         NOT NULL PRIMARY KEY,
    issue_id    UUID         NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    actor_id    UUID         NOT NULL REFERENCES users(id),
    type        VARCHAR(40)  NOT NULL,
    comment_id  UUID         REFERENCES comments(id) ON DELETE SET NULL,
    old_value   TEXT,
    new_value   TEXT,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL
);
```

Index: `idx_issue_activity_issue` on `(issue_id, created_at DESC)`.

---

## API Endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/v1/projects/{key}/issues/{issueKey}/comments` | USER | Create a comment; returns 201 with `CommentResponse` |
| `GET` | `/api/v1/projects/{key}/issues/{issueKey}/comments` | USER | List non-deleted comments for issue |
| `PUT` | `/api/v1/projects/{key}/issues/{issueKey}/comments/{commentId}` | USER | Edit comment body; restricted to author only |
| `DELETE` | `/api/v1/projects/{key}/issues/{issueKey}/comments/{commentId}` | USER | Soft-delete; allowed for author or project admin; returns 204 |
| `GET` | `/api/v1/projects/{key}/issues/{issueKey}/activity` | USER | Paged activity feed; query params `page` (default 0), `size` (default 50); sorted by `createdAt DESC` |

---

## Events Emitted

| Event | Published by | Payload |
|---|---|---|
| `CommentCreatedEvent` | `CommentService.addComment()` | `comment`, `issue`, `actorEmail`, `actorId` |
| `MentionEvent` | `CommentService` — **not yet wired in production**; `extractAndPublishMentions()` must be called from `addComment()` | `mentionedUser: User`, `comment`, `issue` |

`MentionEvent` is defined and consumed by `NotificationService.onMention()` and `EmailService.onMention()`, but `CommentService.addComment()` does not yet call `extractAndPublishMentions()`. Wire the call in `addComment()` to enable mention notifications.

---

## Events Consumed

`ActivityService` subscribes to events from other modules to populate `issue_activities`:

| Event | Handler | Activity type written |
|---|---|---|
| `CommentCreatedEvent` | `ActivityService.onCommentCreated()` | `COMMENT` (skipped for system comments where `authorId` is null) |
| `IssueFieldChangedEvent` | `ActivityService.onIssueFieldChanged()` | Mapped per `field` value (title, description, priority, storyPoints, dueDate, sprint, assignee) |
| `IssueStatusChangedEvent` | `ActivityService.onIssueStatusChanged()` | `STATUS_CHANGED` (skipped if `actor` is null) |
| `AttachmentAddedEvent` | `ActivityService.onAttachmentAdded()` | `ATTACHMENT_ADDED` with `newValue = filename` |

---

## Key Files

- `backend/src/main/kotlin/com/taskowolf/comments/domain/Comment.kt`
- `backend/src/main/kotlin/com/taskowolf/comments/domain/IssueActivity.kt`
- `backend/src/main/kotlin/com/taskowolf/comments/domain/ActivityType.kt`
- `backend/src/main/kotlin/com/taskowolf/comments/domain/events/CommentCreatedEvent.kt`
- `backend/src/main/kotlin/com/taskowolf/comments/domain/events/MentionEvent.kt`
- `backend/src/main/kotlin/com/taskowolf/comments/application/CommentService.kt`
- `backend/src/main/kotlin/com/taskowolf/comments/application/ActivityService.kt`
- `backend/src/main/kotlin/com/taskowolf/comments/api/CommentController.kt`
- `backend/src/main/resources/db/migration/V7__create_comments.sql`

---

## Extension Points

- **New mentionable entity type:** Extend the @mention parser in `CommentService` to recognise the new prefix (e.g. `#issueKey`), resolve the target entity, and publish the appropriate mention event (subclass `MentionEvent` or add a new event type).
- **New activity type:** Add the value to `ActivityType`, add an `@EventListener` in `ActivityService` that handles the corresponding domain event, and save an `IssueActivity` with the new type.

---

## Common Pitfalls

- Comments can only be edited by their author. Deletion is allowed for the author or a project ADMIN. Do not allow any other mutation.
- DO NOT store raw HTML in comments — sanitize input with the existing utility before persisting; `body` is rendered by the frontend, not the backend.
- System-generated comments (`authorId == null`) are valid but do not produce `IssueActivity` records; `ActivityService.onCommentCreated()` returns early when `authorId` is null.
- `IssueFieldChangedEvent` with an unknown `field` value is silently skipped by `ActivityService` (logged at WARN); add the mapping explicitly for any new field.

---

## Example

`CommentService.addComment` saves the comment and publishes `CommentCreatedEvent`, which triggers both `ActivityService` and `NotificationService` listeners in the same transaction boundary:

```kotlin
@Transactional
fun addComment(projectKey: String, issueKey: String, body: String, actor: User): Comment {
    val issue = issueService.findByKey(projectKey, issueKey, actor.id)
    val comment = commentRepository.save(
        Comment(issueId = issue.id, authorId = actor.id, body = body)
    )
    eventPublisher.publish(
        CommentCreatedEvent(comment, issue, actorEmail = actor.email, actorId = actor.id)
    )
    return comment
}
```

`editComment` restricts mutation to the original author; ADMIN deletion uses `deleteComment` which additionally checks `ProjectService.isProjectAdmin`. Note the HTML sanitization step — storing raw HTML in `body` is a pitfall (see Common Pitfalls):

```kotlin
@Transactional
fun editComment(commentId: UUID, body: String, actor: User): Comment {
    val comment = commentRepository.findById(commentId)
        .orElseThrow { NotFoundException("Comment not found: $commentId") }
    if (comment.authorId != actor.id) throw ForbiddenException("Not the comment author")
    val sanitizedBody = htmlSanitizer.sanitize(body)
    comment.body = sanitizedBody
    comment.editedAt = Instant.now()
    return commentRepository.save(comment)
}
```

Mention extraction from comment body:

```kotlin
private val MENTION_REGEX = Regex("""@([\w.]+)""")

private fun extractAndPublishMentions(body: String, comment: Comment, issue: Issue) {
    MENTION_REGEX.findAll(body)
        .map { it.groupValues[1] }
        .distinct()
        .forEach { displayName ->
            val mentioned = userRepository.findByDisplayNameIgnoreCase(displayName)
                ?: return@forEach
            eventPublisher.publish(
                MentionEvent(mentionedUser = mentioned, comment = comment, issue = issue)
            )
        }
}
```

`extractAndPublishMentions()` shows the intended wiring. Call it from `addComment()` after saving to enable mention notifications. `NotificationService.onMention()` and `EmailService.onMention()` are the consumers.

---

## Test Patterns

- **`CommentServiceTest`** — pure unit test with MockK; mocks `CommentRepository`, `IssueService`, `ProjectService`, `DomainEventPublisher`. Verifies: comment creation publishes `CommentCreatedEvent`; edit sets `editedAt`; `ForbiddenException` when non-author edits; soft-delete sets `deletedAt`; project admin can delete any comment.
- **`ActivityServiceTest`** — pure unit test with MockK; uses a `@BeforeEach` stub (`repository.save(any()) returnsArgument 0`). Verifies: each event type produces the correct `ActivityType` and correct `oldValue`/`newValue`; system comment (null `authorId`) produces no activity record.
