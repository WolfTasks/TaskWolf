package com.taskowolf.servicedesk.api

import com.taskowolf.core.infrastructure.BadRequestException
import com.taskowolf.core.infrastructure.NotFoundException
import com.taskowolf.projects.infrastructure.ProjectRepository
import com.taskowolf.servicedesk.api.dto.CreateIncidentRequest
import com.taskowolf.servicedesk.api.dto.IncidentResponse
import com.taskowolf.servicedesk.api.dto.ResolveIncidentRequest
import com.taskowolf.servicedesk.application.IncidentService
import com.taskowolf.servicedesk.domain.IncidentSeverity
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/projects/{key}/incidents")
class IncidentController(
    private val incidentService: IncidentService,
    private val projectRepository: ProjectRepository
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@projectSecurity.isProjectAdmin(#key, authentication)")
    fun create(
        @PathVariable key: String,
        @RequestBody req: CreateIncidentRequest
    ): IncidentResponse {
        val severity = try {
            IncidentSeverity.valueOf(req.severity)
        } catch (e: IllegalArgumentException) {
            throw BadRequestException.keyed("incident.invalidSeverity", req.severity, IncidentSeverity.entries.joinToString())
        }
        return IncidentResponse.from(
            incidentService.create(
                req.issueId,
                severity,
                req.onCallAssigneeId,
                req.notifyUserIds
            )
        )
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    fun list(@PathVariable key: String): List<IncidentResponse> {
        val project = projectRepository.findByKey(key) ?: throw NotFoundException.keyed("project.notFound", key)
        return incidentService.listByProject(project.id).map { IncidentResponse.from(it) }
    }

    @PatchMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@projectSecurity.isProjectAdmin(#key, authentication)")
    fun resolve(
        @PathVariable key: String,
        @PathVariable id: UUID,
        @RequestBody req: ResolveIncidentRequest
    ) {
        incidentService.resolve(id, req.postmortemBody)
    }
}
