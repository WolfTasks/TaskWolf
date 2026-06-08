package com.taskowolf.workflows.api

import com.taskowolf.auth.domain.User
import com.taskowolf.projects.application.ProjectService
import com.taskowolf.workflows.application.WorkflowService
import com.taskowolf.workflows.domain.WorkflowStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

data class StatusResponse(val id: UUID, val name: String, val category: String, val color: String, val position: Int) {
    companion object { fun from(s: WorkflowStatus) = StatusResponse(s.id, s.name, s.category.name, s.color, s.position) }
}
data class WorkflowResponse(val id: UUID, val name: String, val statuses: List<StatusResponse>)

@RestController
@RequestMapping("/api/v1/projects/{key}/workflows")
class WorkflowController(
    private val projectService: ProjectService,
    private val workflowService: WorkflowService
) {
    @GetMapping
    fun list(@PathVariable key: String, @AuthenticationPrincipal user: User): List<WorkflowResponse> {
        val project = projectService.requireMember(key, user.id)
        return workflowService.findByProject(project.id).map { wf ->
            WorkflowResponse(wf.id, wf.name, wf.statuses.map { StatusResponse.from(it) })
        }
    }
}
