package com.taskowolf.sprints.api

import com.taskowolf.auth.domain.User
import com.taskowolf.sprints.api.dto.*
import com.taskowolf.sprints.application.SprintService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/projects/{key}/sprints")
class SprintController(private val sprintService: SprintService) {

    @GetMapping
    fun list(@PathVariable key: String, @AuthenticationPrincipal user: User) =
        sprintService.listByProject(key, user.id).map { SprintResponse.from(it) }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@projectSecurity.canWrite(#key, authentication)")
    fun create(
        @PathVariable key: String,
        @Valid @RequestBody request: CreateSprintRequest,
        @AuthenticationPrincipal user: User
    ) = SprintResponse.from(sprintService.create(key, request, user))

    @PatchMapping("/{sprintId}")
    @PreAuthorize("@projectSecurity.canWrite(#key, authentication)")
    fun update(
        @PathVariable key: String,
        @PathVariable sprintId: UUID,
        @RequestBody request: UpdateSprintRequest,
        @AuthenticationPrincipal user: User
    ) = SprintResponse.from(sprintService.update(key, sprintId, request, user))

    @PostMapping("/{sprintId}/start")
    @PreAuthorize("@projectSecurity.canWrite(#key, authentication)")
    fun start(
        @PathVariable key: String,
        @PathVariable sprintId: UUID,
        @AuthenticationPrincipal user: User
    ) = SprintResponse.from(sprintService.start(key, sprintId, user))

    @PostMapping("/{sprintId}/complete")
    @PreAuthorize("@projectSecurity.canWrite(#key, authentication)")
    fun complete(
        @PathVariable key: String,
        @PathVariable sprintId: UUID,
        @AuthenticationPrincipal user: User
    ): SprintCompleteResponse {
        val result = sprintService.complete(key, sprintId, user)
        return SprintCompleteResponse(SprintResponse.from(result.sprint), result.movedToBacklogCount)
    }

    @PutMapping("/{sprintId}/issues/{issueId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@projectSecurity.canWrite(#key, authentication)")
    fun assignIssue(
        @PathVariable key: String,
        @PathVariable sprintId: UUID,
        @PathVariable issueId: UUID,
        @AuthenticationPrincipal user: User
    ) = sprintService.assignIssue(key, sprintId, issueId, user)

    @DeleteMapping("/{sprintId}/issues/{issueId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@projectSecurity.canWrite(#key, authentication)")
    fun unassignIssue(
        @PathVariable key: String,
        @PathVariable sprintId: UUID,
        @PathVariable issueId: UUID,
        @AuthenticationPrincipal user: User
    ) = sprintService.unassignIssue(key, sprintId, issueId, user)
}
