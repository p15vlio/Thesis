package net.kustax.opendelivery.domain.port

import net.kustax.opendelivery.domain.entity.platform.CatalogProduct
import net.kustax.opendelivery.domain.entity.platform.MenuItem
import net.kustax.opendelivery.domain.entity.platform.Restaurant
import net.kustax.opendelivery.domain.entity.tenant.Order
import net.kustax.opendelivery.domain.entity.tenant.OrderItem
import net.kustax.opendelivery.domain.enum.ExternalPlatform

// One implementation per external food delivery platform (efood, Wolt, Box). The owner's API key is
// passed on every call, as a real platform API would require it. Order status changes are not polled:
// the platform reports them asynchronously through FoodOrderEventListener, like a webhook.
interface FoodDeliveryProvider {
    val platform: ExternalPlatform
    suspend fun verifyApiKey(apiKey: String): Boolean
    suspend fun listRestaurants(apiKey: String): List<Restaurant>
    suspend fun getMenu(apiKey: String, restaurantId: String): List<MenuItem>

    // The platform's retail (market) catalog of convenience goods, as sold on e.g. Wolt Market / efood Market
    suspend fun listCatalogProducts(apiKey: String): List<CatalogProduct>

    // order.restaurantId selects the restaurant; items reference MenuItem ids as productId.
    // schemaName identifies the tenant so status events can be routed back to the right order.
    // Returns the platform's own order id.
    suspend fun placeOrder(apiKey: String, schemaName: String, order: Order, items: List<OrderItem>, deliveryAddress: String): String
    suspend fun cancelOrder(apiKey: String, externalOrderId: String): Boolean
}
