package com.taskowolf.sprints.api.dto

import java.time.LocalDate

data class UpdateSprintRequest(
    val name: String? = null,
    val goal: String? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null
)
