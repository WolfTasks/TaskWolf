package com.taskowolf.servicedesk.api

import com.taskowolf.auth.domain.User
import com.taskowolf.issues.api.dto.IssueResponse
import com.taskowolf.issues.application.IssueService
import com.taskowolf.issues.domain.IssuePriority
import com.taskowolf.projects.infrastructure.ProjectRepository
import com.taskowolf.servicedesk.api.dto.*
import com.taskowolf.servicedesk.application.ServiceDeskService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@RestController
@RequestMapping("/api/v1/projects/{key}/service-desk")
class ServiceDeskController(
    private val serviceDeskService: ServiceDeskService,
    private val issueService: IssueService,
    private val projectRepository: ProjectRepository
) {

    @PostMapping("/enable")
    @PreAuthorize("@projectSecurity.isProjectAdmin(#key, authentication)")
    fun enable(
        @PathVariable key: String,
        @RequestBody req: CreateServiceDeskRequest
    ): ServiceDeskResponse {
        val project = projectRepository.findByKey(key) ?: error("Project not found: $key")
        return ServiceDeskResponse.from(serviceDeskService.enable(project.id, req.emailAddress))
    }

    @GetMapping
    fun get(@PathVariable key: String): ServiceDeskResponse {
        val project = projectRepository.findByKey(key) ?: error("Project not found: $key")
        return ServiceDeskResponse.from(
            serviceDeskService.findByProject(project.id) ?: error("Service desk not enabled for project: $key")
        )
    }

    /** Permit-all: external users can submit tickets without authentication. */
    @PostMapping("/tickets")
    @ResponseStatus(HttpStatus.CREATED)
    fun submitTicket(
        @PathVariable key: String,
        @RequestBody req: SubmitTicketRequest
    ) {
        val project = projectRepository.findByKey(key) ?: error("Project not found: $key")
        issueService.createTicketFromEmail(project.id, req.title, req.description, req.senderEmail ?: "anonymous")
    }

    @GetMapping("/tickets")
    @PreAuthorize("isAuthenticated()")
    fun listTickets(
        @PathVariable key: String,
        @AuthenticationPrincipal user: User,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int
    ): List<IssueResponse> =
        issueService.findByProject(key, user.id, page, size)
            .content.map { IssueResponse.from(it) }

    @PostMapping("/sla-policies")
    @PreAuthorize("@projectSecurity.isProjectAdmin(#key, authentication)")
    fun addSlaPolicy(
        @PathVariable key: String,
        @Valid @RequestBody req: CreateSlaPolicyRequest
    ): SlaPolicyResponse {
        val project = projectRepository.findByKey(key) ?: error("Project not found: $key")
        val sd = serviceDeskService.findByProject(project.id) ?: error("Service desk not enabled for project: $key")
        val priority = try {
            IssuePriority.valueOf(req.priority)
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Invalid priority: ${req.priority}. Valid values: ${IssuePriority.entries.joinToString()}")
        }
        return SlaPolicyResponse.from(
            serviceDeskService.addSlaPolicy(sd.id, req.name, priority, req.responseMinutes, req.resolutionMinutes)
        )
    }

    @GetMapping("/sla-policies")
    fun listSlaPolicies(@PathVariable key: String): List<SlaPolicyResponse> {
        val project = projectRepository.findByKey(key) ?: error("Project not found: $key")
        val sd = serviceDeskService.findByProject(project.id) ?: error("Service desk not enabled for project: $key")
        return serviceDeskService.listSlaPolicies(sd.id).map { SlaPolicyResponse.from(it) }
    }

    @DeleteMapping("/sla-policies/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@projectSecurity.isProjectAdmin(#key, authentication)")
    fun deleteSlaPolicy(
        @PathVariable key: String,
        @PathVariable id: UUID
    ) {
        serviceDeskService.deleteSlaPolicy(id)
    }

    @PostMapping("/sla-policies/{id}/escalation-rules")
    @PreAuthorize("@projectSecurity.isProjectAdmin(#key, authentication)")
    fun addEscalationRule(
        @PathVariable key: String,
        @PathVariable id: UUID,
        @RequestBody req: CreateEscalationRuleRequest
    ): EscalationRuleResponse =
        EscalationRuleResponse.from(
            serviceDeskService.addEscalationRule(
                id,
                req.escalateAfterMinutes,
                req.assigneeId,
                req.notifyUserIds.toTypedArray()
            )
        )
}
