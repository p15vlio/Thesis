package net.kustax.opendelivery.backend.security

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import net.kustax.opendelivery.domain.enum.UserRole
import java.util.Date

object JwtConfig {

    private val secret: String by lazy {
        System.getenv("JWT_SECRET")?.takeIf { it.isNotBlank() }
            ?: error("JWT_SECRET environment variable must be set to a non-blank value; refusing to start")
    }

    // Called once at startup, so a missing or blank secret stops the server before it serves anything
    fun requireConfigured() {
        secret
    }

    const val ISSUER = "opendelivery"
    const val AUDIENCE = "opendelivery-api"
    const val ACCESS_TOKEN_TTL_MS = 15 * 60 * 1000L

    // Kiosk devices are unattended hardware, not interactive user sessions — there is no login/refresh
    // UX for them to re-authenticate every 15 minutes, so their token is long-lived (1 year) rather than
    // session-scoped. Deactivating the device record revokes it at once: withTenant checks the device on every request.
    const val DEVICE_TOKEN_TTL_MS = 365L * 24 * 60 * 60 * 1000

    const val CLAIM_ROLE = "role"
    const val CLAIM_SCHEMA = "schemaName"

    fun generateAccessToken(userId: String, role: UserRole, schemaName: String?, ttlMs: Long = ACCESS_TOKEN_TTL_MS): String =
        JWT.create()
            .withIssuer(ISSUER)
            .withAudience(AUDIENCE)
            .withSubject(userId)
            .withClaim(CLAIM_ROLE, role.name)
            .withClaim(CLAIM_SCHEMA, schemaName)
            .withExpiresAt(Date(System.currentTimeMillis() + ttlMs))
            .sign(Algorithm.HMAC256(secret))

    fun verifier(): JWTVerifier =
        JWT.require(Algorithm.HMAC256(secret))
            .withIssuer(ISSUER)
            .withAudience(AUDIENCE)
            .build()
}
