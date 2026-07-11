package com.taskowolf.projects

import com.fasterxml.jackson.databind.ObjectMapper
import com.taskowolf.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class ProjectMyRoleIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper

    private fun registerAndGetToken(email: String): String {
        val result = mockMvc.perform(
            post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","displayName":"Test","password":"password123"}""")
        ).andReturn()
        return objectMapper.readTree(result.response.contentAsString).get("accessToken").asText()
    }

    private fun myId(token: String): String {
        val result = mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer $token")).andReturn()
        return objectMapper.readTree(result.response.contentAsString).get("id").asText()
    }

    @Test
    fun `myRole reflects the caller's role`() {
        val ownerToken = registerAndGetToken("mr-owner@test.com")
        val viewerToken = registerAndGetToken("mr-viewer@test.com")
        mockMvc.perform(
            post("/api/v1/projects").header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"key":"MYR","name":"MyRole"}""")
        ).andExpect(status().isCreated)
        mockMvc.perform(
            post("/api/v1/projects/MYR/members").header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userId":"${myId(viewerToken)}","role":"VIEWER"}""")
        ).andExpect(status().isCreated)

        mockMvc.perform(get("/api/v1/projects/MYR").header("Authorization", "Bearer $ownerToken"))
            .andExpect(status().isOk).andExpect(jsonPath("$.myRole").value("ADMIN"))
        mockMvc.perform(get("/api/v1/projects/MYR").header("Authorization", "Bearer $viewerToken"))
            .andExpect(status().isOk).andExpect(jsonPath("$.myRole").value("VIEWER"))
    }
}
