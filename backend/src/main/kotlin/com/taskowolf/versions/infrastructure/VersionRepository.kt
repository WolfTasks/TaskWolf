// backend/src/main/kotlin/com/taskowolf/versions/infrastructure/VersionRepository.kt
package com.taskowolf.versions.infrastructure

import com.taskowolf.versions.domain.Version
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface VersionRepository : JpaRepository<Version, UUID> {
    fun findByProjectId(projectId: UUID): List<Version>
    fun existsByProjectIdAndName(projectId: UUID, name: String): Boolean

    @Query(
        value = "SELECT v.* FROM versions v INNER JOIN issue_versions iv ON v.id = iv.version_id WHERE iv.issue_id = :issueId AND iv.type = :type",
        nativeQuery = true
    )
    fun findByIssueIdAndType(@Param("issueId") issueId: UUID, @Param("type") type: String): List<Version>
}
