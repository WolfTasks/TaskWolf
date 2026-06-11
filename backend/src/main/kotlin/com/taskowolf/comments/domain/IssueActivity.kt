package com.taskowolf.comments.domain

import com.taskowolf.core.domain.AuditableEntity
import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "issue_activities")
class IssueActivity(
    @Column(name = "issue_id", nullable = false)
    val issueId: UUID,

    @Column(name = "actor_id", nullable = false)
    val actorId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    val type: ActivityType,

    @Column(name = "comment_id")
    val commentId: UUID? = null,

    @Column(name = "old_value", columnDefinition = "TEXT")
    val oldValue: String? = null,

    @Column(name = "new_value", columnDefinition = "TEXT")
    val newValue: String? = null
) : AuditableEntity()
