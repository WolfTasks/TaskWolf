package com.taskowolf.customfields.domain

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(
    name = "custom_field_options",
    uniqueConstraints = [UniqueConstraint(columnNames = ["field_id", "label"])]
)
class CustomFieldOption(
    @Column(nullable = false, length = 100)
    var label: String,

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "field_id", nullable = false)
    val field: CustomFieldDefinition
) {
    @Id
    val id: UUID = UUID.randomUUID()
}
