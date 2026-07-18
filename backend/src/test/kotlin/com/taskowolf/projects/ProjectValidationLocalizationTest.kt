package com.taskowolf.projects

import com.fasterxml.jackson.databind.ObjectMapper
import com.taskowolf.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class ProjectValidationLocalizationTest : IntegrationTestBase() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper

    private fun register(email: String): String {
        val res = mockMvc.perform(
            post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","displayName":"User","password":"password123"}""")
        ).andReturn()
        return objectMapper.readTree(res.response.contentAsString).get("accessToken").asText()
    }

    @Test
    fun `invalid project key pattern returns localized german validation message`() {
        val token = register("proj-val-de@test.com")
        mockMvc.perform(
            post("/api/v1/projects").header("Authorization", "Bearer $token")
                .header("Accept-Language", "de")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"key":"lowercase","name":"Demo"}""")
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.details.key").value("Schlüssel muss aus Großbuchstaben und Ziffern bestehen"))
    }

    @Test
    fun `invalid project key pattern returns english by default`() {
        val token = register("proj-val-en@test.com")
        mockMvc.perform(
            post("/api/v1/projects").header("Authorization", "Bearer $token")
                .header("Accept-Language", "en")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"key":"lowercase","name":"Demo"}""")
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.details.key").value("Key must be uppercase letters and digits"))
    }
}
