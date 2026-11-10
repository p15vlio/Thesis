package net.kustax.opendelivery.data.response

import kotlinx.serialization.Serializable

@Serializable
data class ImportProductsResponse(
    val imported: List<ProductResponse>,
    val skipped: List<String>
)
