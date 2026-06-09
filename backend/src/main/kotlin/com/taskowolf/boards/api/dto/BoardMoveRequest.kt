package com.taskowolf.boards.api.dto

import java.util.UUID

data class BoardMoveRequest(val issueId: UUID, val newStatusId: UUID)
