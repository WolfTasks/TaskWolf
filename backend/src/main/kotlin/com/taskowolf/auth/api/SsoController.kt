package com.taskowolf.auth.api

import com.taskowolf.auth.api.dto.SsoConfigRequest
import com.taskowolf.auth.api.dto.SsoConfigResponse
import com.taskowolf.auth.application.SsoService
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/sso")
class SsoController(private val ssoService: SsoService) {

    @GetMapping
    fun list() = ssoService.listAll().map {
        SsoConfigResponse(it.id.toString(), it.name, it.issuerUrl, it.clientId, it.enabled, it.autoProvision)
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    fun create(@RequestBody req: SsoConfigRequest) =
        ssoService.createConfig(
            req.name,
            req.issuerUrl,
            req.clientId,
            req.clientSecret ?: error("clientSecret required")
        ).let { SsoConfigResponse(it.id.toString(), it.name, it.issuerUrl, it.clientId, it.enabled, it.autoProvision) }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    fun delete(@PathVariable id: UUID) {
        ssoService.deleteConfig(id)
    }
}
