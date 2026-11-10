package net.kustax.opendelivery.domain.entity.platform

import net.kustax.opendelivery.domain.enum.ExternalPlatform

// A restaurant as listed by an external food delivery platform; ids are only unique within a platform
data class Restaurant(
    val id: String,
    val platform: ExternalPlatform,
    val name: String,
    val cuisine: String,
    val rating: Double,
    val deliveryFeeCents: Long,
    val etaMinutes: Int,
    val minOrderCents: Long?
)
