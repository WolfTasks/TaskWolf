package com.taskowolf.issues.infrastructure

import com.taskowolf.issues.domain.Issue
import com.taskowolf.workflows.domain.StatusCategory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface IssueRepository : JpaRepository<Issue, UUID>, JpaSpecificationExecutor<Issue> {
    fun findByKeyAndProjectId(key: String, projectId: UUID): Issue?
    fun findAllByProjectId(projectId: UUID, pageable: Pageable): Page<Issue>
    fun countByProjectId(projectId: UUID): Long

    @Query("SELECT COALESCE(MAX(i.keyNumber), 0) FROM Issue i WHERE i.project.id = :projectId")
    fun maxKeyNumberByProject(projectId: UUID): Int

    fun findBySprintId(sprintId: UUID): List<Issue>

    fun findByProjectIdAndSprintIsNull(projectId: UUID): List<Issue>

    @Query("SELECT COALESCE(SUM(COALESCE(i.storyPoints, 0)), 0) FROM Issue i WHERE i.sprint.id = :sprintId")
    fun sumStoryPointsBySprintId(sprintId: UUID): Long

    fun findByProjectIdAndAssigneeId(projectId: UUID, assigneeId: UUID, pageable: Pageable): Page<Issue>

    fun findBySlaStartTimeIsNotNull(): List<Issue>

    @Query("""
        SELECT i FROM Issue i
        WHERE i.project.id = :projectId
          AND i.dueDate < CURRENT_DATE
          AND i.status.category <> :done
        ORDER BY i.dueDate ASC
    """)
    fun findOverdueByProjectId(
        projectId: UUID,
        @Param("done") done: StatusCategory,
        pageable: Pageable
    ): Page<Issue>

    @Query("""
        SELECT i FROM Issue i
        WHERE i.project.id = :projectId
          AND i.assignee.id = :assigneeId
          AND i.dueDate < CURRENT_DATE
          AND i.status.category <> :done
        ORDER BY i.dueDate ASC
    """)
    fun findOverdueByProjectIdAndAssigneeId(
        projectId: UUID,
        assigneeId: UUID,
        @Param("done") done: StatusCategory,
        pageable: Pageable
    ): Page<Issue>

    @Query("SELECT i FROM Issue i JOIN i.labels l WHERE i.project.id = :projectId AND l.id = :labelId")
    fun findAllByProjectIdAndLabelId(
        projectId: UUID,
        labelId: UUID,
        pageable: Pageable
    ): Page<Issue>

    @Query(
        value = "SELECT i.* FROM issues i INNER JOIN issue_versions iv ON i.id = iv.issue_id WHERE i.project_id = :projectId AND iv.version_id = :versionId AND iv.type = 'FIX'",
        countQuery = "SELECT count(*) FROM issues i INNER JOIN issue_versions iv ON i.id = iv.issue_id WHERE i.project_id = :projectId AND iv.version_id = :versionId AND iv.type = 'FIX'",
        nativeQuery = true
    )
    fun findAllByProjectIdAndFixVersionId(
        @Param("projectId") projectId: UUID,
        @Param("versionId") versionId: UUID,
        pageable: Pageable
    ): Page<Issue>

    @Query(
        value = "SELECT i.* FROM issues i INNER JOIN issue_versions iv ON i.id = iv.issue_id WHERE i.project_id = :projectId AND iv.version_id = :versionId AND iv.type = 'AFFECTS'",
        countQuery = "SELECT count(*) FROM issues i INNER JOIN issue_versions iv ON i.id = iv.issue_id WHERE i.project_id = :projectId AND iv.version_id = :versionId AND iv.type = 'AFFECTS'",
        nativeQuery = true
    )
    fun findAllByProjectIdAndAffectsVersionId(
        @Param("projectId") projectId: UUID,
        @Param("versionId") versionId: UUID,
        pageable: Pageable
    ): Page<Issue>

    @Query(
        value = "SELECT i.* FROM issues i INNER JOIN issue_versions iv1 ON i.id = iv1.issue_id AND iv1.type = 'FIX' INNER JOIN issue_versions iv2 ON i.id = iv2.issue_id AND iv2.type = 'AFFECTS' WHERE i.project_id = :projectId AND iv1.version_id = :fixVersionId AND iv2.version_id = :affectsVersionId",
        countQuery = "SELECT count(*) FROM issues i INNER JOIN issue_versions iv1 ON i.id = iv1.issue_id AND iv1.type = 'FIX' INNER JOIN issue_versions iv2 ON i.id = iv2.issue_id AND iv2.type = 'AFFECTS' WHERE i.project_id = :projectId AND iv1.version_id = :fixVersionId AND iv2.version_id = :affectsVersionId",
        nativeQuery = true
    )
    fun findAllByProjectIdAndBothVersionIds(
        @Param("projectId") projectId: UUID,
        @Param("fixVersionId") fixVersionId: UUID,
        @Param("affectsVersionId") affectsVersionId: UUID,
        pageable: Pageable
    ): Page<Issue>
}
