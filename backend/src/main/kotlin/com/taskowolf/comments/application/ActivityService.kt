package com.taskowolf.comments.application

import com.taskowolf.attachments.domain.events.AttachmentAddedEvent
import com.taskowolf.comments.domain.ActivityType
import com.taskowolf.comments.domain.IssueActivity
import com.taskowolf.comments.domain.events.CommentCreatedEvent
import com.taskowolf.comments.infrastructure.IssueActivityRepository
import com.taskowolf.issues.domain.events.IssueFieldChangedEvent
import com.taskowolf.issues.domain.events.IssueStatusChangedEvent
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ActivityService(private val repository: IssueActivityRepository) {

    private val log = LoggerFactory.getLogger(ActivityService::class.java)

    @EventListener
    @Transactional
    fun onCommentCreated(event: CommentCreatedEvent) {
        repository.save(
            IssueActivity(
                issueId = event.issue.id,
                actorId = event.comment.authorId,
                type = ActivityType.COMMENT,
                commentId = event.comment.id
            )
        )
    }

    @EventListener
    @Transactional
    fun onIssueFieldChanged(event: IssueFieldChangedEvent) {
        val type = when (event.field) {
            "title" -> ActivityType.TITLE_CHANGED
            "description" -> ActivityType.DESCRIPTION_CHANGED
            "priority" -> ActivityType.PRIORITY_CHANGED
            "storyPoints" -> ActivityType.STORY_POINTS_CHANGED
            "dueDate" -> ActivityType.DUE_DATE_CHANGED
            "sprint" -> ActivityType.SPRINT_CHANGED
            "assignee" -> if (event.newValue != null) ActivityType.ASSIGNED else ActivityType.UNASSIGNED
            else -> {
                log.warn("ActivityService: unknown field '${event.field}', skipping activity record")
                return
            }
        }
        repository.save(
            IssueActivity(
                issueId = event.issue.id,
                actorId = event.actor.id,
                type = type,
                oldValue = event.oldValue,
                newValue = event.newValue
            )
        )
    }

    @EventListener
    @Transactional
    fun onIssueStatusChanged(event: IssueStatusChangedEvent) {
        if (event.actor == null) {
            log.warn("ActivityService: IssueStatusChangedEvent has no actor for issue ${event.issue.id}, skipping")
            return
        }
        repository.save(
            IssueActivity(
                issueId = event.issue.id,
                actorId = event.actor.id,
                type = ActivityType.STATUS_CHANGED,
                oldValue = event.oldStatus.name,
                newValue = event.newStatus.name
            )
        )
    }

    @EventListener
    @Transactional
    fun onAttachmentAdded(event: AttachmentAddedEvent) {
        repository.save(
            IssueActivity(
                issueId = event.issue.id,
                actorId = event.attachment.uploaderId,
                type = ActivityType.ATTACHMENT_ADDED,
                newValue = event.attachment.filename
            )
        )
    }
}
