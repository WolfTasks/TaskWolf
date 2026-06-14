package com.taskowolf.workflows.api

import com.taskowolf.auth.domain.User
import com.taskowolf.projects.application.ProjectService
import com.taskowolf.workflows.application.StatusPositionInput
import com.taskowolf.workflows.application.WorkflowService
import com.taskowolf.workflows.domain.StatusCategory
import com.taskowolf.workflows.domain.TransitionGuard
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

data class WorkflowEditorResponse(
    val id: UUID,
    val name: String,
    val statuses: List<StatusResponse>,
    val transitions: List<TransitionResponse>,
    val layout: List<StatusPositionResponse>
)

data class TransitionResponse(
    val id: UUID,
    val fromStatusId: UUID?,
    val toStatusId: UUID,
    val guards: String?
)

data class StatusPositionResponse(val statusId: UUID, val x: Int, val y: Int)

data class CreateStatusRequest(val name: String, val category: String, val color: String = "#6c8fef")
data class UpdateStatusRequest(val name: String?, val category: String?, val color: String?)
data class CreateTransitionRequest(val fromStatusId: UUID?, val toStatusId: UUID)
data class UpdateGuardsRequest(val guards: List<TransitionGuard>)
data class SaveLayoutRequest(val positions: List<StatusPositionInput>)

@RestController
@RequestMapping("/api/v1/projects/{key}/workflow")
class WorkflowEditorController(
    private val projectService: ProjectService,
    private val workflowService: WorkflowService
) {
    @GetMapping
    fun get(@PathVariable key: String, @AuthenticationPrincipal user: User): WorkflowEditorResponse {
        val project = projectService.requireMember(key, user.id)
        // Use dedicated editor method that fetches statuses, transitions, and layout
        // within a single transaction to avoid LazyInitializationException
        val data = workflowService.findWorkflowForEditor(project.id)
        return WorkflowEditorResponse(
            id = data.id,
            name = data.name,
            statuses = data.statuses.map { StatusResponse.from(it) },
            transitions = data.transitions.map {
                TransitionResponse(it.id, it.fromStatus?.id, it.toStatus.id, it.guards)
            },
            layout = data.layout.map { StatusPositionResponse(it.id.statusId, it.x, it.y) }
        )
    }

    @PostMapping("/statuses")
    @ResponseStatus(HttpStatus.CREATED)
    fun createStatus(
        @PathVariable key: String,
        @RequestBody req: CreateStatusRequest,
        @AuthenticationPrincipal user: User
    ): StatusResponse {
        val project = projectService.requireAdmin(key, user.id)
        val wf = workflowService.findWorkflowByProjectId(project.id)
        val status = workflowService.createStatus(wf.id, req.name, StatusCategory.valueOf(req.category), req.color)
        return StatusResponse.from(status)
    }

    @PutMapping("/statuses/{sid}")
    fun updateStatus(
        @PathVariable key: String,
        @PathVariable sid: UUID,
        @RequestBody req: UpdateStatusRequest,
        @AuthenticationPrincipal user: User
    ): StatusResponse {
        projectService.requireAdmin(key, user.id)
        val status = workflowService.updateStatus(
            sid, req.name, req.category?.let { StatusCategory.valueOf(it) }, req.color
        )
        return StatusResponse.from(status)
    }

    @DeleteMapping("/statuses/{sid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteStatus(
        @PathVariable key: String,
        @PathVariable sid: UUID,
        @AuthenticationPrincipal user: User
    ) {
        projectService.requireAdmin(key, user.id)
        workflowService.deleteStatus(sid)
    }

    @PostMapping("/transitions")
    @ResponseStatus(HttpStatus.CREATED)
    fun createTransition(
        @PathVariable key: String,
        @RequestBody req: CreateTransitionRequest,
        @AuthenticationPrincipal user: User
    ): TransitionResponse {
        val project = projectService.requireAdmin(key, user.id)
        val wf = workflowService.findWorkflowByProjectId(project.id)
        val t = workflowService.createTransition(wf.id, req.fromStatusId, req.toStatusId)
        return TransitionResponse(t.id, t.fromStatus?.id, t.toStatus.id, t.guards)
    }

    @DeleteMapping("/transitions/{tid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteTransition(
        @PathVariable key: String,
        @PathVariable tid: UUID,
        @AuthenticationPrincipal user: User
    ) {
        projectService.requireAdmin(key, user.id)
        workflowService.deleteTransition(tid)
    }

    @PutMapping("/transitions/{tid}/guards")
    fun updateGuards(
        @PathVariable key: String,
        @PathVariable tid: UUID,
        @RequestBody req: UpdateGuardsRequest,
        @AuthenticationPrincipal user: User
    ): TransitionResponse {
        projectService.requireAdmin(key, user.id)
        val t = workflowService.updateGuards(tid, req.guards)
        return TransitionResponse(t.id, t.fromStatus?.id, t.toStatus.id, t.guards)
    }

    @PutMapping("/layout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun saveLayout(
        @PathVariable key: String,
        @RequestBody req: SaveLayoutRequest,
        @AuthenticationPrincipal user: User
    ) {
        val project = projectService.requireAdmin(key, user.id)
        val wf = workflowService.findWorkflowByProjectId(project.id)
        workflowService.saveLayout(wf.id, req.positions)
    }
}
