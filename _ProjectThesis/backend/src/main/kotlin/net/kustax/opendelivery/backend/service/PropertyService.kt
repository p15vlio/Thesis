package net.kustax.opendelivery.backend.service

import java.util.UUID
import net.kustax.opendelivery.data.request.CreatePropertyRequest
import net.kustax.opendelivery.data.request.UpdatePropertyRequest
import net.kustax.opendelivery.data.response.PropertyResponse
import net.kustax.opendelivery.domain.entity.tenant.Property
import net.kustax.opendelivery.domain.repository.OrderRepository
import net.kustax.opendelivery.domain.repository.PropertyRepository

class PropertyService(
    private val propertyRepository: PropertyRepository,
    private val auditLogService: AuditLogService,
    private val deviceService: DeviceService,
    private val orderRepository: OrderRepository
) {

    suspend fun create(request: CreatePropertyRequest, actorId: String, actorRole: String, schemaName: String): PropertyResponse {
        validate(request.name, request.streetName, request.streetNo, request.postalCode, request.nameOnDoorbell, request.contactPhone, request.city)
        validateGuestInfo(request.wifiName, request.wifiPassword, request.checkOutTime, request.houseRules)
        val property = Property(
            id = UUID.randomUUID().toString(),
            name = request.name,
            streetName = request.streetName,
            streetNo = request.streetNo,
            postalCode = request.postalCode,
            area = request.area,
            level = request.level,
            nameOnDoorbell = request.nameOnDoorbell,
            contactPhone = request.contactPhone,
            city = request.city,
            country = "Greece",
            timezone = "Europe/Athens",
            notes = request.notes,
            latitude = request.latitude,
            longitude = request.longitude,
            wifiName = request.wifiName.blankToNull(),
            wifiPassword = request.wifiPassword.blankToNull(),
            checkOutTime = request.checkOutTime.blankToNull(),
            houseRules = request.houseRules.blankToNull(),
            isActive = true,
            createdAt = System.currentTimeMillis()
        )
        propertyRepository.create(property)
        auditLogService.log(actorId, actorRole, "CREATE_PROPERTY", "Property", property.id, schemaName)
        return property.toResponse()
    }

    suspend fun findAll(): List<PropertyResponse> =
        propertyRepository.findAll().map { it.toResponse() }

    suspend fun findById(id: String): PropertyResponse {
        val property = propertyRepository.findById(id)
            ?: throw NotFoundException("Property not found: $id")
        return property.toResponse()
    }

    suspend fun update(id: String, request: UpdatePropertyRequest, actorId: String, actorRole: String, schemaName: String): PropertyResponse {
        validate(request.name, request.streetName, request.streetNo, request.postalCode, request.nameOnDoorbell, request.contactPhone, request.city)
        validateGuestInfo(request.wifiName, request.wifiPassword, request.checkOutTime, request.houseRules)
        val existing = propertyRepository.findById(id)
            ?: throw NotFoundException("Property not found: $id")
        val updated = existing.copy(
            name = request.name,
            streetName = request.streetName,
            streetNo = request.streetNo,
            postalCode = request.postalCode,
            area = request.area,
            level = request.level,
            nameOnDoorbell = request.nameOnDoorbell,
            contactPhone = request.contactPhone,
            city = request.city,
            notes = request.notes,
            latitude = request.latitude,
            longitude = request.longitude,
            wifiName = request.wifiName.blankToNull(),
            wifiPassword = request.wifiPassword.blankToNull(),
            checkOutTime = request.checkOutTime.blankToNull(),
            houseRules = request.houseRules.blankToNull()
        )
        propertyRepository.update(updated)
        auditLogService.log(actorId, actorRole, "UPDATE_PROPERTY", "Property", id, schemaName)
        return updated.toResponse()
    }

    suspend fun deactivate(id: String, actorId: String, actorRole: String, schemaName: String) {
        propertyRepository.findById(id) ?: throw NotFoundException("Property not found: $id")
        propertyRepository.deactivate(id)
        deviceService.deactivateByPropertyId(id, actorId, actorRole, schemaName)
        auditLogService.log(actorId, actorRole, "DEACTIVATE_PROPERTY", "Property", id, schemaName)
    }

    // Intentionally does NOT cascade-reactivate devices: the owner may have deactivated specific devices
    // for unrelated reasons, so reactivating a property should only reactivate the property itself.
    suspend fun activate(id: String, actorId: String, actorRole: String, schemaName: String) {
        propertyRepository.findById(id) ?: throw NotFoundException("Property not found: $id")
        propertyRepository.activate(id)
        auditLogService.log(actorId, actorRole, "ACTIVATE_PROPERTY", "Property", id, schemaName)
    }

    suspend fun delete(id: String, actorId: String, actorRole: String, schemaName: String) {
        propertyRepository.findById(id) ?: throw NotFoundException("Property not found: $id")
        if (orderRepository.findByPropertyId(id).isNotEmpty()) {
            throw ConflictException("Cannot delete a property with existing orders — deactivate it instead")
        }
        if (deviceService.findByPropertyId(id).isNotEmpty()) {
            throw ConflictException("Cannot delete a property that still has devices — delete its devices first")
        }
        propertyRepository.delete(id)
        auditLogService.log(actorId, actorRole, "DELETE_PROPERTY", "Property", id, schemaName)
    }

    // Optional guest info shown on the kiosk; blank means "not set"
    private fun validateGuestInfo(wifiName: String?, wifiPassword: String?, checkOutTime: String?, houseRules: String?) {
        require((wifiName?.length ?: 0) <= 128) { "Wi-Fi name must be at most 128 characters" }
        require((wifiPassword?.length ?: 0) <= 128) { "Wi-Fi password must be at most 128 characters" }
        require(checkOutTime.isNullOrBlank() || CHECK_OUT_TIME_REGEX.matches(checkOutTime)) { "Check-out time must be in HH:mm format" }
        require((houseRules?.length ?: 0) <= 4000) { "House rules must be at most 4000 characters" }
    }

    private fun String?.blankToNull(): String? = this?.takeIf { it.isNotBlank() }

    private fun validate(
        name: String,
        streetName: String,
        streetNo: String,
        postalCode: String,
        nameOnDoorbell: String,
        contactPhone: String,
        city: String
    ) {
        require(name.isNotBlank()) { "Property name is required" }
        require(streetName.isNotBlank()) { "Street name is required" }
        require(streetNo.isNotBlank()) { "Street number is required" }
        require(postalCode.isNotBlank()) { "Postal code is required" }
        require(nameOnDoorbell.isNotBlank()) { "Name on doorbell is required" }
        require(contactPhone.isNotBlank()) { "Contact phone is required" }
        require(city.isNotBlank()) { "City is required" }
    }
}

private fun Property.toResponse() = PropertyResponse(
    id = id,
    name = name,
    streetName = streetName,
    streetNo = streetNo,
    postalCode = postalCode,
    area = area,
    level = level,
    nameOnDoorbell = nameOnDoorbell,
    contactPhone = contactPhone,
    city = city,
    country = country,
    timezone = timezone,
    notes = notes,
    latitude = latitude,
    longitude = longitude,
    wifiName = wifiName,
    wifiPassword = wifiPassword,
    checkOutTime = checkOutTime,
    houseRules = houseRules,
    isActive = isActive,
    createdAt = createdAt
)

private val CHECK_OUT_TIME_REGEX = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")
