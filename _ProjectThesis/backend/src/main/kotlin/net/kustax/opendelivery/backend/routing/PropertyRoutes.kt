package net.kustax.opendelivery.backend.routing

import io.ktor.http.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.auth.principal
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.kustax.opendelivery.backend.service.ForbiddenException
import net.kustax.opendelivery.backend.service.PropertyService
import net.kustax.opendelivery.data.request.CreatePropertyRequest
import net.kustax.opendelivery.data.request.UpdatePropertyRequest
import net.kustax.opendelivery.domain.enum.UserRole

fun Route.propertyRoutes(propertyService: PropertyService) {
    route("/properties") {
        post {
            if (call.userRole() != UserRole.OWNER && call.userRole() != UserRole.PLATFORM_ADMIN) {
                throw ForbiddenException("Owner access required")
            }
            val actorId = call.principal<JWTPrincipal>()?.subject ?: ""
            val actorRole = call.userRole().name
            val schema = call.tenantSchema() ?: ""
            val request = call.receive<CreatePropertyRequest>()
            call.withTenant {
                val response = propertyService.create(request, actorId, actorRole, schema)
                call.respond(HttpStatusCode.Created, response)
            }
        }

        get {
            if (call.userRole() != UserRole.OWNER && call.userRole() != UserRole.PLATFORM_ADMIN) {
                throw ForbiddenException("Owner access required")
            }
            call.withTenant {
                val response = propertyService.findAll()
                call.respond(HttpStatusCode.OK, response)
            }
        }

        get("/{id}") {
            val role = call.userRole()
            if (role != UserRole.OWNER && role != UserRole.PLATFORM_ADMIN && role != UserRole.DEVICE) {
                throw ForbiddenException("Owner access required")
            }
            val id = call.parameters["id"]!!
            call.withTenant {
                if (call.userRole() == UserRole.DEVICE) {
                    val device = currentTenantDevice()
                    if (device?.propertyId != id) {
                        throw ForbiddenException("Device access required to own property")
                    }
                }
                val response = propertyService.findById(id)
                call.respond(HttpStatusCode.OK, response)
            }
        }

        put("/{id}") {
            if (call.userRole() != UserRole.OWNER && call.userRole() != UserRole.PLATFORM_ADMIN) {
                throw ForbiddenException("Owner access required")
            }
            val actorId = call.principal<JWTPrincipal>()?.subject ?: ""
            val actorRole = call.userRole().name
            val schema = call.tenantSchema() ?: ""
            val id = call.parameters["id"]!!
            val request = call.receive<UpdatePropertyRequest>()
            call.withTenant {
                val response = propertyService.update(id, request, actorId, actorRole, schema)
                call.respond(HttpStatusCode.OK, response)
            }
        }

        patch("/{id}/deactivate") {
            if (call.userRole() != UserRole.OWNER && call.userRole() != UserRole.PLATFORM_ADMIN) {
                throw ForbiddenException("Owner access required")
            }
            val actorId = call.principal<JWTPrincipal>()?.subject ?: ""
            val actorRole = call.userRole().name
            val schema = call.tenantSchema() ?: ""
            val id = call.parameters["id"]!!
            call.withTenant {
                propertyService.deactivate(id, actorId, actorRole, schema)
                call.respond(HttpStatusCode.NoContent)
            }
        }

        patch("/{id}/activate") {
            if (call.userRole() != UserRole.OWNER && call.userRole() != UserRole.PLATFORM_ADMIN) {
                throw ForbiddenException("Owner access required")
            }
            val actorId = call.principal<JWTPrincipal>()?.subject ?: ""
            val actorRole = call.userRole().name
            val schema = call.tenantSchema() ?: ""
            val id = call.parameters["id"]!!
            call.withTenant {
                propertyService.activate(id, actorId, actorRole, schema)
                call.respond(HttpStatusCode.NoContent)
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
                propertyService.delete(id, actorId, actorRole, schema)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
