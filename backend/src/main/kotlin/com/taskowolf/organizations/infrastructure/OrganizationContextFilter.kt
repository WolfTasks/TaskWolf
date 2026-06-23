package com.taskowolf.organizations.infrastructure

import com.taskowolf.auth.application.JwtService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
@Order(5)
class OrganizationContextFilter(private val jwtService: JwtService) : OncePerRequestFilter() {
    override fun doFilterInternal(req: HttpServletRequest, res: HttpServletResponse, chain: FilterChain) {
        try {
            val token = req.getHeader("Authorization")?.removePrefix("Bearer ")
            if (token != null) {
                val orgId = jwtService.extractOrgId(token)
                OrganizationContextHolder.set(orgId)
            }
            chain.doFilter(req, res)
        } finally {
            OrganizationContextHolder.clear()
        }
    }
}
