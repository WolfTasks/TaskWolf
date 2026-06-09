package com.taskowolf.projects

import com.fasterxml.jackson.databind.ObjectMapper
import com.taskowolf.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class ProjectAndIssueIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private fun registerAndGetToken(email: String): String {
        val result = mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","displayName":"Test","password":"password123"}""")
        ).andReturn()
        val body = objectMapper.readTree(result.response.contentAsString)
        return body.get("accessToken").asText()
    }

    @Test
    fun `create project creates default workflow with 3 statuses`() {
        val token = registerAndGetToken("proj1@test.com")

        // Create project
        mockMvc.perform(
            post("/api/v1/projects")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"key":"PROJ1","name":"Test Project"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.key").value("PROJ1"))

        // Verify default workflow was created with 3 statuses
        val workflowResult = mockMvc.perform(
            get("/api/v1/projects/PROJ1/workflows")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].statuses.length()").value(3))
            .andReturn()
    }

    @Test
    fun `create issue generates correct key sequence`() {
        val token = registerAndGetToken("issue1@test.com")

        mockMvc.perform(
            post("/api/v1/projects")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"key":"ISS1","name":"Issue Test"}""")
        ).andExpect(status().isCreated)

        mockMvc.perform(
            post("/api/v1/projects/ISS1/issues")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"title":"First Issue"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.key").value("ISS1-1"))
            .andExpect(jsonPath("$.statusCategory").value("TODO"))

        mockMvc.perform(
            post("/api/v1/projects/ISS1/issues")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"title":"Second Issue"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.key").value("ISS1-2"))
    }

    @Test
    fun `non-member cannot access project`() {
        val token1 = registerAndGetToken("owner2@test.com")
        val token2 = registerAndGetToken("nonmember@test.com")

        mockMvc.perform(
            post("/api/v1/projects")
                .header("Authorization", "Bearer $token1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"key":"PRIV","name":"Private"}""")
        ).andExpect(status().isCreated)

        mockMvc.perform(
            get("/api/v1/projects/PRIV")
                .header("Authorization", "Bearer $token2")
        )
            .andExpect(status().isForbidden)
    }
}
