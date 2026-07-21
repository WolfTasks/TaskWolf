# Backend i18n Phase 3 — E-Mails + Notifications Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render backend-generated email text (subject + body) and in-app notification titles (plus the two templated notification bodies) in the **recipient's** language, completing the #16 backend-i18n rollout.

**Architecture:** `EmailService` and `NotificationService` resolve the recipient's locale explicitly (`localizedMessages.get(key, localeOf(user), args…)`, never `LocaleContextHolder`) and render from the `messages(.properties|_de.properties)` catalog. `NotificationService.createDirect` is converted to a **keyed** signature that renders internally after looking up the recipient's `user.language`; its three callers become thin (keys + args). The Incident notification switches from the raw issue UUID to the human `issue.key`.

**Tech Stack:** Kotlin, Spring Boot, Spring `MessageSource` (base `messages.properties` = English, `messages_de.properties` = German `\uXXXX`-escaped), JUnit5, MockK, Gradle.

**Spec:** `docs/superpowers/specs/2026-07-21-backend-i18n-phase3-emails-notifications-design.md`

## Global Constraints

- **English lives in the base bundle** `backend/src/main/resources/messages.properties` (there is NO `messages_en.properties`). German is `messages_de.properties`.
- **`.properties` files are ISO-8859-1: every non-ASCII char in `messages_de.properties` MUST be `\uXXXX`-escaped** (`ä`=`ä`, `ö`=`ö`, `ü`=`ü`, `ß`=`ß`). Procedure: write German with raw umlauts, then run the PowerShell non-ASCII→`\uXXXX` pass and confirm 0 non-ASCII bytes remain. UTF-8 resolution tests assert the decoded strings.
- **Recipient locale, not request locale.** Async/clientless text (email, notification title/templated-body) is rendered with the *explicit* locale overload `LocalizedMessages.get(key, locale, vararg args)` where `locale = localizedMessages.localeOf(recipientUser)`. Never use the request-locale `get(key, vararg args)` overload here.
- **MessageFormat single-quote rule:** a message resolved *with arguments* runs through `java.text.MessageFormat` where `'` is an escape char. None of the Phase-3 values contain a literal single quote → **no `''` doubling**. Do not add quotes.
- **Numeric args passed as `String`** (`policy.resolutionMinutes.toString()`) so MessageFormat does not apply locale digit-grouping (`1440`, not `1.440`).
- `ErrorResponse.code` values and existing keys are untouched; Phase 3 only **appends** new keys.
- **Languages: en (default/fallback) and de only.**
- **CI gate that must stay green:** `MessagesParityTest` (en/de identical key set + placeholder-`{n}` parity + non-blank). It automatically covers the new keys. (`KeyedReferenceIntegrityTest` scans `.keyed(...)`/`message="{k}"` only — Phase-3 keys go through `LocalizedMessages.get(...)` and are not scanned; unit tests cover rendering.)
- Base branch is fresh `main` (`4ec60a3` or later). No stacking — the PR targets `main` directly.
- TDD: failing test first → watch it fail → minimal implementation → watch it pass. **One commit per task.**

All work runs from the repo root; Gradle commands run from `backend/`.

---

### Task 1: Message catalog keys (en + de) + resolution test

Foundation: the 11 new keys both later tasks consume, plus a data-driven resolution test proving en/de render correctly.

**Files:**
- Modify: `backend/src/main/resources/messages.properties` (append `email.*`, `notification.*` keys)
- Modify: `backend/src/main/resources/messages_de.properties` (append German, `\uXXXX`-escaped)
- Create: `backend/src/test/kotlin/com/taskowolf/i18n/EmailNotificationMessagesTest.kt`

**Interfaces:**
- Produces (consumed by Tasks 2 & 3) — the exact catalog keys:
  - `email.mention.subject` (args: issueKey)
  - `email.mention.body` (args: issueKey, issueTitle, commentExcerpt)
  - `email.assigned.subject` (args: issueKey)
  - `email.assigned.body` (args: issueKey, issueTitle)
  - `notification.mention.title` (args: issueKey)
  - `notification.assigned.title` (args: issueKey)
  - `notification.incident.title` (args: severity, issueKey)
  - `notification.incident.body` (args: severity, issueKey)
  - `notification.slaBreached.title` (args: issueKey)
  - `notification.slaBreached.body` (args: issueKey, minutesString)
  - `notification.automation.title` (args: issueKey)

- [ ] **Step 1: Append English catalog keys**

Append to the end of `backend/src/main/resources/messages.properties`:

```properties

# --- email ---
email.mention.subject=You were mentioned in {0}
email.mention.body={0}: {1}\n\n{2}
email.assigned.subject=You were assigned to {0}
email.assigned.body=You have been assigned to: {0}\n{1}
# --- notification titles + templated bodies ---
notification.mention.title=You were mentioned in {0}
notification.assigned.title=You were assigned to {0}
notification.incident.title=Incident declared: {0} on issue {1}
notification.incident.body=A {0} incident has been declared for issue {1}.
notification.slaBreached.title=SLA Breached: {0}
notification.slaBreached.body=Issue {0} has exceeded its SLA resolution time of {1} minutes.
notification.automation.title=Automation: {0}
```

- [ ] **Step 2: Append German catalog keys (raw umlauts first)**

Append to the end of `backend/src/main/resources/messages_de.properties`:

```properties

# --- email ---
email.mention.subject=Sie wurden in {0} erwähnt
email.mention.body={0}: {1}\n\n{2}
email.assigned.subject=Ihnen wurde {0} zugewiesen
email.assigned.body=Sie wurden zugewiesen zu: {0}\n{1}
# --- notification titles + templated bodies ---
notification.mention.title=Sie wurden in {0} erwähnt
notification.assigned.title=Ihnen wurde {0} zugewiesen
notification.incident.title=Incident gemeldet: {0} für Vorgang {1}
notification.incident.body=Ein {0}-Incident wurde für Vorgang {1} gemeldet.
notification.slaBreached.title=SLA verletzt: {0}
notification.slaBreached.body=Vorgang {0} hat seine SLA-Lösungszeit von {1} Minuten überschritten.
notification.automation.title=Automatisierung: {0}
```

- [ ] **Step 3: Escape the German file (non-ASCII → `\uXXXX`)**

Run this PowerShell pass, then confirm zero non-ASCII bytes remain:

