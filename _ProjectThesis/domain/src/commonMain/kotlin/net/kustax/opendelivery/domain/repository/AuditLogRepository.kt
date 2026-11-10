package net.kustax.opendelivery.domain.repository

import net.kustax.opendelivery.domain.entity.platform.AuditLog

interface AuditLogRepository {
    suspend fun insert(entry: AuditLog)
    suspend fun findAll(limit: Int, offset: Long): List<AuditLog>
}
