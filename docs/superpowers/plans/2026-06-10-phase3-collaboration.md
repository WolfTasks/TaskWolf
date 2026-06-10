# Phase 3: Collaboration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Comments with @mentions, full Activity Feed, In-App + WebSocket + optional Email Notifications, and File Attachments to TaskWolf.

**Architecture:** Event-driven modular monolith — `IssueService` publishes `IssueFieldChangedEvent` per changed field; `ActivityService` and `NotificationService` listen via Spring ApplicationEvent bus. Three new modules: `comments` (Comment, IssueActivity, ActivityService, CommentService), `notifications` (Notification, NotificationService, EmailService), `attachments` (Attachment, StorageService, AttachmentService).

**Tech Stack:** Kotlin 2.x / Spring Boot 3.3 / JPA + Flyway / Spring WebSocket STOMP (SimpMessagingTemplate) / spring-boot-starter-mail (optional SMTP) / React 19 + TypeScript + React Query / @stomp/stompjs / native HTML5 File API

---

### Task 1: Database Migrations V6, V7, V8

**Files:**
- Create: `backend/src/main/resources/db/migration/V6__create_comments.sql`
- Create: `backend/src/main/resources/db/migration/V7__create_notifications.sql`
- Create: `backend/src/main/resources/db/migration/V8__create_attachments.sql`

- [ ] **Step 1: Write V6 — comments + issue_activities**

`backend/src/main/resources/db/migration/V6__create_comments.sql`:
```sql
CREATE TABLE comments (
    id           UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    body         TEXT         NOT NULL,
    issue_id     UUID         NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    author_id    UUID         NOT NULL REFERENCES users(id),
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    edited_at    TIMESTAMPTZ,
    deleted_at   TIMESTAMPTZ
);

CREATE TABLE issue_activities (
    id          UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    issue_id    UUID         NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    actor_id    UUID         NOT NULL REFERENCES users(id),
    type        VARCHAR(40)  NOT NULL,
    comment_id  UUID         REFERENCES comments(id) ON DELETE SET NULL,
    old_value   TEXT,
    new_value   TEXT,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_issue_activity_issue ON issue_activities (issue_id, created_at DESC);
```

- [ ] **Step 2: Write V7 — notifications**

`backend/src/main/resources/db/migration/V7__create_notifications.sql`:
```sql
CREATE TABLE notifications (
    id         UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type       VARCHAR(30)  NOT NULL,
    title      VARCHAR(255) NOT NULL,
    body       TEXT,
    link       VARCHAR(500),
    read       BOOLEAN      NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_notifications_user ON notifications (user_id, read, created_at DESC);
```

- [ ] **Step 3: Write V8 — attachments**

`backend/src/main/resources/db/migration/V8__create_attachments.sql`:
```sql
CREATE TABLE attachments (
    id            UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    issue_id      UUID         NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    uploader_id   UUID         NOT NULL REFERENCES users(id),
    filename      VARCHAR(255) NOT NULL,
    stored_name   VARCHAR(255) NOT NULL,
    content_type  VARCHAR(127) NOT NULL,
    size          BIGINT       NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL
);
```

- [ ] **Step 4: Start backend to verify Flyway applies all three migrations**

Run: `./gradlew :backend:bootRun`  
Expected: Flyway log shows `V6`, `V7`, `V8` applied successfully, app starts on port 8080.  
Stop the app (Ctrl+C).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/db/migration/
git commit -m "feat: add DB migrations V6 comments, V7 notifications, V8 attachments"
```

---

### Task 2: Build Config + Application Properties

**Files:**
- Modify: `backend/build.gradle.kts`
- Modify: `backend/src/main/resources/application.yml`

- [ ] **Step 1: Add spring-boot-starter-mail to build.gradle.kts**

Open `backend/build.gradle.kts`. In the `dependencies { }` block, add after the existing Spring starters:
```kotlin
implementation("org.springframework.boot:spring-boot-starter-mail")
```

- [ ] **Step 2: Add mail + multipart + attachment config to application.yml**

Open `backend/src/main/resources/application.yml`. Add at the end of the file:
```yaml
spring:
  mail:
    host: ${TW_SMTP_HOST:}
    port: ${TW_SMTP_PORT:587}
    username: ${TW_SMTP_USER:}
    password: ${TW_SMTP_PASS:}
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true
  servlet:
    multipart:
      max-file-size: ${TW_ATTACHMENT_MAX_SIZE:26214400}
      max-request-size: ${TW_ATTACHMENT_MAX_SIZE:26214400}

taskowolf:
  attachment:
    max-size: ${TW_ATTACHMENT_MAX_SIZE:26214400}
    path: ${TW_ATTACHMENT_PATH:./data/attachments}
  smtp:
    from: ${TW_SMTP_FROM:TaskWolf <noreply@example.com>}
```

Note: `TW_STORAGE_PATH` was a previous property for the storage path — use `TW_ATTACHMENT_PATH` going forward. If `application.yml` already has a `taskowolf.storage-path` key, remove it and replace with the block above.

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :backend:compileKotlin`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add backend/build.gradle.kts backend/src/main/resources/application.yml
git commit -m "feat: add mail dependency and multipart/attachment config"
```

---

### Task 3: Prep — UserRepository, ProjectController /members, IssueFieldChangedEvent, IssueStatusChangedEvent actor

**Files:**
- Modify: `backend/src/main/kotlin/com/taskowolf/auth/infrastructure/UserRepository.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/projects/api/ProjectController.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/issues/domain/events/IssueFieldChangedEvent.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/issues/domain/events/IssueStatusChangedEvent.kt`

- [ ] **Step 1: Add findByDisplayNameIgnoreCase to UserRepository**

Open `backend/src/main/kotlin/com/taskowolf/auth/infrastructure/UserRepository.kt`. Add:
```kotlin
fun findByDisplayNameIgnoreCase(displayName: String): User?
```

- [ ] **Step 2: Add GET /{key}/members to ProjectController**

Open `backend/src/main/kotlin/com/taskowolf/projects/api/ProjectController.kt`. Inject `ProjectMemberRepository` and `UserRepository`. Add the endpoint at the end of the controller class:
```kotlin
@GetMapping("/{key}/members")
fun getMembers(
    @PathVariable key: String,
    @AuthenticationPrincipal principal: UserDetails
): ResponseEntity<List<UserResponse>> {
    val project = projectService.getByKey(key)
    val members = projectMemberRepository.findAllByProjectId(project.id)
        .mapNotNull { userRepository.findById(it.userId).orElse(null) }
        .map { UserResponse.from(it) }
    return ResponseEntity.ok(members)
}
```

If `ProjectController` does not already inject `ProjectMemberRepository` and `UserRepository`, add them to the constructor. Check the existing constructor signature and add the missing parameters. Example:
```kotlin
@RestController
@RequestMapping("/api/v1/projects")
class ProjectController(
    private val projectService: ProjectService,
    private val projectMemberRepository: ProjectMemberRepository,
    private val userRepository: UserRepository,
)
```

Import `com.taskowolf.projects.infrastructure.ProjectMemberRepository` and `com.taskowolf.auth.infrastructure.UserRepository`.

- [ ] **Step 3: Create IssueFieldChangedEvent**

Create `backend/src/main/kotlin/com/taskowolf/issues/domain/events/IssueFieldChangedEvent.kt`:
```kotlin
package com.taskowolf.issues.domain.events

import com.taskowolf.auth.domain.User
import com.taskowolf.issues.domain.Issue

data class IssueFieldChangedEvent(
    val issue: Issue,
    val actor: User,
    val field: String,
    val oldValue: String?,
    val newValue: String?
)
```

- [ ] **Step 4: Add actor to IssueStatusChangedEvent**

Open `backend/src/main/kotlin/com/taskowolf/issues/domain/events/IssueStatusChangedEvent.kt`. Add `actor: User? = null` as last parameter (nullable with default null for backwards compatibility with existing `BoardEventPublisher` which doesn't pass actor):
```kotlin
package com.taskowolf.issues.domain.events

import com.taskowolf.auth.domain.User
import com.taskowolf.issues.domain.Issue
import com.taskowolf.sprints.domain.WorkflowStatus

data class IssueStatusChangedEvent(
    val issue: Issue,
    val oldStatus: WorkflowStatus,
    val newStatus: WorkflowStatus,
    val actor: User? = null
)
```

- [ ] **Step 5: Verify compilation**

Run: `./gradlew :backend:compileKotlin`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/auth/infrastructure/UserRepository.kt
git add backend/src/main/kotlin/com/taskowolf/projects/api/ProjectController.kt
git add backend/src/main/kotlin/com/taskowolf/issues/domain/events/
git commit -m "feat: add UserRepository.findByDisplayName, /members endpoint, IssueFieldChangedEvent"
```

---

### Task 4: Comment Domain + IssueActivity Domain

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/comments/domain/ActivityType.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/comments/domain/Comment.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/comments/domain/IssueActivity.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/comments/domain/events/CommentCreatedEvent.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/comments/domain/events/MentionEvent.kt`

- [ ] **Step 1: Create ActivityType enum**

Create `backend/src/main/kotlin/com/taskowolf/comments/domain/ActivityType.kt`:
```kotlin
package com.taskowolf.comments.domain

enum class ActivityType {
    COMMENT,
    STATUS_CHANGED,
    ASSIGNED,
    UNASSIGNED,
    PRIORITY_CHANGED,
    TITLE_CHANGED,
    DESCRIPTION_CHANGED,
    STORY_POINTS_CHANGED,
    DUE_DATE_CHANGED,
    SPRINT_CHANGED,
    ATTACHMENT_ADDED,
    ATTACHMENT_REMOVED
}
```

- [ ] **Step 2: Create Comment entity**

Create `backend/src/main/kotlin/com/taskowolf/comments/domain/Comment.kt`:
```kotlin
package com.taskowolf.comments.domain

import com.taskowolf.core.domain.AuditableEntity
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "comments")
class Comment(
    @Column(name = "issue_id", nullable = false)
    val issueId: UUID,

    @Column(name = "author_id", nullable = false)
    val authorId: UUID,

    @Column(columnDefinition = "TEXT", nullable = false)
    var body: String,

    @Column(name = "edited_at")
    var editedAt: Instant? = null,

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null
) : AuditableEntity()
```

- [ ] **Step 3: Create IssueActivity entity**

Create `backend/src/main/kotlin/com/taskowolf/comments/domain/IssueActivity.kt`:
```kotlin
package com.taskowolf.comments.domain

import com.taskowolf.core.domain.AuditableEntity
import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "issue_activities")
class IssueActivity(
    @Column(name = "issue_id", nullable = false)
    val issueId: UUID,

    @Column(name = "actor_id", nullable = false)
    val actorId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    val type: ActivityType,

    @Column(name = "comment_id")
    val commentId: UUID? = null,

    @Column(name = "old_value", columnDefinition = "TEXT")
    val oldValue: String? = null,

    @Column(name = "new_value", columnDefinition = "TEXT")
    val newValue: String? = null
) : AuditableEntity()
```

- [ ] **Step 4: Create CommentCreatedEvent**

Create `backend/src/main/kotlin/com/taskowolf/comments/domain/events/CommentCreatedEvent.kt`:
```kotlin
package com.taskowolf.comments.domain.events

import com.taskowolf.comments.domain.Comment
import com.taskowolf.issues.domain.Issue

data class CommentCreatedEvent(
    val comment: Comment,
    val issue: Issue
)
```

- [ ] **Step 5: Create MentionEvent**

Create `backend/src/main/kotlin/com/taskowolf/comments/domain/events/MentionEvent.kt`:
```kotlin
package com.taskowolf.comments.domain.events

import com.taskowolf.auth.domain.User
import com.taskowolf.comments.domain.Comment
import com.taskowolf.issues.domain.Issue

data class MentionEvent(
    val mentionedUser: User,
    val comment: Comment,
    val issue: Issue
)
```

- [ ] **Step 6: Verify compilation**

Run: `./gradlew :backend:compileKotlin`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/comments/
git commit -m "feat: add Comment, IssueActivity domain entities and events"
```

---

### Task 5: Attachment Domain

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/attachments/domain/Attachment.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/attachments/domain/events/AttachmentAddedEvent.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/attachments/domain/events/AttachmentRemovedEvent.kt`

- [ ] **Step 1: Create Attachment entity**

Create `backend/src/main/kotlin/com/taskowolf/attachments/domain/Attachment.kt`:
```kotlin
package com.taskowolf.attachments.domain

import com.taskowolf.core.domain.AuditableEntity
import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "attachments")
class Attachment(
    @Column(name = "issue_id", nullable = false)
    val issueId: UUID,

    @Column(name = "uploader_id", nullable = false)
    val uploaderId: UUID,

    @Column(nullable = false, length = 255)
    val filename: String,

    @Column(name = "stored_name", nullable = false, length = 255)
    val storedName: String,

    @Column(name = "content_type", nullable = false, length = 127)
    val contentType: String,

    @Column(nullable = false)
    val size: Long
) : AuditableEntity()
```

- [ ] **Step 2: Create AttachmentAddedEvent**

Create `backend/src/main/kotlin/com/taskowolf/attachments/domain/events/AttachmentAddedEvent.kt`:
```kotlin
package com.taskowolf.attachments.domain.events

import com.taskowolf.attachments.domain.Attachment
import com.taskowolf.issues.domain.Issue

data class AttachmentAddedEvent(
    val attachment: Attachment,
    val issue: Issue
)
```

- [ ] **Step 3: Create AttachmentRemovedEvent**

Create `backend/src/main/kotlin/com/taskowolf/attachments/domain/events/AttachmentRemovedEvent.kt`:
```kotlin
package com.taskowolf.attachments.domain.events

import com.taskowolf.attachments.domain.Attachment
import com.taskowolf.issues.domain.Issue

data class AttachmentRemovedEvent(
    val attachment: Attachment,
    val issue: Issue
)
```

- [ ] **Step 4: Verify compilation**

Run: `./gradlew :backend:compileKotlin`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/attachments/
git commit -m "feat: add Attachment domain entity and events"
```

---

