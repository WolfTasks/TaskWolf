package com.taskowolf.organizations

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

class OrganizationMemberIntegrationTest : IntegrationTestBase() {

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
    fun createOrg(adminToken: String, slug: String): String {
        val res = mockMvc.perform(
            post("/api/v1/organizations").header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"name":"$slug","slug":"$slug"}""")
        ).andExpect(status().isCreated).andReturn()
        return objectMapper.readTree(res.response.contentAsString).get("id").asText()
    }
    fun addMember(actorToken: String, orgId: String, userId: String, role: String) =
        mockMvc.perform(
            post("/api/v1/organizations/$orgId/members").header("Authorization", "Bearer $actorToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"userId":"$userId","role":"$role"}""")
        )

    @Test
    fun `non-system org ADMIN can add members`() {
        val sysToken = register("om-sys@test.com"); makeSystemAdmin("om-sys@test.com")
        val orgAdminToken = register("om-orgadmin@test.com")
        val orgAdminId = myId(orgAdminToken)
        val targetToken = register("om-target@test.com")
        val targetId = myId(targetToken)
        val orgId = createOrg(sysToken, "om-org")
        addMember(sysToken, orgId, orgAdminId, "ADMIN").andExpect(status().isCreated)

        // Org-ADMIN (kein System-Admin) darf jetzt selbst Member hinzufügen
        addMember(orgAdminToken, orgId, targetId, "MEMBER").andExpect(status().isCreated)
    }

    @Test
    fun `plain org MEMBER cannot add members`() {
        val sysToken = register("om-sys2@test.com"); makeSystemAdmin("om-sys2@test.com")
        val memberToken = register("om-plain@test.com")
        val memberId = myId(memberToken)
        val targetToken = register("om-target2@test.com")
        val targetId = myId(targetToken)
        val orgId = createOrg(sysToken, "om-org2")
        addMember(sysToken, orgId, memberId, "MEMBER").andExpect(status().isCreated)

        addMember(memberToken, orgId, targetId, "MEMBER").andExpect(status().isForbidden)
    }
}
