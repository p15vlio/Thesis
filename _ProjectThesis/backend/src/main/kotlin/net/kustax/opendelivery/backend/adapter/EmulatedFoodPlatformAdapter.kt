package net.kustax.opendelivery.backend.adapter

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.kustax.opendelivery.backend.service.ExternalServiceException
import net.kustax.opendelivery.domain.entity.platform.CatalogProduct
import net.kustax.opendelivery.domain.entity.platform.MenuItem
import net.kustax.opendelivery.domain.entity.platform.Restaurant
import net.kustax.opendelivery.domain.entity.tenant.Order
import net.kustax.opendelivery.domain.entity.tenant.OrderItem
import net.kustax.opendelivery.domain.enum.ExternalPlatform
import net.kustax.opendelivery.domain.enum.OrderStatus
import net.kustax.opendelivery.domain.enum.ProductCategory
import net.kustax.opendelivery.domain.port.FoodDeliveryProvider
import net.kustax.opendelivery.domain.port.FoodOrderEventListener
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

// Shared behaviour of the emulated efood / Wolt / Box adapters; each subclass only names its platform and
// fixture files. Restaurants and menus, and the retail catalog, are loaded from
// backend/src/main/resources/food-platforms/.
//
// Emulation rule for API keys: any key of at least 8 characters is accepted (a real platform would check
// the key against its own account database).
//
// Like a real platform, an accepted order is advanced by the platform itself: a coroutine owned by this
// adapter reports PREPARING, OUT_FOR_DELIVERY and DELIVERED through the FoodOrderEventListener, one step
// apart — the stand-in for the platform's status webhook.
abstract class EmulatedFoodPlatformAdapter(
    final override val platform: ExternalPlatform,
    fixtureResource: String,
    catalogResource: String,
    private val listener: FoodOrderEventListener,
    private val stepMillis: Long
) : FoodDeliveryProvider {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val simulations = ConcurrentHashMap<String, Job>()

    fun close() = scope.cancel()

    private val restaurants: List<FixtureRestaurant> = (javaClass.getResourceAsStream(fixtureResource)
        ?: error("Missing fixture $fixtureResource")).use {
        Json { ignoreUnknownKeys = true }.decodeFromString<Fixture>(it.readBytes().decodeToString()).restaurants
    }

    private val catalog: List<FixtureCatalogProduct> = (javaClass.getResourceAsStream(catalogResource)
        ?: error("Missing fixture $catalogResource")).use {
        Json { ignoreUnknownKeys = true }.decodeFromString<CatalogFixture>(it.readBytes().decodeToString()).products
    }

    override suspend fun verifyApiKey(apiKey: String): Boolean = apiKey.trim().length >= MIN_API_KEY_LENGTH

    override suspend fun listRestaurants(apiKey: String): List<Restaurant> {
        requireValidKey(apiKey)
        return restaurants.map { it.toRestaurant() }
    }

    override suspend fun getMenu(apiKey: String, restaurantId: String): List<MenuItem> {
        requireValidKey(apiKey)
        val restaurant = restaurants.firstOrNull { it.id == restaurantId }
            ?: throw IllegalArgumentException("Unknown restaurant: $restaurantId")
        return restaurant.menu.map { it.toMenuItem(restaurant.id) }
    }

    override suspend fun listCatalogProducts(apiKey: String): List<CatalogProduct> {
        requireValidKey(apiKey)
        return catalog.map { it.toCatalogProduct() }
    }

    override suspend fun placeOrder(
        apiKey: String,
        schemaName: String,
        order: Order,
        items: List<OrderItem>,
        deliveryAddress: String
    ): String {
        requireValidKey(apiKey)
        val restaurant = restaurants.firstOrNull { it.id == order.restaurantId }
            ?: throw IllegalArgumentException("Unknown restaurant: ${order.restaurantId}")
        items.forEach { item ->
            require(restaurant.menu.any { it.id == item.productId }) { "Unknown menu item: ${item.productId}" }
        }
        val externalOrderId = "${platform.name}-${UUID.randomUUID().toString().take(8).uppercase()}"
        logger.info("Emulated {} order {} accepted for order {} ({} items, deliver to: {})", platform, externalOrderId, order.id, items.size, deliveryAddress)
        simulate(schemaName, order.id, externalOrderId, restaurant.etaMinutes, LIFECYCLE)
        return externalOrderId
    }

    override suspend fun cancelOrder(apiKey: String, externalOrderId: String): Boolean {
        requireValidKey(apiKey)
        simulations.remove(externalOrderId)?.cancel()
        logger.info("Emulated {} order {} cancelled", platform, externalOrderId)
        return true
    }

    // Re-schedules the remaining status updates of an order that was already placed before a backend
    // restart (the in-memory timers are lost on restart; a real platform would simply keep sending webhooks).
    fun resume(schemaName: String, order: Order) {
        val externalOrderId = order.externalOrderId ?: return
        val restaurant = restaurants.firstOrNull { it.id == order.restaurantId } ?: return
        simulate(schemaName, order.id, externalOrderId, restaurant.etaMinutes, LIFECYCLE.filter { it.ordinal > order.status.ordinal })
    }

    private fun simulate(schemaName: String, orderId: String, externalOrderId: String, etaMinutes: Int, steps: List<OrderStatus>) {
        simulations[externalOrderId] = scope.launch {
            for (status in steps) {
                delay(stepMillis)
                try {
                    listener.onStatusChanged(schemaName, orderId, status, etaFor(status, etaMinutes))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.error("Failed to deliver {} status event for order {}", status, orderId, e)
                }
            }
            simulations.remove(externalOrderId)
        }
    }

    private fun etaFor(status: OrderStatus, restaurantEtaMinutes: Int): Int? = when (status) {
        OrderStatus.PREPARING -> restaurantEtaMinutes
        OrderStatus.OUT_FOR_DELIVERY -> (restaurantEtaMinutes / 2).coerceAtLeast(5)
        else -> null
    }

    private fun requireValidKey(apiKey: String) {
        if (apiKey.trim().length < MIN_API_KEY_LENGTH) throw ExternalServiceException("$platform rejected the API key")
    }

    private companion object {
        const val MIN_API_KEY_LENGTH = 8
        val LIFECYCLE = listOf(OrderStatus.PREPARING, OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED)
    }

    private fun FixtureRestaurant.toRestaurant() = Restaurant(
        id = id,
        platform = platform,
        name = name,
        cuisine = cuisine,
        rating = rating,
        deliveryFeeCents = deliveryFeeCents,
        etaMinutes = etaMinutes,
        minOrderCents = minOrderCents
    )

    private fun FixtureMenuItem.toMenuItem(restaurantId: String) = MenuItem(
        id = id,
        restaurantId = restaurantId,
        name = name,
        description = description,
        priceCents = priceCents,
        category = category
    )

    private fun FixtureCatalogProduct.toCatalogProduct() = CatalogProduct(
        id = id,
        platform = platform,
        name = name,
        description = description,
        category = ProductCategory.valueOf(category),
        priceCents = priceCents,
        brand = brand
    )
}

@Serializable
private class Fixture(val restaurants: List<FixtureRestaurant>)

@Serializable
private class FixtureRestaurant(
    val id: String,
    val name: String,
    val cuisine: String,
    val rating: Double,
    val deliveryFeeCents: Long,
    val etaMinutes: Int,
    val minOrderCents: Long? = null,
    val menu: List<FixtureMenuItem>
)

@Serializable
private class FixtureMenuItem(
    val id: String,
    val name: String,
    val description: String,
    val priceCents: Long,
    val category: String
)

@Serializable
private class CatalogFixture(val products: List<FixtureCatalogProduct>)

@Serializable
private class FixtureCatalogProduct(
    val id: String,
    val name: String,
    val brand: String? = null,
    val description: String,
    val category: String,
    val priceCents: Long
)
