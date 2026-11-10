package net.kustax.opendelivery.domain.repository

import net.kustax.opendelivery.domain.entity.tenant.Device

interface DeviceRepository {
    suspend fun create(device: Device): Device
    suspend fun findById(id: String): Device?
    suspend fun findByPropertyId(propertyId: String): List<Device>
    suspend fun findByActivationCode(code: String): Device?
    // Returns false when the device was already activated or its activation code no longer matches
    suspend fun activate(id: String, activationCode: String, androidDeviceId: String, activatedAt: Long): Boolean
    suspend fun updateLastSeen(id: String, timestamp: Long)
    suspend fun deactivate(id: String)
    suspend fun reactivate(id: String)
    suspend fun deactivateByPropertyId(propertyId: String)
    suspend fun unregister(id: String, newActivationCode: String)
    suspend fun delete(id: String)
}
