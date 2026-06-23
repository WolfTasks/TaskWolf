package com.taskowolf.servicedesk.application

import com.taskowolf.issues.infrastructure.IssueRepository
import com.taskowolf.issues.domain.events.IssueStatusChangedEvent
import com.taskowolf.workflows.domain.StatusCategory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class SlaEventListener(private val issueRepository: IssueRepository) {

    @EventListener
    @Transactional
    fun onStatusChanged(e: IssueStatusChangedEvent) {
        val issue = issueRepository.findById(e.issue.id).orElse(null) ?: return
        if (e.newStatus.category == StatusCategory.IN_PROGRESS && issue.slaStartTime == null) {
            issue.slaStartTime = Instant.now()
        } else if (e.newStatus.category == StatusCategory.DONE) {
            issue.slaStartTime = null
        }
    }
}
