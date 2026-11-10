package net.kustax.opendelivery.backend.repository.platform

import net.kustax.opendelivery.backend.database.DatabaseFactory
import net.kustax.opendelivery.backend.database.table.platform.AuditLogTable
import net.kustax.opendelivery.domain.entity.platform.AuditLog
import net.kustax.opendelivery.domain.repository.AuditLogRepository
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*

class ExposedAuditLogRepository : AuditLogRepository {

    override suspend fun insert(entry: AuditLog): Unit = DatabaseFactory.dbQuery {
        AuditLogTable.insert { row ->
            row[id] = entry.id
            row[actorId] = entry.actorId
            row[actorRole] = entry.actorRole
            row[action] = entry.action
            row[targetType] = entry.targetType
            row[targetId] = entry.targetId
            row[tenantSchemaName] = entry.schemaName
            row[details] = entry.details
            row[createdAt] = entry.createdAt
        }
    }

    override suspend fun findAll(limit: Int, offset: Long): List<AuditLog> = DatabaseFactory.dbQuery {
        AuditLogTable.selectAll()
            .orderBy(AuditLogTable.createdAt, SortOrder.DESC)
            .limit(limit).offset(offset)
            .map { it.toAuditLog() }
    }
}

private fun ResultRow.toAuditLog() = AuditLog(
    id = this[AuditLogTable.id],
    actorId = this[AuditLogTable.actorId],
    actorRole = this[AuditLogTable.actorRole],
    action = this[AuditLogTable.action],
    targetType = this[AuditLogTable.targetType],
    targetId = this[AuditLogTable.targetId],
    schemaName = this[AuditLogTable.tenantSchemaName],
    details = this[AuditLogTable.details],
    createdAt = this[AuditLogTable.createdAt]
)
