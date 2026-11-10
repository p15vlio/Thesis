package net.kustax.opendelivery.backend.repository.platform

import net.kustax.opendelivery.backend.database.DatabaseFactory
import net.kustax.opendelivery.backend.database.table.platform.PlatformSettingsTable
import net.kustax.opendelivery.domain.repository.PlatformSettingsRepository
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*

class ExposedPlatformSettingsRepository : PlatformSettingsRepository {

    override suspend fun findAll(keys: Collection<String>): Map<String, String> = DatabaseFactory.dbQuery {
        PlatformSettingsTable.selectAll()
            .where { PlatformSettingsTable.key inList keys }
            .associate { it[PlatformSettingsTable.key] to it[PlatformSettingsTable.value] }
    }

    override suspend fun saveAll(values: Map<String, String>): Unit = DatabaseFactory.dbQuery {
        val now = System.currentTimeMillis()
        values.forEach { (k, v) ->
            PlatformSettingsTable.upsert {
                it[key] = k
                it[value] = v
                it[updatedAt] = now
            }
        }
    }
}
