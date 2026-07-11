package com.taskowolf.projects.application

import com.taskowolf.auth.domain.User
import com.taskowolf.core.infrastructure.ConflictException
import com.taskowolf.core.infrastructure.ForbiddenException
import com.taskowolf.core.infrastructure.NotFoundException
import com.taskowolf.projects.api.dto.CreateProjectRequest
import com.taskowolf.projects.domain.Project
import com.taskowolf.projects.domain.ProjectMember
import com.taskowolf.projects.domain.ProjectRole
import com.taskowolf.projects.infrastructure.ProjectMemberRepository
import com.taskowolf.projects.infrastructure.ProjectRepository
import com.taskowolf.workflows.application.WorkflowService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ProjectService(
    private val projectRepository: ProjectRepository,
    private val memberRepository: ProjectMemberRepository,
    private val workflowService: WorkflowService
) {
    @Transactional
    fun create(request: CreateProjectRequest, owner: User): Project {
        if (projectRepository.existsByKey(request.key)) {
            throw ConflictException("Project key already exists: ${request.key}")
        }
        val project = projectRepository.save(
            Project(key = request.key, name = request.name, description = request.description, owner = owner)
        )
        memberRepository.save(ProjectMember(project = project, user = owner, role = ProjectRole.ADMIN))
        val workflow = workflowService.createDefault(project)
        project.workflow = workflow
        projectRepository.save(project)
        return project
    }

    @Transactional(readOnly = true)
    fun findAllForUser(userId: UUID) = projectRepository.findAllByMemberOrOwner(userId)

    @Transactional(readOnly = true)
    fun findByKey(key: String) = projectRepository.findByKey(key)
        ?: throw NotFoundException("Project not found: $key")

    @Transactional(readOnly = true)
    fun findById(projectId: UUID) = projectRepository.findById(projectId)
        .orElseThrow { NotFoundException("Project not found: $projectId") }

    @Transactional(readOnly = true)
    fun requireMember(projectKey: String, userId: UUID): Project {
        val project = findByKey(projectKey)
        val isMember = memberRepository.existsByProjectIdAndUserId(project.id, userId)
        val isOwner = project.owner.id == userId
        if (!isMember && !isOwner) throw ForbiddenException("Not a member of project $projectKey")
        return project
    }

    @Transactional(readOnly = true)
    fun requireAdmin(projectKey: String, userId: UUID): Project {
        val project = requireMember(projectKey, userId)
        if (!isProjectAdmin(projectKey, userId))
            throw ForbiddenException("Project admin role required")
        return project
    }

    @Transactional(readOnly = true)
    fun isMember(project: Project, userId: UUID): Boolean =
        project.owner.id == userId || memberRepository.existsByProjectIdAndUserId(project.id, userId)

    @Transactional(readOnly = true)
    fun isProjectAdmin(projectKey: String, userId: UUID): Boolean {
        val project = findByKey(projectKey)
        if (project.owner.id == userId) return true
        val member = memberRepository.findByProjectIdAndUserId(project.id, userId)
        return member?.role == ProjectRole.ADMIN
    }

    @Transactional(readOnly = true)
    fun roleOf(project: Project, userId: UUID): ProjectRole? =
        if (project.owner.id == userId) ProjectRole.ADMIN
        else memberRepository.findByProjectIdAndUserId(project.id, userId)?.role

    @Transactional(readOnly = true)
    fun canWrite(projectKey: String, userId: UUID): Boolean {
        val project = findByKey(projectKey)
        val role = roleOf(project, userId) ?: return false
        return role != ProjectRole.VIEWER
    }
}
