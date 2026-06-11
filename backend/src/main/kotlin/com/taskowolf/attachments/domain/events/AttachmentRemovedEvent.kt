package com.taskowolf.attachments.domain.events

import com.taskowolf.attachments.domain.Attachment
import com.taskowolf.issues.domain.Issue

data class AttachmentRemovedEvent(
    val attachment: Attachment,
    val issue: Issue
)
