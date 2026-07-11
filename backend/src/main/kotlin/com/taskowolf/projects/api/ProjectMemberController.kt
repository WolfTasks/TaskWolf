package com.taskowolf.projects.api

import com.taskowolf.auth.domain.User
import com.taskowolf.projects.api.dto.AddProjectMemberRequest
import com.taskowolf.projects.api.dto.ProjectMemberResponse
import com.taskowolf.projects.api.dto.UpdateProjectMemberRoleRequest
import com.taskowolf.projects.application.ProjectService
import com.taskowolf.projects.infrastructure.ProjectMemberRepository
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/projects/{key}/members")
class ProjectMemberController(
    private val projectService: ProjectService,
    private val projectMemberRepository: ProjectMemberRepository
) {
    @GetMapping
    // Keeps the session open while ProjectMemberResponse.from() reads the lazy user association (OSIV is disabled).
    @Transactional(readOnly = true)
    fun list(@PathVariable key: String, @AuthenticationPrincipal user: User): List<ProjectMemberResponse> {
        val project = projectService.requireMember(key, user.id)
        return projectMemberRepository.findAllByProjectId(project.id).map { ProjectMemberResponse.from(it) }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun add(
        @PathVariable key: String,
        @Valid @RequestBody request: AddProjectMemberRequest,
        @AuthenticationPrincipal user: User
    ) = ProjectMemberResponse.from(projectService.addMember(key, user.id, request.userId, request.role))

    @PatchMapping("/{userId}")
    // Keeps the session open while ProjectMemberResponse.from() reads the lazy user association (OSIV is disabled).
    @Transactional
    fun changeRole(
        @PathVariable key: String,
        @PathVariable userId: UUID,
        @Valid @RequestBody request: UpdateProjectMemberRoleRequest,
        @AuthenticationPrincipal user: User
    ) = ProjectMemberResponse.from(projectService.changeMemberRole(key, user.id, userId, request.role))

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun remove(
        @PathVariable key: String,
        @PathVariable userId: UUID,
        @AuthenticationPrincipal user: User
    ) = projectService.removeMember(key, user.id, userId)
}
