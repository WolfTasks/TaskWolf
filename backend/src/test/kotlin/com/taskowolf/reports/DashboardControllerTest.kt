package com.taskowolf.reports

import com.fasterxml.jackson.databind.ObjectMapper
import com.taskowolf.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class DashboardControllerTest : IntegrationTestBase() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper

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

    @Test
    fun `GET dashboard auto-creates empty dashboard for project member`() {
        val token = registerAndLogin("dash1@test.com")
        createProject(token, "DASH1")

        mockMvc.perform(
            get("/api/v1/projects/DASH1/dashboard")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.projectId").isNotEmpty)
            .andExpect(jsonPath("$.widgets").isArray)
            .andExpect(jsonPath("$.widgets.length()").value(0))
    }

    @Test
    fun `POST widget adds widget to dashboard (owner is admin)`() {
        val token = registerAndLogin("dash2@test.com")
        createProject(token, "DASH2")

        mockMvc.perform(
            post("/api/v1/projects/DASH2/dashboard/widgets")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"type":"VELOCITY","gridX":0,"gridY":0,"gridW":6,"gridH":4}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.type").value("VELOCITY"))
            .andExpect(jsonPath("$.gridW").value(6))
    }

    @Test
    fun `non-member cannot GET dashboard`() {
        val ownerToken = registerAndLogin("dash3owner@test.com")
        val otherToken = registerAndLogin("dash3other@test.com")
        createProject(ownerToken, "DASH3")

        mockMvc.perform(
            get("/api/v1/projects/DASH3/dashboard")
                .header("Authorization", "Bearer $otherToken")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `DELETE widget removes it from dashboard`() {
        val token = registerAndLogin("dash4@test.com")
        createProject(token, "DASH4")

        val addResult = mockMvc.perform(
            post("/api/v1/projects/DASH4/dashboard/widgets")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"type":"ISSUE_COUNT","gridX":0,"gridY":0,"gridW":3,"gridH":2}""")
        ).andReturn()
        val widgetId = objectMapper.readTree(addResult.response.contentAsString).get("id").asText()

        mockMvc.perform(
            delete("/api/v1/projects/DASH4/dashboard/widgets/$widgetId")
                .header("Authorization", "Bearer $token")
        ).andExpect(status().isNoContent)

        mockMvc.perform(
            get("/api/v1/projects/DASH4/dashboard")
                .header("Authorization", "Bearer $token")
        ).andExpect(jsonPath("$.widgets.length()").value(0))
    }

    @Test
    fun `PUT layout updates widget grid positions`() {
        val token = registerAndLogin("dash5@test.com")
        createProject(token, "DASH5")

        // Add a widget first
        val addResult = mockMvc.perform(
            post("/api/v1/projects/DASH5/dashboard/widgets")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"type":"VELOCITY","gridX":0,"gridY":0,"gridW":4,"gridH":4}""")
        ).andReturn()
        val widgetId = objectMapper.readTree(addResult.response.contentAsString).get("id").asText()

        // Save new layout positions
        mockMvc.perform(
            put("/api/v1/projects/DASH5/dashboard/layout")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""[{"widgetId":"$widgetId","gridX":6,"gridY":2,"gridW":6,"gridH":5}]""")
        )
            .andExpect(status().isOk)

        // Verify positions changed on GET
        mockMvc.perform(
            get("/api/v1/projects/DASH5/dashboard")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(jsonPath("$.widgets[0].gridX").value(6))
            .andExpect(jsonPath("$.widgets[0].gridY").value(2))
            .andExpect(jsonPath("$.widgets[0].gridW").value(6))
            .andExpect(jsonPath("$.widgets[0].gridH").value(5))
    }

    @Test
    fun `PUT layout requires admin role`() {
        val ownerToken = registerAndLogin("dash6owner@test.com")
        val memberToken = registerAndLogin("dash6member@test.com")
        createProject(ownerToken, "DASH6")

        // non-member cannot call PUT layout
        mockMvc.perform(
            put("/api/v1/projects/DASH6/dashboard/layout")
                .header("Authorization", "Bearer $memberToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[]")
        ).andExpect(status().isForbidden)
    }
}
