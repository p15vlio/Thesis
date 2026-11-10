package net.kustax.opendelivery.backend.routing

import io.ktor.http.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.auth.principal
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.kustax.opendelivery.backend.service.ForbiddenException
import net.kustax.opendelivery.backend.service.ProductService
import net.kustax.opendelivery.data.request.CreateProductRequest
import net.kustax.opendelivery.data.request.ImportProductsRequest
import net.kustax.opendelivery.data.request.SetProductAvailabilityRequest
import net.kustax.opendelivery.data.request.UpdateProductRequest
import net.kustax.opendelivery.domain.enum.UserRole

fun Route.productRoutes(productService: ProductService) {
    route("/products") {
        post {
            if (call.userRole() != UserRole.OWNER && call.userRole() != UserRole.PLATFORM_ADMIN) {
                throw ForbiddenException("Owner access required")
            }
            val request = call.receive<CreateProductRequest>()
            call.withTenant {
                val response = productService.create(request)
                call.respond(HttpStatusCode.Created, response)
            }
        }

        post("/import") {
            if (call.userRole() != UserRole.OWNER && call.userRole() != UserRole.PLATFORM_ADMIN) {
                throw ForbiddenException("Owner access required")
            }
            val actorId = call.principal<JWTPrincipal>()?.subject ?: ""
            val actorRole = call.userRole().name
            val schema = call.tenantSchema() ?: ""
            val request = call.receive<ImportProductsRequest>()
            call.withTenant {
                call.respond(HttpStatusCode.OK, productService.importFromPlatform(request, actorId, actorRole, schema))
            }
        }

        get {
            call.withTenant {
                val response = if (call.userRole() == UserRole.DEVICE) {
                    val device = currentTenantDevice() ?: throw ForbiddenException("Missing device identity")
                    productService.findAvailableForProperty(device.propertyId)
                } else {
                    productService.findAll()
                }
                call.respond(HttpStatusCode.OK, response)
            }
        }

        get("/{id}") {
            val id = call.parameters["id"]!!
            call.withTenant {
                val response = productService.findById(id)
                call.respond(HttpStatusCode.OK, response)
            }
        }

        put("/{id}") {
            if (call.userRole() != UserRole.OWNER && call.userRole() != UserRole.PLATFORM_ADMIN) {
                throw ForbiddenException("Owner access required")
            }
            val id = call.parameters["id"]!!
            val request = call.receive<UpdateProductRequest>()
            call.withTenant {
                val response = productService.update(id, request)
                call.respond(HttpStatusCode.OK, response)
            }
        }

        patch("/{id}/availability") {
            if (call.userRole() != UserRole.OWNER && call.userRole() != UserRole.PLATFORM_ADMIN) {
                throw ForbiddenException("Owner access required")
            }
            val id = call.parameters["id"]!!
            val request = call.receive<UpdateProductRequest>()
            call.withTenant {
                val response = productService.toggleAvailability(id, request.isAvailable)
                call.respond(HttpStatusCode.OK, response)
            }
        }

        patch("/{id}/properties/{propertyId}/availability") {
            if (call.userRole() != UserRole.OWNER && call.userRole() != UserRole.PLATFORM_ADMIN) {
                throw ForbiddenException("Owner access required")
            }
            val actorId = call.principal<JWTPrincipal>()?.subject ?: ""
            val actorRole = call.userRole().name
            val schema = call.tenantSchema() ?: ""
            val id = call.parameters["id"]!!
            val propertyId = call.parameters["propertyId"]!!
            val request = call.receive<SetProductAvailabilityRequest>()
            call.withTenant {
                val response = productService.setPropertyAvailability(id, propertyId, request.isAvailable, actorId, actorRole, schema)
                call.respond(HttpStatusCode.OK, response)
            }
        }

        delete("/{id}") {
            if (call.userRole() != UserRole.OWNER && call.userRole() != UserRole.PLATFORM_ADMIN) {
                throw ForbiddenException("Owner access required")
            }
            val actorId = call.principal<JWTPrincipal>()?.subject ?: ""
            val actorRole = call.userRole().name
            val schema = call.tenantSchema() ?: ""
            val id = call.parameters["id"]!!
            call.withTenant {
                productService.delete(id, actorId, actorRole, schema)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
