package com.taskowolf.auth.domain

import com.taskowolf.core.domain.AuditableEntity
import jakarta.persistence.*

@Entity
@Table(name = "sso_configs")
class SsoConfig(
    @Column(nullable = false) val name: String,
    @Column(nullable = false) val issuerUrl: String,
    @Column(nullable = false) val clientId: String,
    @Column(nullable = false) var clientSecretEnc: String,
    @Column(nullable = false) var enabled: Boolean = true,
    @Column(nullable = false) var autoProvision: Boolean = true
) : AuditableEntity()
