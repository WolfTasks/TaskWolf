package com.taskowolf.boards.events

import com.taskowolf.issues.domain.events.IssueStatusChangedEvent
import com.taskowolf.sprints.domain.events.SprintCompletedEvent
import com.taskowolf.sprints.domain.events.SprintStartedEvent
import org.springframework.context.event.EventListener
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component

@Component
class BoardEventPublisher(private val messagingTemplate: SimpMessagingTemplate) {

    @EventListener
    fun onIssueStatusChanged(event: IssueStatusChangedEvent) {
        messagingTemplate.convertAndSend(
            "/topic/projects/${event.issue.project.key}",
            mapOf("type" to "ISSUE_MOVED", "issueId" to event.issue.id, "newStatusId" to event.newStatus.id, "projectKey" to event.issue.project.key)
        )
    }

    @EventListener
    fun onSprintStarted(event: SprintStartedEvent) {
        messagingTemplate.convertAndSend(
            "/topic/projects/${event.sprint.project.key}",
            mapOf("type" to "SPRINT_UPDATED", "sprintId" to event.sprint.id, "projectKey" to event.sprint.project.key)
        )
    }

    @EventListener
    fun onSprintCompleted(event: SprintCompletedEvent) {
        messagingTemplate.convertAndSend(
            "/topic/projects/${event.sprint.project.key}",
            mapOf("type" to "SPRINT_UPDATED", "sprintId" to event.sprint.id, "projectKey" to event.sprint.project.key)
        )
    }
}
