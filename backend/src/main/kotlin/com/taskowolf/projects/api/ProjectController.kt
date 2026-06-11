package com.taskowolf.projects.api

import com.taskowolf.auth.api.dto.UserResponse
import com.taskowolf.auth.domain.User
import com.taskowolf.projects.api.dto.CreateProjectRequest
import com.taskowolf.projects.api.dto.ProjectResponse
import com.taskowolf.projects.application.ProjectService
import com.taskowolf.projects.infrastructure.ProjectMemberRepository
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/projects")
class ProjectController(
    private val projectService: ProjectService,
    private val projectMemberRepository: ProjectMemberRepository
) {

    @GetMapping
    fun list(@AuthenticationPrincipal user: User) =
        projectService.findAllForUser(user.id).map { ProjectResponse.from(it) }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: CreateProjectRequest,
        @AuthenticationPrincipal user: User
    ) = ProjectResponse.from(projectService.create(request, user))

    @GetMapping("/{key}")
    fun get(@PathVariable key: String, @AuthenticationPrincipal user: User) =
        ProjectResponse.from(projectService.requireMember(key, user.id))

    @GetMapping("/{key}/members")
    fun getMembers(
        @PathVariable key: String,
        @AuthenticationPrincipal user: User
    ): ResponseEntity<List<UserResponse>> {
        val project = projectService.requireMember(key, user.id)
        val members = projectMemberRepository.findAllByProjectId(project.id)
            .map { UserResponse.from(it.user) }
        return ResponseEntity.ok(members)
    }
}
