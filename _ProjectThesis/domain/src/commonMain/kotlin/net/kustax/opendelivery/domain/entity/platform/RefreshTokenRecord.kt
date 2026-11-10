package net.kustax.opendelivery.domain.entity.platform

data class RefreshTokenRecord(
    val id: String,
    val userId: String,
    val role: String,
    val tokenHash: String,
    val schemaName: String?,
    val expiresAt: Long,
    val createdAt: Long
)
