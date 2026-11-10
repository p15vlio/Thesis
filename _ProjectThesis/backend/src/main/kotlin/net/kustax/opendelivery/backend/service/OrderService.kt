package net.kustax.opendelivery.backend.service

import java.util.UUID
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import net.kustax.opendelivery.backend.repository.tenantQuery
import net.kustax.opendelivery.data.request.CreateOrderRequest
import net.kustax.opendelivery.data.request.UpdateOrderStatusRequest
import net.kustax.opendelivery.data.response.OrderItemResponse
import net.kustax.opendelivery.data.response.OrderResponse
import net.kustax.opendelivery.domain.entity.platform.Restaurant
import net.kustax.opendelivery.domain.entity.tenant.Order
import net.kustax.opendelivery.domain.entity.tenant.OrderItem
import net.kustax.opendelivery.domain.enum.ExternalPlatform
import net.kustax.opendelivery.domain.enum.OrderStatus
import net.kustax.opendelivery.domain.enum.OrderType
import net.kustax.opendelivery.domain.enum.PaymentStatus
import net.kustax.opendelivery.domain.repository.OrderItemRepository
import net.kustax.opendelivery.domain.repository.OrderRepository
import net.kustax.opendelivery.domain.repository.PaymentRepository
import net.kustax.opendelivery.domain.repository.ProductPropertyOverrideRepository
import net.kustax.opendelivery.domain.repository.ProductRepository
import org.slf4j.LoggerFactory

