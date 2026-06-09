package com.taskowolf.auth.api.dto

import jakarta.validation.constraints.NotBlank

data class RefreshRequest(@field:NotBlank val refreshToken: String)
