package com.taskowolf.automation.domain

import com.taskowolf.core.domain.AuditableEntity
import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "automation_rules")
class AutomationRule(
    @Column(name = "project_id")
    val projectId: UUID? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    val scope: RuleScope,

    @Column(nullable = false)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    val triggerType: TriggerType,

    @Column(columnDefinition = "TEXT")
    val triggerPayload: String? = null,

    @Column(nullable = false)
    var enabled: Boolean = true,

    @Column(nullable = false)
    val createdBy: UUID,

    @OneToMany(mappedBy = "rule", cascade = [CascadeType.ALL], orphanRemoval = true)
    val conditionGroups: MutableList<RuleConditionGroup> = mutableListOf(),

    @OneToMany(mappedBy = "rule", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("position ASC")
    val actions: MutableList<RuleAction> = mutableListOf()
) : AuditableEntity()

enum class RuleScope { PROJECT, SYSTEM }
