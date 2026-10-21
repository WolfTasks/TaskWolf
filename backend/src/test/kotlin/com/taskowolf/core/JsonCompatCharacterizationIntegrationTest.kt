package com.taskowolf.core

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.taskowolf.IntegrationTestBase
import com.taskowolf.workflows.domain.RequiredFieldGuard
import com.taskowolf.workflows.domain.RoleRestrictionGuard
import com.taskowolf.workflows.domain.TransitionGuard
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * M3 Phase 2 characterization: written against Jackson 2, must stay green unchanged on Jackson 3.
 * Covers JSON that already sits in production databases and request tolerance the frontend relies on.
 */
class JsonCompatCharacterizationIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    // Exactly what WorkflowService persisted into workflow_transitions.guards under Jackson 2.
    private val storedGuards =
        """[{"type":"REQUIRED_FIELD","field":"assigneeId"},{"type":"ROLE_RESTRICTION","roles":["ADMIN","MEMBER"]}]"""

    private val guardListType = object : TypeReference<List<TransitionGuard>>() {}

    @Test
    fun `stored transition guards still deserialize polymorphically`() {
        val guards = objectMapper.readValue(storedGuards, guardListType)
        assertEquals(
            listOf(RequiredFieldGuard("assigneeId"), RoleRestrictionGuard(listOf("ADMIN", "MEMBER"))),
            guards
        )
    }

    @Test
    fun `transition guards serialize exactly as WorkflowService writes them today`() {
        val guards: List<TransitionGuard> =
            listOf(RequiredFieldGuard("assigneeId"), RoleRestrictionGuard(listOf("ADMIN", "MEMBER")))
        // Characterizes the current write path (WorkflowService.updateGuards): the List's element type is
        // erased at runtime, so the "type" discriminator is NOT written. Pre-existing bug, tracked separately;
        // the Jackson 3 switch must not change this output.
        assertEquals(
            """[{"field":"assigneeId"},{"roles":["ADMIN","MEMBER"]}]""",
            objectMapper.writeValueAsString(guards)
        )
    }

    private fun token(email: String): String =
        objectMapper.readTree(
            mockMvc.perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"email":"$email","displayName":"Compat","password":"password123"}""")
            ).andReturn().response.contentAsString
        ).get("accessToken").asText()

    @Test
    fun `unknown request properties are ignored`() {
        val t = token("jsoncompat-unknown@test.com")
        mockMvc.perform(
            post("/api/v1/projects")
                .header("Authorization", "Bearer $t")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"key":"JCU","name":"Compat","somethingTheFrontendAdds":true}""")
        ).andExpect(status().isCreated)
    }

    @Test
    fun `validation error keeps code, message and details shape`() {
        val t = token("jsoncompat-error@test.com")
        mockMvc.perform(
            post("/api/v1/projects")
                .header("Authorization", "Bearer $t")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"key":"JCE","name":"Compat"}""")
        ).andExpect(status().isCreated)

        val body = objectMapper.readTree(
            mockMvc.perform(
                post("/api/v1/projects/JCE/issues")
                    .header("Authorization", "Bearer $t")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"title":""}""")
            ).andExpect(status().isBadRequest).andReturn().response.contentAsString
        )
        assertEquals(setOf("code", "message", "details"), body.fieldNames().asSequence().toSet())
        assertTrue(body.get("code").isTextual, "code must be a string")
        assertTrue(body.get("message").isTextual, "message must be a string")
        assertTrue(body.get("details").isObject, "details must be an object")
    }
}
