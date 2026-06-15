package com.taskowolf.attachments.api.dto

import com.taskowolf.attachments.domain.Attachment
import java.time.Instant
import java.util.UUID

data class AttachmentResponse(
    val id: UUID,
    val issueId: UUID,
    val uploaderId: UUID,
    val filename: String,
    val contentType: String,
    val size: Long,
    val createdAt: Instant
) {
    companion object {
        fun from(a: Attachment) = AttachmentResponse(
            id = a.id,
            issueId = a.issueId,
            uploaderId = a.uploaderId,
            filename = a.filename,
            contentType = a.contentType,
            size = a.size,
            createdAt = a.createdAt!!
        )
    }
}
