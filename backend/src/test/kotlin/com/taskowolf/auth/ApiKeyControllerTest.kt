package com.taskowolf.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.taskowolf.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class ApiKeyControllerTest : IntegrationTestBase() {

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

    private fun createProject(token: String, key: String) {
        mockMvc.perform(
            post("/api/v1/projects")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"key":"$key","name":"$key Project"}""")
        ).andExpect(status().isCreated)
    }

    @Test
    fun `POST api-keys creates key and returns plaintext once`() {
        val token = registerAndLogin("apikey1@test.com")
        createProject(token, "AK1")

        val result = mockMvc.perform(
            post("/api/v1/projects/AK1/api-keys")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"CI Key"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.plaintext").isString)
            .andExpect(jsonPath("$.keyPrefix").value(org.hamcrest.Matchers.startsWith("tw_")))
            .andReturn()

        val plaintext = objectMapper.readTree(result.response.contentAsString).get("plaintext").asText()
        assert(plaintext.startsWith("tw_")) { "plaintext must start with tw_" }
    }

    @Test
    fun `GET api-keys lists keys without plaintext`() {
        val token = registerAndLogin("apikey2@test.com")
        createProject(token, "AK2")
        mockMvc.perform(
            post("/api/v1/projects/AK2/api-keys")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"My Key"}""")
        ).andExpect(status().isCreated)

        mockMvc.perform(
            get("/api/v1/projects/AK2/api-keys")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].name").value("My Key"))
            .andExpect(jsonPath("$[0].keyPrefix").isString)
    }

    @Test
    fun `DELETE api-keys revokes key`() {
        val token = registerAndLogin("apikey3@test.com")
        createProject(token, "AK3")
        val createResult = mockMvc.perform(
            post("/api/v1/projects/AK3/api-keys")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Del Key"}""")
        ).andReturn()
        val keyId = objectMapper.readTree(createResult.response.contentAsString).get("id").asText()

        mockMvc.perform(
            delete("/api/v1/projects/AK3/api-keys/$keyId")
                .header("Authorization", "Bearer $token")
        ).andExpect(status().isNoContent)

        mockMvc.perform(
            get("/api/v1/projects/AK3/api-keys")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `API key can authenticate requests`() {
        val token = registerAndLogin("apikey4@test.com")
        createProject(token, "AK4")
        val createResult = mockMvc.perform(
            post("/api/v1/projects/AK4/api-keys")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Auth Key"}""")
        ).andReturn()
        val plaintext = objectMapper.readTree(createResult.response.contentAsString).get("plaintext").asText()

        mockMvc.perform(
            get("/api/v1/projects/AK4/api-keys")
                .header("Authorization", "Bearer $plaintext")
        ).andExpect(status().isOk)
    }
}
