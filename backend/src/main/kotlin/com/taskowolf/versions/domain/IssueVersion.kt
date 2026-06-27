// backend/src/main/kotlin/com/taskowolf/versions/domain/IssueVersion.kt
package com.taskowolf.versions.domain

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "issue_versions")
@IdClass(IssueVersionId::class)
class IssueVersion(
    @Id
    @Column(name = "issue_id")
    val issueId: UUID,

    @Id
    @Column(name = "version_id")
    val versionId: UUID,

    @Id
    @Column(name = "type", length = 8)
    val type: String
)
