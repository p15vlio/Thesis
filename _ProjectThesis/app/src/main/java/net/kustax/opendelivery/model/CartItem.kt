package net.kustax.opendelivery.model

data class CartItem(
    val product: UiProduct,
    val quantity: Int
) {
    val subtotalCents: Long get() = product.priceCents * quantity
}