### Task 6: Repositories

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/comments/infrastructure/CommentRepository.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/comments/infrastructure/IssueActivityRepository.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/notifications/infrastructure/NotificationRepository.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/attachments/infrastructure/AttachmentRepository.kt`

- [ ] **Step 1: Create CommentRepository**

Create `backend/src/main/kotlin/com/taskowolf/comments/infrastructure/CommentRepository.kt`:
```kotlin
package com.taskowolf.comments.infrastructure

import com.taskowolf.comments.domain.Comment
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CommentRepository : JpaRepository<Comment, UUID> {
    fun findAllByIssueId(issueId: UUID): List<Comment>
}
```

- [ ] **Step 2: Create IssueActivityRepository**

Create `backend/src/main/kotlin/com/taskowolf/comments/infrastructure/IssueActivityRepository.kt`:
```kotlin
package com.taskowolf.comments.infrastructure

import com.taskowolf.comments.domain.IssueActivity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface IssueActivityRepository : JpaRepository<IssueActivity, UUID> {
    fun findAllByIssueId(issueId: UUID, pageable: Pageable): Page<IssueActivity>
}
```

- [ ] **Step 3: Create NotificationRepository**

Create `backend/src/main/kotlin/com/taskowolf/notifications/infrastructure/NotificationRepository.kt`:
```kotlin
package com.taskowolf.notifications.infrastructure

import com.taskowolf.notifications.domain.Notification
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface NotificationRepository : JpaRepository<Notification, UUID> {
    fun findAllByUserId(userId: UUID, pageable: Pageable): Page<Notification>
    fun countByUserIdAndReadFalse(userId: UUID): Long

    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.userId = :userId AND n.read = false")
    fun markAllReadByUserId(userId: UUID): Int
}
```

- [ ] **Step 4: Create AttachmentRepository**

Create `backend/src/main/kotlin/com/taskowolf/attachments/infrastructure/AttachmentRepository.kt`:
```kotlin
package com.taskowolf.attachments.infrastructure

import com.taskowolf.attachments.domain.Attachment
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AttachmentRepository : JpaRepository<Attachment, UUID> {
    fun findAllByIssueId(issueId: UUID): List<Attachment>
}
```

- [ ] **Step 5: Verify compilation**

Run: `./gradlew :backend:compileKotlin`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/comments/infrastructure/
git add backend/src/main/kotlin/com/taskowolf/notifications/infrastructure/
git add backend/src/main/kotlin/com/taskowolf/attachments/infrastructure/
git commit -m "feat: add Comment, Notification, Attachment repositories"
```

---

### Task 7: ActivityService (TDD)

**Files:**
- Create: `backend/src/test/kotlin/com/taskowolf/comments/ActivityServiceTest.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/comments/application/ActivityService.kt`

- [ ] **Step 1: Write failing tests**

Create `backend/src/test/kotlin/com/taskowolf/comments/ActivityServiceTest.kt`:
```kotlin
package com.taskowolf.comments

import com.taskowolf.attachments.domain.Attachment
import com.taskowolf.attachments.domain.events.AttachmentAddedEvent
import com.taskowolf.attachments.domain.events.AttachmentRemovedEvent
import com.taskowolf.auth.domain.User
import com.taskowolf.comments.application.ActivityService
import com.taskowolf.comments.domain.ActivityType
import com.taskowolf.comments.domain.Comment
import com.taskowolf.comments.domain.IssueActivity
import com.taskowolf.comments.domain.events.CommentCreatedEvent
import com.taskowolf.comments.infrastructure.IssueActivityRepository
import com.taskowolf.issues.domain.Issue
import com.taskowolf.issues.domain.events.IssueFieldChangedEvent
import com.taskowolf.issues.domain.events.IssueStatusChangedEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.UUID

class ActivityServiceTest {

    private val repo = mockk<IssueActivityRepository>(relaxed = true)
    private val service = ActivityService(repo)

    private fun user(id: UUID = UUID.randomUUID()) = mockk<User> { every { this@mockk.id } returns id }
    private fun issue(id: UUID = UUID.randomUUID()) = mockk<Issue> { every { this@mockk.id } returns id }

    @Test
    fun `onCommentCreated saves COMMENT activity`() {
        val actorId = UUID.randomUUID()
        val issueId = UUID.randomUUID()
        val commentId = UUID.randomUUID()
        val actor = user(actorId)
        val iss = issue(issueId)
        val comment = mockk<Comment> {
            every { id } returns commentId
            every { authorId } returns actorId
        }

        service.onCommentCreated(CommentCreatedEvent(comment, iss))

        val slot = slot<IssueActivity>()
        verify { repo.save(capture(slot)) }
        assert(slot.captured.type == ActivityType.COMMENT)
        assert(slot.captured.issueId == issueId)
        assert(slot.captured.commentId == commentId)
    }

    @Test
    fun `onIssueFieldChanged saves correct ActivityType`() {
        val actor = user()
        val iss = issue()
        val event = IssueFieldChangedEvent(iss, actor, "priority", "LOW", "HIGH")

        service.onIssueFieldChanged(event)

        val slot = slot<IssueActivity>()
        verify { repo.save(capture(slot)) }
        assert(slot.captured.type == ActivityType.PRIORITY_CHANGED)
        assert(slot.captured.oldValue == "LOW")
        assert(slot.captured.newValue == "HIGH")
    }

    @Test
    fun `onIssueStatusChanged saves STATUS_CHANGED activity`() {
        val actorId = UUID.randomUUID()
        val actor = user(actorId)
        val iss = issue()
        val oldStatus = mockk<com.taskowolf.sprints.domain.WorkflowStatus> { every { name } returns "TODO" }
        val newStatus = mockk<com.taskowolf.sprints.domain.WorkflowStatus> { every { name } returns "IN PROGRESS" }
        val event = IssueStatusChangedEvent(iss, oldStatus, newStatus, actor)

        service.onIssueStatusChanged(event)

        val slot = slot<IssueActivity>()
        verify { repo.save(capture(slot)) }
        assert(slot.captured.type == ActivityType.STATUS_CHANGED)
        assert(slot.captured.actorId == actorId)
    }

    @Test
    fun `onAttachmentAdded saves ATTACHMENT_ADDED activity`() {
        val uploaderId = UUID.randomUUID()
        val issueId = UUID.randomUUID()
        val iss = issue(issueId)
        val att = mockk<Attachment> {
            every { this@mockk.uploaderId } returns uploaderId
            every { filename } returns "test.png"
        }

        service.onAttachmentAdded(AttachmentAddedEvent(att, iss))

        val slot = slot<IssueActivity>()
        verify { repo.save(capture(slot)) }
        assert(slot.captured.type == ActivityType.ATTACHMENT_ADDED)
        assert(slot.captured.newValue == "test.png")
    }
}
```

- [ ] **Step 2: Run tests — expect failure**

Run: `./gradlew :backend:test --tests "com.taskowolf.comments.ActivityServiceTest"`  
Expected: Compilation error — `ActivityService` not found.

- [ ] **Step 3: Implement ActivityService**

Create `backend/src/main/kotlin/com/taskowolf/comments/application/ActivityService.kt`:
```kotlin
package com.taskowolf.comments.application

import com.taskowolf.attachments.domain.events.AttachmentAddedEvent
import com.taskowolf.attachments.domain.events.AttachmentRemovedEvent
import com.taskowolf.comments.domain.ActivityType
import com.taskowolf.comments.domain.IssueActivity
import com.taskowolf.comments.domain.events.CommentCreatedEvent
import com.taskowolf.comments.infrastructure.IssueActivityRepository
import com.taskowolf.issues.domain.events.IssueFieldChangedEvent
import com.taskowolf.issues.domain.events.IssueStatusChangedEvent
import org.springframework.context.event.EventListener
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ActivityService(
    private val activityRepository: IssueActivityRepository
) {

    @EventListener
    fun onCommentCreated(event: CommentCreatedEvent) {
        activityRepository.save(IssueActivity(
            issueId = event.issue.id,
            actorId = event.comment.authorId,
            type = ActivityType.COMMENT,
            commentId = event.comment.id
        ))
    }

    @EventListener
    fun onIssueFieldChanged(event: IssueFieldChangedEvent) {
        val type = when (event.field) {
            "title"       -> ActivityType.TITLE_CHANGED
            "description" -> ActivityType.DESCRIPTION_CHANGED
            "priority"    -> ActivityType.PRIORITY_CHANGED
            "storyPoints" -> ActivityType.STORY_POINTS_CHANGED
            "assignee"    -> ActivityType.ASSIGNED
            "sprint"      -> ActivityType.SPRINT_CHANGED
            else          -> return
        }
        activityRepository.save(IssueActivity(
            issueId = event.issue.id,
            actorId = event.actor.id,
            type = type,
            oldValue = event.oldValue,
            newValue = event.newValue
        ))
    }

    @EventListener
    fun onIssueStatusChanged(event: IssueStatusChangedEvent) {
        val actorId = event.actor?.id ?: return
        activityRepository.save(IssueActivity(
            issueId = event.issue.id,
            actorId = actorId,
            type = ActivityType.STATUS_CHANGED,
            oldValue = event.oldStatus.name,
            newValue = event.newStatus.name
        ))
    }

    @EventListener
    fun onAttachmentAdded(event: AttachmentAddedEvent) {
        activityRepository.save(IssueActivity(
            issueId = event.issue.id,
            actorId = event.attachment.uploaderId,
            type = ActivityType.ATTACHMENT_ADDED,
            newValue = event.attachment.filename
        ))
    }

    @EventListener
    fun onAttachmentRemoved(event: AttachmentRemovedEvent) {
        activityRepository.save(IssueActivity(
            issueId = event.issue.id,
            actorId = event.attachment.uploaderId,
            type = ActivityType.ATTACHMENT_REMOVED,
            oldValue = event.attachment.filename
        ))
    }

    fun getActivityFeed(issueId: UUID, pageable: Pageable): Page<IssueActivity> =
        activityRepository.findAllByIssueId(issueId, pageable)
}
```

- [ ] **Step 4: Run tests — expect pass**

Run: `./gradlew :backend:test --tests "com.taskowolf.comments.ActivityServiceTest"`  
Expected: 4 tests pass.

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/kotlin/com/taskowolf/comments/ActivityServiceTest.kt
git add backend/src/main/kotlin/com/taskowolf/comments/application/ActivityService.kt
git commit -m "feat: add ActivityService with event listeners for activity feed"
```

---

### Task 8: CommentService (TDD)

**Files:**
- Create: `backend/src/test/kotlin/com/taskowolf/comments/CommentServiceTest.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/comments/application/CommentService.kt`

- [ ] **Step 1: Write failing tests**

Create `backend/src/test/kotlin/com/taskowolf/comments/CommentServiceTest.kt`:
```kotlin
package com.taskowolf.comments

import com.taskowolf.auth.domain.User
import com.taskowolf.auth.infrastructure.UserRepository
import com.taskowolf.comments.application.CommentService
import com.taskowolf.comments.domain.Comment
import com.taskowolf.comments.domain.events.CommentCreatedEvent
import com.taskowolf.comments.domain.events.MentionEvent
import com.taskowolf.comments.infrastructure.CommentRepository
import com.taskowolf.core.application.DomainEventPublisher
import com.taskowolf.core.exceptions.ForbiddenException
import com.taskowolf.issues.domain.Issue
import com.taskowolf.issues.infrastructure.IssueRepository
import com.taskowolf.projects.application.ProjectService
import io.mockk.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Optional
import java.util.UUID

class CommentServiceTest {

    private val commentRepository = mockk<CommentRepository>(relaxed = true)
    private val issueRepository   = mockk<IssueRepository>()
    private val userRepository    = mockk<UserRepository>()
    private val projectService    = mockk<ProjectService>(relaxed = true)
    private val eventPublisher    = mockk<DomainEventPublisher>(relaxed = true)

    private val service = CommentService(
        commentRepository, issueRepository, userRepository, projectService, eventPublisher
    )

    private fun issue(id: UUID = UUID.randomUUID(), projectKey: String = "WOLF"): Issue =
        mockk { every { this@mockk.id } returns id; every { this@mockk.projectKey } returns projectKey }

    private fun user(id: UUID = UUID.randomUUID(), displayName: String = "max"): User =
        mockk { every { this@mockk.id } returns id; every { this@mockk.displayName } returns displayName }

    @Test
    fun `create saves comment and publishes CommentCreatedEvent`() {
        val issueId = UUID.randomUUID()
        val authorId = UUID.randomUUID()
        val iss = issue(issueId)
        every { issueRepository.findById(issueId) } returns Optional.of(iss)
        every { commentRepository.save(any()) } answers { firstArg() }

        service.create(issueId, "Hello world", authorId)

        verify { commentRepository.save(match { it.body == "Hello world" && it.authorId == authorId }) }
        verify { eventPublisher.publish(ofType(CommentCreatedEvent::class)) }
    }

    @Test
    fun `create with @mention publishes MentionEvent for each mentioned user`() {
        val issueId = UUID.randomUUID()
        val authorId = UUID.randomUUID()
        val mentionedUser = user(displayName = "anna")
        val iss = issue(issueId)
        every { issueRepository.findById(issueId) } returns Optional.of(iss)
        every { userRepository.findByDisplayNameIgnoreCase("anna") } returns mentionedUser
        every { commentRepository.save(any()) } answers { firstArg() }

        service.create(issueId, "Hey @anna can you review?", authorId)

        verify { eventPublisher.publish(ofType(MentionEvent::class)) }
    }

    @Test
    fun `edit updates body and editedAt when caller is author`() {
        val commentId = UUID.randomUUID()
        val authorId = UUID.randomUUID()
        val comment = Comment(issueId = UUID.randomUUID(), authorId = authorId, body = "old")
        every { commentRepository.findById(commentId) } returns Optional.of(comment)
        every { commentRepository.save(any()) } answers { firstArg() }

        service.edit(commentId, "new body", authorId)

        assert(comment.body == "new body")
        assert(comment.editedAt != null)
    }

    @Test
    fun `edit throws ForbiddenException when caller is not author`() {
        val commentId = UUID.randomUUID()
        val comment = Comment(issueId = UUID.randomUUID(), authorId = UUID.randomUUID(), body = "old")
        every { commentRepository.findById(commentId) } returns Optional.of(comment)

        assertThrows<ForbiddenException> {
            service.edit(commentId, "new body", UUID.randomUUID())
        }
    }

