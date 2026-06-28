package com.taskowolf.customfields.api.dto

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class CustomFieldValueResponse(
    val fieldId: UUID,
    val fieldName: String,
    val type: String,
    val required: Boolean,
    val textValue: String? = null,
    val numberValue: BigDecimal? = null,
    val dateValue: LocalDate? = null,
    val booleanValue: Boolean? = null,
    val optionId: UUID? = null,
    val optionLabel: String? = null
)
