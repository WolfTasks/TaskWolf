package com.taskowolf.boards.api

import com.taskowolf.auth.domain.User
import com.taskowolf.boards.api.dto.BoardMoveRequest
import com.taskowolf.boards.application.BoardService
import com.taskowolf.issues.api.dto.UpdateIssueRequest
import com.taskowolf.issues.application.IssueService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/projects/{key}")
class BoardController(
    private val boardService: BoardService,
    private val issueService: IssueService
) {
    @GetMapping("/board")
    fun getBoard(@PathVariable key: String, @AuthenticationPrincipal user: User): ResponseEntity<*> {
        val board = boardService.getBoard(key, user.id)
            ?: return ResponseEntity.noContent().build<Unit>()
        return ResponseEntity.ok(board)
    }

    @PatchMapping("/board/move")
    fun move(
        @PathVariable key: String,
        @RequestBody request: BoardMoveRequest,
        @AuthenticationPrincipal user: User
    ) {
        issueService.update(key, request.issueId, UpdateIssueRequest(statusId = request.newStatusId), user)
    }

    @GetMapping("/backlog")
    fun getBacklog(@PathVariable key: String, @AuthenticationPrincipal user: User) =
        boardService.getBacklog(key, user.id)
}
