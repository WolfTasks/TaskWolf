package com.taskowolf.reports.infrastructure

import com.taskowolf.reports.domain.DashboardWidget
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface DashboardWidgetRepository : JpaRepository<DashboardWidget, UUID>