```powershell
$p = "backend/src/main/resources/messages_de.properties"
$lines = Get-Content -Encoding UTF8 $p
$out = foreach ($line in $lines) {
  -join ($line.ToCharArray() | ForEach-Object {
    if ([int]$_ -gt 127) { '\u{0:x4}' -f [int]$_ } else { $_ }
  })
}
Set-Content -Encoding ascii $p $out
Select-String -Pattern '[^\x00-\x7f]' $p   # expect: no output
```

After escaping, the new German lines read (idempotent — prior escaped lines untouched):

```properties
email.mention.subject=Sie wurden in {0} erwähnt
email.mention.body={0}: {1}\n\n{2}
email.assigned.subject=Ihnen wurde {0} zugewiesen
email.assigned.body=Sie wurden zugewiesen zu: {0}\n{1}
notification.mention.title=Sie wurden in {0} erwähnt
notification.assigned.title=Ihnen wurde {0} zugewiesen
notification.incident.title=Incident gemeldet: {0} für Vorgang {1}
notification.incident.body=Ein {0}-Incident wurde für Vorgang {1} gemeldet.
notification.slaBreached.title=SLA verletzt: {0}
notification.slaBreached.body=Vorgang {0} hat seine SLA-Lösungszeit von {1} Minuten überschritten.
notification.automation.title=Automatisierung: {0}
```

Confirm `Select-String` printed nothing.

- [ ] **Step 4: Write the resolution test**

Create `backend/src/test/kotlin/com/taskowolf/i18n/EmailNotificationMessagesTest.kt`:

```kotlin
package com.taskowolf.i18n

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.context.support.ResourceBundleMessageSource
import java.util.Locale

class EmailNotificationMessagesTest {
    private val src = ResourceBundleMessageSource().apply {
        setBasename("messages"); setDefaultEncoding("UTF-8"); setFallbackToSystemLocale(false)
    }
    private fun en(key: String, vararg a: Any?) = src.getMessage(key, a, Locale.ENGLISH)
    private fun de(key: String, vararg a: Any?) = src.getMessage(key, a, Locale.GERMAN)

    @Test
    fun `email keys render en and de`() {
        assertEquals("You were mentioned in WOLF-1", en("email.mention.subject", "WOLF-1"))
        assertEquals("Sie wurden in WOLF-1 erwähnt", de("email.mention.subject", "WOLF-1"))
        assertEquals("WOLF-1: My Issue\n\nGreat comment",
            en("email.mention.body", "WOLF-1", "My Issue", "Great comment"))
        assertEquals("WOLF-1: My Issue\n\nGreat comment",
            de("email.mention.body", "WOLF-1", "My Issue", "Great comment"))
        assertEquals("You were assigned to WOLF-1", en("email.assigned.subject", "WOLF-1"))
        assertEquals("Ihnen wurde WOLF-1 zugewiesen", de("email.assigned.subject", "WOLF-1"))
        assertEquals("You have been assigned to: WOLF-1\nMy Issue",
            en("email.assigned.body", "WOLF-1", "My Issue"))
        assertEquals("Sie wurden zugewiesen zu: WOLF-1\nMy Issue",
            de("email.assigned.body", "WOLF-1", "My Issue"))
    }

    @Test
    fun `notification title keys render en and de`() {
        assertEquals("You were mentioned in WOLF-1", en("notification.mention.title", "WOLF-1"))
        assertEquals("Sie wurden in WOLF-1 erwähnt", de("notification.mention.title", "WOLF-1"))
        assertEquals("You were assigned to WOLF-1", en("notification.assigned.title", "WOLF-1"))
        assertEquals("Ihnen wurde WOLF-1 zugewiesen", de("notification.assigned.title", "WOLF-1"))
        assertEquals("Automation: WOLF-1", en("notification.automation.title", "WOLF-1"))
        assertEquals("Automatisierung: WOLF-1", de("notification.automation.title", "WOLF-1"))
    }

    @Test
    fun `incident and sla keys render en and de (issue key + numeric-as-string)`() {
        assertEquals("Incident declared: P1 on issue WOLF-1", en("notification.incident.title", "P1", "WOLF-1"))
        assertEquals("Incident gemeldet: P1 für Vorgang WOLF-1", de("notification.incident.title", "P1", "WOLF-1"))
        assertEquals("A P1 incident has been declared for issue WOLF-1.",
            en("notification.incident.body", "P1", "WOLF-1"))
        assertEquals("Ein P1-Incident wurde für Vorgang WOLF-1 gemeldet.",
            de("notification.incident.body", "P1", "WOLF-1"))
        assertEquals("SLA Breached: WOLF-1", en("notification.slaBreached.title", "WOLF-1"))
        assertEquals("SLA verletzt: WOLF-1", de("notification.slaBreached.title", "WOLF-1"))
        assertEquals("Issue WOLF-1 has exceeded its SLA resolution time of 1440 minutes.",
            en("notification.slaBreached.body", "WOLF-1", "1440"))
        assertEquals("Vorgang WOLF-1 hat seine SLA-Lösungszeit von 1440 Minuten überschritten.",
            de("notification.slaBreached.body", "WOLF-1", "1440"))
    }
}
```

- [ ] **Step 5: Run the resolution test — expect PASS**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.i18n.EmailNotificationMessagesTest"`
Expected: **PASS**. (If a German umlaut assertion fails with mojibake, the `\uXXXX` escaping is wrong — re-run Step 3.)

- [ ] **Step 6: Run the parity gate**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.*MessagesParityTest"`
Expected: **PASS** — en/de now have identical key sets and matching `{n}` placeholders for the 11 new keys.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/resources/messages.properties \
        backend/src/main/resources/messages_de.properties \
        backend/src/test/kotlin/com/taskowolf/i18n/EmailNotificationMessagesTest.kt
git commit -m "feat(i18n): add email + notification catalog keys (en/de)

Phase 3 of #16: 11 new keys for email subject/body and notification
titles + the 2 templated notification bodies (incident, SLA). German
\uXXXX-escaped. Resolution test asserts en/de rendering incl. issue-key
and numeric-as-string args.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: `EmailService` recipient-locale rendering

Localize both email templates using the recipient's stored language.

