package com.taskowolf.comments.api

import com.taskowolf.auth.domain.User
import com.taskowolf.comments.api.dto.ActivityResponse
import com.taskowolf.comments.api.dto.CommentResponse
import com.taskowolf.comments.api.dto.CreateCommentRequest
import com.taskowolf.comments.api.dto.EditCommentRequest
import com.taskowolf.comments.application.ActivityService
import com.taskowolf.comments.application.CommentService
import com.taskowolf.issues.application.IssueService
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/projects/{key}/issues/{issueKey}")
class CommentController(
    private val commentService: CommentService,
    private val issueService: IssueService,
    private val activityService: ActivityService
) {
    @PostMapping("/comments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@projectSecurity.canWrite(#key, authentication)")
    fun addComment(
        @PathVariable key: String,
        @PathVariable issueKey: String,
        @Valid @RequestBody request: CreateCommentRequest,
        @AuthenticationPrincipal user: User
    ) = CommentResponse.from(commentService.addComment(key, issueKey, request.body, user))

    @GetMapping("/comments")
    fun listComments(
        @PathVariable key: String,
        @PathVariable issueKey: String,
        @AuthenticationPrincipal user: User,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "5") size: Int
    ) = commentService.listComments(key, issueKey, user.id, page, size).map { CommentResponse.from(it) }

    @PutMapping("/comments/{commentId}")
    @PreAuthorize("@projectSecurity.canWrite(#key, authentication)")
    fun editComment(
        @PathVariable key: String,
        @PathVariable issueKey: String,
        @PathVariable commentId: UUID,
        @Valid @RequestBody request: EditCommentRequest,
        @AuthenticationPrincipal user: User
    ) = CommentResponse.from(commentService.editComment(commentId, request.body, user))

    @DeleteMapping("/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@projectSecurity.canWrite(#key, authentication)")
    fun deleteComment(
        @PathVariable key: String,
        @PathVariable issueKey: String,
        @PathVariable commentId: UUID,
        @AuthenticationPrincipal user: User
    ) = commentService.deleteComment(commentId, key, user)

    @GetMapping("/activity")
    fun listActivity(
        @PathVariable key: String,
        @PathVariable issueKey: String,
        @AuthenticationPrincipal user: User,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "5") size: Int
    ): Page<ActivityResponse> {
        val issue = issueService.findByKey(key, issueKey, user.id)
        return activityService.listActivity(issue.id, page, size).map { ActivityResponse.from(it) }
    }
}
