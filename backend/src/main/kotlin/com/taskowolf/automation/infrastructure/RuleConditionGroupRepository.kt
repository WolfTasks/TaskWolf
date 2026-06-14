package com.taskowolf.automation.infrastructure

import com.taskowolf.automation.domain.RuleConditionGroup
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface RuleConditionGroupRepository : JpaRepository<RuleConditionGroup, UUID> {
    fun findByRuleIdAndParentGroupIsNull(ruleId: UUID): RuleConditionGroup?
}
