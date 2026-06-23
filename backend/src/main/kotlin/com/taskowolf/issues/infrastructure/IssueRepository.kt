package com.taskowolf.issues.infrastructure

import com.taskowolf.issues.domain.Issue
import com.taskowolf.workflows.domain.StatusCategory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface IssueRepository : JpaRepository<Issue, UUID> {
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
}
