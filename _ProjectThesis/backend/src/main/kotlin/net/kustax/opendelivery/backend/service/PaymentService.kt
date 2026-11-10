package net.kustax.opendelivery.backend.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kustax.opendelivery.backend.database.SqlState
import net.kustax.opendelivery.backend.repository.tenantQuery
import net.kustax.opendelivery.data.request.InitiatePaymentRequest
import net.kustax.opendelivery.data.response.PaymentResponse
import net.kustax.opendelivery.domain.entity.tenant.Order
import net.kustax.opendelivery.domain.entity.tenant.Payment
import net.kustax.opendelivery.domain.enum.CardEntryMode
import net.kustax.opendelivery.domain.enum.OrderStatus
import net.kustax.opendelivery.domain.enum.OrderType
import net.kustax.opendelivery.domain.enum.PaymentStatus
import net.kustax.opendelivery.domain.port.NotificationService
import net.kustax.opendelivery.domain.port.PaymentGateway
import net.kustax.opendelivery.domain.repository.OrderItemRepository
import net.kustax.opendelivery.domain.repository.OrderRepository
import net.kustax.opendelivery.domain.repository.PaymentRepository
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.slf4j.LoggerFactory
import java.util.UUID

class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val paymentGateway: PaymentGateway,
    private val notificationService: NotificationService,
    private val foodDeliveryService: FoodDeliveryService,
    private val refundService: RefundService
) {

    private val logger = LoggerFactory.getLogger(PaymentService::class.java)

    // Receipt emails run here so a slow or unreachable SMTP server never delays the kiosk's payment response.
    // SupervisorJob: one failed send can't cancel the scope for the next one.
    private val receiptScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun close() = receiptScope.cancel()

    // Records one card payment attempt made from the kiosk device deviceId. A decline is a normal outcome, not
    // an error: the FAILED attempt is stored and returned so the kiosk can show the reason and let the guest
    // retry. An approval confirms the order; the partial unique index payments_one_completed_per_order stays
    // as the database's last line of defence against a second approved payment for the same order.
    suspend fun initiate(request: InitiatePaymentRequest, deviceId: String, schemaName: String): PaymentResponse {
        val entryMode = enumValueOf<CardEntryMode>(request.entryMode)
        request.cardLast4?.let { require(it.length == 4 && it.all(Char::isDigit)) { "cardLast4 must be 4 digits" } }

        // One transaction around check -> authorize -> record. SELECT ... FOR UPDATE on the order row makes
        // concurrent payments for the same order queue up: the second one only gets past the lock after the
        // first has committed, then sees a COMPLETED payment / a non-PENDING order and is refused with 409
        // before it can reach the gateway. (The alternative, inserting a PENDING payment first, needs a
        // second write and a cleanup path when the process dies between the two.) The gateway call happens
        // while a pooled connection is held; acceptable for one short authorization per order.
        val (order, payment) = try {
            tenantQuery {
                val order = orderRepository.findByIdForUpdate(request.orderId)
                    ?: throw NotFoundException("Order not found: ${request.orderId}")
                if (order.deviceId != deviceId) throw ForbiddenException("Order does not belong to this device")
                if (paymentRepository.findByOrderId(order.id).any { it.status == PaymentStatus.COMPLETED }) {
                    throw ConflictException("Order already paid: ${order.id}")
                }
                if (order.status != OrderStatus.PENDING) {
                    throw ConflictException("Order is not awaiting payment: ${order.id}")
                }

                val authorization = paymentGateway.initiatePayment(order.id, order.totalAmount, entryMode, request.pinVerified)
                val payment = Payment(
                    id = UUID.randomUUID().toString(),
                    orderId = order.id,
                    entryMode = entryMode,
                    cardLast4 = request.cardLast4,
                    status = if (authorization.approved) PaymentStatus.COMPLETED else PaymentStatus.FAILED,
                    declineReason = authorization.declineReason,
                    amount = order.totalAmount,
                    providerTransactionId = authorization.transactionId,
                    createdAt = System.currentTimeMillis()
                )
                paymentRepository.create(payment)
                if (authorization.approved) {
                    checkNotNull(orderRepository.updateStatus(order.id, OrderStatus.CONFIRMED, expectedStatus = OrderStatus.PENDING)) {
                        "Order ${order.id} left PENDING while its row was locked"
                    }
                }
                order to payment
            }
        } catch (e: ExposedSQLException) {
            if (e.sqlState == SqlState.UNIQUE_VIOLATION) throw ConflictException("Order already paid: ${request.orderId}")
            throw e
        }

        if (payment.status != PaymentStatus.COMPLETED) return payment.toResponse()

        // The card has been charged: everything from here must run to completion even if the request is
        // cancelled, and nothing here may turn the response into an error.
        return withContext(NonCancellable) {
            val items = orderItemRepository.findByOrderId(order.id)
            if (order.type == OrderType.FOOD_DELIVERY) {
                try {
                    foodDeliveryService.placeOrder(order, items)
                } catch (e: Exception) {
                    logger.error("Placing paid food order {} with its platform failed", order.id, e)
                    return@withContext cancelAndRefund(order, payment, deviceId, schemaName)
                }
            }
            order.guestEmail?.let { email ->
                receiptScope.launch { notificationService.sendReceipt(email, order.id, order.totalAmount, order.deliveryFee, items) }
            }
            payment.toResponse()
        }
    }

    // The platform could not take an order that is already paid for: cancel the order, return the money and
    // leave a trail. The response is still the approved payment, plus the order outcome for the kiosk to show.
    private suspend fun cancelAndRefund(order: Order, payment: Payment, deviceId: String, schemaName: String): PaymentResponse {
        // Invariant: only the path whose compare-and-set moves the order to CANCELLED refunds it. A null result
        // means either a database error (the order stays CONFIRMED and the owner can cancel it manually once the
        // placement grace period in OrderService has passed) or that someone else already cancelled the order
        // and made the refund; refunding here as well would return the money twice.
        val cancelled = runCatching { orderRepository.updateStatus(order.id, OrderStatus.CANCELLED, expectedStatus = OrderStatus.CONFIRMED) }
            .onFailure { logger.error("Could not cancel order {} after its placement failed", order.id, it) }
            .getOrNull()
        if (cancelled == null) {
            val current = runCatching { orderRepository.findById(order.id) }.getOrNull()
            logger.warn("Order {} could not be placed and was not cancelled here (now {}); no refund made here", order.id, current?.status)
            return payment.toResponse(
                orderStatus = current?.status,
                message = if (current?.status == OrderStatus.CANCELLED) {
                    "Your order has been cancelled. Please contact the front desk if your refund does not appear."
                } else {
                    "The restaurant could not accept your order. Please contact the front desk about your refund."
                }
            )
        }

        val refunded = refundService.refund(payment, deviceId, "DEVICE", "PAYMENT_CANCELLED_PLACEMENT_FAILED", schemaName)
        logger.warn("Order {} cancelled because its delivery platform did not accept it", order.id)

        val message = if (refunded) {
            "The restaurant could not accept your order. It has been cancelled and your card will be refunded."
        } else {
            "The restaurant could not accept your order and it has been cancelled. Please contact the front desk about your refund."
        }
        return payment.toResponse(orderStatus = OrderStatus.CANCELLED, message = message)
    }

    suspend fun findByOrderId(orderId: String): List<PaymentResponse> =
        paymentRepository.findByOrderId(orderId).map { it.toResponse() }
}

private fun Payment.toResponse(orderStatus: OrderStatus? = null, message: String? = null) = PaymentResponse(
    id = id,
    orderId = orderId,
    entryMode = entryMode.name,
    cardLast4 = cardLast4,
    status = status.name,
    declineReason = declineReason,
    amount = amount,
    providerTransactionId = providerTransactionId,
    createdAt = createdAt,
    orderStatus = orderStatus?.name,
    message = message
)
