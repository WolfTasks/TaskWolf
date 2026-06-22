package com.taskowolf.integrations.application

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.taskowolf.integrations.domain.IntegrationProvider
import com.taskowolf.integrations.domain.IssueRef
import com.taskowolf.integrations.domain.RefType
import com.taskowolf.integrations.infrastructure.IssueRefRepository
import com.taskowolf.integrations.infrastructure.ProjectIntegrationRepository
import com.taskowolf.issues.infrastructure.IssueRepository
import com.taskowolf.projects.infrastructure.ProjectRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class IncomingWebhookService(
    private val integrationRepository: ProjectIntegrationRepository,
    private val issueRefRepository: IssueRefRepository,
    private val projectRepository: ProjectRepository,
    private val issueRepository: IssueRepository,
    private val issueKeyParser: IssueKeyParser,
    private val hmacSigner: HmacSigner,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(IncomingWebhookService::class.java)

    @Transactional
    fun handleGitHub(projectKey: String, payload: String, signatureHeader: String?) {
        val project = projectRepository.findByKey(projectKey) ?: return
        val integration = integrationRepository.findByProjectIdAndProvider(project.id, IntegrationProvider.GITHUB) ?: return
        if (!verifyGitHubSignature(payload, integration.webhookSecret, signatureHeader)) {
            log.warn("GitHub webhook signature mismatch for project {}", projectKey)
            throw SecurityException("Invalid GitHub webhook signature")
        }
        val node = try {
            objectMapper.readTree(payload)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid JSON payload")
        }
        processGitHubPayload(node, project.id, projectKey)
    }

    @Transactional
    fun handleGitLab(projectKey: String, payload: String, tokenHeader: String?) {
        val project = projectRepository.findByKey(projectKey) ?: return
        val integration = integrationRepository.findByProjectIdAndProvider(project.id, IntegrationProvider.GITLAB) ?: return
        if (integration.webhookSecret != (tokenHeader ?: "")) {
            log.warn("GitLab webhook token mismatch for project {}", projectKey)
            throw SecurityException("Invalid GitLab webhook token")
        }
        val node = try {
            objectMapper.readTree(payload)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid JSON payload")
        }
        processGitLabPayload(node, project.id, projectKey)
    }

    private fun verifyGitHubSignature(payload: String, secret: String, signatureHeader: String?): Boolean {
        if (signatureHeader == null) return false
        return hmacSigner.verify(payload, secret, signatureHeader)
    }

    private fun processGitHubPayload(node: JsonNode, projectId: UUID, projectKey: String) {
        val eventType = detectGitHubEventType(node)
        when (eventType) {
            "push" -> {
                val commits = node.path("commits")
                for (commit in commits) {
                    val sha = commit.path("id").asText()
                    val message = commit.path("message").asText()
                    val url = commit.path("url").asText()
                    linkKeys(issueKeyParser.parseKeys(message), projectId, projectKey,
                        IntegrationProvider.GITHUB, RefType.COMMIT, sha, url, message.take(100))
                }
            }
            "pull_request" -> {
                val pr = node.path("pull_request")
                val number = node.path("number").asText()
                val title = pr.path("title").asText()
                val url = pr.path("html_url").asText()
                val texts = listOf(title, pr.path("body").asText(""))
                val keys = texts.flatMap { issueKeyParser.parseKeys(it) }.distinct()
                linkKeys(keys, projectId, projectKey, IntegrationProvider.GITHUB, RefType.PR, number, url, title)
            }
        }
    }

    private fun processGitLabPayload(node: JsonNode, projectId: UUID, projectKey: String) {
        when (node.path("object_kind").asText()) {
            "push" -> {
                val commits = node.path("commits")
                for (commit in commits) {
                    val sha = commit.path("id").asText()
                    val message = commit.path("message").asText()
                    val url = commit.path("url").asText()
                    linkKeys(issueKeyParser.parseKeys(message), projectId, projectKey,
                        IntegrationProvider.GITLAB, RefType.COMMIT, sha, url, message.take(100))
                }
            }
            "merge_request" -> {
                val mr = node.path("object_attributes")
                val iid = mr.path("iid").asText()
                val title = mr.path("title").asText()
                val url = mr.path("url").asText()
                val keys = issueKeyParser.parseKeys(title) + issueKeyParser.parseKeys(mr.path("description").asText(""))
                linkKeys(keys.distinct(), projectId, projectKey, IntegrationProvider.GITLAB, RefType.PR, iid, url, title)
            }
        }
    }

    private fun detectGitHubEventType(node: JsonNode): String =
        if (node.has("pull_request")) "pull_request" else "push"

    private fun linkKeys(
        keys: List<String>, projectId: UUID, projectKey: String,
        provider: IntegrationProvider, refType: RefType,
        externalId: String, url: String, title: String?
    ) {
        for (key in keys) {
            if (!key.startsWith("$projectKey-")) continue
            val issue = issueRepository.findByKeyAndProjectId(key, projectId) ?: continue
            try {
                issueRefRepository.save(
                    IssueRef(issueId = issue.id, provider = provider, refType = refType,
                        externalId = externalId, url = url, title = title)
                )
                log.info("Linked {} {} to issue {}", provider, refType, key)
            } catch (e: Exception) {
                log.debug("Duplicate ref ignored for issue {} {} {}", key, refType, externalId)
            }
        }
    }

}
