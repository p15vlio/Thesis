package net.kustax.opendelivery.domain.repository

import net.kustax.opendelivery.domain.entity.tenant.Product

interface ProductRepository {
    suspend fun create(product: Product): Product
    suspend fun findById(id: String): Product?
    suspend fun findAll(): List<Product>
    suspend fun update(product: Product): Product
    suspend fun toggleAvailability(id: String, isAvailable: Boolean)
    suspend fun delete(id: String)
}