    @Test
    fun `softDelete sets deletedAt when caller is author`() {
        val commentId = UUID.randomUUID()
        val authorId = UUID.randomUUID()
        val issueId = UUID.randomUUID()
        val comment = Comment(issueId = issueId, authorId = authorId, body = "text")
        every { commentRepository.findById(commentId) } returns Optional.of(comment)
        every { commentRepository.save(any()) } answers { firstArg() }
        every { projectService.isProjectAdmin(any(), authorId) } returns false

        service.softDelete(commentId, authorId)

        assert(comment.deletedAt != null)
    }

    @Test
    fun `softDelete throws ForbiddenException when caller is neither author nor admin`() {
        val commentId = UUID.randomUUID()
        val comment = Comment(issueId = UUID.randomUUID(), authorId = UUID.randomUUID(), body = "text")
        val callerId = UUID.randomUUID()
        every { commentRepository.findById(commentId) } returns Optional.of(comment)
        every { projectService.isProjectAdmin(any(), callerId) } returns false

        assertThrows<ForbiddenException> {
            service.softDelete(commentId, callerId)
        }
    }
}
```

- [ ] **Step 2: Run tests — expect compilation failure**

Run: `./gradlew :backend:test --tests "com.taskowolf.comments.CommentServiceTest"`  
Expected: Compilation error — `CommentService` not found.

- [ ] **Step 3: Add isProjectAdmin helper to ProjectService**

Open `backend/src/main/kotlin/com/taskowolf/projects/application/ProjectService.kt`. Add:
```kotlin
fun isProjectAdmin(projectId: UUID, userId: UUID): Boolean =
    projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
        ?.role == ProjectRole.ADMIN
```

Import `com.taskowolf.projects.domain.ProjectRole`.

- [ ] **Step 4: Implement CommentService**

Create `backend/src/main/kotlin/com/taskowolf/comments/application/CommentService.kt`:
```kotlin
package com.taskowolf.comments.application

import com.taskowolf.auth.infrastructure.UserRepository
import com.taskowolf.comments.domain.Comment
import com.taskowolf.comments.domain.events.CommentCreatedEvent
import com.taskowolf.comments.domain.events.MentionEvent
import com.taskowolf.comments.infrastructure.CommentRepository
import com.taskowolf.core.application.DomainEventPublisher
import com.taskowolf.core.exceptions.ForbiddenException
import com.taskowolf.core.exceptions.NotFoundException
import com.taskowolf.issues.infrastructure.IssueRepository
import com.taskowolf.projects.application.ProjectService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

private val MENTION_REGEX = Regex("@(\\w+)")

@Service
class CommentService(
    private val commentRepository: CommentRepository,
    private val issueRepository: IssueRepository,
    private val userRepository: UserRepository,
    private val projectService: ProjectService,
    private val eventPublisher: DomainEventPublisher
) {

    @Transactional
    fun create(issueId: UUID, body: String, authorId: UUID): Comment {
        val issue = issueRepository.findById(issueId)
            .orElseThrow { NotFoundException("Issue not found") }

        val comment = commentRepository.save(Comment(
            issueId = issueId,
            authorId = authorId,
            body = body
        ))

        eventPublisher.publish(CommentCreatedEvent(comment, issue))

        MENTION_REGEX.findAll(body)
            .mapNotNull { userRepository.findByDisplayNameIgnoreCase(it.groupValues[1]) }
            .forEach { mentionedUser ->
                eventPublisher.publish(MentionEvent(mentionedUser, comment, issue))
            }

        return comment
    }

    @Transactional
    fun edit(commentId: UUID, newBody: String, callerId: UUID): Comment {
        val comment = commentRepository.findById(commentId)
            .orElseThrow { NotFoundException("Comment not found") }
        if (comment.authorId != callerId) throw ForbiddenException("Only the author can edit this comment")
        comment.body = newBody
        comment.editedAt = Instant.now()
        return commentRepository.save(comment)
    }

    @Transactional
    fun softDelete(commentId: UUID, callerId: UUID) {
        val comment = commentRepository.findById(commentId)
            .orElseThrow { NotFoundException("Comment not found") }
        val isAdmin = projectService.isProjectAdmin(comment.issueId, callerId)
        if (comment.authorId != callerId && !isAdmin) {
            throw ForbiddenException("Not authorized to delete this comment")
        }
        comment.deletedAt = Instant.now()
        commentRepository.save(comment)
    }

    fun findById(commentId: UUID): Comment =
        commentRepository.findById(commentId).orElseThrow { NotFoundException("Comment not found") }
}
```

- [ ] **Step 5: Run tests — expect pass**

Run: `./gradlew :backend:test --tests "com.taskowolf.comments.CommentServiceTest"`  
Expected: 5 tests pass.

- [ ] **Step 6: Commit**

```bash
git add backend/src/test/kotlin/com/taskowolf/comments/CommentServiceTest.kt
git add backend/src/main/kotlin/com/taskowolf/comments/application/CommentService.kt
git add backend/src/main/kotlin/com/taskowolf/projects/application/ProjectService.kt
git commit -m "feat: add CommentService with @mention parsing and soft-delete"
```

---

### Task 9: Comment + Activity DTOs and Controller

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/comments/api/dto/CommentResponse.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/comments/api/dto/CreateCommentRequest.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/comments/api/dto/UpdateCommentRequest.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/comments/api/dto/ActivityEntryResponse.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/comments/api/CommentController.kt`

- [ ] **Step 1: Create CommentResponse**

Create `backend/src/main/kotlin/com/taskowolf/comments/api/dto/CommentResponse.kt`:
```kotlin
package com.taskowolf.comments.api.dto

import com.taskowolf.comments.domain.Comment
import java.time.Instant
import java.util.UUID

data class CommentResponse(
    val id: UUID,
    val body: String?,
    val authorId: UUID,
    val createdAt: Instant,
    val editedAt: Instant?,
    val deleted: Boolean
) {
    companion object {
        fun from(comment: Comment) = CommentResponse(
            id = comment.id,
            body = if (comment.deletedAt != null) null else comment.body,
            authorId = comment.authorId,
            createdAt = comment.createdAt,
            editedAt = comment.editedAt,
            deleted = comment.deletedAt != null
        )
    }
}
```

- [ ] **Step 2: Create CreateCommentRequest and UpdateCommentRequest**

Create `backend/src/main/kotlin/com/taskowolf/comments/api/dto/CreateCommentRequest.kt`:
```kotlin
package com.taskowolf.comments.api.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateCommentRequest(
    @field:NotBlank
    @field:Size(max = 10000)
    val body: String
)
```

Create `backend/src/main/kotlin/com/taskowolf/comments/api/dto/UpdateCommentRequest.kt`:
```kotlin
package com.taskowolf.comments.api.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class UpdateCommentRequest(
    @field:NotBlank
    @field:Size(max = 10000)
    val body: String
)
```

- [ ] **Step 3: Create ActivityEntryResponse**

Create `backend/src/main/kotlin/com/taskowolf/comments/api/dto/ActivityEntryResponse.kt`:
```kotlin
package com.taskowolf.comments.api.dto

import com.taskowolf.comments.domain.ActivityType
import com.taskowolf.comments.domain.IssueActivity
import java.time.Instant
import java.util.UUID

data class ActivityEntryResponse(
    val id: UUID,
    val type: ActivityType,
    val actorId: UUID,
    val createdAt: Instant,
    val oldValue: String?,
    val newValue: String?,
    val comment: CommentResponse?
) {
    companion object {
        fun from(activity: IssueActivity, comment: CommentResponse? = null) = ActivityEntryResponse(
            id = activity.id,
            type = activity.type,
            actorId = activity.actorId,
            createdAt = activity.createdAt,
            oldValue = activity.oldValue,
            newValue = activity.newValue,
            comment = comment
        )
    }
}
```

- [ ] **Step 4: Create CommentController**

Create `backend/src/main/kotlin/com/taskowolf/comments/api/CommentController.kt`:
```kotlin
package com.taskowolf.comments.api

import com.taskowolf.auth.infrastructure.UserRepository
import com.taskowolf.comments.api.dto.*
import com.taskowolf.comments.application.ActivityService
import com.taskowolf.comments.application.CommentService
import com.taskowolf.comments.infrastructure.CommentRepository
import com.taskowolf.core.exceptions.NotFoundException
import com.taskowolf.issues.infrastructure.IssueRepository
import com.taskowolf.projects.application.ProjectService
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/projects/{key}/issues/{issueId}")
class CommentController(
    private val commentService: CommentService,
    private val activityService: ActivityService,
    private val commentRepository: CommentRepository,
    private val issueRepository: IssueRepository,
    private val userRepository: UserRepository,
    private val projectService: ProjectService
) {

    @GetMapping("/activity")
    fun getActivity(
        @PathVariable key: String,
        @PathVariable issueId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int
    ): ResponseEntity<*> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        val activities = activityService.getActivityFeed(issueId, pageable)
        val commentMap = commentRepository.findAllByIssueId(issueId)
            .associateBy { it.id }
        val response = activities.map { activity ->
            val comment = activity.commentId?.let { cid ->
                commentMap[cid]?.let { CommentResponse.from(it) }
            }
            ActivityEntryResponse.from(activity, comment)
        }
        return ResponseEntity.ok(response)
    }

    @PostMapping("/comments")
    fun createComment(
        @PathVariable key: String,
        @PathVariable issueId: UUID,
        @Valid @RequestBody request: CreateCommentRequest,
        @AuthenticationPrincipal principal: UserDetails
    ): ResponseEntity<CommentResponse> {
        val author = userRepository.findByEmail(principal.username)
            ?: throw NotFoundException("User not found")
        val comment = commentService.create(issueId, request.body, author.id)
        return ResponseEntity.status(201).body(CommentResponse.from(comment))
    }

    @PatchMapping("/comments/{commentId}")
    fun editComment(
        @PathVariable key: String,
        @PathVariable issueId: UUID,
        @PathVariable commentId: UUID,
        @Valid @RequestBody request: UpdateCommentRequest,
        @AuthenticationPrincipal principal: UserDetails
    ): ResponseEntity<CommentResponse> {
        val caller = userRepository.findByEmail(principal.username)
            ?: throw NotFoundException("User not found")
        val comment = commentService.edit(commentId, request.body, caller.id)
        return ResponseEntity.ok(CommentResponse.from(comment))
    }

    @DeleteMapping("/comments/{commentId}")
    fun deleteComment(
        @PathVariable key: String,
        @PathVariable issueId: UUID,
        @PathVariable commentId: UUID,
        @AuthenticationPrincipal principal: UserDetails
    ): ResponseEntity<Void> {
        val caller = userRepository.findByEmail(principal.username)
            ?: throw NotFoundException("User not found")
        commentService.softDelete(commentId, caller.id)
        return ResponseEntity.noContent().build()
    }
}
```

- [ ] **Step 5: Verify compilation**

Run: `./gradlew :backend:compileKotlin`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/comments/api/
git commit -m "feat: add CommentController with activity feed and comment CRUD"
```

---

### Task 10: Notification Domain + NotificationService (TDD)

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/notifications/domain/NotificationType.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/notifications/domain/Notification.kt`
- Create: `backend/src/test/kotlin/com/taskowolf/notifications/NotificationServiceTest.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/notifications/application/NotificationService.kt`

- [ ] **Step 1: Create NotificationType enum**

Create `backend/src/main/kotlin/com/taskowolf/notifications/domain/NotificationType.kt`:
```kotlin
package com.taskowolf.notifications.domain

enum class NotificationType {
    MENTION,
    COMMENT,
    ASSIGNED,
    STATUS_CHANGED,
    SPRINT_STARTED,
    SPRINT_COMPLETED
}
```

- [ ] **Step 2: Create Notification entity**

Create `backend/src/main/kotlin/com/taskowolf/notifications/domain/Notification.kt`:
```kotlin
package com.taskowolf.notifications.domain

import com.taskowolf.core.domain.AuditableEntity
import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "notifications")
class Notification(
    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val type: NotificationType,

    @Column(nullable = false, length = 255)
    val title: String,

    @Column(columnDefinition = "TEXT")
    val body: String? = null,

    @Column(length = 500)
    val link: String? = null,

    @Column(nullable = false)
    var read: Boolean = false
) : AuditableEntity()
```

- [ ] **Step 3: Write failing tests**

Create `backend/src/test/kotlin/com/taskowolf/notifications/NotificationServiceTest.kt`:
```kotlin
package com.taskowolf.notifications

import com.taskowolf.auth.domain.User
import com.taskowolf.auth.infrastructure.UserRepository
import com.taskowolf.comments.domain.Comment
import com.taskowolf.comments.domain.events.CommentCreatedEvent
import com.taskowolf.comments.domain.events.MentionEvent
import com.taskowolf.issues.domain.Issue
import com.taskowolf.issues.domain.events.IssueFieldChangedEvent
import com.taskowolf.issues.domain.events.IssueStatusChangedEvent
import com.taskowolf.notifications.application.NotificationService
import com.taskowolf.notifications.domain.Notification
import com.taskowolf.notifications.domain.NotificationType
import com.taskowolf.notifications.infrastructure.NotificationRepository
import com.taskowolf.projects.infrastructure.ProjectMemberRepository
import io.mockk.*
import org.junit.jupiter.api.Test
import org.springframework.messaging.simp.SimpMessagingTemplate
import java.util.UUID

class NotificationServiceTest {

    private val notificationRepository = mockk<NotificationRepository>(relaxed = true)
    private val messagingTemplate = mockk<SimpMessagingTemplate>(relaxed = true)
    private val userRepository = mockk<UserRepository>()
    private val projectMemberRepository = mockk<ProjectMemberRepository>()

    private val service = NotificationService(
        notificationRepository, messagingTemplate, userRepository, projectMemberRepository
    )

    private fun user(id: UUID = UUID.randomUUID(), email: String = "u@test.com"): User =
        mockk { every { this@mockk.id } returns id; every { this@mockk.email } returns email }

    private fun issue(id: UUID = UUID.randomUUID(), key: String = "WOLF-1"): Issue =
        mockk { every { this@mockk.id } returns id; every { this@mockk.issueKey } returns key }

    @Test
    fun `onMentionEvent saves MENTION notification and pushes via WebSocket`() {
        val userId = UUID.randomUUID()
        val mentioned = user(userId)
        val iss = issue()
        val comment = mockk<Comment> { every { id } returns UUID.randomUUID() }

        service.onMentionEvent(MentionEvent(mentioned, comment, iss))

        val slot = slot<Notification>()
        verify { notificationRepository.save(capture(slot)) }
        assert(slot.captured.type == NotificationType.MENTION)
        assert(slot.captured.userId == userId)
        verify { messagingTemplate.convertAndSend(match<String> { it.contains(userId.toString()) }, any<Any>()) }
    }

    @Test
    fun `onIssueFieldChanged with assignee field saves ASSIGNED notification`() {
        val newAssigneeId = UUID.randomUUID()
        val newAssignee = user(newAssigneeId)
        val actor = user()
        val iss = issue()
        every { userRepository.findById(newAssigneeId) } returns java.util.Optional.of(newAssignee)
        val event = IssueFieldChangedEvent(iss, actor, "assignee", null, newAssigneeId.toString())

        service.onIssueFieldChanged(event)

        val slot = slot<Notification>()
        verify { notificationRepository.save(capture(slot)) }
        assert(slot.captured.type == NotificationType.ASSIGNED)
        assert(slot.captured.userId == newAssigneeId)
    }

    @Test
    fun `onIssueFieldChanged with non-assignee field is ignored`() {
        val actor = user()
        val iss = issue()
        val event = IssueFieldChangedEvent(iss, actor, "priority", "LOW", "HIGH")

        service.onIssueFieldChanged(event)

        verify(exactly = 0) { notificationRepository.save(any()) }
    }
}
```