class OrderService(
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val productRepository: ProductRepository,
    private val productPropertyOverrideRepository: ProductPropertyOverrideRepository,
    private val paymentRepository: PaymentRepository,
    private val foodDeliveryService: FoodDeliveryService,
    private val refundService: RefundService
) {

    private val logger = LoggerFactory.getLogger(OrderService::class.java)

    suspend fun create(request: CreateOrderRequest, deviceId: String, propertyId: String): OrderResponse {
        val orderId = UUID.randomUUID().toString()
        val orderType = enumValueOf<OrderType>(request.type)
        val now = System.currentTimeMillis()

        require(request.items.isNotEmpty()) { "An order needs at least one item" }
        request.items.forEach {
            require(it.quantity in 1..MAX_ITEM_QUANTITY) { "Quantity must be between 1 and $MAX_ITEM_QUANTITY" }
        }
        // Checked here so an over-long value is a 400 instead of a database error, and the receipt is only
        // ever sent to something shaped like an address
        val guestEmail = request.guestEmail?.trim()?.ifBlank { null }
        if (guestEmail != null) {
            require(guestEmail.length <= MAX_EMAIL_LENGTH && EMAIL_REGEX.matches(guestEmail)) { "A valid email address is required" }
        }
        require((request.notes?.length ?: 0) <= MAX_NOTES_LENGTH) { "Notes can be at most $MAX_NOTES_LENGTH characters" }

        // Food orders are priced from the platform menu, store orders from the owner catalog; either
        // way the client only ever supplies ids and quantities.
        val externalPlatform: ExternalPlatform?
        val restaurant: Restaurant?
        val orderItems: List<OrderItem>
        when (orderType) {
            OrderType.IN_ROOM_STORE -> {
                externalPlatform = null
                restaurant = null
                orderItems = request.items.map { itemRequest ->
                    val product = productRepository.findById(itemRequest.productId)
                        ?: throw NotFoundException("Product not found: ${itemRequest.productId}")
                    if (!product.isAvailable || productPropertyOverrideRepository.isDisabled(product.id, propertyId)) {
                        throw ConflictException("Product not available: ${itemRequest.productId}")
                    }
                    OrderItem(
                        id = UUID.randomUUID().toString(),
                        orderId = orderId,
                        productId = product.id,
                        productName = product.name,
                        unitPrice = product.basePrice,
                        quantity = itemRequest.quantity,
                        subtotal = product.basePrice * itemRequest.quantity
                    )
                }
            }
            OrderType.FOOD_DELIVERY -> {
                externalPlatform = enumValueOf<ExternalPlatform>(
                    requireNotNull(request.platform) { "platform is required for food delivery orders" }.uppercase()
                )
                val restaurantId = requireNotNull(request.restaurantId) { "restaurantId is required for food delivery orders" }
                val priced = foodDeliveryService.priceOrder(propertyId, externalPlatform, restaurantId, request.items)
                restaurant = priced.restaurant
                orderItems = priced.lines.map { (menuItem, quantity) ->
                    OrderItem(
                        id = UUID.randomUUID().toString(),
                        orderId = orderId,
                        productId = menuItem.id,
                        productName = menuItem.name,
                        unitPrice = menuItem.priceCents,
                        quantity = quantity,
                        subtotal = menuItem.priceCents * quantity
                    )
                }
            }
        }
        val deliveryFee = restaurant?.deliveryFeeCents ?: 0L
        val totalAmount = orderItems.sumOf { it.subtotal } + deliveryFee

        val order = Order(
            id = orderId,
            deviceId = deviceId,
            propertyId = propertyId,
            guestEmail = guestEmail,
            status = OrderStatus.PENDING,
            type = orderType,
            externalPlatform = externalPlatform,
            externalOrderId = null,
            restaurantId = restaurant?.id,
            restaurantName = restaurant?.name,
            deliveryFee = deliveryFee,
            estimatedDeliveryAt = null,
            totalAmount = totalAmount,
            notes = request.notes,
            createdAt = now,
            updatedAt = now
        )
        // The order insert and its items share one outer tenantQuery { } so all the nested repository calls
        // (each already wrapped in its own tenantQuery) join the same transaction — a failure anywhere in
        // here rolls back the whole sequence instead of leaving a partial/stuck order row. The order stays
        // PENDING until PaymentService records an approved card payment; a food order is only handed to its
        // platform once it is paid.
        tenantQuery {
            orderRepository.create(order)
            orderItems.forEach { orderItemRepository.insert(it) }
        }

        val createdOrder = orderRepository.findById(orderId)
            ?: throw NotFoundException("Order not found after creation: $orderId")
        val items = orderItemRepository.findByOrderId(orderId)
        return createdOrder.toResponse(items)
    }

    // requireDeviceId, when set, restricts the call to the order it was placed from — used by the
    // kiosk-facing /device/orders routes so one device can't read or cancel another device's orders.
    suspend fun updateStatus(
        id: String,
        request: UpdateOrderStatusRequest,
        actorId: String,
        actorRole: String,
        schemaName: String,
        requireDeviceId: String? = null
    ): OrderResponse {
        val order = orderRepository.findById(id) ?: throw NotFoundException("Order not found: $id")
        if (requireDeviceId != null && order.deviceId != requireDeviceId) {
            throw ForbiddenException("Order does not belong to this device")
        }
        val newStatus = enumValueOf<OrderStatus>(request.status)

        if (!isValidTransition(order.status, newStatus)) {
            throw ConflictException("Cannot transition from ${order.status} to $newStatus")
        }
        // The kiosk may only cancel an order that nobody has started on yet; once it is being prepared or is
        // on its way, cancelling (and refunding) is the owner's decision
        if (requireDeviceId != null && newStatus == OrderStatus.CANCELLED && order.status !in GUEST_CANCELLABLE) {
            throw ConflictException("This order is already being prepared and can no longer be cancelled here. Please contact your host.")
        }
        // The delivery platform drives a food order through its lifecycle; only cancelling is a manual action
        if (order.type == OrderType.FOOD_DELIVERY && newStatus != OrderStatus.CANCELLED) {
            throw ConflictException("Food delivery orders are advanced by the delivery platform")
        }
        // A paid food order without a platform order id is being placed right now (PaymentService is waiting on
        // the platform). Cancelling it here would refund while the platform may still accept the order, so the
        // cancel is refused until the id is recorded (the cancel then also cancels it on the platform) or the
        // placement fails (PaymentService cancels and refunds it itself). After PLACEMENT_GRACE_MS the placement is
        // taken as lost (e.g. a restart during the call) so the order can still be cancelled and refunded; if that
        // placement does complete later, assignExternalOrder refuses to record it and the platform order is taken back.
        // externalOrderId only ever goes from null to set, so a stale read can only make this check stricter.
        if (order.type == OrderType.FOOD_DELIVERY && newStatus == OrderStatus.CANCELLED &&
            order.status != OrderStatus.PENDING && order.externalOrderId == null &&
            System.currentTimeMillis() - order.updatedAt < PLACEMENT_GRACE_MS
        ) {
            throw ConflictException("The order is being placed with the delivery platform, try again in a moment")
        }

        // Status change, platform cancel and refund form one sequence that must not stop half-way because the
        // request was cancelled (the order would be CANCELLED with no refund and no audit entry), so it runs
        // in NonCancellable, like the post-payment steps in PaymentService.
        val changed = withContext(NonCancellable) {
            // Compare-and-set on the status we validated against: if the order moved on in the meantime (a
            // payment, a platform event, another cancel) nothing is written and the caller is told to reload.
            // This runs first, so a platform order is never cancelled while the local order stays live.
            val updated = orderRepository.updateStatus(id, newStatus, expectedStatus = order.status)
                ?: throw ConflictException("Order status changed in the meantime — reload and try again")
            if (order.type == OrderType.FOOD_DELIVERY) {
                try {
                    foodDeliveryService.cancelOrder(order)
                } catch (e: Exception) {
                    // The platform refused (or could not be reached), so the order is still live there and the
                    // restaurant may deliver it. Keep the previous rule: the cancellation fails as a whole, no
                    // refund is made and the order goes back to the status it had. If even that compare-and-set
                    // fails, the order stays CANCELLED without a refund and the owner must resolve it by hand.
                    val reverted = runCatching {
                        orderRepository.updateStatus(id, order.status, expectedStatus = OrderStatus.CANCELLED)
                    }.getOrNull()
                    if (reverted == null) {
                        logger.error("Order {} is CANCELLED locally but its platform cancellation failed; not refunded", id, e)
                    }
                    throw e
                }
            }
            // A paid order that is cancelled gets its money back (a PENDING order has no completed payment). Only
            // the caller whose compare-and-set above won gets here, and RefundService claims each payment with its
            // own compare-and-set, so a payment is never refunded twice.
            if (newStatus == OrderStatus.CANCELLED) {
                paymentRepository.findByOrderId(id).filter { it.status == PaymentStatus.COMPLETED }.forEach {
                    refundService.refund(it, actorId, actorRole, "PAYMENT_REFUNDED_ORDER_CANCELLED", schemaName)
                }
            }
            updated
        }
        val items = orderItemRepository.findByOrderId(id)
        return changed.toResponse(items)
    }

    suspend fun findById(id: String, requireDeviceId: String? = null): OrderResponse {
        val order = orderRepository.findById(id) ?: throw NotFoundException("Order not found: $id")
        if (requireDeviceId != null && order.deviceId != requireDeviceId) {
            throw ForbiddenException("Order does not belong to this device")
        }
        val items = orderItemRepository.findByOrderId(id)
        return order.toResponse(items)
    }

    suspend fun findByPropertyId(propertyId: String): List<OrderResponse> =
        orderRepository.findByPropertyId(propertyId).map { order ->
            val items = orderItemRepository.findByOrderId(order.id)
            order.toResponse(items)
        }

    suspend fun findByDeviceId(deviceId: String): List<OrderResponse> =
        orderRepository.findByDeviceId(deviceId).map { order ->
            val items = orderItemRepository.findByOrderId(order.id)
            order.toResponse(items)
        }

    suspend fun findByStatus(status: OrderStatus): List<OrderResponse> =
        orderRepository.findByStatus(status).map { order ->
            val items = orderItemRepository.findByOrderId(order.id)
            order.toResponse(items)
        }

    private fun isValidTransition(from: OrderStatus, to: OrderStatus): Boolean {
        val finalStates = setOf(OrderStatus.DELIVERED, OrderStatus.CANCELLED)
        if (from in finalStates) return false
        if (to == OrderStatus.CANCELLED) return true
        return when (from) {
            // PENDING means unpaid: only an approved card payment confirms an order, the owner can only cancel it
            OrderStatus.PENDING -> false
            OrderStatus.CONFIRMED -> to == OrderStatus.PREPARING
            OrderStatus.PREPARING -> to == OrderStatus.OUT_FOR_DELIVERY
            OrderStatus.OUT_FOR_DELIVERY -> to == OrderStatus.DELIVERED
            else -> false
        }
    }
}

