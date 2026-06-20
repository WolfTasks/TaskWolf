package com.taskowolf.reports.api

import com.taskowolf.auth.domain.User
import com.taskowolf.reports.application.ReportsService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/projects/{key}/reports")
class ReportsController(private val reportsService: ReportsService) {

    @GetMapping("/burndown")
    fun burndown(
        @PathVariable key: String,
        @RequestParam sprintId: UUID,
        @AuthenticationPrincipal user: User
    ) = reportsService.getBurndown(key, sprintId, user.id)

    @GetMapping("/velocity")
    fun velocity(@PathVariable key: String, @AuthenticationPrincipal user: User) =
        reportsService.getVelocity(key, user.id)

    @GetMapping("/cycle-time")
    fun cycleTime(
        @PathVariable key: String,
        @RequestParam(required = false) sprintId: UUID?,
        @AuthenticationPrincipal user: User
    ) = if (sprintId != null)
        reportsService.getCycleTime(key, sprintId, user.id)
    else
        reportsService.getCycleTimeAggregate(key, user.id)
}
