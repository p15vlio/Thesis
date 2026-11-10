package net.kustax.opendelivery.backend.security

import at.favre.lib.crypto.bcrypt.BCrypt
import java.security.MessageDigest

object PasswordHasher {

    fun hash(password: String): String =
        BCrypt.withDefaults().hashToString(12, password.toCharArray())

    fun verify(password: String, hash: String): Boolean =
        BCrypt.verifyer().verify(password.toCharArray(), hash).verified

    // SHA-256 hex digest — deterministic, used for refresh token lookup
    fun hashForLookup(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(token.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
