package com.taskowolf.auth.domain

import com.taskowolf.core.domain.AuditableEntity
import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "users")
class User(
    @Column(nullable = false, unique = true)
    val email: String,

    @Column(nullable = false)
    var displayName: String,

    var avatarUrl: String? = null,

    var passwordHash: String? = null,

    var oauthProvider: String? = null,

    var oauthSubject: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var systemRole: SystemRole = SystemRole.MEMBER,

    @Column
    var orgId: UUID? = null
) : AuditableEntity()
