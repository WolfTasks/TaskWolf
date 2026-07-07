package com.taskowolf.auth.application

import com.taskowolf.auth.domain.TokenScope
import com.taskowolf.auth.domain.User

data class AuthenticatedToken(val user: User, val scope: TokenScope)
