package com.taskowolf.integrations

import com.taskowolf.integrations.application.HmacSigner
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HmacSignerTest {
    private val signer = HmacSigner()

    @Test
    fun `sign produces sha256= prefixed hex string`() {
        val sig = signer.sign("""{"event":"ping"}""", "my-secret")
        assertTrue(sig.startsWith("sha256="), "signature must start with sha256=")
        assertEquals(71, sig.length, "sha256= (7) + 64 hex chars = 71")
    }

    @Test
    fun `same input produces same signature`() {
        val a = signer.sign("payload", "secret")
        val b = signer.sign("payload", "secret")
        assertEquals(a, b)
    }

    @Test
    fun `different secret produces different signature`() {
        val a = signer.sign("payload", "secret1")
        val b = signer.sign("payload", "secret2")
        assertNotEquals(a, b)
    }

    @Test
    fun `verify returns true for matching signature`() {
        val payload = """{"event":"issue.created"}"""
        val secret = "test-secret"
        val sig = signer.sign(payload, secret)
        assertTrue(signer.verify(payload, secret, sig))
    }

    @Test
    fun `verify returns false for tampered payload`() {
        val secret = "test-secret"
        val sig = signer.sign("original", secret)
        assertFalse(signer.verify("tampered", secret, sig))
    }
}
