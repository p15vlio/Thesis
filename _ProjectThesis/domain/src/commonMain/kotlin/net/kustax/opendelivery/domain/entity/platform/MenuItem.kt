package net.kustax.opendelivery.domain.entity.platform

// A dish on an external platform's menu; the platform's price is the source of truth for ordering
data class MenuItem(
    val id: String,
    val restaurantId: String,
    val name: String,
    val description: String,
    val priceCents: Long,
    val category: String
)
