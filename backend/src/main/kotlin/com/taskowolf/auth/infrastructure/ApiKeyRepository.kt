package com.taskowolf.auth.infrastructure

import com.taskowolf.auth.domain.ApiKey
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ApiKeyRepository : JpaRepository<ApiKey, UUID> {
    fun findByKeyHash(keyHash: String): ApiKey?
    fun findByProjectId(projectId: UUID): List<ApiKey>
    fun findByIdAndProjectId(id: UUID, projectId: UUID): ApiKey?
}
