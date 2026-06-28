package com.taskowolf.issues.infrastructure

import com.taskowolf.customfields.domain.CustomFieldValue
import com.taskowolf.customfields.domain.FieldType
import com.taskowolf.issues.domain.Issue
import com.taskowolf.versions.domain.IssueVersion
import com.taskowolf.workflows.domain.StatusCategory
import jakarta.persistence.criteria.*
import org.springframework.data.jpa.domain.Specification
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

object IssueSpecification {

    fun inProject(projectId: UUID): Specification<Issue> =
        Specification { root, _, cb ->
            cb.equal(root.get<Any>("project").get<UUID>("id"), projectId)
        }

    fun assignedTo(userId: UUID): Specification<Issue> =
        Specification { root, _, cb ->
            cb.equal(root.get<Any>("assignee").get<UUID>("id"), userId)
        }

    fun overdue(): Specification<Issue> =
        Specification { root, _, cb ->
            cb.and(
                cb.lessThan(root.get("dueDate"), LocalDate.now()),
                cb.notEqual(root.get<Any>("status").get<StatusCategory>("category"), StatusCategory.DONE)
            )
        }

    fun hasLabel(labelId: UUID): Specification<Issue> =
        Specification { root, query, cb ->
            query!!.distinct(true)
            val labelJoin = root.join<Issue, Any>("labels", JoinType.INNER)
            cb.equal(labelJoin.get<UUID>("id"), labelId)
        }

    fun hasFixVersion(versionId: UUID): Specification<Issue> =
        Specification { root, query, cb ->
            val sub = query!!.subquery(Long::class.java)
            val iv = sub.from(IssueVersion::class.java)
            sub.select(cb.literal(1L)).where(
                cb.equal(iv.get<UUID>("issueId"), root.get<UUID>("id")),
                cb.equal(iv.get<UUID>("versionId"), versionId),
                cb.equal(iv.get<String>("type"), "FIX")
            )
            cb.exists(sub)
        }

    fun hasAffectsVersion(versionId: UUID): Specification<Issue> =
        Specification { root, query, cb ->
            val sub = query!!.subquery(Long::class.java)
            val iv = sub.from(IssueVersion::class.java)
            sub.select(cb.literal(1L)).where(
                cb.equal(iv.get<UUID>("issueId"), root.get<UUID>("id")),
                cb.equal(iv.get<UUID>("versionId"), versionId),
                cb.equal(iv.get<String>("type"), "AFFECTS")
            )
            cb.exists(sub)
        }

    fun hasCustomFieldValue(fieldId: UUID, rawValue: String, fieldType: FieldType): Specification<Issue> =
        Specification { root, query, cb ->
            val sub = query!!.subquery(Long::class.java)
            val cfv = sub.from(CustomFieldValue::class.java)
            val fieldJoin = cfv.join<Any, Any>("field", JoinType.INNER)

            val issueIdMatch = cb.equal(cfv.get<UUID>("issueId"), root.get<UUID>("id"))
            val fieldIdMatch = cb.equal(fieldJoin.get<UUID>("id"), fieldId)

            val valueMatch: Predicate = when (fieldType) {
                FieldType.TEXT -> cb.like(cb.lower(cfv.get("textValue")), "%${rawValue.lowercase()}%")
                FieldType.NUMBER -> rawValue.toBigDecimalOrNull()?.let { num ->
                    cb.equal(cfv.get<BigDecimal>("numberValue"), num)
                } ?: cb.conjunction()
                FieldType.DATE -> runCatching { LocalDate.parse(rawValue) }.getOrNull()?.let { date ->
                    cb.equal(cfv.get<LocalDate>("dateValue"), date)
                } ?: cb.conjunction()
                FieldType.CHECKBOX -> cb.equal(
                    cfv.get<Boolean>("booleanValue"),
                    rawValue.equals("true", ignoreCase = true)
                )
                FieldType.DROPDOWN -> runCatching { UUID.fromString(rawValue) }.getOrNull()?.let { optId ->
                    val optJoin = cfv.join<Any, Any>("option", JoinType.INNER)
                    cb.equal(optJoin.get<UUID>("id"), optId)
                } ?: cb.conjunction()
            }

            sub.select(cb.literal(1L)).where(issueIdMatch, fieldIdMatch, valueMatch)
            cb.exists(sub)
        }
}
