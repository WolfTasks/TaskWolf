package com.taskowolf.labels.application

import com.taskowolf.auth.domain.User
import com.taskowolf.core.infrastructure.ConflictException
import com.taskowolf.core.infrastructure.NotFoundException
import com.taskowolf.labels.api.dto.LabelRequest
import com.taskowolf.labels.domain.Label
import com.taskowolf.labels.infrastructure.LabelRepository
import com.taskowolf.projects.application.ProjectService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class LabelService(
    private val labelRepository: LabelRepository,
    private val projectService: ProjectService
) {
    @Transactional(readOnly = true)
    fun list(projectKey: String, userId: UUID): List<Label> {
        val project = projectService.requireMember(projectKey, userId)
        return labelRepository.findByProjectId(project.id)
    }

    @Transactional
    fun create(projectKey: String, request: LabelRequest, actor: User): Label {
        val project = projectService.requireMember(projectKey, actor.id)
        if (labelRepository.existsByProjectIdAndName(project.id, request.name)) {
            throw ConflictException("Label '${request.name}' already exists in this project")
        }
        return labelRepository.save(Label(name = request.name, color = request.color, project = project))
    }

    @Transactional
    fun update(projectKey: String, labelId: UUID, request: LabelRequest, actor: User): Label {
        val project = projectService.requireMember(projectKey, actor.id)
        val label = labelRepository.findById(labelId)
            .filter { it.project.id == project.id }
            .orElseThrow { NotFoundException("Label not found: $labelId") }
        if (label.name != request.name && labelRepository.existsByProjectIdAndName(project.id, request.name)) {
            throw ConflictException("Label '${request.name}' already exists in this project")
        }
        label.name = request.name
        label.color = request.color
        return labelRepository.save(label)
    }

    @Transactional
    fun delete(projectKey: String, labelId: UUID, actor: User) {
        val project = projectService.requireMember(projectKey, actor.id)
        val label = labelRepository.findById(labelId)
            .filter { it.project.id == project.id }
            .orElseThrow { NotFoundException("Label not found: $labelId") }
        labelRepository.delete(label)
    }
}
