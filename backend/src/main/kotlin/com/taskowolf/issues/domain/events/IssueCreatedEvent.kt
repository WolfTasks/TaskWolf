package com.taskowolf.issues.domain.events

import com.taskowolf.issues.domain.Issue
import java.util.UUID

data class IssueCreatedEvent(
    val issue: Issue,
    val actorEmail: String,
    val actorId: UUID
)
