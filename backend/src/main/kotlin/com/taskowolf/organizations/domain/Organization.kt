package com.taskowolf.organizations.domain

import com.taskowolf.core.domain.AuditableEntity
import jakarta.persistence.*

@Entity
@Table(name = "organizations")
class Organization(
    @Column(nullable = false)
    val name: String,

    @Column(nullable = false, unique = true)
    val slug: String
) : AuditableEntity()
