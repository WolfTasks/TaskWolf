package com.taskowolf.customfields.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(
    name = "custom_field_values",
    uniqueConstraints = [UniqueConstraint(columnNames = ["issue_id", "field_id"])]
)
class CustomFieldValue(
    @Column(name = "issue_id", nullable = false)
    val issueId: UUID,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "field_id", nullable = false)
    val field: CustomFieldDefinition,

    @Column(name = "text_value")
    var textValue: String? = null,

    @Column(name = "number_value", precision = 19, scale = 4)
    var numberValue: BigDecimal? = null,

    @Column(name = "date_value")
    var dateValue: LocalDate? = null,

    @Column(name = "boolean_value")
    var booleanValue: Boolean? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "option_id")
    var option: CustomFieldOption? = null
) {
    @Id
    val id: UUID = UUID.randomUUID()
}
