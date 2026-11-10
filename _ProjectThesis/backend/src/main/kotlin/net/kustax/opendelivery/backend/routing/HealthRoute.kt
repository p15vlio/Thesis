package net.kustax.opendelivery.backend.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(val status: String)

fun Route.healthRoute() {
    get("/health") {
        call.respond(HttpStatusCode.OK, HealthResponse("ok"))
    }
}
