package com.taskowolf.issues

import com.fasterxml.jackson.databind.ObjectMapper
import com.taskowolf.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

/**
 * Guards the DTO-assembly-outside-a-transaction pattern for the list and update endpoints.
 *
 * IssueResponse.from() dereferences lazy @ManyToOne associations (status, reporter). With OSIV
 * disabled, the controller must keep the persistence session open (@Transactional(readOnly=true))
 * or these endpoints throw LazyInitializationException. These tests assert the response bodies
 * carry populated statusName/reporterName over real HTTP.
 */
class IssueControllerLazyLoadingTest : IntegrationTestBase() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private fun registerAndGetToken(email: String): String {
        val result = mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","displayName":"Lazy Tester","password":"password123"}""")
        ).andReturn()
        val body = objectMapper.readTree(result.response.contentAsString)
        return body.get("accessToken").asText()
    }

    /** Returns the created issue as (id, key). */
    private fun createProjectWithIssue(token: String, key: String): Pair<String, String> {
        mockMvc.perform(
            post("/api/v1/projects")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"key":"$key","name":"Lazy Project"}""")
        ).andExpect(status().isCreated)

        val created = mockMvc.perform(
            post("/api/v1/projects/$key/issues")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"title":"Lazy Issue"}""")
        ).andExpect(status().isCreated).andReturn()

        val body = objectMapper.readTree(created.response.contentAsString)
        return body.get("id").asText() to body.get("key").asText()
    }

    @Test
    fun `GET issues list populates statusName and reporterName`() {
        val token = registerAndGetToken("lazy-list@test.com")
        createProjectWithIssue(token, "LAZL")

        mockMvc.perform(
            get("/api/v1/projects/LAZL/issues")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].statusName").value("To Do"))
            .andExpect(jsonPath("$.content[0].reporterName").value("Lazy Tester"))
    }

    @Test
    fun `PATCH issue update populates statusName and reporterName`() {
        val token = registerAndGetToken("lazy-update@test.com")
        val (issueId, issueKey) = createProjectWithIssue(token, "LAZU")

        mockMvc.perform(
            patch("/api/v1/projects/LAZU/issues/$issueId")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"title":"Updated Title"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.title").value("Updated Title"))
            .andExpect(jsonPath("$.statusName").value("To Do"))
            .andExpect(jsonPath("$.reporterName").value("Lazy Tester"))

        // Re-fetch to prove the write actually persisted (a readOnly outer tx would silently
        // drop the mutation while still returning the mutated in-memory DTO above).
        mockMvc.perform(
            get("/api/v1/projects/LAZU/issues/$issueKey")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.title").value("Updated Title"))
    }
}
