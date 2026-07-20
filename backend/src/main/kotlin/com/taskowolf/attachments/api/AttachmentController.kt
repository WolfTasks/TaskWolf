package com.taskowolf.attachments.api

import com.taskowolf.attachments.api.dto.AttachmentResponse
import com.taskowolf.attachments.application.AttachmentService
import com.taskowolf.attachments.application.StorageService
import com.taskowolf.auth.domain.User
import org.springframework.core.io.Resource
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@RequestMapping("/api/v1/projects/{key}/issues/{issueKey}/attachments")
class AttachmentController(
    private val attachmentService: AttachmentService,
    private val storageService: StorageService
) {
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@projectSecurity.canWrite(#key, authentication)")
    fun upload(
        @PathVariable key: String,
        @PathVariable issueKey: String,
        @RequestParam("file") file: MultipartFile,
        @AuthenticationPrincipal user: User
    ) = AttachmentResponse.from(attachmentService.upload(key, issueKey, file, user))

    @GetMapping
    fun list(
        @PathVariable key: String,
        @PathVariable issueKey: String,
        @AuthenticationPrincipal user: User
    ) = attachmentService.listForIssue(key, issueKey, user.id).map { AttachmentResponse.from(it) }

    @DeleteMapping("/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@projectSecurity.canWrite(#key, authentication)")
    fun delete(
        @PathVariable key: String,
        @PathVariable issueKey: String,
        @PathVariable attachmentId: UUID,
        @AuthenticationPrincipal user: User
    ) = attachmentService.delete(key, issueKey, attachmentId, user)

    @GetMapping("/{attachmentId}/download")
    fun download(
        @PathVariable key: String,
        @PathVariable issueKey: String,
        @PathVariable attachmentId: UUID,
        @AuthenticationPrincipal user: User
    ): ResponseEntity<Resource> {
        val attachments = attachmentService.listForIssue(key, issueKey, user.id)
        val attachment = attachments.firstOrNull { it.id == attachmentId }
            ?: throw com.taskowolf.core.infrastructure.NotFoundException.keyed("attachment.notFound", attachmentId)
        val resource = storageService.load(attachment.storedName)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(attachment.contentType))
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(attachment.filename).build().toString()
            )
            .body(resource)
    }
}
