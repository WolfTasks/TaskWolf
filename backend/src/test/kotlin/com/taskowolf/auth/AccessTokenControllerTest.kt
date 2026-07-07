package com.taskowolf.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.taskowolf.IntegrationTestBase
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class AccessTokenControllerTest : IntegrationTestBase() {

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

    private fun createToken(jwt: String, name: String, scope: String): String {
        val result = mockMvc.perform(
            post("/api/v1/me/tokens")
                .header("Authorization", "Bearer $jwt")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"$name","scope":"$scope"}""")
        ).andExpect(status().isCreated).andReturn()
        return objectMapper.readTree(result.response.contentAsString).get("plaintext").asText()
    }

    @Test
    fun `POST creates token with twk_ prefix returned once`() {
        val jwt = registerAndLogin("pat-create@test.com")
        mockMvc.perform(
            post("/api/v1/me/tokens")
                .header("Authorization", "Bearer $jwt")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"CLI","scope":"READ_WRITE"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.plaintext").value(startsWith("twk_")))
            .andExpect(jsonPath("$.tokenPrefix").value(startsWith("twk_")))
            .andExpect(jsonPath("$.scope").value("READ_WRITE"))
    }

    @Test
    fun `GET lists tokens without plaintext`() {
        val jwt = registerAndLogin("pat-list@test.com")
        createToken(jwt, "Key A", "READ_WRITE")
        mockMvc.perform(get("/api/v1/me/tokens").header("Authorization", "Bearer $jwt"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].name").value("Key A"))
            .andExpect(jsonPath("$[0].tokenPrefix").value(startsWith("twk_")))
            .andExpect(jsonPath("$[0].plaintext").doesNotExist())
    }

    @Test
    fun `DELETE revokes token`() {
        val jwt = registerAndLogin("pat-revoke@test.com")
        val result = mockMvc.perform(
            post("/api/v1/me/tokens")
                .header("Authorization", "Bearer $jwt")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Del","scope":"READ_WRITE"}""")
        ).andReturn()
        val id = objectMapper.readTree(result.response.contentAsString).get("id").asText()

        mockMvc.perform(delete("/api/v1/me/tokens/$id").header("Authorization", "Bearer $jwt"))
            .andExpect(status().isNoContent)
        mockMvc.perform(get("/api/v1/me/tokens").header("Authorization", "Bearer $jwt"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `read-write token authenticates GET and POST`() {
        val jwt = registerAndLogin("pat-rw@test.com")
        val plaintext = createToken(jwt, "RW", "READ_WRITE")

        mockMvc.perform(get("/api/v1/me/tokens").header("Authorization", "Bearer $plaintext"))
            .andExpect(status().isOk)
        mockMvc.perform(
            post("/api/v1/me/tokens")
                .header("Authorization", "Bearer $plaintext")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"second","scope":"READ_WRITE"}""")
        ).andExpect(status().isCreated)
    }

    @Test
    fun `read-only token allows GET but forbids POST`() {
        val jwt = registerAndLogin("pat-ro@test.com")
        val plaintext = createToken(jwt, "RO", "READ_ONLY")

        mockMvc.perform(get("/api/v1/me/tokens").header("Authorization", "Bearer $plaintext"))
            .andExpect(status().isOk)
        mockMvc.perform(
            post("/api/v1/me/tokens")
                .header("Authorization", "Bearer $plaintext")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"blocked","scope":"READ_ONLY"}""")
        ).andExpect(status().isForbidden)
    }
}
