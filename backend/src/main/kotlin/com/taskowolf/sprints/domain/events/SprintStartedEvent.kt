package com.taskowolf.sprints.domain.events

import com.taskowolf.sprints.domain.Sprint
import java.util.UUID

data class SprintStartedEvent(
    val sprint: Sprint,
    val actorEmail: String,
    val actorId: UUID
)
