# Phase 3: Collaboration — Design Spec

**Date:** 2026-06-10
**Status:** Approved

## Overview

Phase 3 fügt Collaboration-Features zu TaskWolf hinzu: Kommentare mit Markdown und @Mentions, eine vollständige Audit-Historie (Activity Feed), In-App- und E-Mail-Benachrichtigungen mit WebSocket-Push sowie Datei-Attachments pro Issue.

## Scope

| Feature | Entscheidung |
|---|---|
| Kommentare | Editierbar (Author), Soft Delete, Markdown |
| Activity Feed | Vollständige Audit-Historie — jede Feldänderung wird geloggt |
| @Mentions | In Kommentaren und Issue-Beschreibungen |
| Notifications | In-App + WebSocket Push + E-Mail (SMTP optional — wird übersprungen wenn nicht konfiguriert) |
| Notification-Trigger | @Mentions, neue Kommentare auf eigenen/zugewiesenen Issues, Issue zugewiesen, Statuswechsel auf eigenen Issues, Sprint gestartet/abgeschlossen |
| Attachments | Max 25 MB, alle Typen, gespeichert mit `0644`-Permissions (nicht ausführbar), konfigurierbar via `TW_ATTACHMENT_MAX_SIZE` |
| Attachment-Storage | Lokales Filesystem in Phase 3 (S3-kompatibel ab Phase 6) |
| Issue-Detail-Layout | Two-Column: links Beschreibung + Activity Feed, rechts Sidebar (Metadaten) + Attachments |
| Notification UI | Kompaktes Dropdown in TopNav, Badge mit Unread-Count, Link zu vollständiger Notifications-Seite |

## Architektur

**Muster:** Event-driven — alle Module kommunizieren über den bestehenden Spring `ApplicationEvent`-Bus. `issues` bleibt unwissend über `comments`, `notifications` und `attachments`.

```
IssueService.update()
  → publiziert IssueFieldChangedEvent(issue, field, oldVal, newVal)
      ↓
  ActivityService     → schreibt IssueActivity
  NotificationService → schreibt Notification + WebSocket Push + E-Mail (falls SMTP)

CommentService.create()
  → parst @mentions → publiziert MentionEvent(mentionedUser, issue, comment)
  → publiziert CommentCreatedEvent
      ↓
  ActivityService     → IssueActivity vom Typ COMMENT
  NotificationService → Notification vom Typ MENTION / COMMENT
```

### Neue Module

| Modul | Verantwortung | Abhängigkeiten |
|---|---|---|
| `comments` | Comment, IssueActivity, CommentService, ActivityService, CommentController | core, issues |
| `notifications` | Notification, NotificationService, NotificationController | core (Event Bus) |
| `attachments` | Attachment, AttachmentService, AttachmentController | core, issues |

`IssueActivity` lebt im `comments`-Modul (laut Design-Spec ist der Aktivitäts-Feed Verantwortung von `comments`).

## Datenmodell

### Comment

```
id          UUID         PK
body        TEXT         NOT NULL  (Markdown, max 10.000 Zeichen)
issue_id    UUID         FK → issues(id) ON DELETE CASCADE
author_id   UUID         FK → users(id)
created_at  TIMESTAMP    NOT NULL
edited_at   TIMESTAMP
deleted_at  TIMESTAMP                (Soft Delete)
```

### IssueActivity

```
id          UUID         PK
issue_id    UUID         FK → issues(id) ON DELETE CASCADE
actor_id    UUID         FK → users(id)
type        VARCHAR(40)  NOT NULL
  -- COMMENT | STATUS_CHANGED | ASSIGNED | UNASSIGNED
  -- PRIORITY_CHANGED | TITLE_CHANGED | DESCRIPTION_CHANGED
  -- STORY_POINTS_CHANGED | DUE_DATE_CHANGED | SPRINT_CHANGED
  -- ATTACHMENT_ADDED | ATTACHMENT_REMOVED | LINK_ADDED | LINK_REMOVED
comment_id  UUID         FK → comments(id) ON DELETE SET NULL  (nur bei type=COMMENT)
old_value   TEXT                      (Klartext oder JSON)
new_value   TEXT
created_at  TIMESTAMP    NOT NULL
```

