package com.taskowolf.auth.api

import com.taskowolf.auth.api.dto.*
import com.taskowolf.auth.application.AuthService
import com.taskowolf.auth.application.JwtService
import com.taskowolf.auth.domain.User
import com.taskowolf.core.infrastructure.ForbiddenException
import com.taskowolf.organizations.api.dto.SwitchOrgResponse
import com.taskowolf.organizations.application.OrganizationService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService,
    private val jwtService: JwtService,
    private val organizationService: OrganizationService
) {

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@Valid @RequestBody request: RegisterRequest) =
        authService.register(request)

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest) =
        authService.login(request)

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody body: RefreshRequest) =
        authService.refresh(body.refreshToken)

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(@AuthenticationPrincipal user: User?) {
        if (user != null) authService.logout(user.id)
    }

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal user: User) = UserResponse.from(user)

    @PostMapping("/switch-org/{orgId}")
    @PreAuthorize("isAuthenticated()")
    fun switchOrg(
        @PathVariable orgId: UUID,
        @AuthenticationPrincipal user: User
    ): SwitchOrgResponse {
        val userOrgs = organizationService.listOrgsForUser(user.id)
        if (userOrgs.none { it.id == orgId }) {
            throw ForbiddenException.keyed("org.notMemberCurrent")
        }
        return SwitchOrgResponse(accessToken = jwtService.generateAccessToken(user.id, orgId))
    }
}
