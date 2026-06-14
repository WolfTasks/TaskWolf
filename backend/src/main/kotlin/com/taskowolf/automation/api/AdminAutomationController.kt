package com.taskowolf.automation.api

import com.taskowolf.auth.domain.SystemRole
import com.taskowolf.auth.domain.User
import com.taskowolf.automation.application.AutomationService
import com.taskowolf.automation.application.CreateRuleRequest
import com.taskowolf.automation.api.dto.AutomationRuleResponse
import com.taskowolf.automation.domain.GroupLogic
import com.taskowolf.automation.domain.RuleScope
import com.taskowolf.automation.domain.TriggerType
import com.taskowolf.core.infrastructure.ForbiddenException
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/automation/rules")
class AdminAutomationController(private val automationService: AutomationService) {

    private fun requireSystemAdmin(user: User) {
        if (user.systemRole != SystemRole.ADMIN) throw ForbiddenException("System admin role required")
    }

    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @AuthenticationPrincipal user: User
    ) = automationService.listSystem(PageRequest.of(page, size)).map { AutomationRuleResponse.from(it) }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody req: CreateAutomationRuleRequest, @AuthenticationPrincipal user: User): AutomationRuleResponse {
        requireSystemAdmin(user)
        return AutomationRuleResponse.from(
            automationService.create(
                CreateRuleRequest(req.name, TriggerType.valueOf(req.triggerType),
                    req.triggerPayload, GroupLogic.valueOf(req.rootGroupLogic), RuleScope.SYSTEM),
                projectId = null, createdBy = user.id
            )
        )
    }

    @DeleteMapping("/{rid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable rid: UUID, @AuthenticationPrincipal user: User) {
        requireSystemAdmin(user)
        automationService.delete(rid)
    }

    @PatchMapping("/{rid}/toggle")
    fun toggle(@PathVariable rid: UUID, @AuthenticationPrincipal user: User): AutomationRuleResponse {
        requireSystemAdmin(user)
        return AutomationRuleResponse.from(automationService.toggle(rid))
    }
}
