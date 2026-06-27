// backend/src/main/kotlin/com/taskowolf/versions/domain/IssueVersionId.kt
package com.taskowolf.versions.domain

import java.io.Serializable
import java.util.UUID

data class IssueVersionId(
    val issueId: UUID = UUID.randomUUID(),
    val versionId: UUID = UUID.randomUUID(),
    val type: String = ""
) : Serializable
