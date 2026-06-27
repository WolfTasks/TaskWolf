package com.taskowolf.issues.api

import com.taskowolf.auth.domain.User
import com.taskowolf.integrations.api.dto.IssueRefResponse
import com.taskowolf.integrations.infrastructure.IssueRefRepository
import com.taskowolf.issues.api.dto.CreateIssueRequest
import com.taskowolf.issues.api.dto.IssueResponse
import com.taskowolf.issues.api.dto.UpdateIssueRequest
import com.taskowolf.issues.application.IssueService
import com.taskowolf.labels.api.dto.LabelResponse
import com.taskowolf.labels.infrastructure.LabelRepository
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/projects/{key}/issues")
class IssueController(
    private val issueService: IssueService,
    private val issueRefRepository: IssueRefRepository,
    private val labelRepository: LabelRepository
) {

    @GetMapping
    fun list(
        @PathVariable key: String,
        @AuthenticationPrincipal user: User,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
        @RequestParam(defaultValue = "false") assigneeMe: Boolean,
        @RequestParam(required = false) sort: String?,
        @RequestParam(defaultValue = "false") overdue: Boolean,
        @RequestParam(required = false) labelId: UUID?
    ) = issueService.findByProject(key, user.id, page, size, assigneeMe, sort, overdue, labelId)
            .map { IssueResponse.from(it) }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @PathVariable key: String,
        @Valid @RequestBody request: CreateIssueRequest,
        @AuthenticationPrincipal user: User
    ) = IssueResponse.from(issueService.create(key, request, user))

    @GetMapping("/{issueKey}")
    fun get(
        @PathVariable key: String,
        @PathVariable issueKey: String,
        @AuthenticationPrincipal user: User
    ): IssueResponse {
        val issue = issueService.findByKey(key, issueKey, user.id)
        val refs = issueRefRepository.findByIssueIdOrderByCreatedAtAsc(issue.id).map { IssueRefResponse.from(it) }
        val labels = labelRepository.findByIssueId(issue.id).map { LabelResponse.from(it) }
        return IssueResponse.from(issue, refs, labels)
    }

    @PatchMapping("/{id}")
    fun update(
        @PathVariable key: String,
        @PathVariable id: UUID,
        @RequestBody request: UpdateIssueRequest,
        @AuthenticationPrincipal user: User
    ) = IssueResponse.from(issueService.update(key, id, request, user))

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable key: String,
        @PathVariable id: UUID,
        @AuthenticationPrincipal user: User
    ) = issueService.delete(key, id, user)
}
