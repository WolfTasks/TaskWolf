// backend/src/main/kotlin/com/taskowolf/versions/infrastructure/IssueVersionRepository.kt
package com.taskowolf.versions.infrastructure

import com.taskowolf.versions.domain.IssueVersion
import com.taskowolf.versions.domain.IssueVersionId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface IssueVersionRepository : JpaRepository<IssueVersion, IssueVersionId> {
    @Modifying
    @Query("DELETE FROM IssueVersion iv WHERE iv.issueId = :issueId AND iv.type = :type")
    fun deleteByIssueIdAndType(@Param("issueId") issueId: UUID, @Param("type") type: String)
}
