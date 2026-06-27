package com.taskowolf.labels.infrastructure

import com.taskowolf.labels.domain.Label
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface LabelRepository : JpaRepository<Label, UUID> {
    fun findByProjectId(projectId: UUID): List<Label>
    fun existsByProjectIdAndName(projectId: UUID, name: String): Boolean

    @Query(
        value = "SELECT l.* FROM labels l INNER JOIN issue_labels il ON l.id = il.label_id WHERE il.issue_id = :issueId",
        nativeQuery = true
    )
    fun findByIssueId(@Param("issueId") issueId: UUID): List<Label>
}
