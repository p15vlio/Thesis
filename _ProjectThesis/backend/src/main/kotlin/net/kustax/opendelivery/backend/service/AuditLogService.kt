package net.kustax.opendelivery.backend.service

import java.util.UUID
import net.kustax.opendelivery.domain.entity.platform.AuditLog
import net.kustax.opendelivery.domain.repository.AuditLogRepository

class AuditLogService(private val repo: AuditLogRepository) {

    suspend fun log(
        actorId: String,
        actorRole: String,
        action: String,
        targetType: String,
        targetId: String? = null,
        schemaName: String? = null,
        details: String? = null
    ) {
        repo.insert(
            AuditLog(
                id = UUID.randomUUID().toString(),
                actorId = actorId,
                actorRole = actorRole,
                action = action,
                targetType = targetType,
                targetId = targetId,
                schemaName = schemaName,
                details = details,
                createdAt = System.currentTimeMillis()
            )
        )
    }
}
