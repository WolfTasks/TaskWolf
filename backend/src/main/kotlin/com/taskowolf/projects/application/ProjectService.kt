package com.taskowolf.projects.application

import com.taskowolf.auth.domain.SystemRole
import com.taskowolf.auth.domain.User
import com.taskowolf.auth.infrastructure.UserRepository
import com.taskowolf.core.infrastructure.ConflictException
import com.taskowolf.core.infrastructure.ForbiddenException
import com.taskowolf.core.infrastructure.NotFoundException
import com.taskowolf.organizations.application.OrgMembershipLookup
import com.taskowolf.organizations.domain.OrgRole
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
    private val workflowService: WorkflowService,
    private val userRepository: UserRepository,
    private val orgMembershipLookup: OrgMembershipLookup
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
    fun findAllForUser(userId: UUID): List<Project> {
        val direct = projectRepository.findAllByMemberOrOwner(userId)
        val orgIds = orgMembershipLookup.orgIdsForUser(userId)
        val viaOrg = if (orgIds.isEmpty()) emptyList() else projectRepository.findAllByOrgIdIn(orgIds)
        return (direct + viaOrg).distinctBy { it.id }
    }

    @Transactional(readOnly = true)
    fun findByKey(key: String) = projectRepository.findByKey(key)
        ?: throw NotFoundException("Project not found: $key")

    @Transactional(readOnly = true)
    fun findById(projectId: UUID) = projectRepository.findById(projectId)
        .orElseThrow { NotFoundException("Project not found: $projectId") }

    @Transactional(readOnly = true)
    fun requireMember(projectKey: String, userId: UUID): Project {
        val project = findByKey(projectKey)
        if (roleOf(project, userId) == null) throw ForbiddenException("Not a member of project $projectKey")
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
    fun isMember(project: Project, userId: UUID): Boolean = roleOf(project, userId) != null

    @Transactional(readOnly = true)
    fun isProjectAdmin(projectKey: String, userId: UUID): Boolean {
        val project = findByKey(projectKey)
        return roleOf(project, userId) == ProjectRole.ADMIN
    }

    @Transactional(readOnly = true)
    fun roleOf(project: Project, userId: UUID): ProjectRole? {
        val explicit = if (project.owner.id == userId) ProjectRole.ADMIN
            else memberRepository.findByProjectIdAndUserId(project.id, userId)?.role
        return maxRole(explicit, inheritedRole(project, userId))
    }

    @Transactional(readOnly = true)
    fun canWrite(projectKey: String, userId: UUID): Boolean {
        val project = findByKey(projectKey)
        val role = roleOf(project, userId) ?: return false
        return role != ProjectRole.VIEWER
    }

    @Transactional
    fun addMember(projectKey: String, actorId: UUID, targetUserId: UUID, role: ProjectRole): ProjectMember {
        val project = requireAdmin(projectKey, actorId)
        if (project.owner.id == targetUserId || memberRepository.existsByProjectIdAndUserId(project.id, targetUserId)) {
            throw ConflictException("User is already a member of this project")
        }
        val user = userRepository.findById(targetUserId)
            .orElseThrow { NotFoundException("User not found") }
        return memberRepository.save(ProjectMember(project = project, user = user, role = role))
    }

    @Transactional
    fun changeMemberRole(projectKey: String, actorId: UUID, targetUserId: UUID, role: ProjectRole): ProjectMember {
        val project = requireAdmin(projectKey, actorId)
        if (actorId == targetUserId) throw ForbiddenException("You cannot change your own role")
        if (project.owner.id == targetUserId) throw ForbiddenException("Cannot change the project owner's role")
        val member = memberRepository.findByProjectIdAndUserId(project.id, targetUserId)
            ?: throw NotFoundException("Member not found")
        member.role = role
        return memberRepository.save(member)
    }

    @Transactional
    fun removeMember(projectKey: String, actorId: UUID, targetUserId: UUID) {
        val project = requireAdmin(projectKey, actorId)
        if (project.owner.id == targetUserId) throw ForbiddenException("Cannot remove the project owner")
        val member = memberRepository.findByProjectIdAndUserId(project.id, targetUserId)
            ?: throw NotFoundException("Member not found")
        memberRepository.delete(member)
    }

    @Transactional(readOnly = true)
    fun canManageAnyProjectMembers(user: User): Boolean {
        if (user.systemRole == SystemRole.ADMIN) return true
        return memberRepository.existsByUserIdAndRole(user.id, ProjectRole.ADMIN) ||
            projectRepository.existsByOwnerId(user.id)
    }

    private fun rank(role: ProjectRole): Int = when (role) {
        ProjectRole.VIEWER -> 0
        ProjectRole.MEMBER -> 1
        ProjectRole.ADMIN -> 2
    }

    private fun maxRole(a: ProjectRole?, b: ProjectRole?): ProjectRole? = when {
        a == null -> b
        b == null -> a
        rank(a) >= rank(b) -> a
        else -> b
    }

    private fun orgRoleToProjectRole(orgRole: OrgRole): ProjectRole = when (orgRole) {
        OrgRole.OWNER, OrgRole.ADMIN -> ProjectRole.ADMIN
        OrgRole.MEMBER -> ProjectRole.VIEWER
    }

    private fun inheritedRole(project: Project, userId: UUID): ProjectRole? {
        val orgId = project.orgId ?: return null
        val orgRole = orgMembershipLookup.roleOf(orgId, userId) ?: return null
        return orgRoleToProjectRole(orgRole)
    }
}
