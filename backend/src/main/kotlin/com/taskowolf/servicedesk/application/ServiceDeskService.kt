package com.taskowolf.servicedesk.application

import com.taskowolf.issues.domain.IssuePriority
import com.taskowolf.servicedesk.domain.EscalationRule
import com.taskowolf.servicedesk.domain.ServiceDesk
import com.taskowolf.servicedesk.domain.SlaPolicy
import com.taskowolf.servicedesk.infrastructure.EscalationRuleRepository
import com.taskowolf.servicedesk.infrastructure.ServiceDeskRepository
import com.taskowolf.servicedesk.infrastructure.SlaPolicyRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ServiceDeskService(
    private val serviceDeskRepo: ServiceDeskRepository,
    private val slaPolicyRepo: SlaPolicyRepository,
    private val escalationRuleRepo: EscalationRuleRepository
) {

    @Transactional
    fun enable(projectId: UUID, emailAddress: String?): ServiceDesk {
        val existing = serviceDeskRepo.findByProjectId(projectId)
        return if (existing != null) {
            existing.enabled = true
            existing.emailAddress = emailAddress
            serviceDeskRepo.save(existing)
        } else {
            serviceDeskRepo.save(ServiceDesk(projectId = projectId, emailAddress = emailAddress))
        }
    }

    @Transactional(readOnly = true)
    fun findByProject(projectId: UUID): ServiceDesk? = serviceDeskRepo.findByProjectId(projectId)

    @Transactional
    fun addSlaPolicy(
        serviceDeskId: UUID,
        name: String,
        priority: IssuePriority,
        responseMinutes: Int,
        resolutionMinutes: Int
    ): SlaPolicy = slaPolicyRepo.save(SlaPolicy(serviceDeskId, name, priority, responseMinutes, resolutionMinutes))

    @Transactional(readOnly = true)
    fun listSlaPolicies(serviceDeskId: UUID): List<SlaPolicy> = slaPolicyRepo.findByServiceDeskId(serviceDeskId)

    @Transactional
    fun deleteSlaPolicy(id: UUID) = slaPolicyRepo.deleteById(id)

    @Transactional
    fun addEscalationRule(
        slaPolicyId: UUID,
        escalateAfterMinutes: Int,
        assigneeId: UUID?,
        notifyUserIds: Array<UUID>
    ): EscalationRule = escalationRuleRepo.save(
        EscalationRule(slaPolicyId, escalateAfterMinutes, assigneeId, notifyUserIds)
    )
}
