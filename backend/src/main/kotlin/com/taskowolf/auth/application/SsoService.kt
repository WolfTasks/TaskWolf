package com.taskowolf.auth.application

import com.taskowolf.auth.domain.SsoConfig
import com.taskowolf.auth.infrastructure.SsoConfigRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Service
class SsoService(
    private val repo: SsoConfigRepository,
    @Value("\${taskowolf.jwt.secret}") private val jwtSecret: String
) {
    private val aesKey by lazy {
        val hash = MessageDigest.getInstance("SHA-256").digest(jwtSecret.toByteArray())
        SecretKeySpec(hash, "AES")
    }

    fun encryptSecret(plaintext: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plaintext.toByteArray())
        return Base64.getEncoder().encodeToString(iv + encrypted)
    }

    fun decryptSecret(encoded: String): String {
        val bytes = Base64.getDecoder().decode(encoded)
        val iv = bytes.sliceArray(0..11)
        val data = bytes.sliceArray(12 until bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
        return String(cipher.doFinal(data))
    }

    @Transactional
    fun createConfig(name: String, issuerUrl: String, clientId: String, clientSecret: String): SsoConfig =
        repo.save(SsoConfig(name, issuerUrl, clientId, encryptSecret(clientSecret)))

    @Transactional
    fun updateConfig(id: UUID, name: String, issuerUrl: String, clientId: String, clientSecret: String?, enabled: Boolean, autoProvision: Boolean): SsoConfig {
        val config = repo.findById(id).orElseThrow()
        if (clientSecret != null) config.clientSecretEnc = encryptSecret(clientSecret)
        return repo.save(SsoConfig(name, issuerUrl, clientId, config.clientSecretEnc, enabled, autoProvision))
    }

    @Transactional(readOnly = true)
    fun listEnabled() = repo.findAllByEnabledTrue()

    @Transactional(readOnly = true)
    fun listAll() = repo.findAll()

    @Transactional
    fun deleteConfig(id: UUID) = repo.deleteById(id)
}
