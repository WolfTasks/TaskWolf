package com.taskowolf.issues.api.dto

import java.util.UUID

data class CustomFieldValueInput(
    val fieldId: UUID,
    val value: String?  // null = clear the value
)
