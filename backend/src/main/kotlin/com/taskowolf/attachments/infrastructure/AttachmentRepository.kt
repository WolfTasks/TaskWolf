package com.taskowolf.attachments.infrastructure

import com.taskowolf.attachments.domain.Attachment
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AttachmentRepository : JpaRepository<Attachment, UUID> {
    fun findAllByIssueId(issueId: UUID): List<Attachment>
}
