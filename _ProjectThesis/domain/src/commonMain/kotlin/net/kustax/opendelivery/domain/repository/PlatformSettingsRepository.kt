package net.kustax.opendelivery.domain.repository

interface PlatformSettingsRepository {
    suspend fun findAll(keys: Collection<String>): Map<String, String>
    suspend fun saveAll(values: Map<String, String>)
}
