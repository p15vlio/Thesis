package net.kustax.opendelivery.domain.repository

import net.kustax.opendelivery.domain.entity.tenant.FoodPlatformConnection
import net.kustax.opendelivery.domain.enum.ExternalPlatform

interface FoodPlatformConnectionRepository {
    suspend fun findAll(): List<FoodPlatformConnection>
    suspend fun findByPlatform(platform: ExternalPlatform): FoodPlatformConnection?
    // Reconnecting with a new key replaces the old one
    suspend fun save(platform: ExternalPlatform, encryptedApiKey: String, connectedAt: Long)
    suspend fun delete(platform: ExternalPlatform)
}
