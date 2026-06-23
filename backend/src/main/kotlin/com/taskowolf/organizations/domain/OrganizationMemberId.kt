package com.taskowolf.organizations.domain

import jakarta.persistence.Embeddable
import java.io.Serializable
import java.util.UUID

@Embeddable
data class OrganizationMemberId(
    val orgId: UUID,
    val userId: UUID
) : Serializable
