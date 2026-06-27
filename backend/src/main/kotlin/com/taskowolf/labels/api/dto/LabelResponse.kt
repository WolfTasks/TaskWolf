package com.taskowolf.labels.api.dto

import com.taskowolf.labels.domain.Label
import java.util.UUID

data class LabelResponse(
    val id: UUID,
    val name: String,
    val color: String
) {
    companion object {
        fun from(l: Label) = LabelResponse(id = l.id, name = l.name, color = l.color)
    }
}