**Files:**
- Modify: `backend/src/main/kotlin/com/taskowolf/notifications/application/EmailService.kt`
- Create: `backend/src/test/kotlin/com/taskowolf/notifications/EmailServiceTest.kt`

**Interfaces:**
- Consumes: `LocalizedMessages.get(key, locale, vararg args)` and `LocalizedMessages.localeOf(user)` from `com.taskowolf.core.infrastructure` (existing); catalog keys `email.mention.*` / `email.assigned.*` from Task 1.
- Produces: no new public API (constructor gains a `LocalizedMessages` param).

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/kotlin/com/taskowolf/notifications/EmailServiceTest.kt`:

```kotlin
package com.taskowolf.notifications

import com.taskowolf.auth.domain.User
import com.taskowolf.comments.domain.Comment
import com.taskowolf.comments.domain.events.MentionEvent
import com.taskowolf.core.infrastructure.LocalizedMessages
import com.taskowolf.issues.domain.Issue
import com.taskowolf.issues.domain.IssueType
import com.taskowolf.issues.domain.events.IssueFieldChangedEvent
import com.taskowolf.notifications.application.EmailService
import com.taskowolf.notifications.application.NotificationPreferenceService
import com.taskowolf.projects.domain.Project
import com.taskowolf.workflows.domain.StatusCategory
import com.taskowolf.workflows.domain.Workflow
import com.taskowolf.workflows.domain.WorkflowStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.context.support.ResourceBundleMessageSource
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender

class EmailServiceTest {

    private val preferences = mockk<NotificationPreferenceService>()
    private val mailSender = mockk<JavaMailSender>(relaxed = true)
    private val messageSource = ResourceBundleMessageSource().apply {
        setBasename("messages"); setDefaultEncoding("UTF-8"); setFallbackToSystemLocale(false)
    }
    private val localizedMessages = LocalizedMessages(messageSource)
    private val service = EmailService(preferences, mailSender, "smtp.test", "TaskWolf <noreply@test.com>", localizedMessages)

    private val owner = User(email = "owner@test.com", displayName = "Owner")
    private val workflow = Workflow(name = "Default", project = Project(key = "WOLF", name = "TaskWolf", owner = owner, workflow = null))
    private val status = WorkflowStatus("To Do", StatusCategory.TODO, "#6c8fef", 0, workflow)
    private val project = Project(key = "WOLF", name = "TaskWolf", owner = owner, workflow = workflow)

    private fun recipient(language: String?) = User(email = "r@test.com", displayName = "R").apply { this.language = language }
    private fun issueFor(assignee: User?) = Issue(key = "WOLF-1", keyNumber = 1, title = "My Issue",
        type = IssueType.TASK, status = status, project = project, reporter = owner, assignee = assignee)

    private fun captureMail(): SimpleMailMessage {
        val slot = slot<SimpleMailMessage>()
        verify { mailSender.send(capture(slot)) }
        return slot.captured
    }

    @Test
    fun `onMention renders subject and body in recipient German`() {
        every { preferences.isEnabled(any(), any(), any()) } returns true
        val user = recipient("de")
        val issue = issueFor(assignee = null)
        val comment = Comment(issueId = issue.id, authorId = owner.id, body = "Great comment")

        service.onMention(MentionEvent(mentionedUser = user, comment = comment, issue = issue))

        val mail = captureMail()
        assertEquals("Sie wurden in WOLF-1 erwähnt", mail.subject)
        assertEquals("WOLF-1: My Issue\n\nGreat comment", mail.text)
    }

    @Test
    fun `onMention renders subject in English when language null`() {
        every { preferences.isEnabled(any(), any(), any()) } returns true
        val user = recipient(null)
        val issue = issueFor(assignee = null)
        val comment = Comment(issueId = issue.id, authorId = owner.id, body = "Great comment")

        service.onMention(MentionEvent(mentionedUser = user, comment = comment, issue = issue))

        assertEquals("You were mentioned in WOLF-1", captureMail().subject)
    }

    @Test
    fun `onAssigned renders subject and body in recipient German`() {
        every { preferences.isEnabled(any(), any(), any()) } returns true
        val assignee = recipient("de")
        val issue = issueFor(assignee = assignee)
        val event = IssueFieldChangedEvent(issue = issue, actor = owner,
            field = "assignee", oldValue = null, newValue = assignee.displayName)

        service.onAssigned(event)

        val mail = captureMail()
        assertEquals("Ihnen wurde WOLF-1 zugewiesen", mail.subject)
        assertEquals("Sie wurden zugewiesen zu: WOLF-1\nMy Issue", mail.text)
    }

