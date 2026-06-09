package com.taskowolf.issues.domain

import com.taskowolf.core.domain.AuditableEntity
import jakarta.persistence.*

@Entity
@Table(name = "issue_links")
class IssueLink(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_issue_id", nullable = false)
    val fromIssue: Issue,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_issue_id", nullable = false)
    val toIssue: Issue,

    @Enumerated(EnumType.STRING)
    @Column(name = "link_type", nullable = false)
    val linkType: IssueLinkType
) : AuditableEntity()