- [ ] **Step 4: Run tests — expect compilation failure**

Run: `./gradlew :backend:test --tests "com.taskowolf.notifications.NotificationServiceTest"`  
Expected: Compilation error — `NotificationService` not found.

- [ ] **Step 5: Implement NotificationService**

Create `backend/src/main/kotlin/com/taskowolf/notifications/application/NotificationService.kt`:
```kotlin
package com.taskowolf.notifications.application

import com.taskowolf.auth.infrastructure.UserRepository
import com.taskowolf.comments.domain.events.CommentCreatedEvent
import com.taskowolf.comments.domain.events.MentionEvent
import com.taskowolf.issues.domain.events.IssueFieldChangedEvent
import com.taskowolf.issues.domain.events.IssueStatusChangedEvent
import com.taskowolf.notifications.domain.Notification
import com.taskowolf.notifications.domain.NotificationType
import com.taskowolf.notifications.infrastructure.NotificationRepository
import com.taskowolf.projects.infrastructure.ProjectMemberRepository
import com.taskowolf.sprints.domain.events.SprintCompletedEvent
import com.taskowolf.sprints.domain.events.SprintStartedEvent
import org.springframework.context.event.EventListener
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class NotificationService(
    private val notificationRepository: NotificationRepository,
    private val messagingTemplate: SimpMessagingTemplate,
    private val userRepository: UserRepository,
    private val projectMemberRepository: ProjectMemberRepository
) {

    @EventListener
    fun onMentionEvent(event: MentionEvent) {
        val n = push(Notification(
            userId = event.mentionedUser.id,
            type = NotificationType.MENTION,
            title = "@${event.mentionedUser.displayName} wurde erwähnt",
            body = event.comment.body.take(100),
            link = "/p/${event.issue.projectKey}/issues/${event.issue.issueKey}"
        ))
    }

    @EventListener
    fun onCommentCreated(event: CommentCreatedEvent) {
        val assigneeId = event.issue.assigneeId ?: return
        if (assigneeId == event.comment.authorId) return
        push(Notification(
            userId = assigneeId,
            type = NotificationType.COMMENT,
            title = "Neuer Kommentar auf ${event.issue.issueKey}",
            body = event.comment.body.take(100),
            link = "/p/${event.issue.projectKey}/issues/${event.issue.issueKey}"
        ))
    }

    @EventListener
    fun onIssueFieldChanged(event: IssueFieldChangedEvent) {
        if (event.field != "assignee") return
        val newAssigneeId = event.newValue?.let {
            runCatching { UUID.fromString(it) }.getOrNull()
        } ?: return
        val assignee = userRepository.findById(newAssigneeId).orElse(null) ?: return
        push(Notification(
            userId = newAssigneeId,
            type = NotificationType.ASSIGNED,
            title = "${event.issue.issueKey} dir zugewiesen",
            link = "/p/${event.issue.projectKey}/issues/${event.issue.issueKey}"
        ))
    }

    @EventListener
    fun onIssueStatusChanged(event: IssueStatusChangedEvent) {
        val reporterId = event.issue.reporterId ?: return
        val actorId = event.actor?.id
        if (reporterId == actorId) return
        push(Notification(
            userId = reporterId,
            type = NotificationType.STATUS_CHANGED,
            title = "${event.issue.issueKey} → ${event.newStatus.name}",
            link = "/p/${event.issue.projectKey}/issues/${event.issue.issueKey}"
        ))
    }

    @EventListener
    fun onSprintStarted(event: SprintStartedEvent) {
        val members = projectMemberRepository.findAllByProjectId(event.sprint.projectId)
        members.forEach { member ->
            push(Notification(
                userId = member.userId,
                type = NotificationType.SPRINT_STARTED,
                title = "Sprint '${event.sprint.name}' gestartet"
            ))
        }
    }

    @EventListener
    fun onSprintCompleted(event: SprintCompletedEvent) {
        val members = projectMemberRepository.findAllByProjectId(event.sprint.projectId)
        members.forEach { member ->
            push(Notification(
                userId = member.userId,
                type = NotificationType.SPRINT_COMPLETED,
                title = "Sprint '${event.sprint.name}' abgeschlossen"
            ))
        }
    }

    fun getForUser(userId: UUID, pageable: Pageable): Page<Notification> =
        notificationRepository.findAllByUserId(userId, pageable)

    fun getUnreadCount(userId: UUID): Long =
        notificationRepository.countByUserIdAndReadFalse(userId)

    @Transactional
    fun markRead(notificationId: UUID, userId: UUID) {
        val n = notificationRepository.findById(notificationId).orElse(null) ?: return
        if (n.userId != userId) return
        n.read = true
        notificationRepository.save(n)
    }

    @Transactional
    fun markAllRead(userId: UUID) {
        notificationRepository.markAllReadByUserId(userId)
    }

    private fun push(notification: Notification): Notification {
        val saved = notificationRepository.save(notification)
        val unreadCount = notificationRepository.countByUserIdAndReadFalse(saved.userId)
        messagingTemplate.convertAndSend(
            "/topic/notifications/${saved.userId}",
            mapOf("type" to "NOTIFICATION", "unreadCount" to unreadCount)
        )
        return saved
    }
}
```

Note: `SprintStartedEvent` and `SprintCompletedEvent` must exist in `com.taskowolf.sprints.domain.events`. If they don't exist yet, check the sprints module. If missing, create minimal data classes:
```kotlin
// com/taskowolf/sprints/domain/events/SprintStartedEvent.kt
data class SprintStartedEvent(val sprint: Sprint)
// com/taskowolf/sprints/domain/events/SprintCompletedEvent.kt
data class SprintCompletedEvent(val sprint: Sprint)
```
And ensure `Sprint` has `projectId: UUID` and `name: String` fields.

- [ ] **Step 6: Run tests — expect pass**

Run: `./gradlew :backend:test --tests "com.taskowolf.notifications.NotificationServiceTest"`  
Expected: 3 tests pass.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/notifications/
git add backend/src/test/kotlin/com/taskowolf/notifications/NotificationServiceTest.kt
git commit -m "feat: add Notification domain and NotificationService with WebSocket push"
```

---

### Task 11: NotificationController

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/notifications/api/dto/NotificationResponse.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/notifications/api/dto/UnreadCountResponse.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/notifications/api/NotificationController.kt`

- [ ] **Step 1: Create DTOs**

Create `backend/src/main/kotlin/com/taskowolf/notifications/api/dto/NotificationResponse.kt`:
```kotlin
package com.taskowolf.notifications.api.dto

import com.taskowolf.notifications.domain.Notification
import com.taskowolf.notifications.domain.NotificationType
import java.time.Instant
import java.util.UUID

data class NotificationResponse(
    val id: UUID,
    val type: NotificationType,
    val title: String,
    val body: String?,
    val link: String?,
    val read: Boolean,
    val createdAt: Instant
) {
    companion object {
        fun from(n: Notification) = NotificationResponse(
            id = n.id, type = n.type, title = n.title, body = n.body,
            link = n.link, read = n.read, createdAt = n.createdAt
        )
    }
}
```

Create `backend/src/main/kotlin/com/taskowolf/notifications/api/dto/UnreadCountResponse.kt`:
```kotlin
package com.taskowolf.notifications.api.dto

data class UnreadCountResponse(val count: Long)
```

- [ ] **Step 2: Create NotificationController**

Create `backend/src/main/kotlin/com/taskowolf/notifications/api/NotificationController.kt`:
```kotlin
package com.taskowolf.notifications.api

import com.taskowolf.auth.infrastructure.UserRepository
import com.taskowolf.core.exceptions.NotFoundException
import com.taskowolf.notifications.api.dto.NotificationResponse
import com.taskowolf.notifications.api.dto.UnreadCountResponse
import com.taskowolf.notifications.application.NotificationService
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(
    private val notificationService: NotificationService,
    private val userRepository: UserRepository
) {

    @GetMapping
    fun getNotifications(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @AuthenticationPrincipal principal: UserDetails
    ): ResponseEntity<*> {
        val user = userRepository.findByEmail(principal.username)
            ?: throw NotFoundException("User not found")
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        return ResponseEntity.ok(
            notificationService.getForUser(user.id, pageable).map { NotificationResponse.from(it) }
        )
    }

    @GetMapping("/unread-count")
    fun getUnreadCount(@AuthenticationPrincipal principal: UserDetails): ResponseEntity<UnreadCountResponse> {
        val user = userRepository.findByEmail(principal.username)
            ?: throw NotFoundException("User not found")
        return ResponseEntity.ok(UnreadCountResponse(notificationService.getUnreadCount(user.id)))
    }

    @PatchMapping("/{id}/read")
    fun markRead(
        @PathVariable id: UUID,
        @AuthenticationPrincipal principal: UserDetails
    ): ResponseEntity<Void> {
        val user = userRepository.findByEmail(principal.username)
            ?: throw NotFoundException("User not found")
        notificationService.markRead(id, user.id)
        return ResponseEntity.noContent().build()
    }

    @PatchMapping("/read-all")
    fun markAllRead(@AuthenticationPrincipal principal: UserDetails): ResponseEntity<Void> {
        val user = userRepository.findByEmail(principal.username)
            ?: throw NotFoundException("User not found")
        notificationService.markAllRead(user.id)
        return ResponseEntity.noContent().build()
    }
}
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :backend:compileKotlin`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/notifications/api/
git commit -m "feat: add NotificationController"
```

---

### Task 12: StorageService (TDD)

**Files:**
- Create: `backend/src/test/kotlin/com/taskowolf/attachments/StorageServiceTest.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/attachments/application/StorageService.kt`

- [ ] **Step 1: Write failing tests**

Create `backend/src/test/kotlin/com/taskowolf/attachments/StorageServiceTest.kt`:
```kotlin
package com.taskowolf.attachments

import com.taskowolf.attachments.application.StorageService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.util.UUID

class StorageServiceTest {

    @Test
    fun `save writes file and sets 0644 permissions`(@TempDir tempDir: Path) {
        val service = StorageService(tempDir.toString())
        val issueId = UUID.randomUUID()
        val content = "test content".toByteArray()
        val storedName = UUID.randomUUID().toString()

        service.save(issueId, storedName, content)

        val path = tempDir.resolve(issueId.toString()).resolve(storedName)
        assert(Files.exists(path))
        assert(Files.readAllBytes(path).contentEquals(content))

        val perms = Files.getPosixFilePermissions(path)
        assert(PosixFilePermission.OWNER_READ in perms)
        assert(PosixFilePermission.OWNER_WRITE in perms)
        assert(PosixFilePermission.GROUP_READ in perms)
        assert(PosixFilePermission.OTHERS_READ in perms)
        assert(PosixFilePermission.OWNER_EXECUTE !in perms)
        assert(PosixFilePermission.GROUP_EXECUTE !in perms)
        assert(PosixFilePermission.OTHERS_EXECUTE !in perms)
    }

    @Test
    fun `delete removes file`(@TempDir tempDir: Path) {
        val service = StorageService(tempDir.toString())
        val issueId = UUID.randomUUID()
        val storedName = UUID.randomUUID().toString()
        service.save(issueId, storedName, "data".toByteArray())

        service.delete(issueId, storedName)

        val path = tempDir.resolve(issueId.toString()).resolve(storedName)
        assert(!Files.exists(path))
    }

    @Test
    fun `getPath returns correct resolved path`(@TempDir tempDir: Path) {
        val service = StorageService(tempDir.toString())
        val issueId = UUID.randomUUID()
        val storedName = "file.txt"

        val path = service.getPath(issueId, storedName)

        assert(path == tempDir.resolve(issueId.toString()).resolve(storedName))
    }
}
```

- [ ] **Step 2: Run tests — expect compilation failure**

Run: `./gradlew :backend:test --tests "com.taskowolf.attachments.StorageServiceTest"`  
Expected: Compilation error — `StorageService` not found.

- [ ] **Step 3: Implement StorageService**

Create `backend/src/main/kotlin/com/taskowolf/attachments/application/StorageService.kt`:
```kotlin
package com.taskowolf.attachments.application

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission
import java.util.UUID

