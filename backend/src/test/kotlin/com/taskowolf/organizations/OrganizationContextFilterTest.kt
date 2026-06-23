package com.taskowolf.organizations

import com.taskowolf.auth.application.JwtService
import com.taskowolf.organizations.infrastructure.OrganizationContextFilter
import com.taskowolf.organizations.infrastructure.OrganizationContextHolder
import io.mockk.*
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.util.UUID

class OrganizationContextFilterTest {
    private val jwtService = mockk<JwtService>()
    private val filter = OrganizationContextFilter(jwtService)

    @Test
    fun `sets orgId from JWT in context`() {
        val orgId = UUID.randomUUID()
        every { jwtService.extractOrgId("token") } returns orgId
        val req = MockHttpServletRequest().apply { addHeader("Authorization", "Bearer token") }
        var capturedOrgId: UUID? = null
        filter.doFilter(req, MockHttpServletResponse()) { _, _ -> capturedOrgId = OrganizationContextHolder.get() }
        assertEquals(orgId, capturedOrgId)
    }
}
