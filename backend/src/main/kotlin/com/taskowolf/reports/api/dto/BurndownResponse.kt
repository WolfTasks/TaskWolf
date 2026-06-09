package com.taskowolf.reports.api.dto

import java.time.LocalDate
import java.util.UUID

data class BurndownDay(val date: LocalDate, val idealPoints: Int, val remainingPoints: Int)

data class BurndownResponse(val sprintId: UUID, val days: List<BurndownDay>)
