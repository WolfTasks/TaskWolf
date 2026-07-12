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

    @Test
    fun `member list returns user displayName and duplicate add is 409`() {
        val sysToken = register("en-sys@test.com"); makeSystemAdmin("en-sys@test.com")
        val targetToken = register("en-target@test.com")
        val targetId = myId(targetToken)
        val orgId = createOrg(sysToken, "en-org")
        addMember(sysToken, orgId, targetId, "MEMBER").andExpect(status().isCreated)

        // Liste enthält den User mit displayName (nicht nur eine rohe UUID)
        mockMvc.perform(get("/api/v1/organizations/$orgId/members").header("Authorization", "Bearer $sysToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.user.id=='$targetId')].role").value("MEMBER"))
            .andExpect(jsonPath("$[?(@.user.id=='$targetId')].user.displayName").value("U"))

        // Doppeltes Hinzufügen → 409
        addMember(sysToken, orgId, targetId, "MEMBER").andExpect(status().isConflict)
    }

    @Test
    fun `org admin changes a member role but cannot change own role`() {
        val sysToken = register("cr-sys@test.com"); makeSystemAdmin("cr-sys@test.com")
        val orgAdminToken = register("cr-orgadmin@test.com"); val orgAdminId = myId(orgAdminToken)
        val targetToken = register("cr-target@test.com"); val targetId = myId(targetToken)
        val orgId = createOrg(sysToken, "cr-org")
        addMember(sysToken, orgId, orgAdminId, "ADMIN").andExpect(status().isCreated)
        addMember(sysToken, orgId, targetId, "MEMBER").andExpect(status().isCreated)

        // Org-ADMIN hebt Ziel auf ADMIN → 200
        mockMvc.perform(
            patch("/api/v1/organizations/$orgId/members/$targetId").header("Authorization", "Bearer $orgAdminToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"role":"ADMIN"}""")
        ).andExpect(status().isOk).andExpect(jsonPath("$.role").value("ADMIN"))

        // eigene Rolle ändern → 403
        mockMvc.perform(
            patch("/api/v1/organizations/$orgId/members/$orgAdminId").header("Authorization", "Bearer $orgAdminToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"role":"MEMBER"}""")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `org admin can remove a normal member`() {
        val sysToken = register("rm-sys@test.com"); makeSystemAdmin("rm-sys@test.com")
        val orgAdminToken = register("rm-admin@test.com"); val orgAdminId = myId(orgAdminToken)
        val targetToken = register("rm-target@test.com"); val targetId = myId(targetToken)
        val orgId = createOrg(sysToken, "rm-org")
        addMember(sysToken, orgId, orgAdminId, "ADMIN").andExpect(status().isCreated)
        addMember(sysToken, orgId, targetId, "MEMBER").andExpect(status().isCreated)

        mockMvc.perform(delete("/api/v1/organizations/$orgId/members/$targetId").header("Authorization", "Bearer $orgAdminToken"))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `removing the sole owner is forbidden`() {
        val sysToken = register("so-sys@test.com"); makeSystemAdmin("so-sys@test.com")
        val sysId = myId(sysToken)
        val ownerToken = register("so-owner@test.com"); val ownerId = myId(ownerToken)
        val orgId = createOrg(sysToken, "so-org")
        // createOrg makes the creator (sysToken) an OWNER too, so add a second owner...
        addMember(sysToken, orgId, ownerId, "OWNER").andExpect(status().isCreated)
        // ...then remove the creator's own membership, leaving ownerId as the sole owner.
        mockMvc.perform(delete("/api/v1/organizations/$orgId/members/$sysId").header("Authorization", "Bearer $sysToken"))
            .andExpect(status().isNoContent)

        mockMvc.perform(delete("/api/v1/organizations/$orgId/members/$ownerId").header("Authorization", "Bearer $sysToken"))
            .andExpect(status().isForbidden)
    }
}
