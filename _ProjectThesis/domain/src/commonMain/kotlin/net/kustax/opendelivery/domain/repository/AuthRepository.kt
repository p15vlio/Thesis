package net.kustax.opendelivery.domain.repository

import net.kustax.opendelivery.domain.entity.platform.AuthRecord
import net.kustax.opendelivery.domain.entity.platform.RefreshTokenRecord
import net.kustax.opendelivery.domain.enum.UserRole

interface AuthRepository {
    // Emails are matched case-insensitively
    suspend fun findPlatformAdminByEmail(email: String): AuthRecord?
    suspend fun findOwnerByEmail(email: String): AuthRecord?
    suspend fun isOwnerActive(ownerId: String): Boolean
    // Same check for a device caller, whose token carries only the tenant schema of its owner
    suspend fun isOwnerActiveBySchema(schemaName: String): Boolean
    suspend fun findPasswordHash(userId: String, role: UserRole): String?
    suspend fun updatePasswordHash(userId: String, role: UserRole, passwordHash: String)
    suspend fun saveRefreshToken(record: RefreshTokenRecord)
    suspend fun findRefreshToken(tokenHash: String): RefreshTokenRecord?
    suspend fun deleteRefreshToken(tokenHash: String)
    suspend fun deleteRefreshTokensForUser(userId: String)
}
