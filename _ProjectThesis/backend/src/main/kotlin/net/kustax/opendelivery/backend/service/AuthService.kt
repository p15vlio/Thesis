package net.kustax.opendelivery.backend.service

import net.kustax.opendelivery.backend.security.JwtConfig
import net.kustax.opendelivery.backend.security.PasswordHasher
import net.kustax.opendelivery.data.request.ChangePasswordRequest
import net.kustax.opendelivery.data.request.LoginRequest
import net.kustax.opendelivery.data.request.RefreshTokenRequest
import net.kustax.opendelivery.data.response.TokenResponse
import net.kustax.opendelivery.domain.entity.platform.RefreshTokenRecord
import net.kustax.opendelivery.domain.enum.UserRole
import net.kustax.opendelivery.domain.repository.AuthRepository
import org.slf4j.LoggerFactory
import java.util.UUID

class AuthenticationException(message: String) : Exception(message)

class AuthService(
    private val authRepository: AuthRepository,
    private val auditLogService: AuditLogService
) {
    private val log = LoggerFactory.getLogger(AuthService::class.java)

    suspend fun login(request: LoginRequest): TokenResponse {
        log.debug("Login attempt: role='{}'", request.role)
        val role = parseRole(request.role)
        val authRecord = findUser(role, request.email.trim().lowercase(), request.schemaName)
        log.debug("User lookup result: {}", if (authRecord != null) "found" else "not found")
        if (authRecord == null) throw AuthenticationException("Invalid credentials")

        if (!PasswordHasher.verify(request.password, authRecord.passwordHash)) {
            throw AuthenticationException("Invalid credentials")
        }
        // Checked after the password so the deactivated state is only revealed to someone who knows it
        if (!authRecord.isActive) throw AuthenticationException(ACCOUNT_DEACTIVATED)

        return issueTokenPair(authRecord.id, authRecord.role, authRecord.schemaName)
    }

    suspend fun refresh(request: RefreshTokenRequest): TokenResponse {
        val tokenHash = PasswordHasher.hashForLookup(request.refreshToken)
        val record = authRepository.findRefreshToken(tokenHash)
            ?: throw AuthenticationException("Invalid or expired refresh token")

        if (record.expiresAt < System.currentTimeMillis()) {
            authRepository.deleteRefreshToken(tokenHash)
            throw AuthenticationException("Invalid or expired refresh token")
        }

        // Rotate: delete old token before issuing a new pair
        authRepository.deleteRefreshToken(tokenHash)

        val role = UserRole.valueOf(record.role)
        if (role == UserRole.OWNER && !authRepository.isOwnerActive(record.userId)) {
            throw AuthenticationException(ACCOUNT_DEACTIVATED)
        }
        return issueTokenPair(record.userId, role, record.schemaName)
    }

    // A signed-in admin or owner replaces their own password. Every other session of the user is signed out
    // (their refresh tokens are deleted) and the caller gets a fresh token pair so this session continues.
    suspend fun changePassword(userId: String, role: UserRole, schemaName: String?, request: ChangePasswordRequest): TokenResponse {
        if (role == UserRole.DEVICE) throw ForbiddenException("Devices have no password")
        val currentHash = authRepository.findPasswordHash(userId, role)
            ?: throw AuthenticationException("Account not found")
        if (!PasswordHasher.verify(request.currentPassword, currentHash)) {
            // 400, not 401: the web panel treats a 401 as an expired session and would sign the user out
            throw IllegalArgumentException("Current password is incorrect")
        }
        require(request.newPassword.length >= MIN_PASSWORD_LENGTH) {
            "The new password must be at least $MIN_PASSWORD_LENGTH characters"
        }
        require(request.newPassword != request.currentPassword) { "The new password must differ from the current one" }

        authRepository.updatePasswordHash(userId, role, PasswordHasher.hash(request.newPassword))
        authRepository.deleteRefreshTokensForUser(userId)
        auditLogService.log(userId, role.name, "CHANGE_PASSWORD", role.name, userId)
        return issueTokenPair(userId, role, schemaName)
    }

    private fun parseRole(roleString: String): UserRole =
        runCatching { UserRole.valueOf(roleString) }
            .getOrElse { throw AuthenticationException("Invalid credentials") }

    private suspend fun findUser(role: UserRole, email: String, schemaName: String?) =
        when (role) {
            UserRole.PLATFORM_ADMIN -> authRepository.findPlatformAdminByEmail(email)
            UserRole.OWNER -> authRepository.findOwnerByEmail(email)
            UserRole.DEVICE -> throw AuthenticationException("Invalid credentials")
        }

    private suspend fun issueTokenPair(userId: String, role: UserRole, schemaName: String?): TokenResponse {
        val accessToken = JwtConfig.generateAccessToken(userId, role, schemaName)
        val expiresAt = System.currentTimeMillis() + JwtConfig.ACCESS_TOKEN_TTL_MS

        val rawRefreshToken = UUID.randomUUID().toString()
        val refreshTokenHash = PasswordHasher.hashForLookup(rawRefreshToken)
        val refreshExpiresAt = System.currentTimeMillis() + REFRESH_TOKEN_TTL_MS

        authRepository.saveRefreshToken(
            RefreshTokenRecord(
                id = UUID.randomUUID().toString(),
                userId = userId,
                role = role.name,
                tokenHash = refreshTokenHash,
                schemaName = schemaName,
                expiresAt = refreshExpiresAt,
                createdAt = System.currentTimeMillis()
            )
        )

        return TokenResponse(
            accessToken = accessToken,
            refreshToken = rawRefreshToken,
            role = role.name,
            expiresAt = expiresAt
        )
    }

    companion object {
        private const val REFRESH_TOKEN_TTL_MS = 30L * 24 * 60 * 60 * 1000
        private const val ACCOUNT_DEACTIVATED = "This account has been deactivated. Contact the platform administrator."
        private const val MIN_PASSWORD_LENGTH = 8
    }
}
