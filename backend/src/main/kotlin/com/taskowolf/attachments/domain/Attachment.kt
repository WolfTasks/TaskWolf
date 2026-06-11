package com.taskowolf.attachments.domain

import com.taskowolf.core.domain.AuditableEntity
import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "attachments")
class Attachment(
    @Column(name = "issue_id", nullable = false)
    val issueId: UUID,

    @Column(name = "uploader_id", nullable = false)
    val uploaderId: UUID,

    @Column(nullable = false, length = 255)
    val filename: String,

    @Column(name = "stored_name", nullable = false, length = 255)
    val storedName: String,

    @Column(name = "content_type", nullable = false, length = 127)
    val contentType: String,

    @Column(nullable = false)
    val size: Long
) : AuditableEntity()
