package com.taskowolf.auth.api.dto

import com.taskowolf.auth.domain.User
import java.util.UUID

data class UserResponse(val id: UUID, val email: String, val displayName: String, val avatarUrl: String?) {
    companion object {
        fun from(user: User) = UserResponse(user.id, user.email, user.displayName, user.avatarUrl)
    }
}
