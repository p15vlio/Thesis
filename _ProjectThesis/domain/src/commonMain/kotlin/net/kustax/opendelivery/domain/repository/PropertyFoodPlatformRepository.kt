package net.kustax.opendelivery.domain.repository

import net.kustax.opendelivery.domain.enum.ExternalPlatform

// Row presence means "platform is offered at this property"
interface PropertyFoodPlatformRepository {
    suspend fun findPlatforms(propertyId: String): Set<ExternalPlatform>
    suspend fun replace(propertyId: String, platforms: Set<ExternalPlatform>)
    suspend fun deleteForPlatform(platform: ExternalPlatform)
}
