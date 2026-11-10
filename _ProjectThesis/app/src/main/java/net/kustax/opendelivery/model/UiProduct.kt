package net.kustax.opendelivery.model

data class UiProduct(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val priceCents: Long,
    val imageUrl: String?,
    val isAvailable: Boolean,
    val allergenTags: List<String> = emptyList()
)
