package com.taskowolf.boards.api.dto

import com.taskowolf.issues.api.dto.IssueResponse
import com.taskowolf.sprints.api.dto.SprintResponse

data class BacklogSprintEntry(val sprint: SprintResponse, val issues: List<IssueResponse>, val totalPoints: Int)

data class BacklogResponse(val sprints: List<BacklogSprintEntry>, val backlogIssues: List<IssueResponse>)
