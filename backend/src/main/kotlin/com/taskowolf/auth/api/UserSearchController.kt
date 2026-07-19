package com.taskowolf.auth.api

import com.taskowolf.auth.api.dto.UserSearchResponse
import com.taskowolf.auth.application.UserSearchService
import com.taskowolf.auth.domain.User
import com.taskowolf.core.infrastructure.ForbiddenException
import com.taskowolf.projects.application.ProjectService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/users")
class UserSearchController(
    private val userSearchService: UserSearchService,
    private val projectService: ProjectService
) {
    @GetMapping("/search")
    fun search(
        @RequestParam q: String,
        @AuthenticationPrincipal user: User
    ): List<UserSearchResponse> {
        if (!projectService.canManageAnyProjectMembers(user)) {
            throw ForbiddenException.keyed("auth.searchNotAllowed")
        }
        return userSearchService.search(q).map { UserSearchResponse.from(it) }
    }
}
