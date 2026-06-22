package com.taskowolf.integrations.api.dto

import com.taskowolf.integrations.domain.IssueRef
import java.time.Instant
import java.util.UUID

data class IssueRefResponse(
    val id: UUID,
    val provider: String,
    val refType: String,
    val externalId: String,
    val url: String,
    val title: String?,
    val createdAt: Instant?
) {
    companion object {
        fun from(r: IssueRef) = IssueRefResponse(
            r.id, r.provider.name, r.refType.name, r.externalId, r.url, r.title, r.createdAt
        )
    }
}
