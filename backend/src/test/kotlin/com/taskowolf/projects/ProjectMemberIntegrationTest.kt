package com.taskowolf.projects

import com.fasterxml.jackson.databind.ObjectMapper
import com.taskowolf.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class ProjectMemberIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper

    private fun registerAndGetToken(email: String): String {
        val result = mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","displayName":"Test","password":"password123"}""")
        ).andReturn()
        return objectMapper.readTree(result.response.contentAsString).get("accessToken").asText()
    }

    private fun myId(token: String): String {
        val result = mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer $token")).andReturn()
        return objectMapper.readTree(result.response.contentAsString).get("id").asText()
    }

    private fun createProject(token: String, key: String) {
        mockMvc.perform(
            post("/api/v1/projects").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"key":"$key","name":"$key"}""")
        ).andExpect(status().isCreated)
    }

    @Test
    fun `admin can add, change role, list and remove a member`() {
        val ownerToken = registerAndGetToken("m-owner@test.com")
        val memberToken = registerAndGetToken("m-member@test.com")
        val memberId = myId(memberToken)
        createProject(ownerToken, "MEM")

        // add as VIEWER
        mockMvc.perform(
            post("/api/v1/projects/MEM/members").header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userId":"$memberId","role":"VIEWER"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.role").value("VIEWER"))
            .andExpect(jsonPath("$.user.id").value(memberId))

        // list shows owner + member with roles
        mockMvc.perform(get("/api/v1/projects/MEM/members").header("Authorization", "Bearer $ownerToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.user.id=='$memberId')].role").value("VIEWER"))

        // change role to ADMIN
        mockMvc.perform(
            patch("/api/v1/projects/MEM/members/$memberId").header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"role":"ADMIN"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.role").value("ADMIN"))

        // remove
        mockMvc.perform(delete("/api/v1/projects/MEM/members/$memberId").header("Authorization", "Bearer $ownerToken"))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `non-admin member cannot add members`() {
        val ownerToken = registerAndGetToken("m-owner2@test.com")
        val memberToken = registerAndGetToken("m-member2@test.com")
        val thirdToken = registerAndGetToken("m-third@test.com")
        val memberId = myId(memberToken)
        val thirdId = myId(thirdToken)
        createProject(ownerToken, "MEM2")

        // owner adds member as MEMBER (write, not admin)
        mockMvc.perform(
            post("/api/v1/projects/MEM2/members").header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userId":"$memberId","role":"MEMBER"}""")
        ).andExpect(status().isCreated)

        // member tries to add third → 403
        mockMvc.perform(
            post("/api/v1/projects/MEM2/members").header("Authorization", "Bearer $memberToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userId":"$thirdId","role":"MEMBER"}""")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `adding an existing member returns 409`() {
        val ownerToken = registerAndGetToken("m-owner3@test.com")
        val memberToken = registerAndGetToken("m-member3@test.com")
        val memberId = myId(memberToken)
        createProject(ownerToken, "MEM3")

        val body = """{"userId":"$memberId","role":"MEMBER"}"""
        mockMvc.perform(
            post("/api/v1/projects/MEM3/members").header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON).content(body)
        ).andExpect(status().isCreated)
        mockMvc.perform(
            post("/api/v1/projects/MEM3/members").header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON).content(body)
        ).andExpect(status().isConflict)
    }

    @Test
    fun `a non-owner admin cannot change their own role but can change others`() {
        val ownerToken = registerAndGetToken("self-owner@test.com")
        val adminToken = registerAndGetToken("self-admin@test.com")
        val otherToken = registerAndGetToken("self-other@test.com")
        val adminId = myId(adminToken)
        val otherId = myId(otherToken)
        createProject(ownerToken, "SELF")

        // owner promotes adminToken's user to ADMIN, and adds a third MEMBER
        mockMvc.perform(
            post("/api/v1/projects/SELF/members").header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userId":"$adminId","role":"ADMIN"}""")
        ).andExpect(status().isCreated)
        mockMvc.perform(
            post("/api/v1/projects/SELF/members").header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userId":"$otherId","role":"MEMBER"}""")
        ).andExpect(status().isCreated)

        // the admin tries to change THEIR OWN role → 403
        mockMvc.perform(
            patch("/api/v1/projects/SELF/members/$adminId").header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"role":"MEMBER"}""")
        ).andExpect(status().isForbidden)

        // positive control: the admin changes ANOTHER member's role → 200
        mockMvc.perform(
            patch("/api/v1/projects/SELF/members/$otherId").header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"role":"ADMIN"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.role").value("ADMIN"))
    }

    @Test
    fun `unknown role in body returns 400 not 500`() {
        val ownerToken = registerAndGetToken("m-owner4@test.com")
        val memberToken = registerAndGetToken("m-member4@test.com")
        val memberId = myId(memberToken)
        createProject(ownerToken, "MEM4")

        mockMvc.perform(
            post("/api/v1/projects/MEM4/members").header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userId":"$memberId","role":"SUPERADMIN"}""")
        ).andExpect(status().isBadRequest)
    }
}
