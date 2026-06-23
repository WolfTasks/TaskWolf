package com.taskowolf.audit.domain

import jakarta.persistence.*

@Entity
@Table(name = "audit_config")
class AuditConfig(
    @Id @Enumerated(EnumType.STRING) val level: AuditLevel,
    @Column(nullable = false) var enabled: Boolean
)
