package com.taskowolf.auth.infrastructure

import com.taskowolf.auth.domain.SystemRole
import com.taskowolf.auth.domain.User
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    fun findByEmail(email: String): User?
    fun existsByEmail(email: String): Boolean
    fun findByDisplayNameIgnoreCase(displayName: String): User?
    fun countBySystemRoleAndActiveTrue(systemRole: SystemRole): Long
    fun findByDeletedAtIsNull(): List<User>

    @Query("""
        SELECT u FROM User u
        WHERE u.active = true AND u.deletedAt IS NULL
          AND (LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(u.displayName) LIKE LOWER(CONCAT('%', :q, '%')))
        ORDER BY u.displayName ASC
    """)
    fun searchActive(q: String, pageable: Pageable): List<User>
}
