package net.kustax.opendelivery.domain.entity.platform

data class AuditLog(
    val id: String,
    val actorId: String,
    val actorRole: String,
    val action: String,
    val targetType: String,
    val targetId: String?,
    val schemaName: String?,
    val details: String?,
    val createdAt: Long
)
