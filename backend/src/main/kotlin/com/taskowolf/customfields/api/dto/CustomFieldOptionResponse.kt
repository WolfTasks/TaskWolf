package com.taskowolf.customfields.api.dto

import com.taskowolf.customfields.domain.CustomFieldOption
import java.util.UUID

data class CustomFieldOptionResponse(val id: UUID, val label: String, val sortOrder: Int) {
    companion object {
        fun from(o: CustomFieldOption) = CustomFieldOptionResponse(o.id, o.label, o.sortOrder)
    }
}
