package com.taskowolf.auth.api.dto

import com.taskowolf.auth.domain.User
import java.util.UUID

data class UserSearchResponse(val id: UUID, val email: String, val displayName: String) {
    companion object {
        fun from(u: User) = UserSearchResponse(u.id, u.email, u.displayName)
    }
}
