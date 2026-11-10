package net.kustax.opendelivery.backend.routing

import io.ktor.http.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.auth.principal
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.kustax.opendelivery.backend.service.ForbiddenException
import net.kustax.opendelivery.backend.service.OrderService
import net.kustax.opendelivery.data.request.UpdateOrderStatusRequest
import net.kustax.opendelivery.data.request.CreateOrderRequest
import net.kustax.opendelivery.data.response.ErrorResponse
import net.kustax.opendelivery.domain.enum.OrderStatus
import net.kustax.opendelivery.domain.enum.UserRole

fun Route.orderRoutes(orderService: OrderService) {
    route("/orders") {
        // Kiosk devices post orders with their own device JWT (mounted inside authenticate("jwt")).
        // deviceId comes from the JWT subject, schemaName from the JWT claim (via withTenant) — not
        // client-supplied query params, since the JWT is the trustworthy source for both.
        post {
            if (call.userRole() != UserRole.DEVICE) throw ForbiddenException("Device access required")
            val propertyId = call.request.queryParameters["propertyId"]
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(code = "BAD_REQUEST", message = "propertyId query parameter is required")
                )
            val deviceId = call.principal<JWTPrincipal>()?.subject
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(code = "BAD_REQUEST", message = "Missing device identity")
                )
            val request = call.receive<CreateOrderRequest>()
            call.withTenant {
                // The property decides which food platforms may be ordered from, so it must be the device's own
                if (currentTenantDevice()?.propertyId != propertyId) {
                    throw ForbiddenException("Device access required to own property")
                }
                val response = orderService.create(request, deviceId, propertyId)
                call.respond(HttpStatusCode.Created, response)
            }
        }

        get {
            if (call.userRole() != UserRole.OWNER && call.userRole() != UserRole.PLATFORM_ADMIN) {
                throw ForbiddenException("Owner access required")
            }
            val propertyId = call.request.queryParameters["propertyId"]
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(code = "BAD_REQUEST", message = "propertyId query parameter is required")
                )
            call.withTenant {
                val statusParam = call.request.queryParameters["status"]
                val response = if (statusParam != null) {
                    orderService.findByStatus(enumValueOf<OrderStatus>(statusParam)).filter { it.propertyId == propertyId }
                } else {
                    orderService.findByPropertyId(propertyId)
                }
                call.respond(HttpStatusCode.OK, response)
            }
        }

        get("/{id}") {
            if (call.userRole() != UserRole.OWNER && call.userRole() != UserRole.PLATFORM_ADMIN) {
                throw ForbiddenException("Owner access required")
            }
            val id = call.parameters["id"]!!
            call.withTenant {
                val response = orderService.findById(id)
                call.respond(HttpStatusCode.OK, response)
            }
        }

        patch("/{id}/status") {
            if (call.userRole() != UserRole.OWNER && call.userRole() != UserRole.PLATFORM_ADMIN) {
                throw ForbiddenException("Owner access required")
            }
            val id = call.parameters["id"]!!
            val request = call.receive<UpdateOrderStatusRequest>()
            val actorId = call.principal<JWTPrincipal>()?.subject ?: ""
            val actorRole = call.userRole().name
            val schema = call.tenantSchema() ?: ""
            call.withTenant {
                val response = orderService.updateStatus(id, request, actorId, actorRole, schema)
                call.respond(HttpStatusCode.OK, response)
            }
        }
    }
}

// Mounted inside authenticate("jwt") — the kiosk device authenticates with its own JWT (minted at
// activation), so schemaName is resolved from the token claim via withTenant, not a query param.
fun Route.deviceOrderRoutes(orderService: OrderService) {
    route("/device/orders") {
        get("/{id}") {
            val id = call.parameters["id"]!!
            val deviceId = call.principal<JWTPrincipal>()?.subject
            call.withTenant {
                val response = orderService.findById(id, requireDeviceId = deviceId)
                call.respond(HttpStatusCode.OK, response)
            }
        }

        patch("/{id}/cancel") {
            val id = call.parameters["id"]!!
            val deviceId = call.principal<JWTPrincipal>()?.subject
            val schema = call.tenantSchema() ?: ""
            call.withTenant {
                val response = orderService.updateStatus(
                    id,
                    UpdateOrderStatusRequest(status = "CANCELLED"),
                    deviceId ?: "",
                    UserRole.DEVICE.name,
                    schema,
                    requireDeviceId = deviceId
                )
                call.respond(HttpStatusCode.OK, response)
            }
        }
    }
}
