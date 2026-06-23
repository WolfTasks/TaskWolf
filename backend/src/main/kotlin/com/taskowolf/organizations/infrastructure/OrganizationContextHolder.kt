package com.taskowolf.organizations.infrastructure

import java.util.UUID

object OrganizationContextHolder {
    private val holder = ThreadLocal<UUID?>()
    fun get(): UUID? = holder.get()
    fun set(orgId: UUID?) = holder.set(orgId)
    fun clear() = holder.remove()
}
