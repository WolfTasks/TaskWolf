package com.taskowolf.automation.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.taskowolf.automation.domain.ActionType
import com.taskowolf.automation.domain.RuleAction
import com.taskowolf.comments.domain.Comment
import com.taskowolf.comments.infrastructure.CommentRepository
import com.taskowolf.issues.domain.Issue
import com.taskowolf.issues.domain.IssuePriority
import com.taskowolf.issues.domain.IssueType
import com.taskowolf.issues.infrastructure.IssueRepository
import com.taskowolf.notifications.application.NotificationService
import com.taskowolf.notifications.domain.NotificationType
import com.taskowolf.workflows.infrastructure.WorkflowStatusRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ActionExecutor(
    private val issueRepository: IssueRepository,
    private val statusRepository: WorkflowStatusRepository,
    private val notificationService: NotificationService,
    private val commentRepository: CommentRepository,
    private val mapper: ObjectMapper
) {
    fun execute(actions: List<RuleAction>, issue: Issue) {
        var dirty = false
        for (action in actions.sortedBy { it.position }) {
            val params: Map<String, String> = mapper.readValue(action.params)
            when (action.type) {
                ActionType.SET_STATUS -> {
                    val statusIdStr = params["statusId"]
                    if (statusIdStr != null) {
                        runCatching { UUID.fromString(statusIdStr) }.getOrNull()
                            ?.let { statusRepository.findById(it).ifPresent { s -> issue.status = s; dirty = true } }
                    }
                }
                ActionType.SET_ASSIGNEE -> {
                    // assignee requires UserRepository — handled by caller
                }
                ActionType.SET_PRIORITY -> {
                    val priority = params["priority"]
                    if (priority != null) {
                        runCatching {
                            issue.priority = IssuePriority.valueOf(priority)
                            dirty = true
                        }
                    }
                }
                ActionType.SEND_NOTIFICATION -> {
                    val message = params["message"] ?: "Automation rule fired"
                    val recipientId = params["recipientId"]?.let { UUID.fromString(it) }
                        ?: issue.assignee?.id ?: issue.reporter.id
                    notificationService.createDirect(
                        userId = recipientId,
                        type = NotificationType.AUTOMATION,
                        title = "Automation: ${issue.key}",
                        body = message,
                        link = "/p/${issue.project.key}/issues/${issue.key}"
                    )
                }
                ActionType.CREATE_COMMENT -> {
                    val body = params["body"]
                    if (body != null) {
                        commentRepository.save(Comment(issueId = issue.id, authorId = issue.reporter.id, body = body))
                    }
                }
                ActionType.CREATE_SUBTASK -> {
                    val title = params["title"] ?: "Auto-created subtask"
                    val maxKey = issueRepository.maxKeyNumberByProject(issue.project.id) + 1
                    issueRepository.save(
                        Issue(
                            key = "${issue.project.key}-$maxKey",
                            keyNumber = maxKey,
                            title = title,
                            type = IssueType.SUBTASK,
                            status = issue.status,
                            project = issue.project,
                            reporter = issue.reporter,
                            parent = issue
                        )
                    )
                }
            }
        }
        if (dirty) issueRepository.save(issue)
    }
}
