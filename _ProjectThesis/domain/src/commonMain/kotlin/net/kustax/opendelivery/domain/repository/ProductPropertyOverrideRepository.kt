package net.kustax.opendelivery.domain.repository

// Row presence means "product disabled at this property"
interface ProductPropertyOverrideRepository {
    suspend fun disable(productId: String, propertyId: String)
    suspend fun enable(productId: String, propertyId: String)
    suspend fun findDisabledPropertyIds(productId: String): Set<String>
    suspend fun isDisabled(productId: String, propertyId: String): Boolean
}