    @Test
    fun `onAssigned renders subject in English when language en`() {
        every { preferences.isEnabled(any(), any(), any()) } returns true
        val assignee = recipient("en")
        val issue = issueFor(assignee = assignee)
        val event = IssueFieldChangedEvent(issue = issue, actor = owner,
            field = "assignee", oldValue = null, newValue = assignee.displayName)

        service.onAssigned(event)

        assertEquals("You were assigned to WOLF-1", captureMail().subject)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.notifications.EmailServiceTest"`
Expected: **FAIL** — it won't compile: `EmailService` has no 5-arg constructor with `LocalizedMessages`. (This is the expected RED for a constructor/behavior change.)

- [ ] **Step 3: Localize `EmailService`**

Rewrite `backend/src/main/kotlin/com/taskowolf/notifications/application/EmailService.kt`:

```kotlin
package com.taskowolf.notifications.application

import com.taskowolf.comments.domain.events.MentionEvent
import com.taskowolf.core.infrastructure.LocalizedMessages
import com.taskowolf.issues.domain.events.IssueFieldChangedEvent
import com.taskowolf.notifications.domain.NotificationChannel
import com.taskowolf.notifications.domain.NotificationType
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.event.EventListener
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Service

@Service
class EmailService(
    private val preferences: NotificationPreferenceService,
    private val mailSender: JavaMailSender,
    @Value("\${spring.mail.host:}") private val mailHost: String,
    @Value("\${taskowolf.smtp.from:TaskWolf <noreply@example.com>}") private val fromAddress: String,
    private val localizedMessages: LocalizedMessages
) {
    private val enabled get() = mailHost.isNotBlank()

    @EventListener
    fun onMention(event: MentionEvent) {
        if (!enabled) return
        if (!preferences.isEnabled(event.mentionedUser.id, NotificationType.COMMENT_MENTION, NotificationChannel.EMAIL)) return
        val locale = localizedMessages.localeOf(event.mentionedUser)
        val msg = SimpleMailMessage().apply {
            from = fromAddress
            setTo(event.mentionedUser.email)
            subject = localizedMessages.get("email.mention.subject", locale, event.issue.key)
            text = localizedMessages.get(
                "email.mention.body", locale,
                event.issue.key, event.issue.title, event.comment.body.take(500)
            )
        }
        mailSender.send(msg)
    }

    @EventListener
    fun onAssigned(event: IssueFieldChangedEvent) {
        if (!enabled || event.field != "assignee") return
        val assignee = event.issue.assignee ?: return
        if (!preferences.isEnabled(assignee.id, NotificationType.ISSUE_ASSIGNED, NotificationChannel.EMAIL)) return
        val locale = localizedMessages.localeOf(assignee)
        val msg = SimpleMailMessage().apply {
            from = fromAddress
            setTo(assignee.email)
            subject = localizedMessages.get("email.assigned.subject", locale, event.issue.key)
            text = localizedMessages.get("email.assigned.body", locale, event.issue.key, event.issue.title)
        }
        mailSender.send(msg)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.notifications.EmailServiceTest"`
Expected: **PASS** (all 4 tests).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/notifications/application/EmailService.kt \
        backend/src/test/kotlin/com/taskowolf/notifications/EmailServiceTest.kt
git commit -m "feat(i18n): render mention/assigned emails in recipient language

Phase 3 of #16: EmailService resolves localeOf(recipient) and renders
subject + body via email.mention.* / email.assigned.* keys. New unit
test asserts de/en/null->en rendering with a real ResourceBundleMessageSource.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3: `NotificationService` keyed rendering + thin callers

Localize notification titles (2 listeners), convert `createDirect` to a keyed signature that renders internally (looking up the recipient's language), and migrate the 3 callers + their tests in the same commit (the signature change is compile-breaking, so it is atomic). The Incident notification uses `issue.key` instead of the raw UUID.

**Files:**
- Modify: `backend/src/main/kotlin/com/taskowolf/notifications/application/NotificationService.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/servicedesk/application/IncidentService.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/servicedesk/application/SlaMonitorJob.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/automation/application/ActionExecutor.kt`
- Modify: `backend/src/test/kotlin/com/taskowolf/notifications/NotificationServiceTest.kt`
- Modify: `backend/src/test/kotlin/com/taskowolf/servicedesk/IncidentServiceTest.kt`
- Modify: `backend/src/test/kotlin/com/taskowolf/servicedesk/SlaMonitorJobTest.kt`
- Modify: `backend/src/test/kotlin/com/taskowolf/automation/ActionExecutorTest.kt`

**Interfaces:**
- Consumes: `LocalizedMessages` (existing), `UserRepository` (`com.taskowolf.auth.infrastructure`, existing JpaRepository), `IssueRepository` (`com.taskowolf.issues.infrastructure`, existing), catalog keys from Task 1.
- Produces — the new `NotificationService.createDirect` signature that all three callers depend on:

```kotlin
fun createDirect(
    userId: UUID,
    type: NotificationType,
    titleKey: String,
    link: String,
    titleArgs: Array<out Any?> = emptyArray(),
    bodyKey: String? = null,
    bodyArgs: Array<out Any?> = emptyArray(),
    rawBody: String? = null,
)
```
(`bodyKey` renders a templated body; `rawBody` is a pre-formed user-content body; if neither is set the body is empty.)

- [ ] **Step 1: Update `NotificationServiceTest` (failing test first)**

Rewrite `backend/src/test/kotlin/com/taskowolf/notifications/NotificationServiceTest.kt`. This adds the new constructor deps (real `LocalizedMessages`, mocked `UserRepository`), keeps the existing behavior tests, migrates the disabled-preference `createDirect` call to the keyed signature, and adds title-localization + body-handling tests:

```kotlin
package com.taskowolf.notifications

import com.taskowolf.auth.domain.User
import com.taskowolf.auth.infrastructure.UserRepository
import com.taskowolf.comments.domain.Comment
import com.taskowolf.comments.domain.events.MentionEvent
import com.taskowolf.core.infrastructure.LocalizedMessages
import com.taskowolf.core.infrastructure.NotFoundException
import com.taskowolf.issues.domain.Issue
import com.taskowolf.issues.domain.IssueType
import com.taskowolf.issues.domain.events.IssueFieldChangedEvent
import com.taskowolf.notifications.application.NotificationService
import com.taskowolf.notifications.domain.Notification
import com.taskowolf.notifications.domain.NotificationChannel
import com.taskowolf.notifications.domain.NotificationType
import com.taskowolf.notifications.infrastructure.NotificationRepository
import com.taskowolf.projects.domain.Project
import com.taskowolf.workflows.domain.StatusCategory
import com.taskowolf.workflows.domain.Workflow
import com.taskowolf.workflows.domain.WorkflowStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.support.ResourceBundleMessageSource
import java.util.Optional
import java.util.UUID

class NotificationServiceTest {

    private val repository = mockk<NotificationRepository>()
    private val preferences = mockk<com.taskowolf.notifications.application.NotificationPreferenceService>()
    private val userRepository = mockk<UserRepository>()
    private val messageSource = ResourceBundleMessageSource().apply {
        setBasename("messages"); setDefaultEncoding("UTF-8"); setFallbackToSystemLocale(false)
    }
    private val localizedMessages = LocalizedMessages(messageSource)
    private val service = NotificationService(repository, preferences, localizedMessages, userRepository)

    private val user = User(email = "user@test.com", displayName = "User")
    private val actor = User(email = "actor@test.com", displayName = "Actor")
    private val owner = User(email = "owner@test.com", displayName = "Owner")

    private val workflow = buildWorkflow(owner)
    private val status = WorkflowStatus("To Do", StatusCategory.TODO, "#6c8fef", 0, workflow)
    private val project = Project(key = "WOLF", name = "TaskWolf", owner = owner, workflow = workflow)
    private val issue = Issue(key = "WOLF-1", keyNumber = 1, title = "Test Issue",
        type = IssueType.TASK, status = status, project = project, reporter = owner, assignee = user)

    @Test
    fun `onMention saves COMMENT_MENTION notification with English title for null language`() {
        every { preferences.isEnabled(any(), any(), any()) } returns true
        every { repository.save(any()) } returnsArgument 0
        val comment = Comment(issueId = issue.id, authorId = actor.id, body = "Hey @User check this")
        val event = MentionEvent(mentionedUser = user, comment = comment, issue = issue)

        service.onMention(event)

        val slot = slot<Notification>()
        verify { repository.save(capture(slot)) }
        assertEquals(user.id, slot.captured.userId)
        assertEquals(NotificationType.COMMENT_MENTION, slot.captured.type)
        assertEquals("You were mentioned in WOLF-1", slot.captured.title)
        assertEquals("Hey @User check this", slot.captured.body) // user-content body unchanged
    }

    @Test
    fun `onMention renders title in recipient German`() {
        every { preferences.isEnabled(any(), any(), any()) } returns true
        every { repository.save(any()) } returnsArgument 0
        val germanUser = User(email = "de@test.com", displayName = "DE").apply { language = "de" }
        val comment = Comment(issueId = issue.id, authorId = actor.id, body = "Hallo")
        val event = MentionEvent(mentionedUser = germanUser, comment = comment, issue = issue)

        service.onMention(event)

        val slot = slot<Notification>()
        verify { repository.save(capture(slot)) }
        assertEquals("Sie wurden in WOLF-1 erwähnt", slot.captured.title)
    }

    @Test
    fun `onIssueFieldChanged saves ISSUE_ASSIGNED with localized title, body unchanged`() {
        every { preferences.isEnabled(any(), any(), any()) } returns true
        every { repository.save(any()) } returnsArgument 0
        val event = IssueFieldChangedEvent(issue = issue, actor = actor,
            field = "assignee", oldValue = null, newValue = user.displayName)

        service.onIssueFieldChanged(event)

        val slot = slot<Notification>()
        verify { repository.save(capture(slot)) }
        assertEquals(user.id, slot.captured.userId)
        assertEquals(NotificationType.ISSUE_ASSIGNED, slot.captured.type)
        assertEquals("You were assigned to WOLF-1", slot.captured.title)
        assertEquals("Test Issue", slot.captured.body) // issue.title stays as user-content
    }

    @Test
    fun `onMention skips save when in-app preference disabled`() {
        every { preferences.isEnabled(user.id, NotificationType.COMMENT_MENTION, NotificationChannel.IN_APP) } returns false
        val comment = Comment(issueId = issue.id, authorId = actor.id, body = "Hey @User")
        val event = MentionEvent(mentionedUser = user, comment = comment, issue = issue)

        service.onMention(event)

        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `createDirect renders templated body in recipient German`() {
        val germanUser = User(email = "de@test.com", displayName = "DE").apply { language = "de" }
        every { preferences.isEnabled(germanUser.id, NotificationType.SLA_BREACHED, NotificationChannel.IN_APP) } returns true
        every { userRepository.findById(germanUser.id) } returns Optional.of(germanUser)
        every { repository.save(any()) } returnsArgument 0

        service.createDirect(
            userId = germanUser.id,
            type = NotificationType.SLA_BREACHED,
            titleKey = "notification.slaBreached.title",
            link = "/issues/WOLF-1",
            titleArgs = arrayOf("WOLF-1"),
            bodyKey = "notification.slaBreached.body",
            bodyArgs = arrayOf("WOLF-1", "60"),
        )

        val slot = slot<Notification>()
        verify { repository.save(capture(slot)) }
        assertEquals("SLA verletzt: WOLF-1", slot.captured.title)
        assertEquals("Vorgang WOLF-1 hat seine SLA-Lösungszeit von 60 Minuten überschritten.", slot.captured.body)
    }

    @Test
    fun `createDirect keeps rawBody as user-content and renders title`() {
        every { preferences.isEnabled(user.id, NotificationType.AUTOMATION, NotificationChannel.IN_APP) } returns true
        every { userRepository.findById(user.id) } returns Optional.of(user)
        every { repository.save(any()) } returnsArgument 0

        service.createDirect(
            userId = user.id,
            type = NotificationType.AUTOMATION,
            titleKey = "notification.automation.title",
            link = "/l",
            titleArgs = arrayOf("WOLF-1"),
            rawBody = "Custom automation message",
        )

        val slot = slot<Notification>()
        verify { repository.save(capture(slot)) }
        assertEquals("Automation: WOLF-1", slot.captured.title)
        assertEquals("Custom automation message", slot.captured.body)
    }

    @Test
    fun `createDirect falls back to English when recipient user not found`() {
        every { preferences.isEnabled(user.id, NotificationType.AUTOMATION, NotificationChannel.IN_APP) } returns true
        every { userRepository.findById(user.id) } returns Optional.empty()
        every { repository.save(any()) } returnsArgument 0

        service.createDirect(
            userId = user.id,
            type = NotificationType.AUTOMATION,
            titleKey = "notification.automation.title",
            link = "/l",
            titleArgs = arrayOf("WOLF-1"),
            rawBody = "msg",
        )

        val slot = slot<Notification>()
        verify { repository.save(capture(slot)) }
        assertEquals("Automation: WOLF-1", slot.captured.title)
    }

    @Test
    fun `createDirect skips save when in-app preference disabled`() {
        every { preferences.isEnabled(user.id, NotificationType.AUTOMATION, NotificationChannel.IN_APP) } returns false

        service.createDirect(
            userId = user.id,
            type = NotificationType.AUTOMATION,
            titleKey = "notification.automation.title",
            link = "/l",
            titleArgs = arrayOf("WOLF-1"),
        )

        verify(exactly = 0) { repository.save(any()) }
        verify(exactly = 0) { userRepository.findById(any()) }
    }

    @Test
    fun `markRead marks notification as read`() {
        val notif = Notification(userId = user.id, type = NotificationType.COMMENT_MENTION, title = "Test")
        every { repository.findByIdAndUserId(notif.id, user.id) } returns notif
        every { repository.save(any()) } returnsArgument 0

        service.markRead(notif.id, user.id)

        assert(notif.read)
        verify { repository.save(notif) }
    }

    @Test
    fun `markRead throws NotFoundException for unknown notification`() {
        every { repository.findByIdAndUserId(any(), any()) } returns null
        assertThrows<NotFoundException> { service.markRead(UUID.randomUUID(), user.id) }
    }

    companion object {
        fun buildWorkflow(owner: User): Workflow {
            val project = Project(key = "WOLF", name = "TaskWolf", owner = owner, workflow = null)
            return Workflow(name = "Default", project = project)
        }
    }
}
```

- [ ] **Step 2: Run `NotificationServiceTest` — expect FAIL (won't compile)**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.notifications.NotificationServiceTest"`
Expected: **FAIL** — `NotificationService` has no 4-arg constructor and no keyed `createDirect`. Expected RED.

- [ ] **Step 3: Rewrite `NotificationService`**

Rewrite `backend/src/main/kotlin/com/taskowolf/notifications/application/NotificationService.kt`:

```kotlin
package com.taskowolf.notifications.application

import com.taskowolf.auth.infrastructure.UserRepository
import com.taskowolf.comments.domain.events.MentionEvent
import com.taskowolf.core.infrastructure.LocalizedMessages
import com.taskowolf.core.infrastructure.NotFoundException
import com.taskowolf.issues.domain.events.IssueFieldChangedEvent
import com.taskowolf.notifications.domain.Notification
import com.taskowolf.notifications.domain.NotificationChannel
import com.taskowolf.notifications.domain.NotificationType
import com.taskowolf.notifications.infrastructure.NotificationRepository
import org.springframework.context.event.EventListener
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Locale
import java.util.UUID

@Service
class NotificationService(
    private val repository: NotificationRepository,
    private val preferences: NotificationPreferenceService,
    private val localizedMessages: LocalizedMessages,
    private val userRepository: UserRepository
) {

    @EventListener
    @Transactional
    fun onMention(event: MentionEvent) {
        if (!preferences.isEnabled(event.mentionedUser.id, NotificationType.COMMENT_MENTION, NotificationChannel.IN_APP)) return
        val locale = localizedMessages.localeOf(event.mentionedUser)
        repository.save(
            Notification(
                userId = event.mentionedUser.id,
                type = NotificationType.COMMENT_MENTION,
                title = localizedMessages.get("notification.mention.title", locale, event.issue.key),
                body = event.comment.body.take(200),
                link = "/issues/${event.issue.key}"
            )
        )
    }

    @EventListener
    @Transactional
    fun onIssueFieldChanged(event: IssueFieldChangedEvent) {
        if (event.field != "assignee") return
        val assignee = event.issue.assignee ?: return
        if (!preferences.isEnabled(assignee.id, NotificationType.ISSUE_ASSIGNED, NotificationChannel.IN_APP)) return
        val locale = localizedMessages.localeOf(assignee)
        repository.save(
            Notification(
                userId = assignee.id,
                type = NotificationType.ISSUE_ASSIGNED,
                title = localizedMessages.get("notification.assigned.title", locale, event.issue.key),
                body = event.issue.title,
                link = "/issues/${event.issue.key}"
            )
        )
    }

    @Transactional
    fun markRead(notificationId: UUID, userId: UUID): Notification {
        val notif = repository.findByIdAndUserId(notificationId, userId)
            ?: throw NotFoundException.keyed("notification.notFound")
        notif.read = true
        return repository.save(notif)
    }

    @Transactional(readOnly = true)
    fun listForUser(userId: UUID, page: Int, size: Int): Page<Notification> =
        repository.findAllByUserIdOrderByCreatedAtDesc(
            userId,
            PageRequest.of(page, size)
        )

    @Transactional(readOnly = true)
    fun countUnread(userId: UUID): Long = repository.countByUserIdAndReadFalse(userId)

    /**
     * Creates a notification, rendering [titleKey] (and optional [bodyKey]) in the recipient's
     * stored language. Pass [rawBody] for a user-content body that must not be translated; if
     * neither [bodyKey] nor [rawBody] is set, the body is empty.
     */
    @Transactional
    fun createDirect(
        userId: UUID,
        type: NotificationType,
        titleKey: String,
        link: String,
        titleArgs: Array<out Any?> = emptyArray(),
        bodyKey: String? = null,
        bodyArgs: Array<out Any?> = emptyArray(),
        rawBody: String? = null,
    ) {
        if (!preferences.isEnabled(userId, type, NotificationChannel.IN_APP)) return
        val locale = userRepository.findById(userId)
            .map(localizedMessages::localeOf).orElse(Locale.ENGLISH)
        val title = localizedMessages.get(titleKey, locale, *titleArgs)
        val body = if (bodyKey != null) localizedMessages.get(bodyKey, locale, *bodyArgs) else rawBody.orEmpty()
        repository.save(Notification(userId = userId, type = type, title = title, body = body, link = link))
    }
}
```

- [ ] **Step 4: Migrate `IncidentService` (inject `IssueRepository`, use issue key)**

Rewrite `backend/src/main/kotlin/com/taskowolf/servicedesk/application/IncidentService.kt`. Add the `IssueRepository` import + constructor param; resolve the issue key once (guarded so no lookup happens when there are no recipients); pass keyed args:

```kotlin
package com.taskowolf.servicedesk.application

import com.taskowolf.comments.domain.Comment
import com.taskowolf.comments.infrastructure.CommentRepository
import com.taskowolf.issues.infrastructure.IssueRepository
import com.taskowolf.notifications.application.NotificationService
import com.taskowolf.notifications.domain.NotificationType
import com.taskowolf.servicedesk.domain.Incident
import com.taskowolf.servicedesk.domain.IncidentSeverity
import com.taskowolf.servicedesk.infrastructure.IncidentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class IncidentService(
    private val incidentRepo: IncidentRepository,
    private val commentRepo: CommentRepository,
    private val notificationService: NotificationService,
    private val issueRepository: IssueRepository
) {
    companion object {
        /** Null authorId used for system-generated comments (no FK violation since author_id is nullable). */
        val SYSTEM_USER_ID: UUID? = null
    }

    @Transactional
    fun create(issueId: UUID, severity: IncidentSeverity, onCallAssigneeId: UUID?, notifyUserIds: List<UUID>): Incident {
        val incident = incidentRepo.save(Incident(issueId = issueId, severity = severity, onCallAssigneeId = onCallAssigneeId))
        if (notifyUserIds.isNotEmpty()) {
            val issueKey = issueRepository.findById(issueId).map { it.key }.orElse(issueId.toString())
            notifyUserIds.forEach { uid ->
                notificationService.createDirect(
                    userId = uid,
                    type = NotificationType.AUTOMATION,
                    titleKey = "notification.incident.title",
                    link = "/issues/$issueKey",
                    titleArgs = arrayOf(severity.name, issueKey),
                    bodyKey = "notification.incident.body",
                    bodyArgs = arrayOf(severity.name, issueKey),
                )
            }
        }
        return incident
    }

    @Transactional
    fun resolve(incidentId: UUID, postmortemBody: String?) {
        val incident = incidentRepo.findById(incidentId).orElseThrow()
        incident.resolvedAt = Instant.now()
        if (postmortemBody != null) {
            incident.postmortemBody = postmortemBody
            val body = """## Postmortem

**Severity:** ${incident.severity}
**Resolved:** ${incident.resolvedAt}

$postmortemBody"""
            commentRepo.save(Comment(issueId = incident.issueId, authorId = SYSTEM_USER_ID, body = body))
        }
        incidentRepo.save(incident)
    }

    @Transactional(readOnly = true)
    fun listByProject(projectId: UUID): List<Incident> = incidentRepo.findByProjectId(projectId)
}
```

- [ ] **Step 5: Migrate `SlaMonitorJob` `createDirect` call**

In `backend/src/main/kotlin/com/taskowolf/servicedesk/application/SlaMonitorJob.kt`, replace the `notificationService.createDirect(...)` block (the one inside `rules.forEach { rule -> rule.notifyUserIds.forEach { uid -> ... } }`) with:

```kotlin
                        notificationService.createDirect(
                            userId = uid,
                            type = NotificationType.SLA_BREACHED,
                            titleKey = "notification.slaBreached.title",
                            link = "/issues/${issue.key}",
                            titleArgs = arrayOf(issue.key),
                            bodyKey = "notification.slaBreached.body",
                            bodyArgs = arrayOf(issue.key, policy.resolutionMinutes.toString()),
                        )
```

(No import changes needed — `NotificationType` is already imported.)

- [ ] **Step 6: Migrate `ActionExecutor` `SEND_NOTIFICATION` call**

In `backend/src/main/kotlin/com/taskowolf/automation/application/ActionExecutor.kt`, replace the `notificationService.createDirect(...)` call inside the `ActionType.SEND_NOTIFICATION` branch with (keep the two lines above it that compute `message` and `recipientId`):

```kotlin
                    notificationService.createDirect(
                        userId = recipientId,
                        type = NotificationType.AUTOMATION,
                        titleKey = "notification.automation.title",
                        link = "/p/${issue.project.key}/issues/${issue.key}",
                        titleArgs = arrayOf(issue.key),
                        rawBody = message,
                    )
```

- [ ] **Step 7: Update `IncidentServiceTest` for the new deps + keyed verify**

Rewrite `backend/src/test/kotlin/com/taskowolf/servicedesk/IncidentServiceTest.kt` to inject the mocked `IssueRepository`, stub the issue-key lookup, and verify the keyed `createDirect` args carry the **issue key**:

```kotlin
package com.taskowolf.servicedesk

import com.taskowolf.comments.domain.Comment
import com.taskowolf.comments.infrastructure.CommentRepository
import com.taskowolf.issues.domain.Issue
import com.taskowolf.issues.infrastructure.IssueRepository
import com.taskowolf.notifications.application.NotificationService
import com.taskowolf.notifications.domain.NotificationType
import com.taskowolf.servicedesk.application.IncidentService
import com.taskowolf.servicedesk.domain.Incident
import com.taskowolf.servicedesk.domain.IncidentSeverity
import com.taskowolf.servicedesk.infrastructure.IncidentRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.UUID

class IncidentServiceTest {

    private val incidentRepo = mockk<IncidentRepository>()
    private val commentRepo = mockk<CommentRepository>()
    private val notificationService = mockk<NotificationService>(relaxed = true)
    private val issueRepository = mockk<IssueRepository>()
    private val service = IncidentService(incidentRepo, commentRepo, notificationService, issueRepository)

    private val issueId = UUID.randomUUID()

    @Test
    fun `create saves incident and notifies users with issue key`() {
        val onCallId = UUID.randomUUID()
        val notifyId1 = UUID.randomUUID()
        val notifyId2 = UUID.randomUUID()
        every { incidentRepo.save(any()) } returnsArgument 0
        val issue = mockk<Issue> { every { key } returns "WOLF-1" }
        every { issueRepository.findById(issueId) } returns Optional.of(issue)

        val incident = service.create(issueId, IncidentSeverity.P1, onCallId, listOf(notifyId1, notifyId2))

        assertEquals(issueId, incident.issueId)
        assertEquals(IncidentSeverity.P1, incident.severity)
        assertEquals(onCallId, incident.onCallAssigneeId)
        verify(exactly = 2) {
            notificationService.createDirect(
                userId = any(),
                type = NotificationType.AUTOMATION,
                titleKey = "notification.incident.title",
                link = "/issues/WOLF-1",
                titleArgs = match { it.contains("WOLF-1") },
                bodyKey = "notification.incident.body",
                bodyArgs = match { it.contains("WOLF-1") },
                rawBody = any(),
            )
        }
    }

    @Test
    fun `create without notify users saves incident without notifications or issue lookup`() {
        every { incidentRepo.save(any()) } returnsArgument 0

        val incident = service.create(issueId, IncidentSeverity.P2, null, emptyList())

        assertEquals(issueId, incident.issueId)
        assertEquals(IncidentSeverity.P2, incident.severity)
        verify(exactly = 0) { notificationService.createDirect(any(), any(), any(), any()) }
        verify(exactly = 0) { issueRepository.findById(any()) }
    }

    @Test
    fun `resolve sets resolvedAt and creates postmortem comment`() {
        val incident = Incident(issueId = issueId, severity = IncidentSeverity.P1)
        every { incidentRepo.findById(incident.id) } returns Optional.of(incident)
        every { incidentRepo.save(any()) } returnsArgument 0
        every { commentRepo.save(any()) } returnsArgument 0

        service.resolve(incident.id, "Root cause: DB overload")

        assertNotNull(incident.resolvedAt)
        assertEquals("Root cause: DB overload", incident.postmortemBody)
        val commentSlot = slot<Comment>()
        verify { commentRepo.save(capture(commentSlot)) }
        assertTrue(commentSlot.captured.body.contains("Postmortem"))
        assertEquals(issueId, commentSlot.captured.issueId)
    }

    @Test
    fun `resolve without postmortem body still sets resolvedAt`() {
        val incident = Incident(issueId = issueId, severity = IncidentSeverity.P2)
        every { incidentRepo.findById(incident.id) } returns Optional.of(incident)
        every { incidentRepo.save(any()) } returnsArgument 0

        service.resolve(incident.id, null)

        assertNotNull(incident.resolvedAt)
        verify(exactly = 0) { commentRepo.save(any()) }
    }

    @Test
    fun `listByProject returns incidents for given projectId`() {
        val projectId = UUID.randomUUID()
        val i1 = Incident(issueId = issueId, severity = IncidentSeverity.P1)
        val i2 = Incident(issueId = UUID.randomUUID(), severity = IncidentSeverity.P3)
        every { incidentRepo.findByProjectId(projectId) } returns listOf(i1, i2)

        val result = service.listByProject(projectId)

        assertEquals(2, result.size)
    }
}
```

- [ ] **Step 8: Update `SlaMonitorJobTest` verify blocks for the keyed signature**

In `backend/src/test/kotlin/com/taskowolf/servicedesk/SlaMonitorJobTest.kt`:

Replace the positive-breach verify block in `run fires escalation for issue breaching SLA` (currently the `verify(exactly = 1) { notificationService.createDirect(notifyUserId, NotificationType.SLA_BREACHED, match { it.contains("WOLF-1") }, any(), any()) }` block) with:

```kotlin
        verify(exactly = 1) {
            notificationService.createDirect(
                userId = notifyUserId,
                type = NotificationType.SLA_BREACHED,
                titleKey = "notification.slaBreached.title",
                link = "/issues/WOLF-1",
                titleArgs = match { it.contains("WOLF-1") },
                bodyKey = "notification.slaBreached.body",
                bodyArgs = any(),
                rawBody = any(),
            )
        }
```

Then, in the four `verify(exactly = 0) { notificationService.createDirect(any(), any(), any(), any(), any()) }` blocks (in `run does not fire escalation...`, `run skips issue when no ServiceDesk found`, `run skips issue when no matching SLA policy found`, and `run handles empty issue list gracefully`), change each to the 4-required-arg matcher shape:

```kotlin
        verify(exactly = 0) { notificationService.createDirect(any(), any(), any(), any()) }
```

- [ ] **Step 9: Update `ActionExecutorTest` verify for the keyed signature**

In `backend/src/test/kotlin/com/taskowolf/automation/ActionExecutorTest.kt`, replace the verify in `SEND_NOTIFICATION calls notificationService` with:

```kotlin
        verify {
            notificationService.createDirect(
                userId = any(),
                type = NotificationType.AUTOMATION,
                titleKey = "notification.automation.title",
                link = any(),
                titleArgs = any(),
                bodyKey = any(),
                bodyArgs = any(),
                rawBody = "Hello",
            )
        }
```

- [ ] **Step 10: Run the touched tests — expect PASS**

Run:
```
cd backend && ./gradlew test --tests "com.taskowolf.notifications.NotificationServiceTest" \
  --tests "com.taskowolf.servicedesk.IncidentServiceTest" \
  --tests "com.taskowolf.servicedesk.SlaMonitorJobTest" \
  --tests "com.taskowolf.automation.ActionExecutorTest"
```
Expected: **PASS** (all four). If a `bodyArgs`/`titleArgs` `match {}` fails, confirm the array contents (`severity.name`, `issue.key`) match; if the German body assertion in `NotificationServiceTest` shows mojibake, re-run Task 1 Step 3.

- [ ] **Step 11: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/notifications/application/NotificationService.kt \
        backend/src/main/kotlin/com/taskowolf/servicedesk/application/IncidentService.kt \
        backend/src/main/kotlin/com/taskowolf/servicedesk/application/SlaMonitorJob.kt \
        backend/src/main/kotlin/com/taskowolf/automation/application/ActionExecutor.kt \
        backend/src/test/kotlin/com/taskowolf/notifications/NotificationServiceTest.kt \
        backend/src/test/kotlin/com/taskowolf/servicedesk/IncidentServiceTest.kt \
        backend/src/test/kotlin/com/taskowolf/servicedesk/SlaMonitorJobTest.kt \
        backend/src/test/kotlin/com/taskowolf/automation/ActionExecutorTest.kt
git commit -m "feat(i18n): render notification titles in recipient language

Phase 3 of #16: NotificationService localizes mention/assigned titles via
localeOf(recipient); createDirect converted to a keyed signature that renders
title (+ optional templated body) after looking up user.language, with EN
fallback. IncidentService injects IssueRepository and uses issue.key (not the
raw UUID) in title/body/link. SlaMonitorJob + ActionExecutor migrated to keyed
args; user-content bodies (comment excerpt, issue title, automation message)
stay untranslated.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Final verification (controller-run, after the task reviews — no code changes expected)

- [ ] **Full suite green:** `cd backend && ./gradlew test` → BUILD SUCCESSFUL, including `MessagesParityTest`, `KeyedReferenceIntegrityTest`, `NoUnkeyedUserFacingThrowTest`, `EmailNotificationMessagesTest`, `EmailServiceTest`, `NotificationServiceTest`, `IncidentServiceTest`, `SlaMonitorJobTest`, `ActionExecutorTest`.
- [ ] **No non-ASCII bytes** in `messages_de.properties`: `Select-String -Pattern '[^\x00-\x7f]' backend/src/main/resources/messages_de.properties` → no output.
- [ ] **en/de parity** holds (covered by `MessagesParityTest` in the full-suite run above).
- [ ] **No un-keyed user-facing throw introduced** (covered by `NoUnkeyedUserFacingThrowTest` in the full-suite run — this task adds none).

## Handoff (out of scope for this session)

- Open a PR to `main` (three commits, one per task). No stacking.
- **Owed by Wolfgang, after merge:** manual DE/EN smoke (trigger a mention/assignment email + an incident/SLA/automation notification for a `de` user and an `en` user; confirm recipient-language subject/title and the localized incident/SLA bodies; confirm user-content bodies stay untranslated), then the `v1.0.x` release — which ships **all** of #16 Phases 1–3. With this, backlog #16 is functionally complete.
