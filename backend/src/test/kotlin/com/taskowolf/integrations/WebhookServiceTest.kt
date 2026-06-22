package com.taskowolf.integrations

import com.fasterxml.jackson.databind.ObjectMapper
import com.taskowolf.auth.domain.SystemRole
import com.taskowolf.auth.domain.User
import com.taskowolf.integrations.api.dto.CreateWebhookRequest
import com.taskowolf.integrations.application.HmacSigner
import com.taskowolf.integrations.application.SsrfValidator
import com.taskowolf.integrations.application.WebhookService
import com.taskowolf.integrations.domain.Webhook
import com.taskowolf.integrations.infrastructure.WebhookDeliveryRepository
import com.taskowolf.integrations.infrastructure.WebhookRepository
import com.taskowolf.projects.application.ProjectService
import com.taskowolf.projects.domain.Project
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class WebhookServiceTest {

    private val webhookRepository = mockk<WebhookRepository>()
    private val deliveryRepository = mockk<WebhookDeliveryRepository>()
    private val projectService = mockk<ProjectService>()
    private val ssrfValidator = mockk<SsrfValidator>()
    private val hmacSigner = mockk<HmacSigner>()
    private val objectMapper = ObjectMapper()
    private val service = WebhookService(
        webhookRepository, deliveryRepository, projectService,
        ssrfValidator, hmacSigner, objectMapper
    )

    private fun mockUser() = User(email = "u@t.com", displayName = "U", systemRole = SystemRole.MEMBER)
    private fun mockProject(user: User) = Project(key = "WH", name = "WH", owner = user)

    @Test
    fun `create validates SSRF and stores hashed secret`() {
        val user = mockUser()
        val project = mockProject(user)
        every { projectService.requireAdmin("WH", user.id) } returns project
        justRun { ssrfValidator.validate(any()) }
        every { webhookRepository.save(any<Webhook>()) } returnsArgument 0

        val req = CreateWebhookRequest(
            url = "https://hooks.example.com/payload",
            events = listOf("issue.created"),
            secret = "mysecret"
        )
        service.create("WH", req, user)

        verify { ssrfValidator.validate("https://hooks.example.com/payload") }
        verify {
            webhookRepository.save(withArg { webhook ->
                assert(webhook.secretHash != "mysecret") {
                    "secretHash should be a hash, not the plaintext secret"
                }
            })
        }
    }

    @Test
    fun `create rejects SSRF URL`() {
        val user = mockUser()
        val project = mockProject(user)
        every { projectService.requireAdmin("WH", user.id) } returns project
        every { ssrfValidator.validate(any()) } throws IllegalArgumentException("private IP")

        assertThrows<IllegalArgumentException> {
            service.create(
                "WH",
                CreateWebhookRequest("http://localhost/hook", listOf("issue.created")),
                user
            )
        }
        verify(exactly = 0) { webhookRepository.save(any()) }
    }
}
