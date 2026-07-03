package com.taskowolf.core.domain

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.domain.Persistable
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.util.UUID

@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class AuditableEntity(
    @Id
    private val id: UUID = UUID.randomUUID(),

    @CreatedDate
    @Column(nullable = false, updatable = false)
    var createdAt: Instant? = null,

    @LastModifiedDate
    @Column(nullable = false)
    var updatedAt: Instant = Instant.now()
) : Persistable<UUID> {

    override fun getId(): UUID = id

    override fun isNew(): Boolean = createdAt == null
}
