package com.taskowolf.organizations.application

import com.taskowolf.auth.domain.User
import com.taskowolf.organizations.domain.OrgRole

data class OrgMemberView(val user: User, val role: OrgRole)
