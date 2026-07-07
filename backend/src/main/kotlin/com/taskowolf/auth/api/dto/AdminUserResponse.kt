package com.taskowolf.auth.api.dto

import com.taskowolf.auth.domain.User
import java.util.UUID

data class AdminUserResponse(
    val id: UUID,
    val email: String,
    val displayName: String,
    val systemRole: String,
    val active: Boolean
) {
    companion object {
        fun from(u: User) = AdminUserResponse(u.id, u.email, u.displayName, u.systemRole.name, u.active)
    }
}
