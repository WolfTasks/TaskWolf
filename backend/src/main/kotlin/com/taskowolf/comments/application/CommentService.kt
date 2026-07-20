package com.taskowolf.comments.application

import com.taskowolf.auth.domain.User
import com.taskowolf.comments.domain.Comment
import com.taskowolf.comments.domain.events.CommentCreatedEvent
import com.taskowolf.comments.infrastructure.CommentRepository
import com.taskowolf.core.application.DomainEventPublisher
import com.taskowolf.core.infrastructure.ForbiddenException
import com.taskowolf.core.infrastructure.NotFoundException
import com.taskowolf.issues.application.IssueService
import com.taskowolf.projects.application.ProjectService
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class CommentService(
    private val commentRepository: CommentRepository,
    private val issueService: IssueService,
    private val projectService: ProjectService,
    private val eventPublisher: DomainEventPublisher
) {
    @Transactional
    fun addComment(projectKey: String, issueKey: String, body: String, actor: User): Comment {
        val issue = issueService.findByKey(projectKey, issueKey, actor.id)
        val comment = commentRepository.save(
            Comment(issueId = issue.id, authorId = actor.id, body = body)
        )
        eventPublisher.publish(CommentCreatedEvent(comment, issue, actorEmail = actor.email, actorId = actor.id))
        return comment
    }

    @Transactional
    fun editComment(commentId: UUID, body: String, actor: User): Comment {
        val comment = commentRepository.findById(commentId)
            .orElseThrow { NotFoundException.keyed("comment.notFound", commentId) }
        if (comment.authorId != actor.id) throw ForbiddenException.keyed("comment.notAuthor")
        comment.body = body
        comment.editedAt = Instant.now()
        return commentRepository.save(comment)
    }

    @Transactional(readOnly = true)
    fun listComments(projectKey: String, issueKey: String, userId: UUID, page: Int, size: Int): Page<Comment> {
        val issue = issueService.findByKey(projectKey, issueKey, userId)
        return commentRepository.findByIssueIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            issue.id, PageRequest.of(page, size)
        )
    }

    @Transactional
    fun deleteComment(commentId: UUID, projectKey: String, actor: User) {
        val comment = commentRepository.findById(commentId)
            .orElseThrow { NotFoundException.keyed("comment.notFound", commentId) }
        if (comment.authorId != actor.id && !projectService.isProjectAdmin(projectKey, actor.id)) {
            throw ForbiddenException.keyed("comment.cannotDelete")
        }
        comment.deletedAt = Instant.now()
        commentRepository.save(comment)
    }
}
