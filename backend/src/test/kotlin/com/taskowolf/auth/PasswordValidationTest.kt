package com.taskowolf.auth

import com.taskowolf.auth.api.dto.ChangePasswordRequest
import com.taskowolf.auth.api.dto.RegisterRequest
import jakarta.validation.Validation
import jakarta.validation.Validator
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PasswordValidationTest {

    private val validator: Validator =
        Validation.buildDefaultValidatorFactory().validator

    @Test
    fun `changePassword rejects blank (whitespace-only) newPassword`() {
        val req = ChangePasswordRequest(currentPassword = "oldpassword", newPassword = "        ")
        val violations = validator.validate(req)
        assertTrue(violations.any { it.propertyPath.toString() == "newPassword" })
    }

    @Test
    fun `changePassword accepts a valid newPassword`() {
        val req = ChangePasswordRequest(currentPassword = "oldpassword", newPassword = "password123")
        val violations = validator.validate(req)
        assertFalse(violations.any { it.propertyPath.toString() == "newPassword" })
    }

    @Test
    fun `register rejects blank (whitespace-only) password`() {
        val req = RegisterRequest(email = "a@b.com", displayName = "A", password = "        ")
        val violations = validator.validate(req)
        assertTrue(violations.any { it.propertyPath.toString() == "password" })
    }

    @Test
    fun `register accepts a valid password`() {
        val req = RegisterRequest(email = "a@b.com", displayName = "A", password = "password123")
        val violations = validator.validate(req)
        assertFalse(violations.any { it.propertyPath.toString() == "password" })
    }
}
