package net.kustax.opendelivery.backend.routing

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.auth.principal
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.routing.*
import kotlinx.coroutines.withContext
import net.kustax.opendelivery.backend.database.TenantContext
import net.kustax.opendelivery.backend.service.DeviceService
import net.kustax.opendelivery.backend.service.ForbiddenException
import net.kustax.opendelivery.backend.service.OrderService
import net.kustax.opendelivery.data.request.ActivateDeviceRequest
import net.kustax.opendelivery.data.request.CreateDeviceRequest
import net.kustax.opendelivery.data.response.ErrorResponse
import net.kustax.opendelivery.domain.enum.UserRole

// Mounted outside the authenticate block in Routing.kt: the kiosk's public routes (resolve, activate), the device's
// authenticated ping and the owner's authenticated /devices/{id} routes must share one "/devices" route tree. As separate trees Ktor can match
// "resolve" against the authenticated {id} parameter route first, which answered 401 for the public resolve call.
fun Route.deviceRoutes(deviceService: DeviceService, orderService: OrderService) {
    authenticate("jwt") { propertyDeviceRoutes(deviceService) }

    route("/devices") {
        rateLimit(PublicRateLimit) { deviceResolveRoute(deviceService) }
        route("/{id}") {
            rateLimit(PublicRateLimit) { deviceActivateRoute(deviceService) }
            authenticate("jwt") {
                devicePingRoute(deviceService)
                ownerDeviceRoutes(deviceService, orderService)
            }
        }
    }
}

private fun Route.propertyDeviceRoutes(deviceService: DeviceService) {
    route("/properties/{propertyId}/devices") {
        post {
            if (call.userRole() != UserRole.OWNER && call.userRole() != UserRole.PLATFORM_ADMIN) {
                throw ForbiddenException("Owner access required")
            }
            val actorId = call.principal<JWTPrincipal>()?.subject ?: ""
            val actorRole = call.userRole().name
            val schema = call.tenantSchema() ?: ""
            val propertyId = call.parameters["propertyId"]!!
            val request = call.receive<CreateDeviceRequest>()
            call.withTenant {
                val response = deviceService.create(request, propertyId, actorId, actorRole, schema)
                call.respond(HttpStatusCode.Created, response)
            }
        }

        get {
            if (call.userRole() != UserRole.OWNER && call.userRole() != UserRole.PLATFORM_ADMIN) {
                throw ForbiddenException("Owner access required")
            }
            val propertyId = call.parameters["propertyId"]!!
            call.withTenant {
                val response = deviceService.findByPropertyId(propertyId)
                call.respond(HttpStatusCode.OK, response)
            }
        }
    }
}

private fun Route.ownerDeviceRoutes(deviceService: DeviceService, orderService: OrderService) {
    get {
        if (call.userRole() != UserRole.OWNER && call.userRole() != UserRole.PLATFORM_ADMIN) {
            throw ForbiddenException("Owner access required")
        }
        val id = call.parameters["id"]!!
        val schema = call.tenantSchema() ?: ""
        call.withTenant {
            val response = deviceService.findById(id, schema)
            call.respond(HttpStatusCode.OK, response)
        }
    }

    delete {
        if (call.userRole() != UserRole.OWNER && call.userRole() != UserRole.PLATFORM_ADMIN) {
            throw ForbiddenException("Owner access required")
        }
        val actorId = call.principal<JWTPrincipal>()?.subject ?: ""
        val actorRole = call.userRole().name
        val schema = call.tenantSchema() ?: ""
        val id = call.parameters["id"]!!
        call.withTenant {
            deviceService.delete(id, actorId, actorRole, schema)
            call.respond(HttpStatusCode.NoContent)
        }
    }

    patch("/deactivate") {
        if (call.userRole() != UserRole.OWNER && call.userRole() != UserRole.PLATFORM_ADMIN) {
            throw ForbiddenException("Owner access required")
        }
        val actorId = call.principal<JWTPrincipal>()?.subject ?: ""
        val actorRole = call.userRole().name
        val schema = call.tenantSchema() ?: ""
        val id = call.parameters["id"]!!
        call.withTenant {
            deviceService.deactivate(id, actorId, actorRole, schema)
            call.respond(HttpStatusCode.NoContent)
        }
    }

    patch("/reactivate") {
        if (call.userRole() != UserRole.OWNER && call.userRole() != UserRole.PLATFORM_ADMIN) {
            throw ForbiddenException("Owner access required")
        }
        val actorId = call.principal<JWTPrincipal>()?.subject ?: ""
        val actorRole = call.userRole().name
        val schema = call.tenantSchema() ?: ""
        val id = call.parameters["id"]!!
        call.withTenant {
            deviceService.reactivate(id, actorId, actorRole, schema)
            call.respond(HttpStatusCode.NoContent)
        }
    }

    post("/unregister") {
        if (call.userRole() != UserRole.OWNER && call.userRole() != UserRole.PLATFORM_ADMIN) {
            throw ForbiddenException("Owner access required")
        }
        val actorId = call.principal<JWTPrincipal>()?.subject ?: ""
        val actorRole = call.userRole().name
        val schema = call.tenantSchema() ?: ""
        val id = call.parameters["id"]!!
        call.withTenant {
            val response = deviceService.unregister(id, actorId, actorRole, schema)
            call.respond(HttpStatusCode.OK, response)
        }
    }

    get("/orders") {
        if (call.userRole() != UserRole.OWNER && call.userRole() != UserRole.PLATFORM_ADMIN) {
            throw ForbiddenException("Owner access required")
        }
        val id = call.parameters["id"]!!
        call.withTenant {
            val response = orderService.findByDeviceId(id)
            call.respond(HttpStatusCode.OK, response)
        }
    }
}

// Heartbeat: an activated kiosk reports that it is alive with its own device JWT, so only the device itself
// can update its last-seen time. withTenant also rejects a deactivated device, which the kiosk then notices.
private fun Route.devicePingRoute(deviceService: DeviceService) {
    patch("/ping") {
        if (call.userRole() != UserRole.DEVICE) throw ForbiddenException("Device access required")
        val id = call.parameters["id"]!!
        if (call.principal<JWTPrincipal>()?.subject != id) throw ForbiddenException("A device can only report for itself")
        call.withTenant { deviceService.updateLastSeen(id) }
        call.respond(HttpStatusCode.NoContent)
    }
}

// Mounted outside the authenticate block — the kiosk app self-registers using a QR-scanned activation code.
private fun Route.deviceActivateRoute(deviceService: DeviceService) {
    post("/activate") {
        val id = call.parameters["id"]!!
        val schema = call.request.queryParameters["schemaName"]
            ?: return@post call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(code = "BAD_REQUEST", message = "schemaName query parameter is required")
            )
        val request = call.receive<ActivateDeviceRequest>()
        withContext(TenantContext(schema)) {
            val response = deviceService.activate(id, request.activationCode, request.androidDeviceId, schema)
            call.respond(HttpStatusCode.OK, response)
        }
    }
}

// Mounted outside the authenticate block — the kiosk device doesn't have a token yet; this is how
// the manual-activation-code path discovers which tenant schema the code belongs to.
private fun Route.deviceResolveRoute(deviceService: DeviceService) {
    get("/resolve") {
        val code = call.request.queryParameters["code"]
            ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(code = "BAD_REQUEST", message = "code query parameter is required")
            )
        val response = deviceService.resolveByCode(code)
        call.respond(HttpStatusCode.OK, response)
    }
}