@Service
class StorageService(
    @Value("\${taskowolf.attachment.path:./data/attachments}")
    private val basePath: String
) {

    private val PERMISSIONS = setOf(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE,
        PosixFilePermission.GROUP_READ,
        PosixFilePermission.OTHERS_READ
    )

    fun save(issueId: UUID, storedName: String, bytes: ByteArray) {
        val dir = Paths.get(basePath, issueId.toString())
        Files.createDirectories(dir)
        val file = dir.resolve(storedName)
        Files.write(file, bytes)
        runCatching { Files.setPosixFilePermissions(file, PERMISSIONS) }
    }

    fun delete(issueId: UUID, storedName: String) {
        Files.deleteIfExists(getPath(issueId, storedName))
    }

    fun getPath(issueId: UUID, storedName: String): Path =
        Paths.get(basePath, issueId.toString(), storedName)
}
```

Note: `setPosixFilePermissions` is wrapped in `runCatching` so it silently skips on Windows (which doesn't support POSIX permissions). On Linux/Mac in production the permissions are set correctly.

- [ ] **Step 4: Run tests — expect pass**

Run: `./gradlew :backend:test --tests "com.taskowolf.attachments.StorageServiceTest"`  
Expected: 3 tests pass (permission assertions may be skipped on Windows — that's acceptable).

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/kotlin/com/taskowolf/attachments/StorageServiceTest.kt
git add backend/src/main/kotlin/com/taskowolf/attachments/application/StorageService.kt
git commit -m "feat: add StorageService with 0644 file permissions"
```

---

### Task 13: AttachmentService (TDD)

**Files:**
- Create: `backend/src/test/kotlin/com/taskowolf/attachments/AttachmentServiceTest.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/attachments/application/AttachmentService.kt`

- [ ] **Step 1: Write failing tests**

Create `backend/src/test/kotlin/com/taskowolf/attachments/AttachmentServiceTest.kt`:
```kotlin
package com.taskowolf.attachments

import com.taskowolf.attachments.application.AttachmentService
import com.taskowolf.attachments.application.StorageService
import com.taskowolf.attachments.domain.Attachment
import com.taskowolf.attachments.domain.events.AttachmentAddedEvent
import com.taskowolf.attachments.infrastructure.AttachmentRepository
import com.taskowolf.core.application.DomainEventPublisher
import com.taskowolf.core.exceptions.ForbiddenException
import com.taskowolf.issues.domain.Issue
import com.taskowolf.issues.infrastructure.IssueRepository
import com.taskowolf.projects.application.ProjectService
import io.mockk.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.multipart.MultipartFile
import java.util.Optional
import java.util.UUID

class AttachmentServiceTest {

    private val attachmentRepository = mockk<AttachmentRepository>(relaxed = true)
    private val issueRepository = mockk<IssueRepository>()
    private val storageService = mockk<StorageService>(relaxed = true)
    private val eventPublisher = mockk<DomainEventPublisher>(relaxed = true)
    private val projectService = mockk<ProjectService>(relaxed = true)

    private val service = AttachmentService(
        attachmentRepository, issueRepository, storageService, eventPublisher, projectService,
        maxSizeBytes = 26214400L
    )

    @Test
    fun `upload saves attachment and publishes AttachmentAddedEvent`() {
        val issueId = UUID.randomUUID()
        val uploaderId = UUID.randomUUID()
        val issue = mockk<Issue> { every { id } returns issueId }
        every { issueRepository.findById(issueId) } returns Optional.of(issue)
        every { attachmentRepository.save(any()) } answers { firstArg() }

        val file = mockk<MultipartFile> {
            every { originalFilename } returns "test.png"
            every { contentType } returns "image/png"
            every { size } returns 1024L
            every { bytes } returns ByteArray(1024)
        }

        service.upload(issueId, file, uploaderId)

        verify { storageService.save(issueId, any(), any()) }
        verify { attachmentRepository.save(any()) }
        verify { eventPublisher.publish(ofType(AttachmentAddedEvent::class)) }
    }

    @Test
    fun `upload throws IllegalArgumentException when file exceeds max size`() {
        val issueId = UUID.randomUUID()
        val issue = mockk<Issue> { every { id } returns issueId }
        every { issueRepository.findById(issueId) } returns Optional.of(issue)

        val file = mockk<MultipartFile> {
            every { originalFilename } returns "huge.zip"
            every { size } returns 30_000_000L
        }

        assertThrows<IllegalArgumentException> {
            service.upload(issueId, file, UUID.randomUUID())
        }
    }

    @Test
    fun `delete removes file and publishes AttachmentRemovedEvent when caller is uploader`() {
        val uploaderId = UUID.randomUUID()
        val issueId = UUID.randomUUID()
        val attachmentId = UUID.randomUUID()
        val issue = mockk<Issue> { every { id } returns issueId }
        val attachment = mockk<Attachment> {
            every { id } returns attachmentId
            every { this@mockk.uploaderId } returns uploaderId
            every { this@mockk.issueId } returns issueId
            every { storedName } returns "stored.png"
        }
        every { attachmentRepository.findById(attachmentId) } returns Optional.of(attachment)
        every { issueRepository.findById(issueId) } returns Optional.of(issue)
        every { projectService.isProjectAdmin(issueId, uploaderId) } returns false

        service.delete(attachmentId, uploaderId)

        verify { storageService.delete(issueId, "stored.png") }
        verify { attachmentRepository.delete(attachment) }
    }

    @Test
    fun `delete throws ForbiddenException when caller is neither uploader nor admin`() {
        val attachmentId = UUID.randomUUID()
        val attachment = mockk<Attachment> {
            every { id } returns attachmentId
            every { uploaderId } returns UUID.randomUUID()
            every { issueId } returns UUID.randomUUID()
        }
        every { attachmentRepository.findById(attachmentId) } returns Optional.of(attachment)
        every { projectService.isProjectAdmin(any(), any()) } returns false

        assertThrows<ForbiddenException> {
            service.delete(attachmentId, UUID.randomUUID())
        }
    }
}
```

- [ ] **Step 2: Run tests — expect compilation failure**

Run: `./gradlew :backend:test --tests "com.taskowolf.attachments.AttachmentServiceTest"`  
Expected: Compilation error — `AttachmentService` not found.

- [ ] **Step 3: Implement AttachmentService**

Create `backend/src/main/kotlin/com/taskowolf/attachments/application/AttachmentService.kt`:
```kotlin
package com.taskowolf.attachments.application

import com.taskowolf.attachments.domain.Attachment
import com.taskowolf.attachments.domain.events.AttachmentAddedEvent
import com.taskowolf.attachments.domain.events.AttachmentRemovedEvent
import com.taskowolf.attachments.infrastructure.AttachmentRepository
import com.taskowolf.core.application.DomainEventPublisher
import com.taskowolf.core.exceptions.ForbiddenException
import com.taskowolf.core.exceptions.NotFoundException
import com.taskowolf.issues.infrastructure.IssueRepository
import com.taskowolf.projects.application.ProjectService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Path
import java.util.UUID

@Service
class AttachmentService(
    private val attachmentRepository: AttachmentRepository,
    private val issueRepository: IssueRepository,
    private val storageService: StorageService,
    private val eventPublisher: DomainEventPublisher,
    private val projectService: ProjectService,
    @Value("\${taskowolf.attachment.max-size:26214400}")
    private val maxSizeBytes: Long
) {

    @Transactional
    fun upload(issueId: UUID, file: MultipartFile, uploaderId: UUID): Attachment {
        if (file.size > maxSizeBytes) {
            throw IllegalArgumentException("File size ${file.size} exceeds maximum $maxSizeBytes bytes")
        }
        val issue = issueRepository.findById(issueId)
            .orElseThrow { NotFoundException("Issue not found") }

        val storedName = UUID.randomUUID().toString()
        storageService.save(issueId, storedName, file.bytes)

        val attachment = attachmentRepository.save(Attachment(
            issueId = issueId,
            uploaderId = uploaderId,
            filename = file.originalFilename ?: "file",
            storedName = storedName,
            contentType = file.contentType ?: "application/octet-stream",
            size = file.size
        ))

        eventPublisher.publish(AttachmentAddedEvent(attachment, issue))
        return attachment
    }

    @Transactional
    fun delete(attachmentId: UUID, callerId: UUID) {
        val attachment = attachmentRepository.findById(attachmentId)
            .orElseThrow { NotFoundException("Attachment not found") }
        val isAdmin = projectService.isProjectAdmin(attachment.issueId, callerId)
        if (attachment.uploaderId != callerId && !isAdmin) {
            throw ForbiddenException("Not authorized to delete this attachment")
        }
        val issue = issueRepository.findById(attachment.issueId)
            .orElseThrow { NotFoundException("Issue not found") }
        storageService.delete(attachment.issueId, attachment.storedName)
        attachmentRepository.delete(attachment)
        eventPublisher.publish(AttachmentRemovedEvent(attachment, issue))
    }

    fun getDownloadPath(attachmentId: UUID): Pair<Attachment, Path> {
        val attachment = attachmentRepository.findById(attachmentId)
            .orElseThrow { NotFoundException("Attachment not found") }
        return attachment to storageService.getPath(attachment.issueId, attachment.storedName)
    }

    fun findAllByIssueId(issueId: UUID): List<Attachment> =
        attachmentRepository.findAllByIssueId(issueId)
}
```

- [ ] **Step 4: Run tests — expect pass**

Run: `./gradlew :backend:test --tests "com.taskowolf.attachments.AttachmentServiceTest"`  
Expected: 4 tests pass.

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/kotlin/com/taskowolf/attachments/AttachmentServiceTest.kt
git add backend/src/main/kotlin/com/taskowolf/attachments/application/AttachmentService.kt
git commit -m "feat: add AttachmentService with upload, delete, download"
```

---

### Task 14: AttachmentController

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/attachments/api/dto/AttachmentResponse.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/attachments/api/AttachmentController.kt`

- [ ] **Step 1: Create AttachmentResponse**

Create `backend/src/main/kotlin/com/taskowolf/attachments/api/dto/AttachmentResponse.kt`:
```kotlin
package com.taskowolf.attachments.api.dto

import com.taskowolf.attachments.domain.Attachment
import java.time.Instant
import java.util.UUID

data class AttachmentResponse(
    val id: UUID,
    val filename: String,
    val contentType: String,
    val size: Long,
    val uploaderId: UUID,
    val createdAt: Instant
) {
    companion object {
        fun from(a: Attachment) = AttachmentResponse(
            id = a.id, filename = a.filename, contentType = a.contentType,
            size = a.size, uploaderId = a.uploaderId, createdAt = a.createdAt
        )
    }
}
```

- [ ] **Step 2: Create AttachmentController**

Create `backend/src/main/kotlin/com/taskowolf/attachments/api/AttachmentController.kt`:
```kotlin
package com.taskowolf.attachments.api

import com.taskowolf.attachments.api.dto.AttachmentResponse
import com.taskowolf.attachments.application.AttachmentService
import com.taskowolf.auth.infrastructure.UserRepository
import com.taskowolf.core.exceptions.NotFoundException
import org.springframework.core.io.PathResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@RequestMapping("/api/v1/projects/{key}/issues/{issueId}/attachments")
class AttachmentController(
    private val attachmentService: AttachmentService,
    private val userRepository: UserRepository
) {

    @PostMapping(consumes = ["multipart/form-data"])
    fun upload(
        @PathVariable key: String,
        @PathVariable issueId: UUID,
        @RequestParam("file") file: MultipartFile,
        @AuthenticationPrincipal principal: UserDetails
    ): ResponseEntity<AttachmentResponse> {
        val uploader = userRepository.findByEmail(principal.username)
            ?: throw NotFoundException("User not found")
        val attachment = attachmentService.upload(issueId, file, uploader.id)
        return ResponseEntity.status(201).body(AttachmentResponse.from(attachment))
    }

    @GetMapping("/{attachmentId}/download")
    fun download(
        @PathVariable key: String,
        @PathVariable issueId: UUID,
        @PathVariable attachmentId: UUID
    ): ResponseEntity<PathResource> {
        val (attachment, path) = attachmentService.getDownloadPath(attachmentId)
        val resource = PathResource(path)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${attachment.filename}\"")
            .contentType(MediaType.parseMediaType(attachment.contentType))
            .body(resource)
    }

    @DeleteMapping("/{attachmentId}")
    fun delete(
        @PathVariable key: String,
        @PathVariable issueId: UUID,
        @PathVariable attachmentId: UUID,
        @AuthenticationPrincipal principal: UserDetails
    ): ResponseEntity<Void> {
        val caller = userRepository.findByEmail(principal.username)
            ?: throw NotFoundException("User not found")
        attachmentService.delete(attachmentId, caller.id)
        return ResponseEntity.noContent().build()
    }
}
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :backend:compileKotlin`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/attachments/api/
git commit -m "feat: add AttachmentController (upload, download, delete)"
```

---

### Task 15: EmailService

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/notifications/application/EmailService.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/notifications/application/NotificationService.kt`

- [ ] **Step 1: Create EmailService**

Create `backend/src/main/kotlin/com/taskowolf/notifications/application/EmailService.kt`:
```kotlin
package com.taskowolf.notifications.application

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service

@Service
class EmailService(
    @Autowired(required = false)
    private val mailSender: JavaMailSender?,
    @Value("\${taskowolf.smtp.from:TaskWolf <noreply@example.com>}")
    private val fromAddress: String
) {
    private val log = LoggerFactory.getLogger(EmailService::class.java)

    fun sendNotification(to: String, subject: String, text: String) {
        if (mailSender == null) return
        try {
            val msg = mailSender.createMimeMessage()
            MimeMessageHelper(msg, false, "UTF-8").apply {
                setFrom(fromAddress)
                setTo(to)
                setSubject(subject)
                setText(text, false)
            }
            mailSender.send(msg)
        } catch (e: Exception) {
            log.warn("Failed to send email to $to: ${e.message}")
        }
    }
}
```

- [ ] **Step 2: Inject EmailService into NotificationService and send on MENTION and ASSIGNED**

Open `backend/src/main/kotlin/com/taskowolf/notifications/application/NotificationService.kt`. Add `emailService: EmailService` to the constructor:
```kotlin
@Service
class NotificationService(
    private val notificationRepository: NotificationRepository,
    private val messagingTemplate: SimpMessagingTemplate,
    private val userRepository: UserRepository,
    private val projectMemberRepository: ProjectMemberRepository,
    private val emailService: EmailService
)
```

In `onMentionEvent`, after `push(...)`, add:
```kotlin
emailService.sendNotification(
    to = event.mentionedUser.email,
    subject = "Du wurdest in ${event.issue.issueKey} erwähnt",
    text = "Kommentar: ${event.comment.body.take(200)}\n\n/p/${event.issue.projectKey}/issues/${event.issue.issueKey}"
)
```

