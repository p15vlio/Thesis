package net.kustax.opendelivery.domain.port

import net.kustax.opendelivery.domain.entity.platform.CompanyRegistryRecord

// Official business registry lookup (GEMH in Greece); returns null when no company has this VAT ID
interface CompanyRegistry {
    suspend fun findByVatId(vatId: String): CompanyRegistryRecord?
}
