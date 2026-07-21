package com.taskowolf.servicedesk

import com.fasterxml.jackson.databind.ObjectMapper
import com.taskowolf.IntegrationTestBase
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

class ServiceDeskErrorLocalizationTest : IntegrationTestBase() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper

    private fun register(email: String): String {
        val res = mockMvc.perform(
            post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","displayName":"User","password":"password123"}""")
        ).andReturn()
        return objectMapper.readTree(res.response.contentAsString).get("accessToken").asText()
    }
    private fun createProject(token: String, key: String) =
        mockMvc.perform(
            post("/api/v1/projects").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"key":"$key","name":"Demo"}""")
        ).andExpect(status().isCreated)
    private fun enableServiceDesk(token: String, key: String) =
        mockMvc.perform(
            post("/api/v1/projects/$key/service-desk/enable").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"emailAddress":"desk@test.com"}""")
        ).andExpect(status().isOk)

    @Test
    fun `unknown project returns 404 not 500 (localized de)`() {
        val token = register("sd-nf-de@test.com")
        mockMvc.perform(
            get("/api/v1/projects/NOPE/service-desk").header("Authorization", "Bearer $token")
                .header("Accept-Language", "de")
        ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("Projekt nicht gefunden: NOPE"))
    }

    @Test
    fun `service desk not enabled returns 404 not 500 (localized de)`() {
        val token = register("sd-off-de@test.com")
        createProject(token, "SDOFF")
        mockMvc.perform(
            get("/api/v1/projects/SDOFF/service-desk").header("Authorization", "Bearer $token")
                .header("Accept-Language", "de")
        ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("Servicedesk ist für Projekt SDOFF nicht aktiviert"))
    }

    @Test
    fun `invalid priority returns 400 with localized body (de)`() {
        val token = register("sd-prio-de@test.com")
        createProject(token, "SDPRIO")
        enableServiceDesk(token, "SDPRIO")
        mockMvc.perform(
            post("/api/v1/projects/SDPRIO/service-desk/sla-policies").header("Authorization", "Bearer $token")
                .header("Accept-Language", "de").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"P1","priority":"BOGUS","responseMinutes":10,"resolutionMinutes":60}""")
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
            .andExpect(jsonPath("$.message", startsWith("Ungültige Priorität: BOGUS")))
    }

    @Test
    fun `invalid severity returns 400 with localized body (de)`() {
        val token = register("sd-sev-de@test.com")
        createProject(token, "SDSEV")
        mockMvc.perform(
            post("/api/v1/projects/SDSEV/incidents").header("Authorization", "Bearer $token")
                .header("Accept-Language", "de").contentType(MediaType.APPLICATION_JSON)
                .content("""{"issueId":"${UUID.randomUUID()}","severity":"BOGUS","onCallAssigneeId":null,"notifyUserIds":[]}""")
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
            .andExpect(jsonPath("$.message", startsWith("Ungültiger Schweregrad 'BOGUS'")))
    }
}
