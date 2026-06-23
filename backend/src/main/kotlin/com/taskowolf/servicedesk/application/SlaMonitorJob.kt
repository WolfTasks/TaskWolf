package com.taskowolf.servicedesk.application

import com.taskowolf.audit.application.AuditService
import com.taskowolf.audit.domain.AuditAction
import com.taskowolf.audit.domain.AuditLevel
import com.taskowolf.issues.infrastructure.IssueRepository
import com.taskowolf.notifications.application.NotificationService
import com.taskowolf.notifications.domain.NotificationType
import com.taskowolf.servicedesk.infrastructure.EscalationRuleRepository
import com.taskowolf.servicedesk.infrastructure.ServiceDeskRepository
import com.taskowolf.servicedesk.infrastructure.SlaPolicyRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

@Component
class SlaMonitorJob(
    private val issueRepository: IssueRepository,
    private val serviceDeskRepo: ServiceDeskRepository,
    private val slaPolicyRepo: SlaPolicyRepository,
    private val escalationRuleRepo: EscalationRuleRepository,
    private val notificationService: NotificationService,
    private val auditService: AuditService
) {

    private val log = LoggerFactory.getLogger(SlaMonitorJob::class.java)

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    fun run() {
        val now = Instant.now()
        issueRepository.findBySlaStartTimeIsNotNull().forEach { issue ->
            // Skip if issue appears to be resolved (slaStartTime should be null after fix, but guard against race)
            if (issue.slaStartTime == null) return@forEach
            val projectId = issue.project.id
            val desk = serviceDeskRepo.findByProjectId(projectId) ?: return@forEach
            val policy = slaPolicyRepo.findByServiceDeskId(desk.id)
                .find { it.priority == issue.priority } ?: return@forEach

            val elapsed = Duration.between(issue.slaStartTime, now).toMinutes()
            if (elapsed >= policy.resolutionMinutes) {
                log.warn("SLA breached for issue ${issue.key} (elapsed: ${elapsed}m, limit: ${policy.resolutionMinutes}m)")

                val rules = escalationRuleRepo.findBySlaPolicyId(policy.id)
                rules.forEach { rule ->
                    rule.notifyUserIds.forEach { uid ->
                        notificationService.createDirect(
                            userId = uid,
                            type = NotificationType.SLA_BREACHED,
                            title = "SLA Breached: ${issue.key}",
                            body = "Issue ${issue.key} has exceeded its SLA resolution time of ${policy.resolutionMinutes} minutes.",
                            link = "/issues/${issue.key}"
                        )
                    }
                }

                auditService.log(
                    level = AuditLevel.WRITE,
                    action = AuditAction.SLA_BREACHED,
                    userEmail = "system",
                    projectId = projectId,
                    resourceType = "ISSUE",
                    resourceId = issue.id.toString()
                )
            }
        }
    }
}
