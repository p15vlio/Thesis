package net.kustax.opendelivery.backend.repository.tenant

import net.kustax.opendelivery.backend.database.table.tenant.PropertiesTable
import net.kustax.opendelivery.backend.repository.tenantQuery
import net.kustax.opendelivery.domain.entity.tenant.Property
import net.kustax.opendelivery.domain.repository.PropertyRepository
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*

class ExposedPropertyRepository : PropertyRepository {

    override suspend fun create(property: Property): Property = tenantQuery {
        PropertiesTable.insert { row ->
            row[id] = property.id
            row[name] = property.name
            row[streetName] = property.streetName
            row[streetNo] = property.streetNo
            row[postalCode] = property.postalCode
            row[area] = property.area
            row[level] = property.level
            row[nameOnDoorbell] = property.nameOnDoorbell
            row[contactPhone] = property.contactPhone
            row[city] = property.city
            row[country] = property.country
            row[timezone] = property.timezone
            row[notes] = property.notes
            row[latitude] = property.latitude
            row[longitude] = property.longitude
            row[wifiName] = property.wifiName
            row[wifiPassword] = property.wifiPassword
            row[checkOutTime] = property.checkOutTime
            row[houseRules] = property.houseRules
            row[isActive] = property.isActive
            row[createdAt] = property.createdAt
        }
        property
    }

    override suspend fun findById(id: String): Property? = tenantQuery {
        PropertiesTable.selectAll()
            .where { PropertiesTable.id eq id }
            .singleOrNull()
            ?.toProperty()
    }

    // Every property in this tenant schema belongs to the same owner — the schema IS the owner scope
    override suspend fun findAll(): List<Property> = tenantQuery {
        PropertiesTable.selectAll().map { it.toProperty() }
    }

    override suspend fun update(property: Property): Property = tenantQuery {
        PropertiesTable.update({ PropertiesTable.id eq property.id }) { row ->
            row[name] = property.name
            row[streetName] = property.streetName
            row[streetNo] = property.streetNo
            row[postalCode] = property.postalCode
            row[area] = property.area
            row[level] = property.level
            row[nameOnDoorbell] = property.nameOnDoorbell
            row[contactPhone] = property.contactPhone
            row[city] = property.city
            row[country] = property.country
            row[timezone] = property.timezone
            row[notes] = property.notes
            row[latitude] = property.latitude
            row[longitude] = property.longitude
            row[wifiName] = property.wifiName
            row[wifiPassword] = property.wifiPassword
            row[checkOutTime] = property.checkOutTime
            row[houseRules] = property.houseRules
            row[isActive] = property.isActive
        }
        property
    }

    override suspend fun deactivate(id: String): Unit = tenantQuery {
        PropertiesTable.update({ PropertiesTable.id eq id }) { it[isActive] = false }
    }

    override suspend fun activate(id: String): Unit = tenantQuery {
        PropertiesTable.update({ PropertiesTable.id eq id }) { it[isActive] = true }
    }

    override suspend fun delete(id: String): Unit = tenantQuery {
        PropertiesTable.deleteWhere { PropertiesTable.id eq id }
    }
}

private fun ResultRow.toProperty() = Property(
    id = this[PropertiesTable.id],
    name = this[PropertiesTable.name],
    streetName = this[PropertiesTable.streetName],
    streetNo = this[PropertiesTable.streetNo],
    postalCode = this[PropertiesTable.postalCode],
    area = this[PropertiesTable.area],
    level = this[PropertiesTable.level],
    nameOnDoorbell = this[PropertiesTable.nameOnDoorbell],
    contactPhone = this[PropertiesTable.contactPhone],
    city = this[PropertiesTable.city],
    country = this[PropertiesTable.country],
    timezone = this[PropertiesTable.timezone],
    notes = this[PropertiesTable.notes],
    latitude = this[PropertiesTable.latitude],
    longitude = this[PropertiesTable.longitude],
    wifiName = this[PropertiesTable.wifiName],
    wifiPassword = this[PropertiesTable.wifiPassword],
    checkOutTime = this[PropertiesTable.checkOutTime],
    houseRules = this[PropertiesTable.houseRules],
    isActive = this[PropertiesTable.isActive],
    createdAt = this[PropertiesTable.createdAt]
)