private fun Order.toResponse(items: List<OrderItem>) = OrderResponse(
    id = id,
    deviceId = deviceId,
    propertyId = propertyId,
    guestEmail = guestEmail,
    status = status.name,
    type = type.name,
    externalPlatform = externalPlatform?.name,
    externalOrderId = externalOrderId,
    restaurantId = restaurantId,
    restaurantName = restaurantName,
    deliveryFee = deliveryFee,
    estimatedDeliveryAt = estimatedDeliveryAt,
    totalAmount = totalAmount,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
    items = items.map { it.toItemResponse() }
)

private fun OrderItem.toItemResponse() = OrderItemResponse(
    id = id,
    orderId = orderId,
    productId = productId,
    productName = productName,
    unitPrice = unitPrice,
    quantity = quantity,
    subtotal = subtotal
)

// Statuses in which the guest can still cancel from the kiosk
private val GUEST_CANCELLABLE = setOf(OrderStatus.PENDING, OrderStatus.CONFIRMED)

// How long a paid food order may wait for its platform order id before a cancel is allowed again; far longer
// than any placement call takes
private const val PLACEMENT_GRACE_MS = 5 * 60_000L

// Per line, so a kiosk can't submit an absurd quantity; well above anything a guest orders for a room
private const val MAX_ITEM_QUANTITY = 99

// The sizes of the guest_email and notes columns
private const val MAX_EMAIL_LENGTH = 255
private const val MAX_NOTES_LENGTH = 1024
