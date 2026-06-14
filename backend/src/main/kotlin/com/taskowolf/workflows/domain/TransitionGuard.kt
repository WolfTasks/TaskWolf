package com.taskowolf.workflows.domain

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = RequiredFieldGuard::class, name = "REQUIRED_FIELD"),
    JsonSubTypes.Type(value = RoleRestrictionGuard::class, name = "ROLE_RESTRICTION")
)
sealed class TransitionGuard

data class RequiredFieldGuard(val field: String) : TransitionGuard()
data class RoleRestrictionGuard(val roles: List<String>) : TransitionGuard()
