package com.taskowolf.automation.api

import com.taskowolf.auth.domain.User
import com.taskowolf.automation.application.AutomationService
import com.taskowolf.automation.application.CreateRuleRequest
import com.taskowolf.automation.api.dto.AutomationRuleResponse
import com.taskowolf.automation.domain.GroupLogic
import com.taskowolf.automation.domain.RuleScope
import com.taskowolf.automation.domain.TriggerType
import com.taskowolf.projects.application.ProjectService
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

data class CreateAutomationRuleRequest(
    val name: String,
    val triggerType: String,
    val triggerPayload: String? = null,
    val rootGroupLogic: String = "AND"
)

@RestController
@RequestMapping("/api/v1/projects/{key}/automation/rules")
class AutomationController(
    private val projectService: ProjectService,
    private val automationService: AutomationService
) {
    @GetMapping
    fun list(
        @PathVariable key: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @AuthenticationPrincipal user: User
    ) = projectService.requireMember(key, user.id).let { project ->
        automationService.listByProject(project.id, PageRequest.of(page, size))
            .map { AutomationRuleResponse.from(it) }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @PathVariable key: String,
        @RequestBody req: CreateAutomationRuleRequest,
        @AuthenticationPrincipal user: User
    ): AutomationRuleResponse {
        val project = projectService.requireAdmin(key, user.id)
        val rule = automationService.create(
            CreateRuleRequest(
                name = req.name,
                triggerType = TriggerType.valueOf(req.triggerType),
                triggerPayload = req.triggerPayload,
                rootGroupLogic = GroupLogic.valueOf(req.rootGroupLogic),
                scope = RuleScope.PROJECT
            ),
            projectId = project.id,
            createdBy = user.id
        )
        return AutomationRuleResponse.from(rule)
    }

    @GetMapping("/{rid}")
    fun get(@PathVariable key: String, @PathVariable rid: UUID, @AuthenticationPrincipal user: User): AutomationRuleResponse {
        projectService.requireMember(key, user.id)
        return AutomationRuleResponse.from(automationService.find(rid))
    }

    @PutMapping("/{rid}")
    fun update(
        @PathVariable key: String, @PathVariable rid: UUID,
        @RequestBody req: Map<String, String>, @AuthenticationPrincipal user: User
    ): AutomationRuleResponse {
        projectService.requireAdmin(key, user.id)
        return AutomationRuleResponse.from(automationService.rename(rid, req["name"] ?: ""))
    }

    @DeleteMapping("/{rid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable key: String, @PathVariable rid: UUID, @AuthenticationPrincipal user: User) {
        projectService.requireAdmin(key, user.id)
        automationService.delete(rid)
    }

    @PatchMapping("/{rid}/toggle")
    fun toggle(@PathVariable key: String, @PathVariable rid: UUID, @AuthenticationPrincipal user: User): AutomationRuleResponse {
        projectService.requireAdmin(key, user.id)
        return AutomationRuleResponse.from(automationService.toggle(rid))
    }
}
