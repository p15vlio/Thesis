package net.kustax.opendelivery.backend.service

import net.kustax.opendelivery.data.response.OwnerResponse
import net.kustax.opendelivery.domain.entity.platform.Owner

internal fun Owner.toOwnerResponse(tempPassword: String? = null) = OwnerResponse(
    id = id,
    displayName = distinctiveTitle ?: companyName,
    companyName = companyName,
    distinctiveTitle = distinctiveTitle,
    vatId = vatId,
    gemhNumber = gemhNumber,
    legalForm = legalForm,
    companyActivity = companyActivity,
    streetName = streetName,
    streetNo = streetNo,
    postalCode = postalCode,
    city = city,
    contactFirstName = contactFirstName,
    contactLastName = contactLastName,
    contactEmail = contactEmail,
    contactPhone = contactPhone,
    secondaryContactName = secondaryContactName,
    secondaryContactPhone = secondaryContactPhone,
    secondaryContactEmail = secondaryContactEmail,
    website = website,
    notes = notes,
    schemaName = schemaName,
    isActive = isActive,
    createdAt = createdAt,
    temporaryPassword = tempPassword
)
