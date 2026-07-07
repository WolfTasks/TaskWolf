package com.taskowolf.auth.infrastructure

import com.taskowolf.auth.domain.SystemRole
import com.taskowolf.auth.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    fun findByEmail(email: String): User?
    fun existsByEmail(email: String): Boolean
    fun findByDisplayNameIgnoreCase(displayName: String): User?
    fun countBySystemRoleAndActiveTrue(systemRole: SystemRole): Long
    fun findByDeletedAtIsNull(): List<User>
}
