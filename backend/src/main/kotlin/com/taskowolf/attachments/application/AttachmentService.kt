package com.taskowolf.attachments.application

import com.taskowolf.attachments.domain.Attachment
import com.taskowolf.attachments.domain.events.AttachmentAddedEvent
import com.taskowolf.attachments.domain.events.AttachmentRemovedEvent
import com.taskowolf.attachments.infrastructure.AttachmentRepository
import com.taskowolf.auth.domain.User
import com.taskowolf.core.application.DomainEventPublisher
import com.taskowolf.core.infrastructure.ForbiddenException
import com.taskowolf.core.infrastructure.NotFoundException
import com.taskowolf.issues.application.IssueService
import com.taskowolf.projects.application.ProjectService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@Service
class AttachmentService(
    private val attachmentRepository: AttachmentRepository,
    private val storageService: StorageService,
    private val issueService: IssueService,
    private val projectService: ProjectService,
    private val eventPublisher: DomainEventPublisher
) {
    @Transactional
    fun upload(projectKey: String, issueKey: String, file: MultipartFile, actor: User): Attachment {
        val issue = issueService.findByKey(projectKey, issueKey, actor.id)
        val storedName = storageService.store(file)
        val attachment = attachmentRepository.save(
            Attachment(
                issueId = issue.id,
                uploaderId = actor.id,
                filename = file.originalFilename ?: "unknown",
                storedName = storedName,
                contentType = file.contentType ?: "application/octet-stream",
                size = file.size
            )
        )
        eventPublisher.publish(AttachmentAddedEvent(attachment, issue))
        return attachment
    }

    @Transactional
    fun delete(projectKey: String, issueKey: String, attachmentId: UUID, actor: User) {
        val attachment = attachmentRepository.findById(attachmentId)
            .orElseThrow { NotFoundException("Attachment not found: $attachmentId") }
        val issue = issueService.findByKey(projectKey, issueKey, actor.id)
        if (attachment.uploaderId != actor.id && !projectService.isProjectAdmin(projectKey, actor.id)) {
            throw ForbiddenException("Cannot delete this attachment")
        }
        storageService.delete(attachment.storedName)
        attachmentRepository.delete(attachment)
        eventPublisher.publish(AttachmentRemovedEvent(attachment, issue))
    }

    @Transactional(readOnly = true)
    fun listForIssue(projectKey: String, issueKey: String, userId: UUID): List<Attachment> {
        val issue = issueService.findByKey(projectKey, issueKey, userId)
        return attachmentRepository.findAllByIssueId(issue.id)
    }
}
