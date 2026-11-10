package net.kustax.opendelivery.backend.service

import com.google.zxing.BarcodeFormat
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.qrcode.QRCodeWriter
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.withContext
import net.kustax.opendelivery.backend.database.TenantContext
import net.kustax.opendelivery.backend.security.JwtConfig
import net.kustax.opendelivery.data.request.CreateDeviceRequest
import net.kustax.opendelivery.data.response.DeviceResolveResponse
import net.kustax.opendelivery.data.response.DeviceResponse
import net.kustax.opendelivery.domain.entity.tenant.Device
import net.kustax.opendelivery.domain.enum.UserRole
import net.kustax.opendelivery.domain.repository.AuthRepository
import net.kustax.opendelivery.domain.repository.DeviceRepository
import net.kustax.opendelivery.domain.repository.OrderRepository
import net.kustax.opendelivery.domain.repository.OwnerRepository
import net.kustax.opendelivery.domain.repository.PropertyRepository

class DeviceService(
    private val deviceRepository: DeviceRepository,
    private val auditLogService: AuditLogService,
    private val ownerRepository: OwnerRepository,
    private val authRepository: AuthRepository,
    private val orderRepository: OrderRepository,
    private val propertyRepository: PropertyRepository
) {

    private val publicApiUrl = System.getenv("PUBLIC_API_URL") ?: "http://localhost:8080"
    private val secureRandom = java.security.SecureRandom()

    suspend fun create(request: CreateDeviceRequest, propertyId: String, actorId: String, actorRole: String, schemaName: String): DeviceResponse {
        require(request.name.isNotBlank()) { "Device name is required" }
        propertyRepository.findById(propertyId) ?: throw NotFoundException("Property not found: $propertyId")
        val id = UUID.randomUUID().toString()
        val activationCode = generateUniqueActivationCode()
        val qrContent = """{"deviceId":"$id","activationCode":"$activationCode","schemaName":"$schemaName","endpoint":"$publicApiUrl"}"""
        val qrBase64 = generateQrBase64(qrContent)
        val device = Device(
            id = id,
            propertyId = propertyId,
            name = request.name,
            androidDeviceId = null,
            isActive = false,
            isKioskEnabled = false,
            lastSeenAt = null,
            activationCode = activationCode,
            activatedAt = null
        )
        deviceRepository.create(device)
        auditLogService.log(actorId, actorRole, "CREATE_DEVICE", "Device", id, schemaName)
        return device.toResponse(qrBase64, activationCode)
    }

    suspend fun activate(id: String, activationCode: String, androidDeviceId: String, schemaName: String): DeviceResponse {
        val device = deviceRepository.findById(id) ?: throw NotFoundException("Device not found: $id")
        if (device.activatedAt != null) throw ConflictException("Device is already activated")
        if (device.activationCode != activationCode) throw ForbiddenException("Invalid activation code")
        // A device waiting for activation is always stored as inactive, so "inactive" here means its property
        // or its owner has been switched off: activating it would silently bring a disabled kiosk back on.
        val property = propertyRepository.findById(device.propertyId)
        if (property == null || !property.isActive) throw ConflictException("Device belongs to a deactivated property")
        if (!authRepository.isOwnerActiveBySchema(schemaName)) {
            throw ConflictException("Device belongs to a deactivated owner")
        }
        val now = System.currentTimeMillis()
        // Compare-and-set: only a not-yet-activated device whose code still matches is updated, so two
        // simultaneous activations with the same code cannot both succeed.
        if (!deviceRepository.activate(id, activationCode, androidDeviceId, now)) {
            throw ConflictException("Device is already activated")
        }
        val token = JwtConfig.generateAccessToken(id, UserRole.DEVICE, schemaName, ttlMs = JwtConfig.DEVICE_TOKEN_TTL_MS)
        return device.copy(androidDeviceId = androidDeviceId, activatedAt = now, activationCode = null, isActive = true)
            .toResponse(token = token)
    }

    suspend fun resolveByCode(code: String): DeviceResolveResponse {
        for (owner in ownerRepository.findAll()) {
            val device = withContext(TenantContext(owner.schemaName)) {
                deviceRepository.findByActivationCode(code)
            }
            if (device != null) {
                return DeviceResolveResponse(deviceId = device.id, schemaName = owner.schemaName)
            }
        }
        throw NotFoundException("Activation code not found")
    }

    suspend fun findByPropertyId(propertyId: String): List<DeviceResponse> =
        deviceRepository.findByPropertyId(propertyId).map { it.toResponse() }

    suspend fun findById(id: String, schemaName: String): DeviceResponse {
        val device = deviceRepository.findById(id) ?: throw NotFoundException("Device not found: $id")
        // activationCode is already null once a device has been activated (cleared by activate()), so
        // passing it through here is always safe — it's never stale/secret data once activation happened.
        val qrBase64 = device.activationCode?.let {
            val qrContent = """{"deviceId":"${device.id}","activationCode":"$it","schemaName":"$schemaName","endpoint":"$publicApiUrl"}"""
            generateQrBase64(qrContent)
        }
        return device.toResponse(qrBase64 = qrBase64, activationCode = device.activationCode)
    }

    suspend fun deactivate(id: String, actorId: String, actorRole: String, schemaName: String) {
        deviceRepository.findById(id) ?: throw NotFoundException("Device not found: $id")
        deviceRepository.deactivate(id)
        auditLogService.log(actorId, actorRole, "DEACTIVATE_DEVICE", "Device", id, schemaName)
    }

    suspend fun reactivate(id: String, actorId: String, actorRole: String, schemaName: String) {
        val device = deviceRepository.findById(id) ?: throw NotFoundException("Device not found: $id")
        if (device.activatedAt == null) {
            throw ConflictException("Device was unregistered — it must be re-activated via QR/manual code, not reactivated")
        }
        deviceRepository.reactivate(id)
        auditLogService.log(actorId, actorRole, "REACTIVATE_DEVICE", "Device", id, schemaName)
    }

    suspend fun deactivateByPropertyId(propertyId: String, actorId: String, actorRole: String, schemaName: String) {
        deviceRepository.deactivateByPropertyId(propertyId)
        auditLogService.log(actorId, actorRole, "DEACTIVATE_DEVICES_BY_PROPERTY", "Device", propertyId, schemaName)
    }

    suspend fun unregister(id: String, actorId: String, actorRole: String, schemaName: String): DeviceResponse {
        deviceRepository.findById(id) ?: throw NotFoundException("Device not found: $id")
        val activationCode = generateUniqueActivationCode()
        deviceRepository.unregister(id, activationCode)
        val qrContent = """{"deviceId":"$id","activationCode":"$activationCode","schemaName":"$schemaName","endpoint":"$publicApiUrl"}"""
        val qrBase64 = generateQrBase64(qrContent)
        auditLogService.log(actorId, actorRole, "UNREGISTER_DEVICE", "Device", id, schemaName)
        val device = deviceRepository.findById(id)!!
        return device.toResponse(qrBase64 = qrBase64, activationCode = activationCode)
    }

    suspend fun delete(id: String, actorId: String, actorRole: String, schemaName: String) {
        deviceRepository.findById(id) ?: throw NotFoundException("Device not found: $id")
        if (orderRepository.findByDeviceId(id).isNotEmpty()) {
            throw ConflictException("Cannot delete a device with existing orders — deactivate it instead")
        }
        deviceRepository.delete(id)
        auditLogService.log(actorId, actorRole, "DELETE_DEVICE", "Device", id, schemaName)
    }

    suspend fun updateLastSeen(id: String) {
        deviceRepository.updateLastSeen(id, System.currentTimeMillis())
    }

    // resolveByCode() resolves a scanned/entered code by scanning every tenant schema for a match, so a
    // code that collides across two tenants (astronomically unlikely per-code, but checked defensively
    // since it would otherwise activate a device into the wrong tenant's schema) would resolve to
    // whichever tenant happens to be found first. Guard against that here, at generation time, rather
    // than at resolve time — this runs on the rare admin "create device" action, not a hot path.
    private suspend fun generateUniqueActivationCode(): String {
        val owners = ownerRepository.findAll()
        repeat(5) {
            val code = generateActivationCode()
            val collides = owners.any { owner ->
                val existing = withContext(TenantContext(owner.schemaName)) {
                    deviceRepository.findByActivationCode(code)
                }
                existing != null
            }
            if (!collides) return code
        }
        throw ConflictException("Could not generate a unique activation code — please retry")
    }

    private fun generateActivationCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..8).map { chars[secureRandom.nextInt(chars.length)] }.joinToString("")
    }

    private fun generateQrBase64(content: String): String {
        val writer = QRCodeWriter()
        val matrix = writer.encode(content, BarcodeFormat.QR_CODE, 256, 256)
        val bos = ByteArrayOutputStream()
        MatrixToImageWriter.writeToStream(matrix, "PNG", bos)
        return Base64.getEncoder().encodeToString(bos.toByteArray())
    }
}

// activationCode and the QR code are only passed while a device waits for activation (create, findById, unregister);
// token is only passed from activate() — never exposed on subsequent reads
private fun Device.toResponse(qrBase64: String? = null, activationCode: String? = null, token: String? = null) = DeviceResponse(
    id = id,
    propertyId = propertyId,
    name = name,
    androidDeviceId = androidDeviceId,
    isActive = isActive,
    isKioskEnabled = isKioskEnabled,
    lastSeenAt = lastSeenAt,
    activationCode = activationCode,
    activatedAt = activatedAt,
    qrCodeBase64 = qrBase64,
    token = token
)
