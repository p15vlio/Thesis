package net.kustax.opendelivery.domain.entity.platform

import net.kustax.opendelivery.domain.enum.UserRole

// Credentials of a platform admin or owner, as needed to verify a login. schemaName is null for admins.
data class AuthRecord(
    val id: String,
    val email: String,
    val passwordHash: String,
    val role: UserRole,
    val schemaName: String?,
    val isActive: Boolean
)
