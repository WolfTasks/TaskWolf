package com.taskowolf.workflows

import com.taskowolf.IntegrationTestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

/**
 * B4: guards saved through the editor must keep their "type" discriminator, otherwise every move over
 * the guarded transition fails with 500. Also covers the V31 repair of guards stored without "type".
 */
class TransitionGuardIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    private fun json(body: String): JsonNode = objectMapper.readTree(body)

    private fun token(email: String): String =
        json(
            mockMvc.perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"email":"$email","displayName":"Guard","password":"password123"}""")
            ).andReturn().response.contentAsString
        ).get("accessToken").asString()

    private fun setup(key: String): Triple<String, String, String> {
        val t = token("${key.lowercase()}@test.com")
        mockMvc.perform(
            post("/api/v1/projects").header("Authorization", "Bearer $t")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"key":"$key","name":"Guards"}""")
        ).andExpect(status().isCreated)
        val wf = json(
            mockMvc.perform(get("/api/v1/projects/$key/workflow").header("Authorization", "Bearer $t"))
                .andReturn().response.contentAsString
        )
        val statusId = { name: String -> wf.get("statuses").first { it.get("name").asString() == name }.get("id").asString() }
        return Triple(t, statusId("To Do"), statusId("In Progress"))
    }

    @Test
    fun `saved guards keep their type and block the move with 400 instead of 500`() {
        val (t, todo, inProgress) = setup("GRD")
        val transitionId = json(
            mockMvc.perform(
                post("/api/v1/projects/GRD/workflow/transitions").header("Authorization", "Bearer $t")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"fromStatusId":"$todo","toStatusId":"$inProgress"}""")
            ).andExpect(status().isCreated).andReturn().response.contentAsString
        ).get("id").asString()

        val saved = json(
            mockMvc.perform(
                put("/api/v1/projects/GRD/workflow/transitions/$transitionId/guards").header("Authorization", "Bearer $t")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"guards":[{"type":"REQUIRED_FIELD","field":"assigneeId"}]}""")
            ).andExpect(status().isOk).andReturn().response.contentAsString
        )
        assertEquals("REQUIRED_FIELD", json(saved.get("guards").asString())[0].get("type").asString())

        val issueId = json(
            mockMvc.perform(
                post("/api/v1/projects/GRD/issues").header("Authorization", "Bearer $t")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"title":"Guarded"}""")
            ).andExpect(status().isCreated).andReturn().response.contentAsString
        ).get("id").asString()

        mockMvc.perform(
            patch("/api/v1/projects/GRD/issues/$issueId").header("Authorization", "Bearer $t")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"statusId":"$inProgress"}""")
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Transition blocked: field 'assigneeId' is required"))
    }

    @Test
    fun `V31 adds the missing type to guards stored without it`() {
        val (t, todo, inProgress) = setup("GRM")
        val transitionId = json(
            mockMvc.perform(
                post("/api/v1/projects/GRM/workflow/transitions").header("Authorization", "Bearer $t")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"fromStatusId":"$todo","toStatusId":"$inProgress"}""")
            ).andExpect(status().isCreated).andReturn().response.contentAsString
        ).get("id").asString()
        // The broken format WorkflowService.updateGuards wrote before the fix.
        jdbcTemplate.update(
            "UPDATE workflow_transitions SET guards = ? WHERE id = ?::uuid",
            """[{"field":"assigneeId"},{"roles":["ADMIN","MEMBER"]}]""", transitionId
        )

        val sql = javaClass.getResource("/db/migration/V31__repair_untyped_transition_guards.sql")!!.readText()
        jdbcTemplate.execute(sql)
        jdbcTemplate.execute(sql) // idempotent

        val repaired = json(
            jdbcTemplate.queryForObject("SELECT guards FROM workflow_transitions WHERE id = ?::uuid", String::class.java, transitionId)!!
        )
        assertEquals("REQUIRED_FIELD", repaired[0].get("type").asString())
        assertEquals("assigneeId", repaired[0].get("field").asString())
        assertEquals("ROLE_RESTRICTION", repaired[1].get("type").asString())
        assertTrue(repaired[1].get("roles").isArray)
    }
}
