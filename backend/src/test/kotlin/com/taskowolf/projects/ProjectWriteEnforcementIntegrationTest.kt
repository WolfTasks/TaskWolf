package com.taskowolf.projects

import com.fasterxml.jackson.databind.ObjectMapper
import com.taskowolf.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.util.UUID

class ProjectWriteEnforcementIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper

    private fun registerAndGetToken(email: String): String {
        val result = mockMvc.perform(
            post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","displayName":"Test","password":"password123"}""")
        ).andReturn()
        return objectMapper.readTree(result.response.contentAsString).get("accessToken").asText()
    }

    private fun myId(token: String): String {
        val result = mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer $token")).andReturn()
        return objectMapper.readTree(result.response.contentAsString).get("id").asText()
    }

    private fun addMember(ownerToken: String, key: String, userId: String, role: String) {
        mockMvc.perform(
            post("/api/v1/projects/$key/members").header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userId":"$userId","role":"$role"}""")
        ).andExpect(status().isCreated)
    }

    @Test
    fun `viewer is blocked from writing but can read, member can write`() {
        val ownerToken = registerAndGetToken("enf-owner@test.com")
        val viewerToken = registerAndGetToken("enf-viewer@test.com")
        val memberToken = registerAndGetToken("enf-member@test.com")
        mockMvc.perform(
            post("/api/v1/projects").header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"key":"ENF","name":"Enf"}""")
        ).andExpect(status().isCreated)
        addMember(ownerToken, "ENF", myId(viewerToken), "VIEWER")
        addMember(ownerToken, "ENF", myId(memberToken), "MEMBER")

        // viewer: create issue → 403
        mockMvc.perform(
            post("/api/v1/projects/ENF/issues").header("Authorization", "Bearer $viewerToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"title":"nope"}""")
        ).andExpect(status().isForbidden)

        // viewer: read issues → 200
        mockMvc.perform(get("/api/v1/projects/ENF/issues").header("Authorization", "Bearer $viewerToken"))
            .andExpect(status().isOk)

        // member: create issue → 201
        mockMvc.perform(
            post("/api/v1/projects/ENF/issues").header("Authorization", "Bearer $memberToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"title":"yes"}""")
        ).andExpect(status().isCreated)

        // viewer: create label → 403 (covers non-issue write path)
        mockMvc.perform(
            post("/api/v1/projects/ENF/labels").header("Authorization", "Bearer $viewerToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"name":"x","color":"#ffffff"}""")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `member demoted to viewer can no longer edit or delete their own comment, but admin still can`() {
        val ownerToken = registerAndGetToken("dem-owner@test.com")
        val memberToken = registerAndGetToken("dem-member@test.com")
        mockMvc.perform(
            post("/api/v1/projects").header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"key":"DEM","name":"Dem"}""")
        ).andExpect(status().isCreated)
        val memberId = myId(memberToken)
        addMember(ownerToken, "DEM", memberId, "MEMBER")

        // member creates an issue → capture its key
        val issueResult = mockMvc.perform(
            post("/api/v1/projects/DEM/issues").header("Authorization", "Bearer $memberToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"title":"has a comment"}""")
        ).andExpect(status().isCreated).andReturn()
        val issueKey = objectMapper.readTree(issueResult.response.contentAsString).get("key").asText()

        // member posts a comment → capture its id
        val commentResult = mockMvc.perform(
            post("/api/v1/projects/DEM/issues/$issueKey/comments").header("Authorization", "Bearer $memberToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"body":"mine"}""")
        ).andExpect(status().isCreated).andReturn()
        val commentId = objectMapper.readTree(commentResult.response.contentAsString).get("id").asText()

        // owner demotes the member to VIEWER
        mockMvc.perform(
            patch("/api/v1/projects/DEM/members/$memberId").header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"role":"VIEWER"}""")
        ).andExpect(status().isOk)

        // demoted viewer: edit their own comment → 403
        mockMvc.perform(
            put("/api/v1/projects/DEM/issues/$issueKey/comments/$commentId").header("Authorization", "Bearer $memberToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"body":"edited"}""")
        ).andExpect(status().isForbidden)

        // demoted viewer: delete their own comment → 403
        mockMvc.perform(
            delete("/api/v1/projects/DEM/issues/$issueKey/comments/$commentId").header("Authorization", "Bearer $memberToken")
        ).andExpect(status().isForbidden)

        // owner (admin/writer): delete the comment → 204 (moderation still works)
        mockMvc.perform(
            delete("/api/v1/projects/DEM/issues/$issueKey/comments/$commentId").header("Authorization", "Bearer $ownerToken")
        ).andExpect(status().isNoContent)
    }

    @Test
    fun `viewer is blocked from moving a card on the board`() {
        val ownerToken = registerAndGetToken("brd-owner@test.com")
        val viewerToken = registerAndGetToken("brd-viewer@test.com")
        mockMvc.perform(
            post("/api/v1/projects").header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"key":"BRD","name":"Brd"}""")
        ).andExpect(status().isCreated)
        addMember(ownerToken, "BRD", myId(viewerToken), "VIEWER")

        // viewer: move a board card → 403 (canWrite denies before the body is resolved, so bogus ids are fine)
        mockMvc.perform(
            patch("/api/v1/projects/BRD/board/move").header("Authorization", "Bearer $viewerToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"issueId":"${UUID.randomUUID()}","newStatusId":"${UUID.randomUUID()}"}""")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `viewer is blocked from uploading an attachment`() {
        val ownerToken = registerAndGetToken("att-owner@test.com")
        val viewerToken = registerAndGetToken("att-viewer@test.com")
        mockMvc.perform(
            post("/api/v1/projects").header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"key":"ATT","name":"Att"}""")
        ).andExpect(status().isCreated)
        addMember(ownerToken, "ATT", myId(viewerToken), "VIEWER")

        // owner creates an issue → capture its key
        val issueResult = mockMvc.perform(
            post("/api/v1/projects/ATT/issues").header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"title":"needs an attachment"}""")
        ).andExpect(status().isCreated).andReturn()
        val issueKey = objectMapper.readTree(issueResult.response.contentAsString).get("key").asText()

        val file = MockMultipartFile("file", "x.txt", "text/plain", "hi".toByteArray())

        // viewer: upload attachment → 403
        mockMvc.perform(
            multipart("/api/v1/projects/ATT/issues/$issueKey/attachments")
                .file(file)
                .header("Authorization", "Bearer $viewerToken")
        ).andExpect(status().isForbidden)
    }
}
