package net.kustax.opendelivery.backend.repository.tenant

import net.kustax.opendelivery.backend.database.table.tenant.OrderItemsTable
import net.kustax.opendelivery.backend.repository.tenantQuery
import net.kustax.opendelivery.domain.entity.tenant.OrderItem
import net.kustax.opendelivery.domain.repository.OrderItemRepository
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*

class ExposedOrderItemRepository : OrderItemRepository {

    override suspend fun insert(item: OrderItem): Unit = tenantQuery {
        OrderItemsTable.insert {
            it[id] = item.id
            it[orderId] = item.orderId
            it[productId] = item.productId
            it[productName] = item.productName
            it[unitPrice] = item.unitPrice
            it[quantity] = item.quantity
            it[subtotal] = item.subtotal
        }
    }

    override suspend fun findByOrderId(orderId: String): List<OrderItem> = tenantQuery {
        OrderItemsTable.selectAll()
            .where { OrderItemsTable.orderId eq orderId }
            .map { it.toOrderItem() }
    }
}

private fun ResultRow.toOrderItem() = OrderItem(
    id = this[OrderItemsTable.id],
    orderId = this[OrderItemsTable.orderId],
    productId = this[OrderItemsTable.productId],
    productName = this[OrderItemsTable.productName],
    unitPrice = this[OrderItemsTable.unitPrice],
    quantity = this[OrderItemsTable.quantity],
    subtotal = this[OrderItemsTable.subtotal]
)
