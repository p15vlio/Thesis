package net.kustax.opendelivery.backend.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// AES-256-GCM encryption for secrets stored in platform_settings (SMTP app password, API keys).
// The key is derived from SETTINGS_SECRET so a database dump alone does not reveal the secrets.
// Stored format: base64(iv || ciphertext+tag).
object SecretCipher {

    private const val IV_LENGTH = 12
    private const val TAG_BITS = 128

    // There is deliberately no default: a missing value must never silently become a known key
    private val key: SecretKeySpec by lazy {
        val secret = System.getenv("SETTINGS_SECRET")?.takeIf { it.isNotBlank() }
            ?: error("SETTINGS_SECRET environment variable must be set to a non-blank value; refusing to start")
        SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(secret.toByteArray()), "AES")
    }

    // Called once at startup so a missing secret stops the server before it serves anything,
    // instead of failing on the first encrypt/decrypt
    fun requireConfigured() {
        key
    }

    private val random = SecureRandom()

    fun encrypt(plain: String): String {
        val iv = ByteArray(IV_LENGTH).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return Base64.getEncoder().encodeToString(iv + cipher.doFinal(plain.toByteArray()))
    }

    fun decrypt(encoded: String): String {
        val bytes = Base64.getDecoder().decode(encoded)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, bytes, 0, IV_LENGTH))
        return String(cipher.doFinal(bytes, IV_LENGTH, bytes.size - IV_LENGTH))
    }
}
