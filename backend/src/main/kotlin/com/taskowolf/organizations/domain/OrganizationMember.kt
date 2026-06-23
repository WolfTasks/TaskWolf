package com.taskowolf.organizations.domain

import jakarta.persistence.*

@Entity
@Table(name = "organization_members")
class OrganizationMember(
    @EmbeddedId
    val id: OrganizationMemberId,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var role: OrgRole
)
