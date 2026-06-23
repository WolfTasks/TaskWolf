package com.taskowolf.auth.api

import com.taskowolf.auth.api.dto.*
import com.taskowolf.auth.application.AuthService
import com.taskowolf.auth.application.JwtService
import com.taskowolf.auth.domain.User
import com.taskowolf.organizations.api.dto.SwitchOrgResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val authService: AuthService, private val jwtService: JwtService) {

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
    fun switchOrg(
        @PathVariable orgId: UUID,
        @AuthenticationPrincipal user: User
    ) = SwitchOrgResponse(accessToken = jwtService.generateAccessToken(user.id, orgId))
}
