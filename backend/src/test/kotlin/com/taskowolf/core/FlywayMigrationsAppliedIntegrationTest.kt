package com.taskowolf.core

import com.taskowolf.IntegrationTestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.jdbc.core.JdbcTemplate

/**
 * Guard for the Spring Boot 4 starter split: flyway-core alone no longer triggers
 * auto-configuration, so a missing spring-boot-starter-flyway would silently skip all
 * migrations. Every versioned script on the classpath must be recorded as applied.
 */
class FlywayMigrationsAppliedIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `all versioned migrations are applied on startup`() {
        val scripts = PathMatchingResourcePatternResolver()
            .getResources("classpath:db/migration/V*__*.sql")
            .size
        val applied = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true AND version IS NOT NULL",
            Int::class.java
        )
        assertEquals(scripts, applied)
    }
}
