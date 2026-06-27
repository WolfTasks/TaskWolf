// backend/src/main/kotlin/com/taskowolf/versions/application/VersionService.kt
package com.taskowolf.versions.application

import com.taskowolf.auth.domain.User
import com.taskowolf.core.infrastructure.ConflictException
import com.taskowolf.core.infrastructure.NotFoundException
import com.taskowolf.projects.application.ProjectService
import com.taskowolf.versions.api.dto.VersionRequest
import com.taskowolf.versions.domain.Version
import com.taskowolf.versions.infrastructure.VersionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class VersionService(
    private val versionRepository: VersionRepository,
    private val projectService: ProjectService
) {
    @Transactional(readOnly = true)
    fun list(projectKey: String, userId: UUID): List<Version> {
        val project = projectService.requireMember(projectKey, userId)
        return versionRepository.findByProjectId(project.id)
    }

    @Transactional
    fun create(projectKey: String, request: VersionRequest, actor: User): Version {
        val project = projectService.requireMember(projectKey, actor.id)
        if (versionRepository.existsByProjectIdAndName(project.id, request.name)) {
            throw ConflictException("Version '${request.name}' already exists in this project")
        }
        return versionRepository.save(Version(name = request.name, project = project))
    }

    @Transactional
    fun update(projectKey: String, versionId: UUID, request: VersionRequest, actor: User): Version {
        val project = projectService.requireMember(projectKey, actor.id)
        val version = versionRepository.findById(versionId)
            .filter { it.project.id == project.id }
            .orElseThrow { NotFoundException("Version not found: $versionId") }
        if (version.name != request.name && versionRepository.existsByProjectIdAndName(project.id, request.name)) {
            throw ConflictException("Version '${request.name}' already exists in this project")
        }
        version.name = request.name
        return versionRepository.save(version)
    }

    @Transactional
    fun delete(projectKey: String, versionId: UUID, actor: User) {
        val project = projectService.requireMember(projectKey, actor.id)
        val version = versionRepository.findById(versionId)
            .filter { it.project.id == project.id }
            .orElseThrow { NotFoundException("Version not found: $versionId") }
        versionRepository.delete(version)
    }
}
