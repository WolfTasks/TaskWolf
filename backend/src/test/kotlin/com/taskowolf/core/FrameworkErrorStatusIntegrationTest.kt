package com.taskowolf.core

import com.taskowolf.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper

/** B6: Spring MVC's own exceptions must keep their status instead of falling into the generic 500 handler. */
class FrameworkErrorStatusIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private fun token(email: String): String =
        objectMapper.readTree(
            mockMvc.perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"email":"$email","displayName":"B6","password":"password123"}""")
            ).andReturn().response.contentAsString
        ).get("accessToken").asString()

    @Test
    fun `unknown path returns 404 NOT_FOUND`() {
        val t = token("b6-unknown@test.com")
        mockMvc.perform(get("/api/v1/does-not-exist").header("Authorization", "Bearer $t"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("Resource not found"))
    }

    @Test
    fun `unsupported method returns 405 METHOD_NOT_ALLOWED`() {
        val t = token("b6-method@test.com")
        mockMvc.perform(patch("/api/v1/organizations").header("Authorization", "Bearer $t"))
            .andExpect(status().isMethodNotAllowed)
            .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
    }

    @Test
    fun `malformed path variable returns 400 BAD_REQUEST`() {
        val t = token("b6-uuid@test.com")
        mockMvc.perform(get("/api/v1/organizations/not-a-uuid").header("Authorization", "Bearer $t"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
    }
}
