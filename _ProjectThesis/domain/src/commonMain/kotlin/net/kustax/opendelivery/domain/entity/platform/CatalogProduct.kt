package net.kustax.opendelivery.domain.entity.platform

import net.kustax.opendelivery.domain.enum.ExternalPlatform
import net.kustax.opendelivery.domain.enum.ProductCategory

// A retail item (convenience goods) from an external platform's market catalog; ids are only unique within a platform
data class CatalogProduct(
    val id: String,
    val platform: ExternalPlatform,
    val name: String,
    val description: String,
    val category: ProductCategory,
    val priceCents: Long,
    val brand: String?
)
