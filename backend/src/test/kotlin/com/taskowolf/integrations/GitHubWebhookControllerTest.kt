package com.taskowolf.integrations

import com.fasterxml.jackson.databind.ObjectMapper
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
        return objectMapper.readTree(result.response.contentAsString).get("accessToken").asText()
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
        return objectMapper.readTree(result.response.contentAsString).get("plaintextSecret").asText()
    }

    @Test
    fun `GitHub push webhook with valid signature returns 200`() {
        val token = registerAndLogin("gh1@test.com")
        createProject(token, "GH1")
        val secret = setupGitHubIntegration(token, "GH1")

        val payload = """{"ref":"refs/heads/main","commits":[{"id":"abc123","message":"fix bug","url":"https://github.com/org/repo/commit/abc123"}]}"""
        val sha256secret = hmacSigner.sign(payload, sha256(secret))

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
        val issueKey = objectMapper.readTree(issueResult.response.contentAsString).get("key").asText()

        val payload = """{"ref":"refs/heads/main","commits":[{"id":"def456","message":"fix $issueKey crash","url":"https://github.com/org/repo/commit/def456"}]}"""
        val sig = hmacSigner.sign(payload, sha256(secret))

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

    private fun sha256(input: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