In `onIssueFieldChanged` (assignee branch), after `push(...)`, add:
```kotlin
emailService.sendNotification(
    to = assignee.email,
    subject = "${event.issue.issueKey} wurde dir zugewiesen",
    text = "Du hast das Issue ${event.issue.issueKey} zugewiesen bekommen."
)
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :backend:compileKotlin`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/notifications/application/EmailService.kt
git add backend/src/main/kotlin/com/taskowolf/notifications/application/NotificationService.kt
git commit -m "feat: add EmailService with optional SMTP and email on mention/assign"
```

---

### Task 16: Update IssueService — publish IssueFieldChangedEvent per changed field

**Files:**
- Modify: `backend/src/main/kotlin/com/taskowolf/issues/application/IssueService.kt`

- [ ] **Step 1: Capture old values and publish IssueFieldChangedEvent in update()**

Open `backend/src/main/kotlin/com/taskowolf/issues/application/IssueService.kt`.

In `update()`, before the existing field assignments, add actor resolution and old-value capture. Then publish one `IssueFieldChangedEvent` per changed field. Also update the `IssueStatusChangedEvent` call to pass `actor`.

Find the `update()` method and replace its body with:
```kotlin
fun update(issueId: UUID, request: UpdateIssueRequest, callerEmail: String): Issue {
    val caller = userRepository.findByEmail(callerEmail)
        ?: throw NotFoundException("User not found")
    val issue = issueRepository.findById(issueId)
        .orElseThrow { NotFoundException("Issue not found") }

    // Capture old values for field change events
    val oldTitle = issue.title
    val oldDescription = issue.description
    val oldPriority = issue.priority
    val oldStoryPoints = issue.storyPoints
    val oldAssigneeId = issue.assigneeId
    val oldStatusId = issue.statusId

    // Apply updates
    request.title?.let { issue.title = it }
    request.description?.let { issue.description = it }
    request.priority?.let { issue.priority = it }
    request.storyPoints?.let { issue.storyPoints = it }
    request.assigneeId?.let { issue.assigneeId = it }

    val statusChanged = request.statusId != null && request.statusId != oldStatusId
    if (statusChanged) {
        val oldStatus = workflowService.getStatus(oldStatusId)
        val newStatus = workflowService.getStatus(request.statusId!!)
        issue.statusId = request.statusId
        eventPublisher.publish(IssueStatusChangedEvent(issue, oldStatus, newStatus, actor = caller))
    }

    val saved = issueRepository.save(issue)

    // Publish field change events
    if (request.title != null && request.title != oldTitle)
        eventPublisher.publish(IssueFieldChangedEvent(saved, caller, "title", oldTitle, request.title))
    if (request.description != null && request.description != oldDescription)
        eventPublisher.publish(IssueFieldChangedEvent(saved, caller, "description", oldDescription, request.description))
    if (request.priority != null && request.priority != oldPriority)
        eventPublisher.publish(IssueFieldChangedEvent(saved, caller, "priority", oldPriority?.name, request.priority.name))
    if (request.storyPoints != null && request.storyPoints != oldStoryPoints)
        eventPublisher.publish(IssueFieldChangedEvent(saved, caller, "storyPoints", oldStoryPoints?.toString(), request.storyPoints.toString()))
    if (request.assigneeId != null && request.assigneeId != oldAssigneeId)
        eventPublisher.publish(IssueFieldChangedEvent(saved, caller, "assignee", oldAssigneeId?.toString(), request.assigneeId.toString()))

    return saved
}
```

Add the missing import at the top of the file:
```kotlin
import com.taskowolf.issues.domain.events.IssueFieldChangedEvent
```

- [ ] **Step 2: Check IssueService constructor for userRepository**

The existing `IssueService` already injects `userRepository` (confirmed during research). If for any reason it's missing, add it to the constructor:
```kotlin
private val userRepository: com.taskowolf.auth.infrastructure.UserRepository
```

- [ ] **Step 3: Verify compilation and tests**

Run: `./gradlew :backend:test`  
Expected: All existing tests pass (no regressions). BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/issues/application/IssueService.kt
git commit -m "feat: publish IssueFieldChangedEvent per changed field in IssueService.update()"
```

---

### Task 17: Collaboration Integration Test

**Files:**
- Create: `backend/src/test/kotlin/com/taskowolf/comments/CollaborationIntegrationTest.kt`

- [ ] **Step 1: Write integration test**

Create `backend/src/test/kotlin/com/taskowolf/comments/CollaborationIntegrationTest.kt`:
```kotlin
package com.taskowolf.comments

import com.taskowolf.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.util.UUID

class CollaborationIntegrationTest : IntegrationTestBase() {

    @Autowired
    lateinit var mvc: MockMvc

    @Test
    fun `POST comment then GET activity feed returns COMMENT entry`() {
        // This test requires a seeded project + issue in the DB.
        // Use @Sql or a setup method to insert test data.
        // For brevity, check the project key and issue ID from your test seed data.
        // Replace "TESTPROJECT" and the UUID below with actual seeded values.
        val projectKey = "TEST"
        val issueId = UUID.randomUUID() // replace with seeded issue ID

        // POST comment
        mvc.perform(
            post("/api/v1/projects/$projectKey/issues/$issueId/comments")
                .with(jwt().jwt { it.subject("test@example.com") })
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"body":"Integration test comment"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.body").value("Integration test comment"))

        // GET activity feed
        mvc.perform(
            get("/api/v1/projects/$projectKey/issues/$issueId/activity")
                .with(jwt().jwt { it.subject("test@example.com") })
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].type").value("COMMENT"))
    }

    @Test
    fun `GET unread-count returns numeric value`() {
        mvc.perform(
            get("/api/v1/notifications/unread-count")
                .with(jwt().jwt { it.subject("test@example.com") })
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.count").isNumber)
    }
}
```

Note: `IntegrationTestBase` is in `backend/src/test/kotlin/com/taskowolf/IntegrationTestBase.kt` and provides the Testcontainers PostgreSQL setup. The comment + activity test requires seeded data — adapt the project key and issue ID to match your test data setup (via `@Sql` annotation or a `@BeforeEach` setup using repositories).

- [ ] **Step 2: Run integration test**

Run: `./gradlew :backend:test --tests "com.taskowolf.comments.CollaborationIntegrationTest"`  
Expected: Tests pass (or document any seed-data gaps and fix the `@Sql` setup).

- [ ] **Step 3: Run full backend test suite**

Run: `./gradlew :backend:test`  
Expected: All tests pass. BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add backend/src/test/kotlin/com/taskowolf/comments/CollaborationIntegrationTest.kt
git commit -m "test: add CollaborationIntegrationTest for comment + activity feed + notification count"
```

---

### Task 18: Frontend — Types + API Modules

**Files:**
- Modify: `frontend/src/types/index.ts`
- Create: `frontend/src/api/comments.ts`
- Create: `frontend/src/api/notifications.ts`
- Create: `frontend/src/api/attachments.ts`
- Modify: `frontend/src/api/projects.ts` (add `getMembers`)

- [ ] **Step 1: Add new types to types/index.ts**

Open `frontend/src/types/index.ts`. Append at the end:
```typescript
export interface Comment {
  id: string
  body: string | null
  authorId: string
  createdAt: string
  editedAt: string | null
  deleted: boolean
}

export type ActivityType =
  | 'COMMENT'
  | 'STATUS_CHANGED'
  | 'ASSIGNED'
  | 'UNASSIGNED'
  | 'PRIORITY_CHANGED'
  | 'TITLE_CHANGED'
  | 'DESCRIPTION_CHANGED'
  | 'STORY_POINTS_CHANGED'
  | 'DUE_DATE_CHANGED'
  | 'SPRINT_CHANGED'
  | 'ATTACHMENT_ADDED'
  | 'ATTACHMENT_REMOVED'

export interface ActivityEntry {
  id: string
  type: ActivityType
  actorId: string
  createdAt: string
  oldValue: string | null
  newValue: string | null
  comment: Comment | null
}

export type NotificationType =
  | 'MENTION'
  | 'COMMENT'
  | 'ASSIGNED'
  | 'STATUS_CHANGED'
  | 'SPRINT_STARTED'
  | 'SPRINT_COMPLETED'

export interface Notification {
  id: string
  type: NotificationType
  title: string
  body: string | null
  link: string | null
  read: boolean
  createdAt: string
}

export interface Attachment {
  id: string
  filename: string
  contentType: string
  size: number
  uploaderId: string
  createdAt: string
}

export interface PageResponse<T> {
  content: T[]
  totalPages: number
  totalElements: number
  number: number
  size: number
}
```

- [ ] **Step 2: Create comments.ts API module**

Create `frontend/src/api/comments.ts`:
```typescript
import apiClient from './client'
import type { ActivityEntry, Comment, PageResponse } from '../types'

export const getActivityFeed = (
  projectKey: string,
  issueId: string,
  page = 0,
  size = 50
): Promise<PageResponse<ActivityEntry>> =>
  apiClient
    .get(`/projects/${projectKey}/issues/${issueId}/activity`, { params: { page, size } })
    .then(r => r.data)

export const createComment = (
  projectKey: string,
  issueId: string,
  body: string
): Promise<Comment> =>
  apiClient
    .post(`/projects/${projectKey}/issues/${issueId}/comments`, { body })
    .then(r => r.data)

export const editComment = (
  projectKey: string,
  issueId: string,
  commentId: string,
  body: string
): Promise<Comment> =>
  apiClient
    .patch(`/projects/${projectKey}/issues/${issueId}/comments/${commentId}`, { body })
    .then(r => r.data)

export const deleteComment = (
  projectKey: string,
  issueId: string,
  commentId: string
): Promise<void> =>
  apiClient
    .delete(`/projects/${projectKey}/issues/${issueId}/comments/${commentId}`)
    .then(() => undefined)
```

- [ ] **Step 3: Create notifications.ts API module**

Create `frontend/src/api/notifications.ts`:
```typescript
import apiClient from './client'
import type { Notification, PageResponse } from '../types'

export const getNotifications = (page = 0, size = 20): Promise<PageResponse<Notification>> =>
  apiClient.get('/notifications', { params: { page, size } }).then(r => r.data)

export const getUnreadCount = (): Promise<{ count: number }> =>
  apiClient.get('/notifications/unread-count').then(r => r.data)

export const markRead = (id: string): Promise<void> =>
  apiClient.patch(`/notifications/${id}/read`).then(() => undefined)

export const markAllRead = (): Promise<void> =>
  apiClient.patch('/notifications/read-all').then(() => undefined)
```

- [ ] **Step 4: Create attachments.ts API module**

Create `frontend/src/api/attachments.ts`:
```typescript
import apiClient from './client'
import type { Attachment } from '../types'

