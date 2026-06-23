package com.taskowolf.audit.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.taskowolf.audit.api.dto.AuditConfigRequest
import com.taskowolf.audit.api.dto.AuditEventResponse
import com.taskowolf.audit.application.AuditService
import com.taskowolf.audit.domain.AuditLevel
import com.taskowolf.audit.infrastructure.AuditEventRepository
import com.taskowolf.projects.infrastructure.ProjectRepository
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
class AuditController(
    private val auditService: AuditService,
    private val auditEventRepository: AuditEventRepository,
    private val projectRepository: ProjectRepository,
    private val objectMapper: ObjectMapper
) {
    @GetMapping("/admin/audit")
    @PreAuthorize("hasRole('ADMIN')")
    fun listAll(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
        @RequestParam from: Instant? = null,
        @RequestParam to: Instant? = null,
        @RequestParam userId: UUID? = null,
        @RequestParam action: String? = null,
        @RequestParam level: String? = null
    ) = auditEventRepository.findFiltered(from, to, userId, action, level, PageRequest.of(page, size))
        .map { AuditEventResponse.from(it) }

    @GetMapping("/admin/audit/export")
    @PreAuthorize("hasRole('ADMIN')")
    fun export(@RequestParam(defaultValue = "json") format: String): ResponseEntity<String> {
        val events = auditEventRepository.findAll().map { AuditEventResponse.from(it) }
        return if (format == "csv") {
            val csv = buildString {
                appendLine("id,timestamp,userEmail,action,level,resourceType,resourceId,ipAddress")
                events.forEach {
                    appendLine("${it.id},${it.timestamp},${it.userEmail},${it.action},${it.level},${it.resourceType},${it.resourceId},${it.ipAddress}")
                }
            }
            ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=audit.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv)
        } else {
            val json = objectMapper.writeValueAsString(events)
            ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(json)
        }
    }

    @GetMapping("/projects/{key}/audit")
    @PreAuthorize("hasRole('ADMIN') or @projectSecurity.isProjectAdmin(#key, authentication)")
    fun listForProject(
        @PathVariable key: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
        @RequestParam from: Instant? = null,
        @RequestParam to: Instant? = null,
        @RequestParam action: String? = null
    ): ResponseEntity<*> {
        val project = projectRepository.findByKey(key)
            ?: return ResponseEntity.notFound().build<Unit>()
        val results = auditEventRepository.findByProject(project.id, from, to, action, PageRequest.of(page, size))
            .map { AuditEventResponse.from(it) }
        return ResponseEntity.ok(results)
    }

    @GetMapping("/admin/audit/config")
    @PreAuthorize("hasRole('ADMIN')")
    fun getConfig() = auditService.getConfig().map { (k, v) -> mapOf("level" to k.name, "enabled" to v) }

    @PutMapping("/admin/audit/config")
    @PreAuthorize("hasRole('ADMIN')")
    fun updateConfig(@RequestBody req: AuditConfigRequest) {
        auditService.updateConfig(AuditLevel.valueOf(req.level), req.enabled)
    }
}
