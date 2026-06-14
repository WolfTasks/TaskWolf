package com.taskowolf.automation.domain

import com.taskowolf.core.domain.AuditableEntity
import jakarta.persistence.*

@Entity
@Table(name = "rule_actions")
class RuleAction(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id", nullable = false)
    val rule: AutomationRule,

    @Column(nullable = false)
    val position: Int,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    val type: ActionType,

    @Column(nullable = false, columnDefinition = "TEXT")
    val params: String
) : AuditableEntity()
