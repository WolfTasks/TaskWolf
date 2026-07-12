package com.taskowolf.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.taskowolf.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class UserLanguageIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper

    private fun register(email: String): String {
        val result = mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","displayName":"User","password":"password123"}""")
        ).andReturn()
        return objectMapper.readTree(result.response.contentAsString).get("accessToken").asText()
    }

    @Test
    fun `auth me returns null language by default`() {
        val token = register("language-default@test.com")

        mockMvc.perform(
            get("/api/v1/auth/me").header("Authorization", "Bearer $token")
        ).andExpect(jsonPath("$.language").value(null))
    }

    @Test
    fun `patch me language persists and 400s on unknown value`() {
        val token = register("language-patch@test.com")

        mockMvc.perform(
            patch("/api/v1/me/language")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"language":"de"}""")
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.language").value("de"))

        mockMvc.perform(
            patch("/api/v1/me/language")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"language":"fr"}""")
        ).andExpect(status().isBadRequest)
    }
}
