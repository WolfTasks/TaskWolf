package com.taskowolf.sprints.domain.events

import com.taskowolf.sprints.domain.Sprint

data class SprintCompletedEvent(val sprint: Sprint, val movedToBacklogCount: Int)
