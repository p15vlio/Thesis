package net.kustax.opendelivery.backend.database.table.platform

import org.jetbrains.exposed.v1.core.Table

object PlatformAdminsTable : Table("platform_admins") {
    val id = varchar("id", 36)
    val email = varchar("email", 255)
    val passwordHash = varchar("password_hash", 255)
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}
