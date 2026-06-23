package com.taskowolf.servicedesk

import com.taskowolf.comments.domain.Comment
import com.taskowolf.comments.infrastructure.CommentRepository
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
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.UUID

class IncidentServiceTest {

    private val incidentRepo = mockk<IncidentRepository>()
    private val commentRepo = mockk<CommentRepository>()
    private val notificationService = mockk<NotificationService>(relaxed = true)
    private val service = IncidentService(incidentRepo, commentRepo, notificationService)

    private val issueId = UUID.randomUUID()

    @Test
    fun `create saves incident and notifies users`() {
        val onCallId = UUID.randomUUID()
        val notifyId1 = UUID.randomUUID()
        val notifyId2 = UUID.randomUUID()
        every { incidentRepo.save(any()) } returnsArgument 0

        val incident = service.create(issueId, IncidentSeverity.P1, onCallId, listOf(notifyId1, notifyId2))

        assertEquals(issueId, incident.issueId)
        assertEquals(IncidentSeverity.P1, incident.severity)
        assertEquals(onCallId, incident.onCallAssigneeId)
        verify(exactly = 2) { notificationService.createDirect(any(), NotificationType.AUTOMATION, any(), any(), any()) }
    }

    @Test
    fun `create without notify users saves incident without notifications`() {
        every { incidentRepo.save(any()) } returnsArgument 0

        val incident = service.create(issueId, IncidentSeverity.P2, null, emptyList())

        assertEquals(issueId, incident.issueId)
        assertEquals(IncidentSeverity.P2, incident.severity)
        verify(exactly = 0) { notificationService.createDirect(any(), any(), any(), any(), any()) }
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
        assert(commentSlot.captured.body.contains("Postmortem"))
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
