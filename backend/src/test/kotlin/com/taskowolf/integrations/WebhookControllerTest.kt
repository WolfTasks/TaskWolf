package com.taskowolf.integrations

import com.fasterxml.jackson.databind.ObjectMapper
import com.taskowolf.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class WebhookControllerTest : IntegrationTestBase() {

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
    fun `POST webhook creates webhook and returns secret once`() {
        val token = registerAndLogin("wh1@test.com")
        createProject(token, "WH1")

        val result = mockMvc.perform(
            post("/api/v1/projects/WH1/webhooks")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"url":"https://hooks.example.com/payload","events":["issue.created"]}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.webhook.url").value("https://hooks.example.com/payload"))
            .andExpect(jsonPath("$.plaintextSecret").isString)
            .andReturn()

        val secret = objectMapper.readTree(result.response.contentAsString).get("plaintextSecret").asText()
        assert(secret.isNotBlank())
    }

    @Test
    fun `GET webhooks lists webhooks`() {
        val token = registerAndLogin("wh2@test.com")
        createProject(token, "WH2")
        mockMvc.perform(
            post("/api/v1/projects/WH2/webhooks")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"url":"https://hooks.example.com/p","events":["sprint.started"]}""")
        ).andExpect(status().isCreated)

        mockMvc.perform(
            get("/api/v1/projects/WH2/webhooks")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].url").value("https://hooks.example.com/p"))
            .andExpect(jsonPath("$[0].events[0]").value("sprint.started"))
    }

    @Test
    fun `POST webhook rejects private IP`() {
        val token = registerAndLogin("wh3@test.com")
        createProject(token, "WH3")

        mockMvc.perform(
            post("/api/v1/projects/WH3/webhooks")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"url":"http://192.168.1.1/hook","events":["issue.created"]}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `DELETE webhook removes it`() {
        val token = registerAndLogin("wh4@test.com")
        createProject(token, "WH4")
        val createResult = mockMvc.perform(
            post("/api/v1/projects/WH4/webhooks")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"url":"https://hooks.example.com/del","events":["issue.created"]}""")
        ).andReturn()
        val webhookId = objectMapper.readTree(createResult.response.contentAsString)
            .get("webhook").get("id").asText()

        mockMvc.perform(
            delete("/api/v1/projects/WH4/webhooks/$webhookId")
                .header("Authorization", "Bearer $token")
        ).andExpect(status().isNoContent)

        mockMvc.perform(
            get("/api/v1/projects/WH4/webhooks")
                .header("Authorization", "Bearer $token")
        ).andExpect(jsonPath("$.length()").value(0))
    }
}
