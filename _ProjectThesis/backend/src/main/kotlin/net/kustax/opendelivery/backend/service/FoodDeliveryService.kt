package net.kustax.opendelivery.backend.service

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import net.kustax.opendelivery.backend.database.TenantContext
import net.kustax.opendelivery.backend.security.SecretCipher
import net.kustax.opendelivery.data.request.OrderItemRequest
import net.kustax.opendelivery.data.response.CatalogProductResponse
import net.kustax.opendelivery.data.response.FoodMenuItemResponse
import net.kustax.opendelivery.data.response.FoodPlatformResponse
import net.kustax.opendelivery.data.response.FoodRestaurantResponse
import net.kustax.opendelivery.data.response.PropertyFoodPlatformsResponse
import net.kustax.opendelivery.domain.entity.platform.CatalogProduct
import net.kustax.opendelivery.domain.entity.platform.MenuItem
import net.kustax.opendelivery.domain.entity.platform.Restaurant
import net.kustax.opendelivery.domain.entity.tenant.Order
import net.kustax.opendelivery.domain.entity.tenant.OrderItem
import net.kustax.opendelivery.domain.enum.ExternalPlatform
import net.kustax.opendelivery.domain.port.FoodDeliveryProvider
import net.kustax.opendelivery.domain.repository.FoodPlatformConnectionRepository
import net.kustax.opendelivery.domain.repository.OrderRepository
import net.kustax.opendelivery.domain.repository.PropertyFoodPlatformRepository
import net.kustax.opendelivery.domain.repository.PropertyRepository
import org.slf4j.LoggerFactory

// A restaurant order priced from the platform's own menu: each line is a menu item and its quantity.
class PricedFoodOrder(val restaurant: Restaurant, val lines: List<Pair<MenuItem, Int>>)

