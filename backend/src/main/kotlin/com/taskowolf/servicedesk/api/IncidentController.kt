package com.taskowolf.servicedesk.api

import com.taskowolf.projects.infrastructure.ProjectRepository
import com.taskowolf.servicedesk.api.dto.CreateIncidentRequest
import com.taskowolf.servicedesk.api.dto.IncidentResponse
import com.taskowolf.servicedesk.api.dto.ResolveIncidentRequest
import com.taskowolf.servicedesk.application.IncidentService
import com.taskowolf.servicedesk.domain.IncidentSeverity
import org.springframework.http.HttpStatus
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
    fun create(
        @PathVariable key: String,
        @RequestBody req: CreateIncidentRequest
    ): IncidentResponse =
        IncidentResponse.from(
            incidentService.create(
                req.issueId,
                IncidentSeverity.valueOf(req.severity),
                req.onCallAssigneeId,
                req.notifyUserIds
            )
        )

    @GetMapping
    fun list(@PathVariable key: String): List<IncidentResponse> {
        val project = projectRepository.findByKey(key) ?: error("Project not found: $key")
        return incidentService.listByProject(project.id).map { IncidentResponse.from(it) }
    }

    @PatchMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun resolve(
        @PathVariable key: String,
        @PathVariable id: UUID,
        @RequestBody req: ResolveIncidentRequest
    ) {
        incidentService.resolve(id, req.postmortemBody)
    }
}
