package com.taskowolf.issues

import com.fasterxml.jackson.databind.ObjectMapper
import com.taskowolf.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class IssueErrorLocalizationTest : IntegrationTestBase() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper

    private fun register(email: String): String {
        val res = mockMvc.perform(
            post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","displayName":"User","password":"password123"}""")
        ).andReturn()
        return objectMapper.readTree(res.response.contentAsString).get("accessToken").asText()
    }

    private fun createProject(token: String, key: String) {
        mockMvc.perform(
            post("/api/v1/projects").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"key":"$key","name":"Demo"}""")
        ).andExpect(status().isCreated)
    }

    @Test
    fun `unknown issue returns localized german message`() {
        val token = register("issue-de@test.com")
        createProject(token, "PILOT")
        mockMvc.perform(
            get("/api/v1/projects/PILOT/issues/PILOT-999")
                .header("Authorization", "Bearer $token")
                .header("Accept-Language", "de")
        ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("Vorgang PILOT-999 nicht gefunden"))
    }

    @Test
    fun `unknown issue returns english by default`() {
        val token = register("issue-en@test.com")
        createProject(token, "PILOU")
        mockMvc.perform(
            get("/api/v1/projects/PILOU/issues/PILOU-999")
                .header("Authorization", "Bearer $token")
                .header("Accept-Language", "en")
        ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("Issue PILOU-999 not found"))
    }
}
