package com.taskowolf.auth.application

import com.taskowolf.auth.api.dto.ApiKeyResponse
import com.taskowolf.auth.api.dto.CreateApiKeyResponse
import com.taskowolf.auth.domain.ApiKey
import com.taskowolf.auth.domain.User
import com.taskowolf.auth.infrastructure.ApiKeyRepository
import com.taskowolf.auth.infrastructure.UserRepository
import com.taskowolf.core.infrastructure.NotFoundException
import com.taskowolf.projects.application.ProjectService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID

@Service
class ApiKeyService(
    private val apiKeyRepository: ApiKeyRepository,
    private val userRepository: UserRepository,
    private val projectService: ProjectService
) {
    @Transactional
    fun generate(projectKey: String, name: String, expiresAt: Instant?, caller: User): CreateApiKeyResponse {
        val project = projectService.requireAdmin(projectKey, caller.id)
        val plaintext = "tw_" + secureToken()
        val hash = sha256(plaintext)
        val prefix = plaintext.take(12)
        val key = apiKeyRepository.save(
            ApiKey(name = name, keyHash = hash, keyPrefix = prefix,
                   projectId = project.id, createdBy = caller.id, expiresAt = expiresAt)
        )
        return CreateApiKeyResponse(key.id, key.name, key.keyPrefix, plaintext)
    }

    @Transactional(readOnly = true)
    fun list(projectKey: String, caller: User): List<ApiKeyResponse> {
        val project = projectService.requireMember(projectKey, caller.id)
        return apiKeyRepository.findByProjectId(project.id).map { ApiKeyResponse.from(it) }
    }

    @Transactional
    fun revoke(projectKey: String, keyId: UUID, caller: User) {
        val project = projectService.requireAdmin(projectKey, caller.id)
        val key = apiKeyRepository.findByIdAndProjectId(keyId, project.id)
            ?: throw NotFoundException.keyed("auth.apiKeyNotFound", keyId)
        apiKeyRepository.delete(key)
    }

    @Transactional
    fun authenticate(rawToken: String): User? {
        if (!rawToken.startsWith("tw_")) return null
        val hash = sha256(rawToken)
        val key = apiKeyRepository.findByKeyHash(hash) ?: return null
        val expiresAt = key.expiresAt
        if (expiresAt != null && expiresAt.isBefore(Instant.now())) return null
        key.lastUsedAt = Instant.now()
        apiKeyRepository.save(key)
        return userRepository.findById(key.createdBy).orElse(null)
    }

    fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun secureToken(): String {
        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
