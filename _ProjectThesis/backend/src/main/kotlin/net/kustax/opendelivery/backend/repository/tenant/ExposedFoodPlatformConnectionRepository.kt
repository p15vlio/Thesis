package net.kustax.opendelivery.backend.repository.tenant

import net.kustax.opendelivery.backend.database.table.tenant.FoodPlatformConnectionsTable
import net.kustax.opendelivery.backend.repository.tenantQuery
import net.kustax.opendelivery.domain.entity.tenant.FoodPlatformConnection
import net.kustax.opendelivery.domain.enum.ExternalPlatform
import net.kustax.opendelivery.domain.repository.FoodPlatformConnectionRepository
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*

class ExposedFoodPlatformConnectionRepository : FoodPlatformConnectionRepository {

    override suspend fun findAll(): List<FoodPlatformConnection> = tenantQuery {
        FoodPlatformConnectionsTable.selectAll().map { it.toConnection() }
    }

    override suspend fun findByPlatform(platform: ExternalPlatform): FoodPlatformConnection? = tenantQuery {
        FoodPlatformConnectionsTable.selectAll()
            .where { FoodPlatformConnectionsTable.platform eq platform.name }
            .singleOrNull()
            ?.toConnection()
    }

    // upsert so reconnecting with a new key replaces the old one atomically
    override suspend fun save(platform: ExternalPlatform, encryptedApiKey: String, connectedAt: Long): Unit = tenantQuery {
        FoodPlatformConnectionsTable.upsert {
            it[FoodPlatformConnectionsTable.platform] = platform.name
            it[apiKey] = encryptedApiKey
            it[FoodPlatformConnectionsTable.connectedAt] = connectedAt
        }
    }

    override suspend fun delete(platform: ExternalPlatform): Unit = tenantQuery {
        FoodPlatformConnectionsTable.deleteWhere { FoodPlatformConnectionsTable.platform eq platform.name }
    }
}

private fun ResultRow.toConnection() = FoodPlatformConnection(
    platform = enumValueOf<ExternalPlatform>(this[FoodPlatformConnectionsTable.platform]),
    encryptedApiKey = this[FoodPlatformConnectionsTable.apiKey],
    connectedAt = this[FoodPlatformConnectionsTable.connectedAt]
)
