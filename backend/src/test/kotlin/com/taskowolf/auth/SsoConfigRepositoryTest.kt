package com.taskowolf.auth

import com.taskowolf.auth.domain.SsoConfig
import com.taskowolf.auth.infrastructure.SsoConfigRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.TestPropertySource

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = [
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false;INIT=CREATE TYPE IF NOT EXISTS JSONB AS TEXT",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false"
])
class SsoConfigRepositoryTest {
    @Autowired lateinit var repo: SsoConfigRepository

    @Test
    fun `findAllByEnabledTrue returns only enabled configs`() {
        repo.save(SsoConfig("Okta", "https://okta.example.com", "cid", "enc", enabled = true))
        repo.save(SsoConfig("Disabled", "https://disabled.example.com", "cid2", "enc2", enabled = false))
        assertEquals(1, repo.findAllByEnabledTrue().size)
    }
}
