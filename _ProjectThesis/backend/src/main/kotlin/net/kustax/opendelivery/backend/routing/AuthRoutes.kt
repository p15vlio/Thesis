package net.kustax.opendelivery.backend.routing

import io.ktor.http.*
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.kustax.opendelivery.backend.service.AuthService
import net.kustax.opendelivery.backend.service.AuthenticationException
import net.kustax.opendelivery.data.request.ChangePasswordRequest
import net.kustax.opendelivery.data.request.LoginRequest
import net.kustax.opendelivery.data.request.RefreshTokenRequest

fun Route.authRoutes(authService: AuthService) {
    post("/auth/login") {
        val request = call.receive<LoginRequest>()
        val response = authService.login(request)
        call.respond(HttpStatusCode.OK, response)
    }

    post("/auth/refresh") {
        val request = call.receive<RefreshTokenRequest>()
        val response = authService.refresh(request)
        call.respond(HttpStatusCode.OK, response)
    }
}

// Mounted inside authenticate("jwt"): a signed-in admin or owner changes their own password
fun Route.changePasswordRoute(authService: AuthService) {
    post("/auth/change-password") {
        val userId = call.principal<JWTPrincipal>()?.subject ?: throw AuthenticationException("Missing user identity")
        val request = call.receive<ChangePasswordRequest>()
        val response = authService.changePassword(userId, call.userRole(), call.tenantSchema(), request)
        call.respond(HttpStatusCode.OK, response)
    }
}
