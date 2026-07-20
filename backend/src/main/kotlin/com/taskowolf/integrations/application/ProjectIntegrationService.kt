package com.taskowolf.integrations.application

import com.taskowolf.auth.domain.User
import com.taskowolf.core.infrastructure.BadRequestException
import com.taskowolf.core.infrastructure.ConflictException
import com.taskowolf.core.infrastructure.NotFoundException
import com.taskowolf.integrations.api.dto.CreateProjectIntegrationRequest
import com.taskowolf.integrations.api.dto.CreateProjectIntegrationResponse
import com.taskowolf.integrations.api.dto.ProjectIntegrationResponse
import com.taskowolf.integrations.domain.IntegrationProvider
import com.taskowolf.integrations.domain.ProjectIntegration
import com.taskowolf.integrations.infrastructure.ProjectIntegrationRepository
import com.taskowolf.projects.application.ProjectService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

@Service
class ProjectIntegrationService(
    private val integrationRepository: ProjectIntegrationRepository,
    private val projectService: ProjectService,
    @Value("\${taskowolf.base-url:http://localhost:8080}") private val baseUrl: String
) {
    @Transactional
    fun create(projectKey: String, req: CreateProjectIntegrationRequest, caller: User): CreateProjectIntegrationResponse {
        val project = projectService.requireAdmin(projectKey, caller.id)
        val provider = try { IntegrationProvider.valueOf(req.provider.uppercase()) }
                       catch (e: IllegalArgumentException) { throw BadRequestException.keyed("integration.unknownProvider", req.provider) }
        if (integrationRepository.findByProjectIdAndProvider(project.id, provider) != null) {
            throw ConflictException.keyed("integration.alreadyExists", provider, projectKey)
        }
        val plainSecret = generateSecret()
        val integration = integrationRepository.save(
            ProjectIntegration(
                projectId = project.id, provider = provider,
                webhookSecret = plainSecret, repoUrl = req.repoUrl
            )
        )
        val providerPath = provider.name.lowercase()
        val webhookUrl = "$baseUrl/api/v1/integrations/$providerPath/$projectKey/webhook"
        return CreateProjectIntegrationResponse(
            integration.id, provider.name, webhookUrl, plainSecret, req.repoUrl
        )
    }

    @Transactional(readOnly = true)
    fun list(projectKey: String, caller: User): List<ProjectIntegrationResponse> {
        val project = projectService.requireMember(projectKey, caller.id)
        return integrationRepository.findByProjectId(project.id).map { ProjectIntegrationResponse.from(it) }
    }

    @Transactional
    fun delete(projectKey: String, integrationId: UUID, caller: User) {
        val project = projectService.requireAdmin(projectKey, caller.id)
        val integration = integrationRepository.findByIdAndProjectId(integrationId, project.id)
            ?: throw NotFoundException.keyed("integration.notFound", integrationId)
        integrationRepository.delete(integration)
    }

    private fun generateSecret(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
