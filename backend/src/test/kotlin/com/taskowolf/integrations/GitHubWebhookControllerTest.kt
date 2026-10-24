package com.taskowolf.integrations

import tools.jackson.databind.ObjectMapper
import com.taskowolf.IntegrationTestBase
import com.taskowolf.integrations.application.HmacSigner
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class GitHubWebhookControllerTest : IntegrationTestBase() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var hmacSigner: HmacSigner

    private fun registerAndLogin(email: String): String {
        val result = mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","displayName":"User","password":"password123"}""")
        ).andReturn()
        return objectMapper.readTree(result.response.contentAsString).get("accessToken").asString()
    }

    private fun createProject(token: String, key: String) {
        mockMvc.perform(
            post("/api/v1/projects")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"key":"$key","name":"$key Project"}""")
        ).andExpect(status().isCreated)
    }

    private fun setupGitHubIntegration(token: String, projectKey: String): String {
        val result = mockMvc.perform(
            post("/api/v1/projects/$projectKey/integrations")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"provider":"GITHUB"}""")
        ).andExpect(status().isCreated).andReturn()
        return objectMapper.readTree(result.response.contentAsString).get("plaintextSecret").asString()
    }

    @Test
    fun `GitHub push webhook with valid signature returns 200`() {
        val token = registerAndLogin("gh1@test.com")
        createProject(token, "GH1")
        val secret = setupGitHubIntegration(token, "GH1")

        val payload = """{"ref":"refs/heads/main","commits":[{"id":"abc123","message":"fix bug","url":"https://github.com/org/repo/commit/abc123"}]}"""
        val sha256secret = hmacSigner.sign(payload, secret)

        mockMvc.perform(
            post("/api/v1/integrations/github/GH1/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .header("X-Hub-Signature-256", sha256secret)
        ).andExpect(status().isOk)
    }

    @Test
    fun `GitHub webhook with invalid signature returns 401`() {
        val token = registerAndLogin("gh2@test.com")
        createProject(token, "GH2")
        setupGitHubIntegration(token, "GH2")

        val payload = """{"ref":"refs/heads/main","commits":[]}"""
        mockMvc.perform(
            post("/api/v1/integrations/github/GH2/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .header("X-Hub-Signature-256", "sha256=invalidsignature")
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `GitHub push with issue key creates IssueRef`() {
        val token = registerAndLogin("gh3@test.com")
        createProject(token, "GH3")
        val secret = setupGitHubIntegration(token, "GH3")

        val issueResult = mockMvc.perform(
            post("/api/v1/projects/GH3/issues")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"title":"Test Issue","type":"TASK","priority":"MEDIUM"}""")
        ).andReturn()
        val issueKey = objectMapper.readTree(issueResult.response.contentAsString).get("key").asString()

        val payload = """{"ref":"refs/heads/main","commits":[{"id":"def456","message":"fix $issueKey crash","url":"https://github.com/org/repo/commit/def456"}]}"""
        val sig = hmacSigner.sign(payload, secret)

        mockMvc.perform(
            post("/api/v1/integrations/github/GH3/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .header("X-Hub-Signature-256", sig)
        ).andExpect(status().isOk)

        mockMvc.perform(
            get("/api/v1/projects/GH3/issues/$issueKey")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.refs[0].provider").value("GITHUB"))
            .andExpect(jsonPath("$.refs[0].refType").value("COMMIT"))
    }

    // H4: Jackson 3's no-arg asString() throws on object/array nodes; a signed but odd payload must not 500.
    @Test
    fun `signed payloads with null, object or array fields are accepted`() {
        val token = registerAndLogin("gh4@test.com")
        createProject(token, "GH4")
        val secret = setupGitHubIntegration(token, "GH4")

        val payloads = listOf(
            """{"ref":"refs/heads/main","commits":[{"id":{"sha":"x"},"message":{"text":"GH4-1"},"url":["u"]}]}""",
            """{"number":7,"pull_request":{"title":null,"body":{"x":1},"html_url":"https://example.com/pr/7"}}""",
            """{"number":[1],"pull_request":{"title":{"t":"GH4-1"},"body":null,"html_url":null}}"""
        )
        for (payload in payloads) {
            mockMvc.perform(
                post("/api/v1/integrations/github/GH4/webhook")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
                    .header("X-Hub-Signature-256", hmacSigner.sign(payload, secret))
            ).andExpect(status().isOk)
        }
    }

}
