package com.taskowolf.comments.domain.events

import com.taskowolf.auth.domain.User
import com.taskowolf.comments.domain.Comment
import com.taskowolf.issues.domain.Issue

data class MentionEvent(
    val mentionedUser: User,
    val comment: Comment,
    val issue: Issue
)