Index: `idx_issue_activity_issue` auf `(issue_id, created_at DESC)`

### Notification

```
id          UUID         PK
user_id     UUID         FK → users(id) ON DELETE CASCADE
type        VARCHAR(30)  NOT NULL
  -- MENTION | COMMENT | ASSIGNED | STATUS_CHANGED
  -- SPRINT_STARTED | SPRINT_COMPLETED
title       VARCHAR(255) NOT NULL
body        TEXT
link        VARCHAR(500)             (z.B. /p/WOLF/issues/42)
read        BOOLEAN      NOT NULL DEFAULT false
created_at  TIMESTAMP    NOT NULL
```

Index: `idx_notifications_user` auf `(user_id, read, created_at DESC)`

### Attachment

```
id            UUID         PK
issue_id      UUID         FK → issues(id) ON DELETE CASCADE
uploader_id   UUID         FK → users(id)
filename      VARCHAR(255) NOT NULL   (Originalname)
stored_name   VARCHAR(255) NOT NULL   (UUID-basiert, eindeutig)
content_type  VARCHAR(127) NOT NULL   (MIME Type)
size          BIGINT       NOT NULL   (Bytes)
created_at    TIMESTAMP    NOT NULL
```

### IssueFieldChangedEvent (neu in issues-Modul)

```kotlin
data class IssueFieldChangedEvent(
    val issue: Issue,
    val field: String,       // "title", "priority", "assignee", etc.
    val oldValue: String?,
    val newValue: String?
)
```

`IssueService.update()` vergleicht alte und neue Werte vor dem Speichern und publiziert für jedes geänderte Feld ein separates Event.

## API

### Comments & Activity

```
GET    /api/v1/projects/{key}/issues/{id}/activity
       Query: page=0, size=50
       Response: Page<ActivityEntryResponse>
       → IssueActivity + Kommentare interleaved, nach created_at DESC

POST   /api/v1/projects/{key}/issues/{id}/comments
       Body: { "body": "..." }
       Response: 201 CommentResponse

PATCH  /api/v1/projects/{key}/issues/{id}/comments/{cid}
       Body: { "body": "..." }
       Response: CommentResponse
       Auth: nur Author

DELETE /api/v1/projects/{key}/issues/{id}/comments/{cid}
       Response: 204
       Auth: Author oder Project-Admin (Moderation)
       Effekt: deletedAt gesetzt, Feed zeigt „Kommentar gelöscht"
```

### Notifications

```
GET    /api/v1/notifications?page=0&size=20
       Response: Page<NotificationResponse>
       Auth: eigene Notifications (JWT)

GET    /api/v1/notifications/unread-count
       Response: { "count": 3 }

PATCH  /api/v1/notifications/{id}/read     → 204
PATCH  /api/v1/notifications/read-all      → 204
```

### Attachments

```
POST   /api/v1/projects/{key}/issues/{id}/attachments
       Content-Type: multipart/form-data, field: "file"
       Max: 25 MB (konfigurierbar via TW_ATTACHMENT_MAX_SIZE)
       Response: 201 AttachmentResponse

GET    /api/v1/projects/{key}/issues/{id}/attachments/{aid}/download
       Response: Datei-Stream mit Content-Disposition: attachment; filename="..."

DELETE /api/v1/projects/{key}/issues/{id}/attachments/{aid}
       Auth: Uploader oder Project-Admin, 204
```

### WebSocket (Erweiterung)

```
SUB /user/queue/notifications
    Push-Payload: { "type": "NOTIFICATION", "unreadCount": 3 }
    → unreadCount ist direkt im Payload enthalten — kein zusätzlicher API-Call nötig
```

## Backend-Dateistruktur

