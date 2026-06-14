package com.taskowolf.automation.infrastructure

import com.taskowolf.automation.domain.RuleCondition
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface RuleConditionRepository : JpaRepository<RuleCondition, UUID>
