package net.kustax.opendelivery.backend.routing

import io.ktor.http.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.auth.principal
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.kustax.opendelivery.backend.service.ForbiddenException
import net.kustax.opendelivery.backend.service.PaymentService
import net.kustax.opendelivery.data.request.InitiatePaymentRequest
import net.kustax.opendelivery.domain.enum.UserRole

fun Route.paymentRoutes(paymentService: PaymentService) {
    // Authenticated kiosk device route
    // The order must have been placed from the calling device (checked by PaymentService), so one kiosk
    // can neither pay for nor probe another kiosk's orders.
    post("/payments") {
        if (call.userRole() != UserRole.DEVICE) throw ForbiddenException("Device access required")
        val request = call.receive<InitiatePaymentRequest>()
        val deviceId = call.principal<JWTPrincipal>()?.subject ?: throw ForbiddenException("Missing device identity")
        val schema = call.tenantSchema() ?: ""
        call.withTenant {
            val response = paymentService.initiate(request, deviceId, schema)
            call.respond(HttpStatusCode.Created, response)
        }
    }

    get("/orders/{orderId}/payments") {
        if (call.userRole() != UserRole.OWNER) {
            throw ForbiddenException("Owner access required")
        }
        val orderId = call.parameters["orderId"]!!
        call.withTenant {
            val response = paymentService.findByOrderId(orderId)
            call.respond(HttpStatusCode.OK, response)
        }
    }
}
