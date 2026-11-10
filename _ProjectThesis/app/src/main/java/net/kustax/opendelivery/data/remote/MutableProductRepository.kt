package net.kustax.opendelivery.data.remote

import net.kustax.opendelivery.model.UiProduct

// Holds the active product list for the current session, populated from the backend via RealProductRepository.
class MutableProductRepository(initialProducts: List<UiProduct> = emptyList()) {

    private var products: List<UiProduct> = initialProducts

    fun replaceAll(newProducts: List<UiProduct>) {
        products = newProducts
    }

    fun getAll(): List<UiProduct> = products.filter { it.isAvailable }

    fun getById(id: String): UiProduct? = products.find { it.id == id }
}
