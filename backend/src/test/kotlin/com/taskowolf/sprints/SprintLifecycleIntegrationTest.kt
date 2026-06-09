package com.taskowolf.sprints

import com.fasterxml.jackson.databind.ObjectMapper
import com.taskowolf.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class SprintLifecycleIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper

    private fun registerAndGetToken(email: String): String {
        val result = mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","displayName":"Dev","password":"password123"}""")
        ).andReturn()
        return objectMapper.readTree(result.response.contentAsString).get("accessToken").asText()
    }

    @Test
    fun `full sprint lifecycle — create, start, move issue, complete, check backlog`() {
        val token = registerAndGetToken("sprint-flow@test.com")

        // Create project
        mockMvc.perform(
            post("/api/v1/projects")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"key":"SPR1","name":"Sprint Test"}""")
        ).andExpect(status().isCreated)

        // Create two issues with story points
        val issue1Result = mockMvc.perform(
            post("/api/v1/projects/SPR1/issues")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"title":"Issue 1","storyPoints":5}""")
        ).andExpect(status().isCreated).andReturn()
        val issue1Id = objectMapper.readTree(issue1Result.response.contentAsString).get("id").asText()

        val issue2Result = mockMvc.perform(
            post("/api/v1/projects/SPR1/issues")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"title":"Issue 2","storyPoints":3}""")
        ).andExpect(status().isCreated).andReturn()
        val issue2Id = objectMapper.readTree(issue2Result.response.contentAsString).get("id").asText()

        // Create sprint
        val sprintResult = mockMvc.perform(
            post("/api/v1/projects/SPR1/sprints")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Sprint 1","goal":"Ship it"}""")
        ).andExpect(status().isCreated).andReturn()
        val sprintId = objectMapper.readTree(sprintResult.response.contentAsString).get("id").asText()

        // Assign issues to sprint
        mockMvc.perform(
            put("/api/v1/projects/SPR1/sprints/$sprintId/issues/$issue1Id")
                .header("Authorization", "Bearer $token")
        ).andExpect(status().isNoContent)

        mockMvc.perform(
            put("/api/v1/projects/SPR1/sprints/$sprintId/issues/$issue2Id")
                .header("Authorization", "Bearer $token")
        ).andExpect(status().isNoContent)

        // Start sprint — plannedPoints should be 8
        mockMvc.perform(
            post("/api/v1/projects/SPR1/sprints/$sprintId/start")
                .header("Authorization", "Bearer $token")
        ).andExpect(status().isOk)
         .andExpect(jsonPath("$.status").value("ACTIVE"))
         .andExpect(jsonPath("$.plannedPoints").value(8))

        // Board should show active sprint
        mockMvc.perform(
            get("/api/v1/projects/SPR1/board")
                .header("Authorization", "Bearer $token")
        ).andExpect(status().isOk)
         .andExpect(jsonPath("$.sprint.name").value("Sprint 1"))

        // Get workflow statuses to find DONE status
        val workflowResult = mockMvc.perform(
            get("/api/v1/projects/SPR1/workflows")
                .header("Authorization", "Bearer $token")
        ).andReturn()
        val statuses = objectMapper.readTree(workflowResult.response.contentAsString)[0]["statuses"]
        val doneStatusId = statuses.first { it["category"].asText() == "DONE" }.get("id").asText()

        // Move issue1 to DONE
        mockMvc.perform(
            patch("/api/v1/projects/SPR1/board/move")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"issueId":"$issue1Id","newStatusId":"$doneStatusId"}""")
        ).andExpect(status().isOk)

        // Complete sprint — issue2 should go back to backlog
        mockMvc.perform(
            post("/api/v1/projects/SPR1/sprints/$sprintId/complete")
                .header("Authorization", "Bearer $token")
        ).andExpect(status().isOk)
         .andExpect(jsonPath("$.sprint.status").value("CLOSED"))
         .andExpect(jsonPath("$.movedToBacklogCount").value(1))

        // Backlog should contain issue2
        mockMvc.perform(
            get("/api/v1/projects/SPR1/backlog")
                .header("Authorization", "Bearer $token")
        ).andExpect(status().isOk)
         .andExpect(jsonPath("$.backlogIssues.length()").value(1))

        // Velocity should show one entry
        mockMvc.perform(
            get("/api/v1/projects/SPR1/reports/velocity")
                .header("Authorization", "Bearer $token")
        ).andExpect(status().isOk)
         .andExpect(jsonPath("$.entries.length()").value(1))
         .andExpect(jsonPath("$.entries[0].completedPoints").value(5))
    }
}
