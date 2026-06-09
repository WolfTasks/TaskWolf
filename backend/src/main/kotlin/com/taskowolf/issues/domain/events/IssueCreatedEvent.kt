package com.taskowolf.issues.domain.events

import com.taskowolf.issues.domain.Issue

data class IssueCreatedEvent(val issue: Issue)
