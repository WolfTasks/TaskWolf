package com.taskowolf.integrations

import com.taskowolf.core.infrastructure.BadRequestException
import com.taskowolf.integrations.application.SsrfValidator
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SsrfValidatorTest {
    private val validator = SsrfValidator()

    @Test
    fun `public URL is accepted`() {
        assertDoesNotThrow { validator.validate("https://hooks.example.com/payload") }
    }

    @Test
    fun `localhost is rejected`() {
        assertThrows(BadRequestException::class.java) {
            validator.validate("http://localhost:8080/hook")
        }
    }

    @Test
    fun `127_0_0_1 is rejected`() {
        assertThrows(BadRequestException::class.java) {
            validator.validate("http://127.0.0.1/hook")
        }
    }

    @Test
    fun `10_x private range is rejected`() {
        assertThrows(BadRequestException::class.java) {
            validator.validate("http://10.0.0.1/hook")
        }
    }

    @Test
    fun `192_168_x private range is rejected`() {
        assertThrows(BadRequestException::class.java) {
            validator.validate("http://192.168.1.100/hook")
        }
    }
}
