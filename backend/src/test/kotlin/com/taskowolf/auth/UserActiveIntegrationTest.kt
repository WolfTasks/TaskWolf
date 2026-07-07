package com.taskowolf.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.taskowolf.IntegrationTestBase
import com.taskowolf.auth.infrastructure.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class UserActiveIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userRepository: UserRepository

    private fun register(email: String): String {
        val result = mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","displayName":"User","password":"password123"}""")
        ).andReturn()
        return objectMapper.readTree(result.response.contentAsString).get("accessToken").asText()
    }

    private fun deactivate(email: String) {
        val user = userRepository.findByEmail(email)!!
        user.active = false
        userRepository.save(user)
    }

    @Test
    fun `inactive user cannot login`() {
        register("inactive-login@test.com")
        deactivate("inactive-login@test.com")

        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"inactive-login@test.com","password":"password123"}""")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `inactive user JWT is rejected`() {
        val token = register("inactive-jwt@test.com")
        deactivate("inactive-jwt@test.com")

        mockMvc.perform(
            get("/api/v1/auth/me").header("Authorization", "Bearer $token")
        ).andExpect(status().isUnauthorized)
    }
}
