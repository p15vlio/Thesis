package net.kustax.opendelivery.backend.routing

import io.ktor.http.*
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.*
import io.ktor.server.auth.principal
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.kustax.opendelivery.backend.service.FoodDeliveryService
import net.kustax.opendelivery.backend.service.ForbiddenException
import net.kustax.opendelivery.data.request.ConnectFoodPlatformRequest
import net.kustax.opendelivery.data.request.SetPropertyFoodPlatformsRequest
import net.kustax.opendelivery.domain.enum.ExternalPlatform
import net.kustax.opendelivery.domain.enum.UserRole

private fun ApplicationCall.requireOwnerOrAdmin() {
    if (userRole() != UserRole.OWNER && userRole() != UserRole.PLATFORM_ADMIN) {
        throw ForbiddenException("Owner access required")
    }
}

private fun ApplicationCall.platformParam(): ExternalPlatform =
    enumValueOf<ExternalPlatform>(parameters["platform"]!!.uppercase())

// Owner-facing: connect platforms (API key) and choose which of them each property offers
fun Route.foodPlatformRoutes(foodDeliveryService: FoodDeliveryService) {
    route("/food-platforms") {
        get {
            call.requireOwnerOrAdmin()
            call.withTenant {
                call.respond(HttpStatusCode.OK, foodDeliveryService.listPlatforms())
            }
        }

        get("/{platform}/catalog") {
            call.requireOwnerOrAdmin()
            val platform = call.platformParam()
            call.withTenant {
                call.respond(HttpStatusCode.OK, foodDeliveryService.listCatalog(platform))
            }
        }

        put("/{platform}") {
            call.requireOwnerOrAdmin()
            val actorId = call.principal<JWTPrincipal>()?.subject ?: ""
            val actorRole = call.userRole().name
            val schema = call.tenantSchema() ?: ""
            val platform = call.platformParam()
            val request = call.receive<ConnectFoodPlatformRequest>()
            call.withTenant {
                val response = foodDeliveryService.connect(platform, request.apiKey, actorId, actorRole, schema)
                call.respond(HttpStatusCode.OK, response)
            }
        }

        delete("/{platform}") {
            call.requireOwnerOrAdmin()
            val actorId = call.principal<JWTPrincipal>()?.subject ?: ""
            val actorRole = call.userRole().name
            val schema = call.tenantSchema() ?: ""
            val platform = call.platformParam()
            call.withTenant {
                foodDeliveryService.disconnect(platform, actorId, actorRole, schema)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }

    route("/properties/{id}/food-platforms") {
        get {
            call.requireOwnerOrAdmin()
            val id = call.parameters["id"]!!
            call.withTenant {
                call.respond(HttpStatusCode.OK, foodDeliveryService.findPropertyPlatforms(id))
            }
        }

        put {
            call.requireOwnerOrAdmin()
            val actorId = call.principal<JWTPrincipal>()?.subject ?: ""
            val actorRole = call.userRole().name
            val schema = call.tenantSchema() ?: ""
            val id = call.parameters["id"]!!
            val request = call.receive<SetPropertyFoodPlatformsRequest>()
            val platforms = request.platforms.map { enumValueOf<ExternalPlatform>(it.uppercase()) }
            call.withTenant {
                val response = foodDeliveryService.setPropertyPlatforms(id, platforms, actorId, actorRole, schema)
                call.respond(HttpStatusCode.OK, response)
            }
        }
    }
}

// Kiosk-facing: the device's property decides which platforms are offered
fun Route.foodCatalogRoutes(foodDeliveryService: FoodDeliveryService) {
    route("/food/restaurants") {
        get {
            if (call.userRole() != UserRole.DEVICE) throw ForbiddenException("Device access required")
            call.withTenant {
                val propertyId = currentTenantDevice()!!.propertyId
                call.respond(HttpStatusCode.OK, foodDeliveryService.listRestaurants(propertyId))
            }
        }

        get("/{platform}/{restaurantId}/menu") {
            if (call.userRole() != UserRole.DEVICE) throw ForbiddenException("Device access required")
            val platform = call.platformParam()
            val restaurantId = call.parameters["restaurantId"]!!
            call.withTenant {
                val propertyId = currentTenantDevice()!!.propertyId
                call.respond(HttpStatusCode.OK, foodDeliveryService.getMenu(propertyId, platform, restaurantId))
            }
        }
    }
}
