package com.taskowolf.issues.domain

import com.taskowolf.auth.domain.User
import com.taskowolf.core.domain.AuditableEntity
import com.taskowolf.labels.domain.Label
import com.taskowolf.projects.domain.Project
import com.taskowolf.sprints.domain.Sprint
import com.taskowolf.workflows.domain.WorkflowStatus
import jakarta.persistence.*
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "issues")
class Issue(
    @Column(name = "\"key\"", nullable = false, unique = true)
    val key: String,

    @Column(nullable = false)
    val keyNumber: Int,

    @Column(nullable = false, length = 500)
    var title: String,

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var type: IssueType = IssueType.TASK,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var priority: IssuePriority = IssuePriority.MEDIUM,

    var storyPoints: Int? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "status_id", nullable = false)
    var status: WorkflowStatus,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    val project: Project,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    var assignee: User? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    val reporter: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sprint_id")
    var sprint: Sprint? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    var parent: Issue? = null,

    var dueDate: LocalDate? = null,

    var slaStartTime: Instant? = null
) : AuditableEntity() {
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "issue_labels",
        joinColumns = [JoinColumn(name = "issue_id")],
        inverseJoinColumns = [JoinColumn(name = "label_id")]
    )
    var labels: MutableSet<Label> = mutableSetOf()
}
