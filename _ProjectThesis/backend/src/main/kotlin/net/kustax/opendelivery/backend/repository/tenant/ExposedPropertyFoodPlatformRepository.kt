package net.kustax.opendelivery.backend.repository.tenant

import net.kustax.opendelivery.backend.database.table.tenant.PropertyFoodPlatformsTable
import net.kustax.opendelivery.backend.repository.tenantQuery
import net.kustax.opendelivery.domain.enum.ExternalPlatform
import net.kustax.opendelivery.domain.repository.PropertyFoodPlatformRepository
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*

// Pure association table, like ExposedProductPropertyOverrideRepository.
class ExposedPropertyFoodPlatformRepository : PropertyFoodPlatformRepository {

    override suspend fun findPlatforms(propertyId: String): Set<ExternalPlatform> = tenantQuery {
        PropertyFoodPlatformsTable.selectAll()
            .where { PropertyFoodPlatformsTable.propertyId eq propertyId }
            .map { enumValueOf<ExternalPlatform>(it[PropertyFoodPlatformsTable.platform]) }
            .toSet()
    }

    // Delete + insert run in a single transaction
    override suspend fun replace(propertyId: String, platforms: Set<ExternalPlatform>): Unit = tenantQuery {
        PropertyFoodPlatformsTable.deleteWhere { PropertyFoodPlatformsTable.propertyId eq propertyId }
        platforms.forEach { platform ->
            PropertyFoodPlatformsTable.insert {
                it[PropertyFoodPlatformsTable.propertyId] = propertyId
                it[PropertyFoodPlatformsTable.platform] = platform.name
            }
        }
    }

    override suspend fun deleteForPlatform(platform: ExternalPlatform): Unit = tenantQuery {
        PropertyFoodPlatformsTable.deleteWhere { PropertyFoodPlatformsTable.platform eq platform.name }
    }
}