export const uploadAttachment = (
  projectKey: string,
  issueId: string,
  file: File
): Promise<Attachment> => {
  const form = new FormData()
  form.append('file', file)
  return apiClient
    .post(`/projects/${projectKey}/issues/${issueId}/attachments`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    .then(r => r.data)
}

export const getDownloadUrl = (
  projectKey: string,
  issueId: string,
  attachmentId: string
): string =>
  `/api/v1/projects/${projectKey}/issues/${issueId}/attachments/${attachmentId}/download`

export const deleteAttachment = (
  projectKey: string,
  issueId: string,
  attachmentId: string
): Promise<void> =>
  apiClient
    .delete(`/projects/${projectKey}/issues/${issueId}/attachments/${attachmentId}`)
    .then(() => undefined)
```

- [ ] **Step 5: Add getMembers to projects.ts**

Open `frontend/src/api/projects.ts`. Add:
```typescript
export const getProjectMembers = (projectKey: string): Promise<User[]> =>
  apiClient.get(`/projects/${projectKey}/members`).then(r => r.data)
```

Import `User` from `'../types'` if not already imported.

- [ ] **Step 6: Verify TypeScript compilation**

Run: `cd frontend && npx tsc --noEmit`  
Expected: No errors.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/types/index.ts frontend/src/api/
git commit -m "feat: add Comment, Notification, Attachment types and API modules"
```

---

### Task 19: Frontend Hooks

**Files:**
- Create: `frontend/src/hooks/useActivity.ts`
- Create: `frontend/src/hooks/useComments.ts`
- Create: `frontend/src/hooks/useNotifications.ts`
- Create: `frontend/src/hooks/useAttachments.ts`
- Create: `frontend/src/hooks/useCurrentUser.ts`

- [ ] **Step 1: Create useCurrentUser**

Create `frontend/src/hooks/useCurrentUser.ts`:
```typescript
import { useQuery } from '@tanstack/react-query'
import apiClient from '../api/client'
import type { User } from '../types'

const fetchCurrentUser = (): Promise<User> =>
  apiClient.get('/auth/me').then(r => r.data)

export function useCurrentUser() {
  return useQuery({ queryKey: ['currentUser'], queryFn: fetchCurrentUser, staleTime: Infinity })
}
```

- [ ] **Step 2: Create useActivity**

Create `frontend/src/hooks/useActivity.ts`:
```typescript
import { useQuery } from '@tanstack/react-query'
import { getActivityFeed } from '../api/comments'

export function useActivity(projectKey: string, issueId: string, page = 0) {
  return useQuery({
    queryKey: ['activity', projectKey, issueId, page],
    queryFn: () => getActivityFeed(projectKey, issueId, page),
    enabled: !!projectKey && !!issueId,
  })
}
```

- [ ] **Step 3: Create useComments**

Create `frontend/src/hooks/useComments.ts`:
```typescript
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { createComment, editComment, deleteComment } from '../api/comments'

export function useCreateComment(projectKey: string, issueId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: string) => createComment(projectKey, issueId, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['activity', projectKey, issueId] }),
  })
}

export function useEditComment(projectKey: string, issueId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ commentId, body }: { commentId: string; body: string }) =>
      editComment(projectKey, issueId, commentId, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['activity', projectKey, issueId] }),
  })
}

export function useDeleteComment(projectKey: string, issueId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (commentId: string) => deleteComment(projectKey, issueId, commentId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['activity', projectKey, issueId] }),
  })
}
```

- [ ] **Step 4: Create useNotifications**

Create `frontend/src/hooks/useNotifications.ts`:
```typescript
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getNotifications, getUnreadCount, markAllRead, markRead } from '../api/notifications'

export function useNotifications(page = 0) {
  return useQuery({
    queryKey: ['notifications', page],
    queryFn: () => getNotifications(page),
  })
}

export function useUnreadCount() {
  return useQuery({
    queryKey: ['notifications', 'unreadCount'],
    queryFn: getUnreadCount,
    refetchInterval: 60_000,
  })
}

export function useMarkRead() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: markRead,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['notifications'] })
    },
  })
}

export function useMarkAllRead() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: markAllRead,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['notifications'] })
    },
  })
}
```

- [ ] **Step 5: Create useAttachments**

Create `frontend/src/hooks/useAttachments.ts`:
```typescript
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { deleteAttachment, uploadAttachment } from '../api/attachments'

export function useUploadAttachment(projectKey: string, issueId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (file: File) => uploadAttachment(projectKey, issueId, file),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['issue', issueId] }),
  })
}

export function useDeleteAttachment(projectKey: string, issueId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (attachmentId: string) => deleteAttachment(projectKey, issueId, attachmentId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['issue', issueId] }),
  })
}
```

- [ ] **Step 6: Verify TypeScript compilation**

Run: `cd frontend && npx tsc --noEmit`  
Expected: No errors.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/hooks/useActivity.ts frontend/src/hooks/useComments.ts
git add frontend/src/hooks/useNotifications.ts frontend/src/hooks/useAttachments.ts
git add frontend/src/hooks/useCurrentUser.ts
git commit -m "feat: add useActivity, useComments, useNotifications, useAttachments, useCurrentUser hooks"
```

---

### Task 20: Activity Feed + Comment Editor Components

**Files:**
- Create: `frontend/src/components/activity/ActivityEntry.tsx`
- Create: `frontend/src/components/activity/CommentItem.tsx`
- Create: `frontend/src/components/activity/CommentEditor.tsx`
- Create: `frontend/src/components/activity/ActivityFeed.tsx`

- [ ] **Step 1: Create ActivityEntry**

Create `frontend/src/components/activity/ActivityEntry.tsx`:
```tsx
import type { ActivityEntry as ActivityEntryType } from '../../types'

const LABEL: Record<string, string> = {
  STATUS_CHANGED: 'Status geändert',
  PRIORITY_CHANGED: 'Priorität geändert',
  TITLE_CHANGED: 'Titel geändert',
  DESCRIPTION_CHANGED: 'Beschreibung geändert',
  STORY_POINTS_CHANGED: 'Story Points geändert',
  SPRINT_CHANGED: 'Sprint geändert',
  ASSIGNED: 'Zugewiesen',
  UNASSIGNED: 'Zuweisung entfernt',
  ATTACHMENT_ADDED: 'Datei hinzugefügt',
  ATTACHMENT_REMOVED: 'Datei entfernt',
}

interface Props {
  entry: ActivityEntryType
}

export function ActivityEntry({ entry }: Props) {
  const label = LABEL[entry.type] ?? entry.type
  return (
    <div className="flex items-start gap-2 text-sm text-muted-foreground py-1">
      <span className="mt-0.5 text-xs">●</span>
      <span>
        {label}
        {entry.oldValue && entry.newValue && (
          <>
            {' '}
            <span className="line-through opacity-60">{entry.oldValue}</span>
            {' → '}
            <span className="font-medium text-foreground">{entry.newValue}</span>
          </>
        )}
        {!entry.oldValue && entry.newValue && (
          <>
            {' '}
            <span className="font-medium text-foreground">{entry.newValue}</span>
          </>
        )}
      </span>
      <span className="ml-auto text-xs shrink-0">
        {new Date(entry.createdAt).toLocaleString('de-DE', { dateStyle: 'short', timeStyle: 'short' })}
      </span>
    </div>
  )
}
```

- [ ] **Step 2: Create CommentItem**

Create `frontend/src/components/activity/CommentItem.tsx`:
```tsx
import { useState } from 'react'
import type { ActivityEntry } from '../../types'
import { useDeleteComment, useEditComment } from '../../hooks/useComments'
import { useCurrentUser } from '../../hooks/useCurrentUser'

interface Props {
  entry: ActivityEntry
  projectKey: string
  issueId: string
}

export function CommentItem({ entry, projectKey, issueId }: Props) {
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState(entry.comment?.body ?? '')
  const { data: me } = useCurrentUser()
  const editMutation = useEditComment(projectKey, issueId)
  const deleteMutation = useDeleteComment(projectKey, issueId)

  const comment = entry.comment
  if (!comment) return null

  const isAuthor = me?.id === comment.authorId

  if (comment.deleted) {
    return (
      <div className="rounded bg-muted/30 px-3 py-2 text-sm text-muted-foreground italic">
        Kommentar gelöscht
      </div>
    )
  }

  const handleSave = () => {
    if (!comment) return
    editMutation.mutate({ commentId: comment.id, body: draft }, { onSuccess: () => setEditing(false) })
  }

  return (
    <div className="rounded bg-muted/20 px-3 py-2 text-sm">
      {editing ? (
        <div className="flex flex-col gap-2">
          <textarea
            className="w-full rounded border bg-background p-2 text-sm"
            rows={3}
            value={draft}
            onChange={e => setDraft(e.target.value)}
          />
          <div className="flex gap-2">
            <button className="text-xs text-primary" onClick={handleSave}>Speichern</button>
            <button className="text-xs text-muted-foreground" onClick={() => setEditing(false)}>Abbrechen</button>
          </div>
        </div>
      ) : (
        <>
          <p className="whitespace-pre-wrap">{comment.body}</p>
          <div className="mt-1 flex items-center gap-3 text-xs text-muted-foreground">
            <span>{new Date(entry.createdAt).toLocaleString('de-DE', { dateStyle: 'short', timeStyle: 'short' })}</span>
            {comment.editedAt && <span>(bearbeitet)</span>}
            {isAuthor && (
              <>
                <button className="hover:text-foreground" onClick={() => setEditing(true)}>Bearbeiten</button>
                <button
                  className="hover:text-destructive"
                  onClick={() => deleteMutation.mutate(comment.id)}
                >
                  Löschen
                </button>
              </>
            )}
          </div>
        </>
      )}
    </div>
  )
}
```

- [ ] **Step 3: Create CommentEditor**

Create `frontend/src/components/activity/CommentEditor.tsx`:
```tsx
import { useRef, useState } from 'react'
import type { User } from '../../types'
import { useCreateComment } from '../../hooks/useComments'

interface Props {
  projectKey: string
  issueId: string
  projectMembers: User[]
}

export function CommentEditor({ projectKey, issueId, projectMembers }: Props) {
  const [body, setBody] = useState('')
  const [mentionQuery, setMentionQuery] = useState<string | null>(null)
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const createMutation = useCreateComment(projectKey, issueId)

  const handleChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    const val = e.target.value
    setBody(val)
    const match = val.slice(0, e.target.selectionStart).match(/@(\w*)$/)
    setMentionQuery(match ? match[1] : null)
  }

  const insertMention = (displayName: string) => {
    const textarea = textareaRef.current
    if (!textarea) return
    const pos = textarea.selectionStart
    const before = body.slice(0, pos).replace(/@\w*$/, '')
    const after = body.slice(pos)
    setBody(`${before}@${displayName} ${after}`)
    setMentionQuery(null)
  }

  const handleSubmit = () => {
    if (!body.trim()) return
    createMutation.mutate(body, { onSuccess: () => setBody('') })
  }

  const suggestions = mentionQuery !== null
    ? projectMembers.filter(m =>
        m.displayName.toLowerCase().startsWith(mentionQuery.toLowerCase())
      ).slice(0, 5)
    : []

  return (
    <div className="relative flex flex-col gap-2">
      <textarea
        ref={textareaRef}
        className="w-full rounded border bg-background p-2 text-sm"
        rows={3}
        placeholder="Kommentar schreiben… (@mention für Personen)"
        value={body}
        onChange={handleChange}
      />
      {suggestions.length > 0 && (
        <div className="absolute bottom-full left-0 z-10 rounded border bg-popover shadow-md">
          {suggestions.map(u => (
            <button
              key={u.id}
              className="block w-full px-3 py-1.5 text-left text-sm hover:bg-accent"
              onMouseDown={e => { e.preventDefault(); insertMention(u.displayName) }}
            >
              @{u.displayName}
            </button>
          ))}
        </div>
      )}
      <div className="flex justify-end">
        <button
          className="rounded bg-primary px-3 py-1 text-sm text-primary-foreground disabled:opacity-50"
          disabled={!body.trim() || createMutation.isPending}
          onClick={handleSubmit}
        >
          {createMutation.isPending ? 'Senden…' : 'Kommentieren'}
        </button>
      </div>
    </div>
  )
}
```

- [ ] **Step 4: Create ActivityFeed**

Create `frontend/src/components/activity/ActivityFeed.tsx`:
```tsx
import type { User } from '../../types'
import { useActivity } from '../../hooks/useActivity'
import { ActivityEntry } from './ActivityEntry'
import { CommentEditor } from './CommentEditor'
import { CommentItem } from './CommentItem'

interface Props {
  projectKey: string
  issueId: string
  projectMembers: User[]
}

export function ActivityFeed({ projectKey, issueId, projectMembers }: Props) {
  const { data, isLoading } = useActivity(projectKey, issueId)

  return (
    <div className="flex flex-col gap-3">
      <h3 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Aktivität</h3>
      {isLoading && <p className="text-sm text-muted-foreground">Laden…</p>}
      {data?.content.map(entry =>
        entry.type === 'COMMENT' ? (
          <CommentItem key={entry.id} entry={entry} projectKey={projectKey} issueId={issueId} />
        ) : (
          <ActivityEntry key={entry.id} entry={entry} />
        )
      )}
      <CommentEditor projectKey={projectKey} issueId={issueId} projectMembers={projectMembers} />
    </div>
  )
}
```

- [ ] **Step 5: Verify TypeScript compilation**

Run: `cd frontend && npx tsc --noEmit`  
Expected: No errors.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/activity/
git commit -m "feat: add ActivityFeed, ActivityEntry, CommentItem, CommentEditor components"
```

---

### Task 21: Attachment Components

**Files:**
- Create: `frontend/src/components/attachments/AttachmentItem.tsx`
- Create: `frontend/src/components/attachments/AttachmentPanel.tsx`

- [ ] **Step 1: Create AttachmentItem**

Create `frontend/src/components/attachments/AttachmentItem.tsx`:
```tsx
import type { Attachment } from '../../types'
import { useDeleteAttachment } from '../../hooks/useAttachments'
import { getDownloadUrl } from '../../api/attachments'
import { useCurrentUser } from '../../hooks/useCurrentUser'

interface Props {
  attachment: Attachment
  projectKey: string
  issueId: string
}

const formatSize = (bytes: number): string => {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1048576) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1048576).toFixed(1)} MB`
}

export function AttachmentItem({ attachment, projectKey, issueId }: Props) {
  const { data: me } = useCurrentUser()
  const deleteMutation = useDeleteAttachment(projectKey, issueId)
  const isUploader = me?.id === attachment.uploaderId

  return (
    <div className="flex items-center gap-2 rounded border px-3 py-2 text-sm">
      <span className="flex-1 truncate">{attachment.filename}</span>
      <span className="text-xs text-muted-foreground shrink-0">{formatSize(attachment.size)}</span>
      <a
        href={getDownloadUrl(projectKey, issueId, attachment.id)}
        download={attachment.filename}
        className="text-xs text-primary hover:underline shrink-0"
      >
        Download
      </a>
      {isUploader && (
        <button
          className="text-xs text-muted-foreground hover:text-destructive shrink-0"
          onClick={() => deleteMutation.mutate(attachment.id)}
        >
          ✕
        </button>
      )}
    </div>
  )
}
```

- [ ] **Step 2: Create AttachmentPanel**

Create `frontend/src/components/attachments/AttachmentPanel.tsx`:
```tsx
import { useRef, useState } from 'react'
import type { Attachment } from '../../types'
import { useUploadAttachment } from '../../hooks/useAttachments'
import { AttachmentItem } from './AttachmentItem'

interface Props {
  attachments: Attachment[]
  projectKey: string
  issueId: string
}

const MAX_SIZE = 25 * 1024 * 1024

export function AttachmentPanel({ attachments, projectKey, issueId }: Props) {
  const [dragging, setDragging] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const inputRef = useRef<HTMLInputElement>(null)
  const uploadMutation = useUploadAttachment(projectKey, issueId)

  const upload = (file: File) => {
    setError(null)
    if (file.size > MAX_SIZE) {
      setError('Datei überschreitet 25 MB Limit')
      return
    }
    uploadMutation.mutate(file)
  }

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault()
    setDragging(false)
    const file = e.dataTransfer.files[0]
    if (file) upload(file)
  }

  return (
    <div className="flex flex-col gap-2">
      <h3 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Attachments</h3>
      {attachments.map(a => (
        <AttachmentItem key={a.id} attachment={a} projectKey={projectKey} issueId={issueId} />
      ))}
      <div
        className={`rounded border-2 border-dashed p-3 text-center text-xs text-muted-foreground cursor-pointer transition-colors ${
          dragging ? 'border-primary bg-primary/5' : 'border-border hover:border-primary/50'
        }`}
        onDragOver={e => { e.preventDefault(); setDragging(true) }}
        onDragLeave={() => setDragging(false)}
        onDrop={handleDrop}
        onClick={() => inputRef.current?.click()}
      >
        {uploadMutation.isPending ? 'Hochladen…' : '+ Datei hinzufügen oder hierher ziehen'}
      </div>
      {error && <p className="text-xs text-destructive">{error}</p>}
      <input
        ref={inputRef}
        type="file"
        className="hidden"
        onChange={e => { const f = e.target.files?.[0]; if (f) upload(f) }}
      />
    </div>
  )
}
```

- [ ] **Step 3: Verify TypeScript compilation**

Run: `cd frontend && npx tsc --noEmit`  
Expected: No errors.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/attachments/
git commit -m "feat: add AttachmentPanel and AttachmentItem components"
```

---

### Task 22: Notification Components + NotificationsPage

