package net.kustax.opendelivery.backend.service

import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.kustax.opendelivery.backend.database.SchemaManager
import net.kustax.opendelivery.backend.security.PasswordHasher
import net.kustax.opendelivery.domain.enum.UserRole
import net.kustax.opendelivery.data.request.CreateOwnerRequest
import net.kustax.opendelivery.data.request.UpdateOwnerRequest
import net.kustax.opendelivery.data.response.OwnerResponse
import net.kustax.opendelivery.domain.entity.platform.Owner
import net.kustax.opendelivery.domain.port.NotificationService
import net.kustax.opendelivery.domain.repository.AuthRepository
import net.kustax.opendelivery.domain.repository.OwnerRepository

class OwnerService(
    private val ownerRepository: OwnerRepository,
    private val authRepository: AuthRepository,
    private val auditLogService: AuditLogService,
    private val notificationService: NotificationService
) {

    private val publicFrontendUrl = System.getenv("PUBLIC_FRONTEND_URL") ?: "http://localhost"
    private val secureRandom = java.security.SecureRandom()

    suspend fun create(request: CreateOwnerRequest, actorId: String, actorRole: String): OwnerResponse {
        require(VAT_ID_REGEX.matches(request.vatId)) { "VAT ID must be exactly 9 digits" }
        requireCompanyFields(request.companyName, request.streetName, request.postalCode, request.city)
        require(request.contactFirstName.isNotBlank()) { "Contact first name is required" }
        require(request.contactLastName.isNotBlank()) { "Contact last name is required" }
        val contactEmail = request.contactEmail.trim().lowercase()
        require(contactEmail.isNotBlank()) { "Contact email is required" }
        require(request.contactPhone.isNotBlank()) { "Contact phone is required" }
        if (ownerRepository.findByEmail(contactEmail) != null) {
            throw ConflictException("An owner with email '$contactEmail' already exists")
        }
        val id = UUID.randomUUID().toString()
        val schemaName = "tenant_${id.replace("-", "").take(12)}"
        val tempPassword = generateTempPassword()
        val owner = Owner(
            id = id,
            companyName = request.companyName,
            distinctiveTitle = request.distinctiveTitle,
            vatId = request.vatId,
            gemhNumber = request.gemhNumber,
            legalForm = request.legalForm,
            companyActivity = request.companyActivity,
            streetName = request.streetName,
            streetNo = request.streetNo,
            postalCode = request.postalCode,
            city = request.city,
            contactFirstName = request.contactFirstName,
            contactLastName = request.contactLastName,
            contactEmail = contactEmail,
            contactPhone = request.contactPhone,
            secondaryContactName = request.secondaryContactName,
            secondaryContactPhone = request.secondaryContactPhone,
            secondaryContactEmail = request.secondaryContactEmail,
            website = request.website,
            notes = request.notes,
            schemaName = schemaName,
            isActive = true,
            createdAt = System.currentTimeMillis()
        )
        ownerRepository.createWithHash(owner, PasswordHasher.hash(tempPassword))
        try {
            withContext(Dispatchers.IO) { SchemaManager.provisionTenant(schemaName) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Schema provisioning failed after the owner row committed. Deactivate rather than leave a
            // permanently broken, ACTIVE owner whose schema doesn't exist — every subsequent call for them
            // would otherwise fail forever with no visible cause. Reuses the existing deactivate mechanism
            // (no hard-delete needed or wanted here — owners are soft-delete-only by design).
            runCatching { ownerRepository.deactivate(id) }
            throw IllegalStateException("Failed to provision tenant schema for new owner: ${e.message}", e)
        }
        notificationService.sendOwnerWelcome(contactEmail, tempPassword, publicFrontendUrl)
        auditLogService.log(actorId, actorRole, "CREATE_OWNER", "Owner", id)
        return owner.toResponse(tempPassword)
    }

    suspend fun findAll(): List<OwnerResponse> =
        ownerRepository.findAll().map { it.toResponse() }

    suspend fun findById(id: String): OwnerResponse {
        val owner = ownerRepository.findById(id) ?: throw NotFoundException("Owner not found: $id")
        return owner.toResponse()
    }

    suspend fun update(id: String, request: UpdateOwnerRequest, actorId: String, actorRole: String): OwnerResponse {
        requireCompanyFields(request.companyName, request.streetName, request.postalCode, request.city)
        require(request.contactFirstName.isNotBlank()) { "Contact first name is required" }
        require(request.contactLastName.isNotBlank()) { "Contact last name is required" }
        val existing = ownerRepository.findById(id) ?: throw NotFoundException("Owner not found: $id")
        val updated = existing.copy(
            companyName = request.companyName,
            distinctiveTitle = request.distinctiveTitle,
            gemhNumber = request.gemhNumber,
            legalForm = request.legalForm,
            companyActivity = request.companyActivity,
            streetName = request.streetName,
            streetNo = request.streetNo,
            postalCode = request.postalCode,
            city = request.city,
            contactFirstName = request.contactFirstName,
            contactLastName = request.contactLastName,
            contactPhone = request.contactPhone,
            secondaryContactName = request.secondaryContactName,
            secondaryContactPhone = request.secondaryContactPhone,
            secondaryContactEmail = request.secondaryContactEmail,
            website = request.website,
            notes = request.notes
        )
        ownerRepository.update(updated)
        auditLogService.log(actorId, actorRole, "UPDATE_OWNER", "Owner", id)
        return updated.toResponse()
    }

    suspend fun deactivate(id: String, actorId: String, actorRole: String) {
        ownerRepository.findById(id) ?: throw NotFoundException("Owner not found: $id")
        ownerRepository.deactivate(id)
        // Signed-out for good: without their refresh tokens a deactivated owner can't mint new access tokens
        authRepository.deleteRefreshTokensForUser(id)
        auditLogService.log(actorId, actorRole, "DEACTIVATE_OWNER", "Owner", id)
    }

    suspend fun activate(id: String, actorId: String, actorRole: String) {
        ownerRepository.findById(id) ?: throw NotFoundException("Owner not found: $id")
        ownerRepository.activate(id)
        auditLogService.log(actorId, actorRole, "ACTIVATE_OWNER", "Owner", id)
    }

    // The owner forgot their password: a new temporary password replaces it, every session of the owner is
    // signed out, and the password is emailed to the owner and returned once, exactly like at creation.
    suspend fun resetPassword(id: String, actorId: String, actorRole: String): OwnerResponse {
        val owner = ownerRepository.findById(id) ?: throw NotFoundException("Owner not found: $id")
        val tempPassword = generateTempPassword()
        authRepository.updatePasswordHash(id, UserRole.OWNER, PasswordHasher.hash(tempPassword))
        authRepository.deleteRefreshTokensForUser(id)
        notificationService.sendPasswordReset(owner.contactEmail, tempPassword, publicFrontendUrl)
        auditLogService.log(actorId, actorRole, "RESET_OWNER_PASSWORD", "Owner", id)
        return owner.toResponse(tempPassword)
    }

    private fun requireCompanyFields(companyName: String, streetName: String, postalCode: String, city: String) {
        require(companyName.isNotBlank()) { "Company name is required" }
        require(streetName.isNotBlank()) { "Street name is required" }
        require(postalCode.isNotBlank()) { "Postal code is required" }
        require(city.isNotBlank()) { "City is required" }
    }

    private fun generateTempPassword(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..12).map { chars[secureRandom.nextInt(chars.length)] }.joinToString("")
    }
}

// Greek AFM: exactly 9 digits (leading zeros are significant)
internal val VAT_ID_REGEX = Regex("^\\d{9}$")

private fun Owner.toResponse(tempPassword: String? = null) = toOwnerResponse(tempPassword)
