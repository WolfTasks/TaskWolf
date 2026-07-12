package com.taskowolf.auth.api

import com.taskowolf.auth.api.dto.ChangePasswordRequest
import com.taskowolf.auth.api.dto.UpdateLanguageRequest
import com.taskowolf.auth.api.dto.UpdateProfileRequest
import com.taskowolf.auth.api.dto.UserResponse
import com.taskowolf.auth.application.UserAccountService
import com.taskowolf.auth.domain.User
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/me")
class MeController(private val userAccountService: UserAccountService) {

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteAccount(@AuthenticationPrincipal user: User) = userAccountService.softDelete(user.id)

    @PatchMapping
    fun updateProfile(
        @Valid @RequestBody request: UpdateProfileRequest,
        @AuthenticationPrincipal user: User
    ) = UserResponse.from(userAccountService.updateProfile(user.id, request.displayName))

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun changePassword(
        @Valid @RequestBody request: ChangePasswordRequest,
        @AuthenticationPrincipal user: User
    ) = userAccountService.changePassword(user.id, request.currentPassword, request.newPassword)

    @PatchMapping("/language")
    fun updateLanguage(
        @Valid @RequestBody request: UpdateLanguageRequest,
        @AuthenticationPrincipal user: User
    ) = UserResponse.from(userAccountService.updateLanguage(user.id, request.language))
}
