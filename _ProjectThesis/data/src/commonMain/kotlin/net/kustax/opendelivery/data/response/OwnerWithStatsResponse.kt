package net.kustax.opendelivery.data.response

import kotlinx.serialization.Serializable

@Serializable
data class OwnerWithStatsResponse(
    val owner: OwnerResponse,
    val propertyCount: Int,
    val deviceCount: Int,
    val productCount: Int
)
