package com.taskowolf.projects

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

class ProjectOrgAssignmentIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userRepository: UserRepository

    private fun register(email: String): String {
        val res = mockMvc.perform(
            post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","displayName":"U","password":"password123"}""")
        ).andReturn()
        return objectMapper.readTree(res.response.contentAsString).get("accessToken").asText()
    }
    private fun makeSystemAdmin(email: String) {
        val u = userRepository.findByEmail(email)!!; u.systemRole = SystemRole.ADMIN; userRepository.save(u)
    }
    private fun myId(token: String): String {
        val res = mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer $token")).andReturn()
        return objectMapper.readTree(res.response.contentAsString).get("id").asText()
    }
    private fun createOrg(adminToken: String, slug: String): String {
        val res = mockMvc.perform(
            post("/api/v1/organizations").header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"name":"$slug","slug":"$slug"}""")
        ).andExpect(status().isCreated).andReturn()
        return objectMapper.readTree(res.response.contentAsString).get("id").asText()
    }
    private fun createProject(token: String, key: String) {
        mockMvc.perform(
            post("/api/v1/projects").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON).content("""{"key":"$key","name":"$key"}""")
        ).andExpect(status().isCreated)
    }
    private fun addOrgMember(adminToken: String, orgId: String, userId: String, role: String) {
        mockMvc.perform(
            post("/api/v1/organizations/$orgId/members").header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"userId":"$userId","role":"$role"}""")
        ).andExpect(status().isCreated)
    }

    @Test
    fun `system admin can assign and unassign a project org`() {
        val adminToken = register("as-admin@test.com"); makeSystemAdmin("as-admin@test.com")
        val ownerToken = register("as-owner@test.com")
        val orgId = createOrg(adminToken, "as-org")
        createProject(ownerToken, "ASG")

        mockMvc.perform(
            patch("/api/v1/projects/ASG/organization").header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"orgId":"$orgId"}""")
        ).andExpect(status().isOk).andExpect(jsonPath("$.orgId").value(orgId))

        // Jackson serialisiert null-Felder standardmäßig als "orgId":null (kein NON_NULL konfiguriert)
        mockMvc.perform(
            patch("/api/v1/projects/ASG/organization").header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"orgId":null}""")
        ).andExpect(status().isOk).andExpect(jsonPath("$.orgId").value(org.hamcrest.Matchers.nullValue()))
    }

    @Test
    fun `project admin who is also org admin can assign`() {
        val adminToken = register("pa-admin@test.com"); makeSystemAdmin("pa-admin@test.com")
        val ownerToken = register("pa-owner@test.com")
        val ownerId = myId(ownerToken)
        val orgId = createOrg(adminToken, "pa-org")
        createProject(ownerToken, "PAG")
        addOrgMember(adminToken, orgId, ownerId, "ADMIN") // Projekt-Owner ist auch Org-ADMIN

        mockMvc.perform(
            patch("/api/v1/projects/PAG/organization").header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"orgId":"$orgId"}""")
        ).andExpect(status().isOk).andExpect(jsonPath("$.orgId").value(orgId))
    }

    @Test
    fun `project admin who is NOT org admin gets 403`() {
        val adminToken = register("na-admin@test.com"); makeSystemAdmin("na-admin@test.com")
        val ownerToken = register("na-owner@test.com")
        val orgId = createOrg(adminToken, "na-org")
        createProject(ownerToken, "NAG") // owner ist NICHT Mitglied der Org

        mockMvc.perform(
            patch("/api/v1/projects/NAG/organization").header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"orgId":"$orgId"}""")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `org admin who is NOT project admin gets 403`() {
        val adminToken = register("op-admin@test.com"); makeSystemAdmin("op-admin@test.com")
        val ownerToken = register("op-owner@test.com")
        val orgAdminToken = register("op-orgadmin@test.com")
        val orgAdminId = myId(orgAdminToken)
        val orgId = createOrg(adminToken, "op-org")
        createProject(ownerToken, "OPG")
        addOrgMember(adminToken, orgId, orgAdminId, "ADMIN") // Org-ADMIN, aber kein Projekt-Mitglied

        mockMvc.perform(
            patch("/api/v1/projects/OPG/organization").header("Authorization", "Bearer $orgAdminToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"orgId":"$orgId"}""")
        ).andExpect(status().isForbidden)
    }
}
