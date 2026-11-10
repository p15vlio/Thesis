package net.kustax.opendelivery.domain.entity.tenant

import net.kustax.opendelivery.domain.enum.ProductCategory

data class Product(
    val id: String,
    val name: String,
    val description: String,
    val category: ProductCategory,
    val basePrice: Long,
    val imageUrl: String?,
    val isAvailable: Boolean,
    val createdAt: Long
)
