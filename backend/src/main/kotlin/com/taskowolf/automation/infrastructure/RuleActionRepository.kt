package com.taskowolf.automation.infrastructure

import com.taskowolf.automation.domain.RuleAction
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface RuleActionRepository : JpaRepository<RuleAction, UUID>
