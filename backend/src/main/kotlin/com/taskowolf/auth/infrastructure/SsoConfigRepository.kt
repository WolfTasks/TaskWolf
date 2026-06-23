package com.taskowolf.auth.infrastructure

import com.taskowolf.auth.domain.SsoConfig
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SsoConfigRepository : JpaRepository<SsoConfig, UUID> {
    fun findAllByEnabledTrue(): List<SsoConfig>
}
