package net.kustax.opendelivery.backend.routing

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.withContext
import net.kustax.opendelivery.backend.database.TenantContext
import net.kustax.opendelivery.backend.service.ForbiddenException
import net.kustax.opendelivery.backend.service.toOwnerResponse
import net.kustax.opendelivery.data.response.AdminOrderSummaryResponse
import net.kustax.opendelivery.data.response.OwnerWithStatsResponse
import net.kustax.opendelivery.domain.entity.platform.Owner
import net.kustax.opendelivery.domain.enum.OrderStatus
import net.kustax.opendelivery.domain.enum.UserRole
import net.kustax.opendelivery.domain.repository.DeviceRepository
import net.kustax.opendelivery.domain.repository.OrderRepository
import net.kustax.opendelivery.domain.repository.OwnerRepository
import net.kustax.opendelivery.domain.repository.ProductRepository
import net.kustax.opendelivery.domain.repository.PropertyRepository

fun Route.adminRoutes(
    ownerRepository: OwnerRepository,
    propertyRepository: PropertyRepository,
    deviceRepository: DeviceRepository,
    productRepository: ProductRepository,
    orderRepository: OrderRepository
) {
    authenticate("jwt") {
        get("/admin/owners/stats") {
            if (call.userRole() != UserRole.PLATFORM_ADMIN) throw ForbiddenException("Platform admin access required")

            val result = withEachOwnerSchema(ownerRepository) { owner ->
                val properties = propertyRepository.findAll()
                OwnerWithStatsResponse(
                    owner = owner.toOwnerResponse(),
                    propertyCount = properties.size,
                    deviceCount = properties.sumOf { property -> deviceRepository.findByPropertyId(property.id).size },
                    productCount = productRepository.findAll().size
                )
            }
            call.respond(HttpStatusCode.OK, result)
        }

        get("/admin/orders/summary") {
            if (call.userRole() != UserRole.PLATFORM_ADMIN) throw ForbiddenException("Platform admin access required")

            val activeStatuses = OrderStatus.entries.filter { it != OrderStatus.DELIVERED && it != OrderStatus.CANCELLED }
            val perOwnerCounts = withEachOwnerSchema(ownerRepository) {
                OrderStatus.entries.associateWith { status -> orderRepository.countByStatus(status) }
            }
            val countsByStatus = OrderStatus.entries.associate { status ->
                status.name to perOwnerCounts.sumOf { it[status] ?: 0L }.toInt()
            }
            val totalActiveOrders = activeStatuses.sumOf { countsByStatus[it.name] ?: 0 }
            call.respond(HttpStatusCode.OK, AdminOrderSummaryResponse(totalActiveOrders, countsByStatus))
        }
    }
}

private suspend fun <T> withEachOwnerSchema(
    ownerRepository: OwnerRepository,
    block: suspend (Owner) -> T
): List<T> = ownerRepository.findAll().map { owner ->
    withContext(TenantContext(owner.schemaName)) {
        block(owner)
    }
}
