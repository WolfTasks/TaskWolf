package com.taskowolf.customfields.infrastructure

import com.taskowolf.customfields.domain.CustomFieldValue
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
import java.util.Optional
import java.util.UUID

interface CustomFieldValueRepository : JpaRepository<CustomFieldValue, UUID> {
    fun findByIssueId(issueId: UUID): List<CustomFieldValue>
    fun findByIssueIdAndField_Id(issueId: UUID, fieldId: UUID): Optional<CustomFieldValue>

    @Modifying
    @Transactional
    @Query("DELETE FROM CustomFieldValue cv WHERE cv.issueId = :issueId AND cv.field.id = :fieldId")
    fun deleteByIssueIdAndFieldId(issueId: UUID, fieldId: UUID)
}
