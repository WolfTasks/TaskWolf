package com.taskowolf.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.taskowolf.IntegrationTestBase
import com.taskowolf.auth.domain.SystemRole
import com.taskowolf.auth.infrastructure.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.util.UUID

class AdminUserControllerTest : IntegrationTestBase() {

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

    private fun setRole(email: String, role: SystemRole): UUID {
        val user = userRepository.findByEmail(email)!!
        user.systemRole = role
        userRepository.save(user)
        return user.id
    }

    private fun createToken(jwt: String): String {
        val result = mockMvc.perform(
            post("/api/v1/me/tokens")
                .header("Authorization", "Bearer $jwt")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"t","scope":"READ_WRITE"}""")
        ).andReturn()
        return objectMapper.readTree(result.response.contentAsString).get("plaintext").asText()
    }

    @Test
    fun `admin deactivates user and their token stops working`() {
        val adminJwt = register("admin-deact@test.com")
        setRole("admin-deact@test.com", SystemRole.ADMIN)
        val memberJwt = register("member-deact@test.com")
        val memberId = setRole("member-deact@test.com", SystemRole.MEMBER)
        val memberToken = createToken(memberJwt)

        // token works before deactivation
        mockMvc.perform(get("/api/v1/me/tokens").header("Authorization", "Bearer $memberToken"))
            .andExpect(status().isOk)

        mockMvc.perform(post("/api/v1/admin/users/$memberId/deactivate").header("Authorization", "Bearer $adminJwt"))
            .andExpect(status().isNoContent)

        // token dead after deactivation
        mockMvc.perform(get("/api/v1/me/tokens").header("Authorization", "Bearer $memberToken"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `non-admin is forbidden from admin users endpoint`() {
        val memberJwt = register("member-forbidden@test.com")
        setRole("member-forbidden@test.com", SystemRole.MEMBER)
        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer $memberJwt"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `self delete anonymizes account and blocks login`() {
        val jwt = register("self-delete@test.com")
        setRole("self-delete@test.com", SystemRole.MEMBER)

        mockMvc.perform(delete("/api/v1/me").header("Authorization", "Bearer $jwt"))
            .andExpect(status().isNoContent)

        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"self-delete@test.com","password":"password123"}""")
        ).andExpect(status().isForbidden)
    }
}