**Files:**
- Create: `frontend/src/components/notifications/NotificationItem.tsx`
- Create: `frontend/src/components/notifications/NotificationDropdown.tsx`
- Create: `frontend/src/components/notifications/NotificationBell.tsx`
- Create: `frontend/src/pages/notifications/NotificationsPage.tsx`

- [ ] **Step 1: Create NotificationItem**

Create `frontend/src/components/notifications/NotificationItem.tsx`:
```tsx
import { useNavigate } from 'react-router-dom'
import type { Notification } from '../../types'
import { useMarkRead } from '../../hooks/useNotifications'

interface Props {
  notification: Notification
  onClose?: () => void
}

export function NotificationItem({ notification, onClose }: Props) {
  const navigate = useNavigate()
  const markRead = useMarkRead()

  const handleClick = () => {
    if (!notification.read) markRead.mutate(notification.id)
    if (notification.link) navigate(notification.link)
    onClose?.()
  }

  return (
    <button
      className={`w-full text-left px-3 py-2 hover:bg-accent/50 transition-colors border-l-2 ${
        notification.read ? 'border-transparent opacity-60' : 'border-primary'
      }`}
      onClick={handleClick}
    >
      <p className="text-sm font-medium leading-snug">{notification.title}</p>
      {notification.body && (
        <p className="text-xs text-muted-foreground mt-0.5 truncate">{notification.body}</p>
      )}
      <p className="text-xs text-muted-foreground/60 mt-0.5">
        {new Date(notification.createdAt).toLocaleString('de-DE', { dateStyle: 'short', timeStyle: 'short' })}
      </p>
    </button>
  )
}
```

- [ ] **Step 2: Create NotificationDropdown**

Create `frontend/src/components/notifications/NotificationDropdown.tsx`:
```tsx
import { useNavigate } from 'react-router-dom'
import { useMarkAllRead, useNotifications } from '../../hooks/useNotifications'
import { NotificationItem } from './NotificationItem'

interface Props {
  onClose: () => void
}

export function NotificationDropdown({ onClose }: Props) {
  const { data } = useNotifications(0)
  const markAllRead = useMarkAllRead()
  const navigate = useNavigate()
  const items = data?.content.slice(0, 8) ?? []

  return (
    <div className="absolute right-0 top-full z-50 mt-1 w-72 rounded-md border bg-popover shadow-lg">
      <div className="flex items-center justify-between border-b px-3 py-2">
        <span className="text-sm font-semibold">Benachrichtigungen</span>
        <button
          className="text-xs text-primary hover:underline"
          onClick={() => markAllRead.mutate()}
        >
          Alle gelesen
        </button>
      </div>
      <div className="divide-y">
        {items.length === 0 && (
          <p className="px-3 py-4 text-center text-sm text-muted-foreground">Keine Benachrichtigungen</p>
        )}
        {items.map(n => (
          <NotificationItem key={n.id} notification={n} onClose={onClose} />
        ))}
      </div>
      <div className="border-t px-3 py-2 text-center">
        <button
          className="text-xs text-primary hover:underline"
          onClick={() => { navigate('/notifications'); onClose() }}
        >
          Alle anzeigen →
        </button>
      </div>
    </div>
  )
}
```

- [ ] **Step 3: Create NotificationBell**

Create `frontend/src/components/notifications/NotificationBell.tsx`:
```tsx
import { useEffect, useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useUnreadCount } from '../../hooks/useNotifications'
import { useCurrentUser } from '../../hooks/useCurrentUser'
import { NotificationDropdown } from './NotificationDropdown'
import { Client } from '@stomp/stompjs'

export function NotificationBell() {
  const [open, setOpen] = useState(false)
  const { data: countData, refetch } = useUnreadCount()
  const { data: me } = useCurrentUser()
  const qc = useQueryClient()
  const containerRef = useRef<HTMLDivElement>(null)
  const count = countData?.count ?? 0

  // Subscribe to per-user WebSocket topic
  useEffect(() => {
    if (!me) return
    const token = localStorage.getItem('accessToken')
    const client = new Client({
      brokerURL: `ws://${window.location.host}/ws-stomp`,
      connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
      onConnect: () => {
        client.subscribe(`/topic/notifications/${me.id}`, () => {
          refetch()
          qc.invalidateQueries({ queryKey: ['notifications'] })
        })
      },
    })
    client.activate()
    return () => { client.deactivate() }
  }, [me?.id])

  // Close dropdown on outside click
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  return (
    <div ref={containerRef} className="relative">
      <button
        className="relative p-2 rounded hover:bg-accent/50 transition-colors"
        onClick={() => setOpen(v => !v)}
        aria-label="Benachrichtigungen"
      >
        <svg className="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
            d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6 6 0 10-12 0v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
        </svg>
        {count > 0 && (
          <span className="absolute -top-1 -right-1 flex h-4 w-4 items-center justify-center rounded-full bg-destructive text-[10px] font-bold text-destructive-foreground">
            {count > 9 ? '9+' : count}
          </span>
        )}
      </button>
      {open && <NotificationDropdown onClose={() => setOpen(false)} />}
    </div>
  )
}
```

- [ ] **Step 4: Create NotificationsPage**

Create `frontend/src/pages/notifications/NotificationsPage.tsx`:
```tsx
import { useState } from 'react'
import { useMarkAllRead, useNotifications } from '../../hooks/useNotifications'
import { NotificationItem } from '../../components/notifications/NotificationItem'

export function NotificationsPage() {
  const [page, setPage] = useState(0)
  const { data, isLoading } = useNotifications(page)
  const markAllRead = useMarkAllRead()

  return (
    <div className="mx-auto max-w-2xl p-6">
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-xl font-semibold">Benachrichtigungen</h1>
        <button
          className="text-sm text-primary hover:underline"
          onClick={() => markAllRead.mutate()}
        >
          Alle als gelesen markieren
        </button>
      </div>
      {isLoading && <p className="text-muted-foreground">Laden…</p>}
      <div className="divide-y rounded-md border">
        {data?.content.map(n => (
          <NotificationItem key={n.id} notification={n} />
        ))}
        {data?.content.length === 0 && (
          <p className="px-4 py-6 text-center text-sm text-muted-foreground">
            Keine Benachrichtigungen
          </p>
        )}
      </div>
      {data && data.totalPages > 1 && (
        <div className="mt-4 flex justify-center gap-2">
          <button
            className="text-sm text-primary disabled:opacity-40"
            disabled={page === 0}
            onClick={() => setPage(p => p - 1)}
          >
            ← Zurück
          </button>
          <span className="text-sm text-muted-foreground">
            Seite {data.number + 1} von {data.totalPages}
          </span>
          <button
            className="text-sm text-primary disabled:opacity-40"
            disabled={page >= data.totalPages - 1}
            onClick={() => setPage(p => p + 1)}
          >
            Weiter →
          </button>
        </div>
      )}
    </div>
  )
}
```

- [ ] **Step 5: Verify TypeScript compilation**

Run: `cd frontend && npx tsc --noEmit`  
Expected: No errors.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/notifications/ frontend/src/pages/notifications/
git commit -m "feat: add NotificationBell, NotificationDropdown, NotificationsPage"
```

---

### Task 23: IssueDetailPage — Two-Column Rewrite

**Files:**
- Modify: `frontend/src/pages/issues/IssueDetailPage.tsx`

- [ ] **Step 1: Rewrite IssueDetailPage to two-column layout**

Replace the entire content of `frontend/src/pages/issues/IssueDetailPage.tsx` with:
```tsx
import { useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import apiClient from '../../api/client'
import { getProjectMembers } from '../../api/projects'
import type { Attachment, Issue, User } from '../../types'
import { ActivityFeed } from '../../components/activity/ActivityFeed'
import { AttachmentPanel } from '../../components/attachments/AttachmentPanel'

const fetchIssue = (projectKey: string, issueId: string): Promise<Issue> =>
  apiClient.get(`/projects/${projectKey}/issues/${issueId}`).then(r => r.data)

const fetchAttachments = (projectKey: string, issueId: string): Promise<Attachment[]> =>
  apiClient.get(`/projects/${projectKey}/issues/${issueId}/attachments`).then(r => r.data)

const PRIORITY_COLOR: Record<string, string> = {
  CRITICAL: 'text-red-500',
  HIGH: 'text-orange-500',
  MEDIUM: 'text-yellow-500',
  LOW: 'text-green-500',
}

export function IssueDetailPage() {
  const { key, issueId } = useParams<{ key: string; issueId: string }>()

  const { data: issue, isLoading } = useQuery({
    queryKey: ['issue', issueId],
    queryFn: () => fetchIssue(key!, issueId!),
    enabled: !!key && !!issueId,
  })

  const { data: attachments = [] } = useQuery({
    queryKey: ['attachments', issueId],
    queryFn: () => fetchAttachments(key!, issueId!),
    enabled: !!key && !!issueId,
  })

  const { data: members = [] } = useQuery({
    queryKey: ['projectMembers', key],
    queryFn: () => getProjectMembers(key!),
    enabled: !!key,
  })

  if (isLoading || !issue) return <div className="p-6 text-muted-foreground">Laden…</div>

  return (
    <div className="flex h-full min-h-0 gap-6 p-6">
      {/* Left column — description + activity */}
      <div className="flex flex-1 flex-col gap-4 min-w-0">
        <div>
          <p className="text-xs text-muted-foreground mb-1">{issue.issueKey}</p>
          <h1 className="text-xl font-semibold">{issue.title}</h1>
        </div>
        {issue.description && (
          <div className="rounded bg-muted/20 p-4 text-sm whitespace-pre-wrap">
            {issue.description}
          </div>
        )}
        <ActivityFeed
          projectKey={key!}
          issueId={issueId!}
          projectMembers={members}
        />
      </div>

      {/* Right column — metadata + attachments */}
      <div className="w-64 shrink-0 flex flex-col gap-4">
        <div className="rounded border p-4 flex flex-col gap-3 text-sm">
          <div>
            <p className="text-xs text-muted-foreground mb-0.5">STATUS</p>
            <p className="font-medium">{issue.status?.name ?? '—'}</p>
          </div>
          <div>
            <p className="text-xs text-muted-foreground mb-0.5">PRIORITÄT</p>
            <p className={`font-medium ${PRIORITY_COLOR[issue.priority ?? ''] ?? ''}`}>
              {issue.priority ?? '—'}
            </p>
          </div>
          <div>
            <p className="text-xs text-muted-foreground mb-0.5">ASSIGNEE</p>
            <p>{issue.assignee?.displayName ?? 'Nicht zugewiesen'}</p>
          </div>
          <div>
            <p className="text-xs text-muted-foreground mb-0.5">SPRINT</p>
            <p>{issue.sprint?.name ?? '—'}</p>
          </div>
          {issue.storyPoints != null && (
            <div>
              <p className="text-xs text-muted-foreground mb-0.5">STORY POINTS</p>
              <p>{issue.storyPoints}</p>
            </div>
          )}
        </div>

        <AttachmentPanel
          attachments={attachments}
          projectKey={key!}
          issueId={issueId!}
        />
      </div>
    </div>
  )
}
```

Note: The `Issue` type may not yet have `status`, `assignee`, `sprint`, `storyPoints` as nested objects vs IDs — check `frontend/src/types/index.ts` and the actual API response shape. Adjust field access accordingly (e.g., `issue.statusName` instead of `issue.status?.name` if the API returns flat data).

- [ ] **Step 2: Verify TypeScript compilation**

Run: `cd frontend && npx tsc --noEmit`  
Expected: No errors (fix any type mismatches against the actual Issue shape).

- [ ] **Step 3: Commit**

```bash
git add frontend/src/pages/issues/IssueDetailPage.tsx
git commit -m "feat: rewrite IssueDetailPage to two-column layout with ActivityFeed and Attachments"
```

---

### Task 24: Wire Up — AppLayout NotificationBell + /notifications Route

**Files:**
- Modify: `frontend/src/layouts/AppLayout.tsx`
- Modify: `frontend/src/app/router.tsx`

- [ ] **Step 1: Add NotificationBell to AppLayout**

Open `frontend/src/layouts/AppLayout.tsx`. Import `NotificationBell`:
```tsx
import { NotificationBell } from '../components/notifications/NotificationBell'
```

Find the top-nav/header section in the layout. Add `<NotificationBell />` in the top-right area. The exact insertion point depends on the current layout structure — look for the header element and add the bell before any user avatar or at the end of the nav items:
```tsx
{/* In the header/top-nav, right side */}
<div className="flex items-center gap-2">
  <NotificationBell />
  {/* existing user menu / avatar if any */}
</div>
```

- [ ] **Step 2: Add /notifications route to router.tsx**

Open `frontend/src/app/router.tsx`. Import the page:
```tsx
import { NotificationsPage } from '../pages/notifications/NotificationsPage'
```

Add the route inside the authenticated route group (same level as other top-level pages):
```tsx
{ path: 'notifications', element: <NotificationsPage /> }
```

- [ ] **Step 3: Verify TypeScript compilation**

Run: `cd frontend && npx tsc --noEmit`  
Expected: No errors.

- [ ] **Step 4: Start dev server and manually verify**

Run: `cd frontend && npm run dev`

Check in the browser:
- [ ] Bell icon appears in the top navigation bar
- [ ] Clicking the bell opens the dropdown
- [ ] "Alle anzeigen →" navigates to `/notifications`
- [ ] `/notifications` renders the full list page
- [ ] Navigating to an issue shows the two-column layout with description on the left and metadata sidebar on the right
- [ ] Activity feed area is visible below description
- [ ] Attachment panel is visible in the right sidebar

Stop the dev server.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/layouts/AppLayout.tsx frontend/src/app/router.tsx
git commit -m "feat: wire NotificationBell into AppLayout and add /notifications route"
```

---

## Phase 3 Complete

All 24 tasks implement the full Collaboration feature set:

- **Backend:** Comments + Activity Feed, Notifications (In-App + WebSocket + optional Email), Attachments (local filesystem, 0644 permissions), IssueFieldChangedEvent for full audit trail
- **Frontend:** Two-column IssueDetail, ActivityFeed with CommentEditor + @mention autocomplete, AttachmentPanel with native drag-and-drop, NotificationBell with WebSocket push, full NotificationsPage
