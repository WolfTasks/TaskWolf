package com.taskowolf.reports.api

import com.taskowolf.auth.domain.User
import com.taskowolf.reports.api.dto.AddWidgetRequest
import com.taskowolf.reports.api.dto.LayoutItem
import com.taskowolf.reports.application.DashboardService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/projects/{key}/dashboard")
class DashboardController(private val dashboardService: DashboardService) {

    @GetMapping
    fun get(@PathVariable key: String, @AuthenticationPrincipal user: User) =
        dashboardService.getDashboard(key, user.id)

    @PutMapping("/layout")
    fun saveLayout(
        @PathVariable key: String,
        @Valid @RequestBody items: List<LayoutItem>,
        @AuthenticationPrincipal user: User
    ) = dashboardService.saveLayout(key, items, user.id)

    @PostMapping("/widgets")
    @ResponseStatus(HttpStatus.CREATED)
    fun addWidget(
        @PathVariable key: String,
        @Valid @RequestBody request: AddWidgetRequest,
        @AuthenticationPrincipal user: User
    ) = dashboardService.addWidget(key, request, user.id)

    @DeleteMapping("/widgets/{widgetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeWidget(
        @PathVariable key: String,
        @PathVariable widgetId: UUID,
        @AuthenticationPrincipal user: User
    ) = dashboardService.removeWidget(key, widgetId, user.id)
}
