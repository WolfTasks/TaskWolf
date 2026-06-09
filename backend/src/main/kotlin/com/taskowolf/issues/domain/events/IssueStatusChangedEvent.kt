package com.taskowolf.issues.domain.events

import com.taskowolf.issues.domain.Issue
import com.taskowolf.workflows.domain.WorkflowStatus

data class IssueStatusChangedEvent(val issue: Issue, val oldStatus: WorkflowStatus, val newStatus: WorkflowStatus)
