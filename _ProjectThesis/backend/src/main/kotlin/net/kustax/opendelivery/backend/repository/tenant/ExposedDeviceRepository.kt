package net.kustax.opendelivery.backend.repository.tenant

import net.kustax.opendelivery.backend.database.table.tenant.DevicesTable
import net.kustax.opendelivery.backend.repository.tenantQuery
import net.kustax.opendelivery.domain.entity.tenant.Device
import net.kustax.opendelivery.domain.repository.DeviceRepository
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*

class ExposedDeviceRepository : DeviceRepository {

    override suspend fun create(device: Device): Device = tenantQuery {
        DevicesTable.insert { row ->
            row[id] = device.id
            row[propertyId] = device.propertyId
            row[name] = device.name
            row[androidDeviceId] = device.androidDeviceId
            row[isActive] = device.isActive
            row[isKioskEnabled] = device.isKioskEnabled
            row[lastSeenAt] = device.lastSeenAt
            row[activationCode] = device.activationCode
            row[activatedAt] = device.activatedAt
        }
        device
    }

    override suspend fun findById(id: String): Device? = tenantQuery {
        DevicesTable.selectAll()
            .where { DevicesTable.id eq id }
            .singleOrNull()
            ?.toDevice()
    }

    override suspend fun findByPropertyId(propertyId: String): List<Device> = tenantQuery {
        DevicesTable.selectAll()
            .where { DevicesTable.propertyId eq propertyId }
            .map { it.toDevice() }
    }

    override suspend fun findByActivationCode(code: String): Device? = tenantQuery {
        DevicesTable.selectAll()
            .where { DevicesTable.activationCode eq code }
            .singleOrNull()
            ?.toDevice()
    }

    override suspend fun activate(id: String, activationCode: String, androidDeviceId: String, activatedAt: Long): Boolean = tenantQuery {
        DevicesTable.update({
            (DevicesTable.id eq id) and DevicesTable.activatedAt.isNull() and (DevicesTable.activationCode eq activationCode)
        }) { row ->
            row[DevicesTable.androidDeviceId] = androidDeviceId
            row[DevicesTable.activatedAt] = activatedAt
            row[DevicesTable.activationCode] = null
            row[DevicesTable.isActive] = true
        } > 0
    }

    override suspend fun updateLastSeen(id: String, timestamp: Long): Unit = tenantQuery {
        DevicesTable.update({ DevicesTable.id eq id }) {
            it[lastSeenAt] = timestamp
        }
    }

    override suspend fun deactivate(id: String): Unit = tenantQuery {
        DevicesTable.update({ DevicesTable.id eq id }) {
            it[isActive] = false
        }
    }

    override suspend fun reactivate(id: String): Unit = tenantQuery {
        DevicesTable.update({ DevicesTable.id eq id }) {
            it[isActive] = true
        }
    }

    override suspend fun deactivateByPropertyId(propertyId: String): Unit = tenantQuery {
        DevicesTable.update({ DevicesTable.propertyId eq propertyId }) {
            it[isActive] = false
        }
    }

    override suspend fun unregister(id: String, newActivationCode: String): Unit = tenantQuery {
        DevicesTable.update({ DevicesTable.id eq id }) {
            it[androidDeviceId] = null
            it[activatedAt] = null
            it[isActive] = false
            it[activationCode] = newActivationCode
        }
    }

    override suspend fun delete(id: String): Unit = tenantQuery {
        DevicesTable.deleteWhere { DevicesTable.id eq id }
    }
}

private fun ResultRow.toDevice() = Device(
    id = this[DevicesTable.id],
    propertyId = this[DevicesTable.propertyId],
    name = this[DevicesTable.name],
    androidDeviceId = this[DevicesTable.androidDeviceId],
    isActive = this[DevicesTable.isActive],
    isKioskEnabled = this[DevicesTable.isKioskEnabled],
    lastSeenAt = this[DevicesTable.lastSeenAt],
    activationCode = this[DevicesTable.activationCode],
    activatedAt = this[DevicesTable.activatedAt]
)
