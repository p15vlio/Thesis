package net.kustax.opendelivery.backend.database.table.platform

import org.jetbrains.exposed.v1.core.Table

object RefreshTokensTable : Table("refresh_tokens") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36)
    val role = varchar("role", 32)
    val tokenHash = varchar("token_hash", 255).index()
    val tenantSchemaName = varchar("schema_name", 128).nullable()
    val expiresAt = long("expires_at")
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}
