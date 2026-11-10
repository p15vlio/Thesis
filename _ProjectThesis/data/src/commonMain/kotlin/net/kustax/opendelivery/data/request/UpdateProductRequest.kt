package net.kustax.opendelivery.data.request

import kotlinx.serialization.Serializable

@Serializable
data class UpdateProductRequest(
    val name: String,
    val description: String,
    val basePrice: Long,
    val imageUrl: String?,
    val isAvailable: Boolean
)
