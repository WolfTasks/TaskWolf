package com.taskowolf.auth.api

import com.taskowolf.auth.api.dto.*
import com.taskowolf.auth.application.AuthService
import com.taskowolf.auth.domain.User
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val authService: AuthService) {

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@Valid @RequestBody request: RegisterRequest) =
        authService.register(request)

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest) =
        authService.login(request)

    @PostMapping("/refresh")
    fun refresh(@RequestBody body: Map<String, String>) =
        authService.refresh(body["refreshToken"] ?: error("Missing refreshToken"))

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal user: User) = UserResponse.from(user)
}