// Owner-level platform connections (API key), per-property availability, and everything a kiosk needs
// to browse and order from a connected platform. Only talks to the FoodDeliveryProvider port.
class FoodDeliveryService(
    providers: List<FoodDeliveryProvider>,
    private val connectionRepository: FoodPlatformConnectionRepository,
    private val propertyPlatformRepository: PropertyFoodPlatformRepository,
    private val propertyRepository: PropertyRepository,
    private val orderRepository: OrderRepository,
    private val auditLogService: AuditLogService
) {

    private val logger = LoggerFactory.getLogger(FoodDeliveryService::class.java)
    private val providers = providers.associateBy { it.platform }

    private fun provider(platform: ExternalPlatform): FoodDeliveryProvider = providers.getValue(platform)

    suspend fun listPlatforms(): List<FoodPlatformResponse> {
        val connections = connectionRepository.findAll().associateBy { it.platform }
        return ExternalPlatform.entries.map { platform ->
            val connection = connections[platform]
            FoodPlatformResponse(platform.name, connection != null, connection?.connectedAt)
        }
    }

    suspend fun connect(platform: ExternalPlatform, apiKey: String, actorId: String, actorRole: String, schemaName: String): FoodPlatformResponse {
        if (!provider(platform).verifyApiKey(apiKey)) {
            throw IllegalArgumentException("${platform.name} rejected the API key")
        }
        val now = System.currentTimeMillis()
        connectionRepository.save(platform, SecretCipher.encrypt(apiKey.trim()), now)
        auditLogService.log(actorId, actorRole, "CONNECT_FOOD_PLATFORM", "FoodPlatform", platform.name, schemaName)
        return FoodPlatformResponse(platform.name, true, now)
    }

    suspend fun disconnect(platform: ExternalPlatform, actorId: String, actorRole: String, schemaName: String) {
        connectionRepository.findByPlatform(platform) ?: throw NotFoundException("${platform.name} is not connected")
        // The platform key is needed to cancel an order that is still being delivered
        if (orderRepository.findInFlightFoodOrders().any { it.externalPlatform == platform }) {
            throw ConflictException("Cannot disconnect ${platform.name} while it has orders in progress")
        }
        propertyPlatformRepository.deleteForPlatform(platform)
        connectionRepository.delete(platform)
        auditLogService.log(actorId, actorRole, "DISCONNECT_FOOD_PLATFORM", "FoodPlatform", platform.name, schemaName)
    }

    suspend fun findPropertyPlatforms(propertyId: String): PropertyFoodPlatformsResponse {
        propertyRepository.findById(propertyId) ?: throw NotFoundException("Property not found: $propertyId")
        return PropertyFoodPlatformsResponse(propertyPlatformRepository.findPlatforms(propertyId).map { it.name }.sorted())
    }

    suspend fun setPropertyPlatforms(
        propertyId: String,
        platforms: List<ExternalPlatform>,
        actorId: String,
        actorRole: String,
        schemaName: String
    ): PropertyFoodPlatformsResponse {
        propertyRepository.findById(propertyId) ?: throw NotFoundException("Property not found: $propertyId")
        val connected = connectionRepository.findAll().map { it.platform }.toSet()
        platforms.firstOrNull { it !in connected }?.let {
            throw IllegalArgumentException("${it.name} is not connected — connect it under Food delivery first")
        }
        propertyPlatformRepository.replace(propertyId, platforms.toSet())
        auditLogService.log(
            actorId, actorRole, "SET_PROPERTY_FOOD_PLATFORMS", "Property", propertyId, schemaName,
            platforms.joinToString(",") { it.name }
        )
        return PropertyFoodPlatformsResponse(platforms.map { it.name }.distinct().sorted())
    }

    // Kiosk-facing: restaurants of every platform that is both connected for the owner and enabled at the property
    suspend fun listRestaurants(propertyId: String): List<FoodRestaurantResponse> {
        val enabled = propertyPlatformRepository.findPlatforms(propertyId)
        return connectionRepository.findAll()
            .filter { it.platform in enabled }
            .flatMap { connection ->
                provider(connection.platform).listRestaurants(SecretCipher.decrypt(connection.encryptedApiKey))
                    .map { it.toResponse() }
            }
    }

    suspend fun getMenu(propertyId: String, platform: ExternalPlatform, restaurantId: String): List<FoodMenuItemResponse> {
        val apiKey = availableApiKey(propertyId, platform)
        findRestaurant(platform, apiKey, restaurantId)
        return provider(platform).getMenu(apiKey, restaurantId).map { it.toResponse() }
    }

    // Owner-facing: the retail catalog of a platform the owner has connected
    suspend fun listCatalog(platform: ExternalPlatform): List<CatalogProductResponse> =
        catalogProducts(platform).map { it.toResponse() }

    suspend fun catalogProducts(platform: ExternalPlatform): List<CatalogProduct> {
        val connection = connectionRepository.findByPlatform(platform)
            ?: throw ForbiddenException("${platform.name} is not connected")
        return provider(platform).listCatalogProducts(SecretCipher.decrypt(connection.encryptedApiKey))
    }

    // Prices an order from the platform's menu — client-supplied prices never reach this point (OrderService has
    // already checked that the items and quantities are sane). The delivery fee of the restaurant is added by
    // the caller.
    suspend fun priceOrder(propertyId: String, platform: ExternalPlatform, restaurantId: String, items: List<OrderItemRequest>): PricedFoodOrder {
        val apiKey = availableApiKey(propertyId, platform)
        val restaurant = findRestaurant(platform, apiKey, restaurantId)
        val menu = provider(platform).getMenu(apiKey, restaurantId).associateBy { it.id }
        val lines = items.map { item ->
            val menuItem = menu[item.productId] ?: throw NotFoundException("Menu item not found: ${item.productId}")
            menuItem to item.quantity
        }
        val subtotal = lines.sumOf { (menuItem, quantity) -> menuItem.priceCents * quantity }
        restaurant.minOrderCents?.let {
            require(subtotal >= it) { "${restaurant.name} requires a minimum order of ${formatEuros(it)}" }
        }
        return PricedFoodOrder(restaurant, lines)
    }

    // Called once a FOOD_DELIVERY order is paid. From here on the platform drives the order's status.
    suspend fun placeOrder(order: Order, items: List<OrderItem>) {
        val platform = order.externalPlatform ?: return
        val schemaName = currentCoroutineContext()[TenantContext]?.schemaName
            ?: error("TenantContext not set in coroutine context")
        val connection = connectionRepository.findByPlatform(platform)
            ?: throw ConflictException("${platform.name} is no longer connected")
        val property = propertyRepository.findById(order.propertyId)
            ?: throw NotFoundException("Property not found: ${order.propertyId}")
        val deliveryAddress = "${property.streetName} ${property.streetNo}, ${property.postalCode} ${property.city}, " +
            "level ${property.level}, doorbell ${property.nameOnDoorbell}, tel ${property.contactPhone}"
        val externalOrderId = provider(platform).placeOrder(
            SecretCipher.decrypt(connection.encryptedApiKey), schemaName, order, items, deliveryAddress
        )
        // Invariant: no live platform order exists for a local order that is not recorded with it. If the id
        // can't be stored (database error) or the local order was cancelled while the platform call was running,
        // nobody could ever track or cancel the platform order, so it is taken back (best effort) before the
        // failure is surfaced; PaymentService then cancels and refunds only if the order is still CONFIRMED.
        val recorded = try {
            orderRepository.assignExternalOrder(order.id, externalOrderId)
        } catch (e: Exception) {
            takeBack(platform, connection.encryptedApiKey, externalOrderId)
            throw e
        }
        if (!recorded) {
            takeBack(platform, connection.encryptedApiKey, externalOrderId)
            throw ConflictException("Order ${order.id} was cancelled while it was being placed with ${platform.name}")
        }
    }

    private suspend fun takeBack(platform: ExternalPlatform, encryptedApiKey: String, externalOrderId: String) {
        withContext(NonCancellable) {
            runCatching { provider(platform).cancelOrder(SecretCipher.decrypt(encryptedApiKey), externalOrderId) }
                .onSuccess { if (!it) logger.error("{} refused to cancel unrecorded order {}", platform, externalOrderId) }
                .onFailure { logger.error("Could not cancel unrecorded {} order {}", platform, externalOrderId, it) }
        }
    }

    // An order that was never placed (not paid yet) has nothing to cancel on the platform
    suspend fun cancelOrder(order: Order) {
        val platform = order.externalPlatform ?: return
        val externalOrderId = order.externalOrderId ?: return
        val connection = connectionRepository.findByPlatform(platform)
            ?: throw ConflictException("${platform.name} is no longer connected")
        if (!provider(platform).cancelOrder(SecretCipher.decrypt(connection.encryptedApiKey), externalOrderId)) {
            throw ConflictException("${platform.name} did not accept the cancellation")
        }
    }

    private suspend fun availableApiKey(propertyId: String, platform: ExternalPlatform): String {
        val connection = connectionRepository.findByPlatform(platform)
        if (connection == null || platform !in propertyPlatformRepository.findPlatforms(propertyId)) {
            throw ForbiddenException("${platform.name} is not available at this property")
        }
        return SecretCipher.decrypt(connection.encryptedApiKey)
    }

    private suspend fun findRestaurant(platform: ExternalPlatform, apiKey: String, restaurantId: String): Restaurant =
        provider(platform).listRestaurants(apiKey).firstOrNull { it.id == restaurantId }
            ?: throw NotFoundException("Restaurant not found: $restaurantId")

    private fun formatEuros(cents: Long) = "€${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
}

private fun Restaurant.toResponse() = FoodRestaurantResponse(
    platform = platform.name,
    id = id,
    name = name,
    cuisine = cuisine,
    rating = rating,
    deliveryFeeCents = deliveryFeeCents,
    etaMinutes = etaMinutes,
    minOrderCents = minOrderCents
)

private fun MenuItem.toResponse() = FoodMenuItemResponse(
    id = id,
    restaurantId = restaurantId,
    name = name,
    description = description,
    priceCents = priceCents,
    category = category
)

private fun CatalogProduct.toResponse() = CatalogProductResponse(
    id = id,
    platform = platform.name,
    name = name,
    description = description,
    category = category.name,
    priceCents = priceCents,
    brand = brand
)
