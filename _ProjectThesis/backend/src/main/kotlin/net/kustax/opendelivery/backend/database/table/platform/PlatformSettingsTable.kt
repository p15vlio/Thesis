package net.kustax.opendelivery.backend.database.table.platform

import org.jetbrains.exposed.v1.core.Table

// Platform-wide configuration managed from the admin Settings page (SMTP, external API keys).
// Secret values are stored encrypted by SecretCipher — never in plain text.
object PlatformSettingsTable : Table("platform_settings") {
    val key = varchar("key", 64)
    val value = text("value")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(key)
}
