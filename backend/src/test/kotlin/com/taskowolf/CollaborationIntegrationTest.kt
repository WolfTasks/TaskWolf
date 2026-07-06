package com.taskowolf

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class CollaborationIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper

    private fun register(email: String, displayName: String = "User"): String {
        val result = mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","displayName":"$displayName","password":"password123"}""")
        )
            .andExpect(status().isCreated)
            .andReturn()
        val tree = objectMapper.readTree(result.response.contentAsString)
        return tree.get("accessToken").asText()
    }

    @Test
    fun `comment lifecycle creates activity entries`() {
        val token = register("collab-test@example.com", "Collab User")

        // Create project
        mockMvc.perform(
            post("/api/v1/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $token")
                .content("""{"key":"CLAB","name":"Collab Test"}""")
        ).andExpect(status().isCreated)

        // Create issue
        val issueResult = mockMvc.perform(
            post("/api/v1/projects/CLAB/issues")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $token")
                .content("""{"title":"Test Issue for Comments"}""")
        )
            .andExpect(status().isCreated)
            .andReturn()
        val issueKey = objectMapper.readTree(issueResult.response.contentAsString).get("key").asText()

        // Post a comment
        val commentResult = mockMvc.perform(
            post("/api/v1/projects/CLAB/issues/$issueKey/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $token")
                .content("""{"body":"This is a test comment"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.body").value("This is a test comment"))
            .andReturn()
        val commentId = objectMapper.readTree(commentResult.response.contentAsString).get("id").asText()

        // List comments — should contain the comment
        mockMvc.perform(
            get("/api/v1/projects/CLAB/issues/$issueKey/comments")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].body").value("This is a test comment"))

        // Edit comment
        mockMvc.perform(
            put("/api/v1/projects/CLAB/issues/$issueKey/comments/$commentId")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $token")
                .content("""{"body":"Updated comment"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.body").value("Updated comment"))

        // Activity feed — should have COMMENT activity
        mockMvc.perform(
            get("/api/v1/projects/CLAB/issues/$issueKey/activity")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)
            .andExpect(jsonPath("$.content[0].type").value("COMMENT"))

        // Delete comment
        mockMvc.perform(
            delete("/api/v1/projects/CLAB/issues/$issueKey/comments/$commentId")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isNoContent)

        // After delete: comment should appear as deleted=true
        mockMvc.perform(
            get("/api/v1/projects/CLAB/issues/$issueKey/comments")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)
        // Soft-deleted comments are filtered out (deletedAt != null filtered in listComments)
        // So the list should be empty
    }
}
