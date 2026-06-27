// backend/src/main/kotlin/com/taskowolf/versions/api/dto/VersionResponse.kt
package com.taskowolf.versions.api.dto

import com.taskowolf.versions.domain.Version
import java.util.UUID

data class VersionResponse(
    val id: UUID,
    val name: String
) {
    companion object {
        fun from(v: Version) = VersionResponse(id = v.id, name = v.name)
    }
}
