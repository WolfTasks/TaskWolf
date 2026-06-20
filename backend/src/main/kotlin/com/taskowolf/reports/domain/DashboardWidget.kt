package com.taskowolf.reports.domain

import com.taskowolf.core.domain.AuditableEntity
import jakarta.persistence.*

@Entity
@Table(name = "dashboard_widget")
class DashboardWidget(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dashboard_id", nullable = false)
    val dashboard: Dashboard,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    val type: WidgetType,

    @Column(columnDefinition = "TEXT")
    val config: String? = null,

    @Column(name = "grid_x", nullable = false)
    var gridX: Int = 0,

    @Column(name = "grid_y", nullable = false)
    var gridY: Int = 0,

    @Column(name = "grid_w", nullable = false)
    var gridW: Int = 4,

    @Column(name = "grid_h", nullable = false)
    var gridH: Int = 4
) : AuditableEntity()
