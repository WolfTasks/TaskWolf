package com.taskowolf.comments.domain

import com.taskowolf.core.domain.AuditableEntity
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "comments")
class Comment(
    @Column(name = "issue_id", nullable = false)
    val issueId: UUID,

    @Column(name = "author_id", nullable = true)
    val authorId: UUID? = null,

    @Column(columnDefinition = "TEXT", nullable = false)
    var body: String,

    @Column(name = "edited_at")
    var editedAt: Instant? = null,

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null
) : AuditableEntity()
