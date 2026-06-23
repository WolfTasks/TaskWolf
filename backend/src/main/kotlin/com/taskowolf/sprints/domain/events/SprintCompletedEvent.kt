package com.taskowolf.sprints.domain.events

import com.taskowolf.sprints.domain.Sprint
import java.util.UUID

data class SprintCompletedEvent(
    val sprint: Sprint,
    val movedToBacklogCount: Int,
    val actorEmail: String,
    val actorId: UUID
)
