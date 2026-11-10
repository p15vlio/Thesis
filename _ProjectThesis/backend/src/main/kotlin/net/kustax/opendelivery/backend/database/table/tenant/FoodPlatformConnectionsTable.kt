package net.kustax.opendelivery.backend.database.table.tenant

import org.jetbrains.exposed.v1.core.Table

// One row per platform the owner has connected; apiKey is SecretCipher ciphertext, never plaintext.
object FoodPlatformConnectionsTable : Table("food_platform_connections") {
    val platform = varchar("platform", 32)
    val apiKey = varchar("api_key", 512)
    val connectedAt = long("connected_at")

    override val primaryKey = PrimaryKey(platform)
}
