package net.kustax.opendelivery.data.response

import kotlinx.serialization.Serializable

@Serializable
data class ProductResponse(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val basePrice: Long,
    val imageUrl: String?,
    val isAvailable: Boolean,
    val createdAt: Long,
    val disabledPropertyIds: List<String> = emptyList()
)
