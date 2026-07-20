package com.taskowolf.organizations

import com.fasterxml.jackson.databind.ObjectMapper
import com.taskowolf.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

class OrgSwitchLocalizationTest : IntegrationTestBase() {

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
    fun `switching to a non-member org returns localized 403 (de)`() {
        val token = register("switch-de@test.com")
        mockMvc.perform(
            post("/api/v1/auth/switch-org/${UUID.randomUUID()}")
                .header("Authorization", "Bearer $token")
                .header("Accept-Language", "de")
        ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("FORBIDDEN"))
            .andExpect(jsonPath("$.message").value("Kein Mitglied dieser Organisation"))
    }

    @Test
    fun `switching to a non-member org returns english by default`() {
        val token = register("switch-en@test.com")
        mockMvc.perform(
            post("/api/v1/auth/switch-org/${UUID.randomUUID()}")
                .header("Authorization", "Bearer $token")
                .header("Accept-Language", "en")
        ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.message").value("Not a member of this organization"))
    }
}
