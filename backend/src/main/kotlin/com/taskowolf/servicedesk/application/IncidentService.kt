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
