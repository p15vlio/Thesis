package net.kustax.opendelivery.backend.database.table.platform

import org.jetbrains.exposed.v1.core.Table

object AuditLogTable : Table("audit_log") {
    val id = varchar("id", 36)
    val actorId = varchar("actor_id", 36)
    val actorRole = varchar("actor_role", 32)
    val action = varchar("action", 64)
    val targetType = varchar("target_type", 64)
    val targetId = varchar("target_id", 36).nullable()
    val tenantSchemaName = varchar("schema_name", 128).nullable()
    val details = text("details").nullable()
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}
