package com.taskowolf.core

import tools.jackson.databind.ObjectMapper
import com.taskowolf.IntegrationTestBase
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.messaging.converter.CompositeMessageConverter
import org.springframework.messaging.converter.JacksonJsonMessageConverter
import org.springframework.messaging.converter.MappingJackson2MessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.MapperFeature
import tools.jackson.databind.cfg.DateTimeFeature

/**
 * M3 guard: HTTP and STOMP JSON must be written by the Jackson 3 converters
 * and the wire format must not drift.
 */
class JsonWireFormatGuardIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var handlerAdapter: RequestMappingHandlerAdapter

    @Test
    fun `first JSON converter is the Jackson 3 converter`() {
        val firstJson = handlerAdapter.messageConverters
            .first { it.canWrite(Map::class.java, MediaType.APPLICATION_JSON) }
        assertTrue(
            firstJson is JacksonJsonHttpMessageConverter,
            "expected Jackson 3 converter first, got ${firstJson::class.qualifiedName}"
        )
    }

    @Test
    fun `issue response keeps ISO instants and explicit nulls`() {
        val token = objectMapper.readTree(
            mockMvc.perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"email":"jsonguard@test.com","displayName":"Json Guard","password":"password123"}""")
            ).andReturn().response.contentAsString
        ).get("accessToken").asString()

        mockMvc.perform(
            post("/api/v1/projects")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"key":"JSG","name":"Json Guard"}""")
        ).andExpect(status().isCreated)

        val issue = objectMapper.readTree(
            mockMvc.perform(
                post("/api/v1/projects/JSG/issues")
                    .header("Authorization", "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"title":"Wire format"}""")
            ).andExpect(status().isCreated).andReturn().response.contentAsString
        )

        val isoInstant = Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?Z$""")
        assertTrue(issue.get("createdAt").isString, "createdAt must be a string, was ${issue.get("createdAt")}")
        assertTrue(isoInstant.matches(issue.get("createdAt").asString()), "createdAt not ISO-8601: ${issue.get("createdAt")}")
        assertTrue(issue.has("description") && issue.get("description").isNull, "description must be present as explicit null")
        assertTrue(issue.has("dueDate") && issue.get("dueDate").isNull, "dueDate must be present as explicit null")
        assertTrue(issue.get("labels").isArray && issue.get("labels").isEmpty, "labels must be an empty array")
        assertTrue(issue.get("type").isString && issue.get("type").asString() == "TASK", "enum must serialize by name")
    }

    @Autowired
    @Qualifier("brokerMessageConverter")
    private lateinit var brokerMessageConverter: CompositeMessageConverter

    @Test
    fun `STOMP payloads use the Jackson 3 converter`() {
        val converters = brokerMessageConverter.converters
        assertTrue(converters.any { it is JacksonJsonMessageConverter }, "got $converters")
        assertFalse(converters.any { it is MappingJackson2MessageConverter }, "got $converters")
    }

    @Test
    fun `Jackson 3 runs with the Jackson 2 defaults`() {
        assertFalse(objectMapper.isEnabled(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY), "property order must stay declaration order")
        assertFalse(objectMapper.isEnabled(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES), "null for primitives must stay lenient")
        assertFalse(objectMapper.isEnabled(DeserializationFeature.FAIL_ON_TRAILING_TOKENS), "trailing tokens must stay lenient")
        assertFalse(objectMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES), "unknown properties must be ignored")
        assertFalse(objectMapper.isEnabled(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS), "dates must be ISO strings")
    }
}