```
backend/src/main/kotlin/com/taskowolf/
  comments/
    domain/Comment.kt
    domain/IssueActivity.kt
    domain/ActivityType.kt
    domain/events/CommentCreatedEvent.kt
    domain/events/MentionEvent.kt
    application/CommentService.kt
    application/ActivityService.kt
    infrastructure/CommentRepository.kt
    infrastructure/IssueActivityRepository.kt
    api/CommentController.kt
    api/dto/CommentResponse.kt
    api/dto/CreateCommentRequest.kt
    api/dto/UpdateCommentRequest.kt
    api/dto/ActivityEntryResponse.kt

  notifications/
    domain/Notification.kt
    domain/NotificationType.kt
    application/NotificationService.kt
    application/EmailService.kt
    infrastructure/NotificationRepository.kt
    api/NotificationController.kt
    api/dto/NotificationResponse.kt
    api/dto/UnreadCountResponse.kt

  attachments/
    domain/Attachment.kt
    domain/events/AttachmentAddedEvent.kt
    domain/events/AttachmentRemovedEvent.kt
    application/AttachmentService.kt
    application/StorageService.kt
    infrastructure/AttachmentRepository.kt
    api/AttachmentController.kt
    api/dto/AttachmentResponse.kt

  issues/
    domain/events/IssueFieldChangedEvent.kt   # NEU

backend/src/main/resources/db/migration/
  V6__create_comments.sql
  V7__create_notifications.sql
  V8__create_attachments.sql

backend/src/test/kotlin/com/taskowolf/
  comments/CommentServiceTest.kt
  comments/ActivityServiceTest.kt
  notifications/NotificationServiceTest.kt
  attachments/AttachmentServiceTest.kt
  comments/CollaborationIntegrationTest.kt
```

## Frontend-Dateistruktur

```
frontend/src/
  api/
    comments.ts          # activity feed, comment CRUD
    notifications.ts     # GET, PATCH read, unread-count
    attachments.ts       # upload, download URL helper, delete

  hooks/
    useActivity.ts       # useActivity(projectKey, issueId)
    useComments.ts       # useCreateComment, useEditComment, useDeleteComment
    useNotifications.ts  # useNotifications, useUnreadCount, useMarkRead, useMarkAllRead
    useAttachments.ts    # useUploadAttachment, useDeleteAttachment

  components/
    activity/
      ActivityFeed.tsx         # Feed aus ActivityEntryResponse, paginiert
      ActivityEntry.tsx        # System-Zeile (z.B. „Max → IN PROGRESS")
      CommentItem.tsx          # Kommentar-Bubble, Edit/Delete-Menü (nur Author)
      CommentEditor.tsx        # Textarea + @mention-Autocomplete + Markdown-Preview-Toggle
    attachments/
      AttachmentPanel.tsx      # Liste + Upload-Drop-Zone (natives HTML5 Drag-and-Drop, keine extra Dependency)
      AttachmentItem.tsx       # Datei-Zeile mit Download-Link + Delete
    notifications/
      NotificationBell.tsx     # Bell-Icon + Unread-Badge in TopNav
      NotificationDropdown.tsx # Kompaktes Panel (max ~8 Einträge + „Alle anzeigen")
      NotificationItem.tsx     # Eine Notification-Zeile mit Link

  pages/
    issues/
      IssueDetail.tsx          # Two-Column: links Description+Feed, rechts Sidebar+Attachments
    notifications/
      NotificationsPage.tsx    # Vollständige Notification-Liste, paginiert

-- Geänderte Dateien:
  layouts/AppLayout.tsx        # NotificationBell in TopNav
  types/index.ts               # Comment, IssueActivity, Notification, Attachment, ActivityEntry
  app/router.tsx               # Route /notifications
```

## Konfiguration (.env Ergänzungen)

```env
TW_ATTACHMENT_MAX_SIZE=26214400     # 25 MB in Bytes
TW_ATTACHMENT_PATH=/data/attachments

# E-Mail (optional — wenn nicht gesetzt, wird E-Mail-Versand übersprungen)
TW_SMTP_HOST=smtp.example.com
TW_SMTP_PORT=587
TW_SMTP_USER=noreply@example.com
TW_SMTP_PASS=secret
TW_SMTP_FROM=TaskWolf <noreply@example.com>
```

## Nicht im Scope (Phase 3)

- S3-kompatibler Attachment-Storage (Phase 6)
- Kommentar-Reaktionen (Emojis)
- Notification-Präferenzen pro User (welche Typen aktiviert)
- Rich-Text-Editor (WYSIWYG) — Markdown-Textarea reicht
- Volltext-Suche über Kommentare
