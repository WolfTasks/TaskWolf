package com.taskowolf.issues.domain.events

import com.taskowolf.auth.domain.User
import com.taskowolf.issues.domain.Issue

data class IssueFieldChangedEvent(
    val issue: Issue,
    val actor: User,
    val field: String,
    val oldValue: String?,
    val newValue: String?
)
