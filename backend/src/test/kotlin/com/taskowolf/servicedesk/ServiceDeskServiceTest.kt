package com.taskowolf.servicedesk

import com.taskowolf.issues.domain.IssuePriority
import com.taskowolf.servicedesk.application.ServiceDeskService
import com.taskowolf.servicedesk.domain.EscalationRule
import com.taskowolf.servicedesk.domain.ServiceDesk
import com.taskowolf.servicedesk.domain.SlaPolicy
import com.taskowolf.servicedesk.infrastructure.EscalationRuleRepository
import com.taskowolf.servicedesk.infrastructure.ServiceDeskRepository
import com.taskowolf.servicedesk.infrastructure.SlaPolicyRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

class ServiceDeskServiceTest {

    private val serviceDeskRepo = mockk<ServiceDeskRepository>()
    private val slaPolicyRepo = mockk<SlaPolicyRepository>()
    private val escalationRuleRepo = mockk<EscalationRuleRepository>()
    private val service = ServiceDeskService(serviceDeskRepo, slaPolicyRepo, escalationRuleRepo)

    private val projectId = UUID.randomUUID()

    @Test
    fun `enable creates ServiceDesk for project when none exists`() {
        every { serviceDeskRepo.findByProjectId(projectId) } returns null
        every { serviceDeskRepo.save(any()) } returnsArgument 0

        val desk = service.enable(projectId, null)

        assertEquals(true, desk.enabled)
        assertEquals(projectId, desk.projectId)
        assertNull(desk.emailAddress)
    }

    @Test
    fun `enable creates ServiceDesk with email address`() {
        every { serviceDeskRepo.findByProjectId(projectId) } returns null
        every { serviceDeskRepo.save(any()) } returnsArgument 0

        val desk = service.enable(projectId, "support@example.com")

        assertEquals(true, desk.enabled)
        assertEquals("support@example.com", desk.emailAddress)
    }

    @Test
    fun `enable re-enables existing disabled ServiceDesk`() {
        val existing = ServiceDesk(projectId = projectId, emailAddress = null, enabled = false)
        every { serviceDeskRepo.findByProjectId(projectId) } returns existing
        every { serviceDeskRepo.save(any()) } returnsArgument 0

        val desk = service.enable(projectId, "new@example.com")

        assertEquals(true, desk.enabled)
        assertEquals("new@example.com", desk.emailAddress)
    }

    @Test
    fun `findByProject returns existing ServiceDesk`() {
        val existing = ServiceDesk(projectId = projectId)
        every { serviceDeskRepo.findByProjectId(projectId) } returns existing

        val result = service.findByProject(projectId)

        assertEquals(existing, result)
    }

    @Test
    fun `findByProject returns null when none exists`() {
        every { serviceDeskRepo.findByProjectId(projectId) } returns null

        val result = service.findByProject(projectId)

        assertNull(result)
    }

    @Test
    fun `addSlaPolicy saves policy with correct fields`() {
        val serviceDeskId = UUID.randomUUID()
        val slot = slot<SlaPolicy>()
        every { slaPolicyRepo.save(capture(slot)) } returnsArgument 0

        service.addSlaPolicy(serviceDeskId, "High Priority SLA", IssuePriority.HIGH, 30, 240)

        assertEquals(serviceDeskId, slot.captured.serviceDeskId)
        assertEquals("High Priority SLA", slot.captured.name)
        assertEquals(IssuePriority.HIGH, slot.captured.priority)
        assertEquals(30, slot.captured.responseMinutes)
        assertEquals(240, slot.captured.resolutionMinutes)
    }

    @Test
    fun `listSlaPolicies returns policies for service desk`() {
        val serviceDeskId = UUID.randomUUID()
        val policy = SlaPolicy(serviceDeskId, "P1", IssuePriority.CRITICAL, 15, 60)
        every { slaPolicyRepo.findByServiceDeskId(serviceDeskId) } returns listOf(policy)

        val result = service.listSlaPolicies(serviceDeskId)

        assertEquals(1, result.size)
        assertEquals(policy, result[0])
    }

    @Test
    fun `deleteSlaPolicy calls deleteById`() {
        val policyId = UUID.randomUUID()
        every { slaPolicyRepo.deleteById(policyId) } returns Unit

        service.deleteSlaPolicy(policyId)

        verify { slaPolicyRepo.deleteById(policyId) }
    }

    @Test
    fun `addEscalationRule saves rule with correct fields`() {
        val slaPolicyId = UUID.randomUUID()
        val assigneeId = UUID.randomUUID()
        val notifyUserId = UUID.randomUUID()
        val slot = slot<EscalationRule>()
        every { escalationRuleRepo.save(capture(slot)) } returnsArgument 0

        service.addEscalationRule(slaPolicyId, 120, assigneeId, arrayOf(notifyUserId))

        assertEquals(slaPolicyId, slot.captured.slaPolicyId)
        assertEquals(120, slot.captured.escalateAfterMinutes)
        assertEquals(assigneeId, slot.captured.assigneeId)
        assertEquals(1, slot.captured.notifyUserIds.size)
        assertEquals(notifyUserId, slot.captured.notifyUserIds[0])
    }
}
