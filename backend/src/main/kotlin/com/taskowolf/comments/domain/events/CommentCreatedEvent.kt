package com.taskowolf.comments.domain.events

import com.taskowolf.comments.domain.Comment
import com.taskowolf.issues.domain.Issue

data class CommentCreatedEvent(
    val comment: Comment,
    val issue: Issue
)
