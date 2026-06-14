package com.taskowolf.automation.domain

import com.taskowolf.core.domain.AuditableEntity
import jakarta.persistence.*

@Entity
@Table(name = "rule_conditions")
class RuleCondition(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    val group: RuleConditionGroup,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    val type: ConditionType,

    @Column(nullable = false, length = 20)
    val operator: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    val params: String
) : AuditableEntity()
