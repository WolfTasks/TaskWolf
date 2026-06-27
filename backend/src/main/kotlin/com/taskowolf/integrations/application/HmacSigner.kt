package com.taskowolf.integrations.application

import org.springframework.stereotype.Component
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
class HmacSigner {
    fun sign(payload: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val hex = mac.doFinal(payload.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "sha256=$hex"
    }

    fun verify(payload: String, secret: String, signature: String): Boolean {
        val expected = sign(payload, secret)
        return MessageDigest.isEqual(expected.toByteArray(), signature.toByteArray())
    }
}
