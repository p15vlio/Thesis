package net.kustax.opendelivery.data.request

import kotlinx.serialization.Serializable

@Serializable
data class ImportProductsRequest(
    val platform: String,
    val catalogProductIds: List<String>
)
