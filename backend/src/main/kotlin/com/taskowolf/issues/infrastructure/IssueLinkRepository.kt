package com.taskowolf.issues.infrastructure

import com.taskowolf.issues.domain.IssueLink
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface IssueLinkRepository : JpaRepository<IssueLink, UUID>
