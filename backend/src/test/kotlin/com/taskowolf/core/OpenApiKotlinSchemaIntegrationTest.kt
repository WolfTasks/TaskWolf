package com.taskowolf.core

import com.taskowolf.IntegrationTestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper

/**
 * M3 guard: springdoc (still Jackson 2 via swagger-core) only derives `required` from Kotlin
 * non-null constructor parameters when the Jackson 2 Kotlin module is on the classpath.
 * Without it the dev OpenAPI document silently loses the required fields of most schemas.
 */
@TestPropertySource(properties = ["springdoc.api-docs.enabled=true"])
class OpenApiKotlinSchemaIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    fun `OpenAPI schema marks non-null Kotlin properties as required`() {
        val doc = objectMapper.readTree(
            mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk).andReturn().response.contentAsString
        )
        // AccessTokenResponse carries no validation annotations: `required` comes from Kotlin nullability only.
        val required = doc.path("components").path("schemas").path("AccessTokenResponse").path("required")
            .values().map { it.asString() }.toSet()
        assertEquals(setOf("id", "name", "tokenPrefix", "scope"), required)
    }
}
