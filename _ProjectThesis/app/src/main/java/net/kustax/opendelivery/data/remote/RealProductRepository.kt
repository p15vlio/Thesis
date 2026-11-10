package net.kustax.opendelivery.data.remote

import net.kustax.opendelivery.data.response.ProductResponse
import net.kustax.opendelivery.model.UiProduct

class RealProductRepository(private val apiClient: AppApiClient) {

    suspend fun getAll(serverUrl: String, token: String): List<UiProduct> {
        return apiClient.getProducts(serverUrl, token)
            .filter { it.isAvailable }
            .map { it.toUiProduct() }
    }

    private fun ProductResponse.toUiProduct(): UiProduct = UiProduct(
        id = id,
        name = name,
        description = description,
        category = category,
        priceCents = basePrice,
        imageUrl = imageUrl,
        isAvailable = isAvailable
    )
}
