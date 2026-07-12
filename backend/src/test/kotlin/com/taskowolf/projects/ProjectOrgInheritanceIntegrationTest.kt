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

class ProjectOrgInheritanceIntegrationTest : IntegrationTestBase() {

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

    private fun assignProjectToOrg(adminToken: String, key: String, orgId: String) {
        mockMvc.perform(
            patch("/api/v1/projects/$key/organization").header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"orgId":"$orgId"}""")
        ).andExpect(status().isOk)
    }

    private fun addOrgMember(adminToken: String, orgId: String, userId: String, role: String) {
        mockMvc.perform(
            post("/api/v1/organizations/$orgId/members").header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"userId":"$userId","role":"$role"}""")
        ).andExpect(status().isCreated)
    }

    @Test
    fun `org member sees inherited org projects in their project list`() {
        val adminToken = register("inh-admin@test.com"); makeSystemAdmin("inh-admin@test.com")
        val ownerToken = register("inh-owner@test.com")
        val memberToken = register("inh-member@test.com")
        val memberId = myId(memberToken)

        val orgId = createOrg(adminToken, "inh-org")
        createProject(ownerToken, "INHL")
        assignProjectToOrg(adminToken, "INHL", orgId)
        addOrgMember(adminToken, orgId, memberId, "MEMBER")

        // Der Org-Member war nie explizit im Projekt, sieht es aber via Org-Erbe in der Liste
        // (GET /projects füllt myRole NICHT — nur der Detail-Endpoint tut das)
        mockMvc.perform(get("/api/v1/projects").header("Authorization", "Bearer $memberToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.key=='INHL')].key").value("INHL"))
        // Detail-Endpoint zeigt die geerbte effektive Rolle
        mockMvc.perform(get("/api/v1/projects/INHL").header("Authorization", "Bearer $memberToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.myRole").value("VIEWER"))
    }

    @Test
    fun `inherited VIEWER can read but not write`() {
        val adminToken = register("v-admin@test.com"); makeSystemAdmin("v-admin@test.com")
        val ownerToken = register("v-owner@test.com")
        val viewerToken = register("v-viewer@test.com")
        val viewerId = myId(viewerToken)
        val orgId = createOrg(adminToken, "v-org")
        createProject(ownerToken, "VRO")
        assignProjectToOrg(adminToken, "VRO", orgId)
        addOrgMember(adminToken, orgId, viewerId, "MEMBER") // → geerbter VIEWER

        // lesen ok
        mockMvc.perform(get("/api/v1/projects/VRO/labels").header("Authorization", "Bearer $viewerToken"))
            .andExpect(status().isOk)
        // schreiben (Label anlegen) → 403
        mockMvc.perform(
            post("/api/v1/projects/VRO/labels").header("Authorization", "Bearer $viewerToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"name":"bug","color":"#ff0000"}""")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `inherited org ADMIN can perform project admin actions`() {
        val adminToken = register("a-admin@test.com"); makeSystemAdmin("a-admin@test.com")
        val ownerToken = register("a-owner@test.com")
        val orgAdminToken = register("a-orgadmin@test.com")
        val orgAdminId = myId(orgAdminToken)
        val thirdToken = register("a-third@test.com")
        val thirdId = myId(thirdToken)
        val orgId = createOrg(adminToken, "a-org")
        createProject(ownerToken, "ARO")
        assignProjectToOrg(adminToken, "ARO", orgId)
        addOrgMember(adminToken, orgId, orgAdminId, "ADMIN") // → geerbter Projekt-ADMIN

        // geerbter ADMIN darf schreiben (Label) …
        mockMvc.perform(
            post("/api/v1/projects/ARO/labels").header("Authorization", "Bearer $orgAdminToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"name":"feat","color":"#00ff00"}""")
        ).andExpect(status().isCreated)
        // … und Admin-Aktionen (Projekt-Member hinzufügen)
        mockMvc.perform(
            post("/api/v1/projects/ARO/members").header("Authorization", "Bearer $orgAdminToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"userId":"$thirdId","role":"VIEWER"}""")
        ).andExpect(status().isCreated)
    }
}
