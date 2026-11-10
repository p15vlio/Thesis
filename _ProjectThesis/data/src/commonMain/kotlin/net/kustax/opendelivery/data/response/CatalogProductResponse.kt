package net.kustax.opendelivery.data.response

import kotlinx.serialization.Serializable

@Serializable
data class CatalogProductResponse(
    val id: String,
    val platform: String,
    val name: String,
    val description: String,
    val category: String,
    val priceCents: Long,
    val brand: String?
)
