package com.taskowolf.issues.infrastructure

import com.taskowolf.issues.domain.Issue
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface IssueRepository : JpaRepository<Issue, UUID> {
    fun findByKey(key: String): Issue?
    fun findAllByProjectId(projectId: UUID, pageable: Pageable): Page<Issue>
    fun countByProjectId(projectId: UUID): Long

    @Query("SELECT COALESCE(MAX(i.keyNumber), 0) FROM Issue i WHERE i.project.id = :projectId")
    fun maxKeyNumberByProject(projectId: UUID): Int
}
