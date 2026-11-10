package net.kustax.opendelivery.data.response

import kotlinx.serialization.Serializable

@Serializable
data class AuditLogResponse(
    val id: String,
    val actorId: String,
    val actorRole: String,
    val action: String,
    val targetType: String,
    val targetId: String? = null,
    val schemaName: String? = null,
    val details: String? = null,
    val createdAt: Long
)
