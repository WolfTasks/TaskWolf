package com.taskowolf.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.taskowolf.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class UserSearchIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper

    private fun registerAndGetToken(email: String, displayName: String): String {
        val result = mockMvc.perform(
            post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","displayName":"$displayName","password":"password123"}""")
        ).andReturn()
        return objectMapper.readTree(result.response.contentAsString).get("accessToken").asText()
    }

    @Test
    fun `project owner can search users by email fragment`() {
        val ownerToken = registerAndGetToken("search-owner@test.com", "Searcher")
        registerAndGetToken("findme@test.com", "Findme Person")
        mockMvc.perform(
            post("/api/v1/projects").header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"key":"SRCH","name":"Search"}""")
        ).andExpect(status().isCreated)

        mockMvc.perform(get("/api/v1/users/search?q=findme").header("Authorization", "Bearer $ownerToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.email=='findme@test.com')]").exists())
    }

    @Test
    fun `user without any admin project gets 403`() {
        // Consume the "first user in the DB becomes SystemRole.ADMIN" bootstrap slot (AuthService.register)
        // so this test's assertions don't depend on suite-wide test execution order.
        registerAndGetToken("bootstrap-admin@test.com", "Bootstrap")
        val plainToken = registerAndGetToken("plain@test.com", "Plain")
        registerAndGetToken("target@test.com", "Target")
        mockMvc.perform(get("/api/v1/users/search?q=target").header("Authorization", "Bearer $plainToken"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `query shorter than 2 chars returns empty list`() {
        val ownerToken = registerAndGetToken("short-owner@test.com", "ShortOwner")
        mockMvc.perform(
            post("/api/v1/projects").header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"key":"SHRT","name":"Short"}""")
        ).andExpect(status().isCreated)

        mockMvc.perform(get("/api/v1/users/search?q=a").header("Authorization", "Bearer $ownerToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }
}
