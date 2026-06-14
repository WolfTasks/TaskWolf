package com.taskowolf.automation.domain

import com.taskowolf.core.domain.AuditableEntity
import jakarta.persistence.*

@Entity
@Table(name = "rule_condition_groups")
class RuleConditionGroup(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id", nullable = false)
    val rule: AutomationRule,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_group_id")
    val parentGroup: RuleConditionGroup? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    val logic: GroupLogic,

    @OneToMany(mappedBy = "group", cascade = [CascadeType.ALL], orphanRemoval = true)
    val conditions: MutableList<RuleCondition> = mutableListOf(),

    @OneToMany(mappedBy = "parentGroup", cascade = [CascadeType.ALL], orphanRemoval = true)
    val childGroups: MutableList<RuleConditionGroup> = mutableListOf()
) : AuditableEntity()

enum class GroupLogic { AND, OR }
