package net.kustax.opendelivery.backend.routing

import io.ktor.http.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.auth.principal
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.kustax.opendelivery.backend.adapter.SmtpEmailAdapter
import net.kustax.opendelivery.backend.service.ForbiddenException
import net.kustax.opendelivery.backend.service.PlatformSettingsService
import net.kustax.opendelivery.data.request.SendTestEmailRequest
import net.kustax.opendelivery.data.request.UpdateGemhSettingsRequest
import net.kustax.opendelivery.data.request.UpdateSmtpSettingsRequest
import net.kustax.opendelivery.domain.enum.UserRole

fun Route.settingsRoutes(settingsService: PlatformSettingsService, emailAdapter: SmtpEmailAdapter) {
    route("/settings") {
        get("/smtp") {
            if (call.userRole() != UserRole.PLATFORM_ADMIN) throw ForbiddenException("Platform admin access required")
            call.respond(HttpStatusCode.OK, settingsService.getSmtpResponse())
        }

        put("/smtp") {
            if (call.userRole() != UserRole.PLATFORM_ADMIN) throw ForbiddenException("Platform admin access required")
            val actorId = call.principal<JWTPrincipal>()?.subject ?: ""
            val request = call.receive<UpdateSmtpSettingsRequest>()
            call.respond(HttpStatusCode.OK, settingsService.updateSmtp(request, actorId, call.userRole().name))
        }

        post("/smtp/test") {
            if (call.userRole() != UserRole.PLATFORM_ADMIN) throw ForbiddenException("Platform admin access required")
            val request = call.receive<SendTestEmailRequest>()
            require(request.toEmail.isNotBlank()) { "Recipient email is required" }
            emailAdapter.sendTestEmail(request.toEmail.trim())
            call.respond(HttpStatusCode.NoContent)
        }

        get("/gemh") {
            if (call.userRole() != UserRole.PLATFORM_ADMIN) throw ForbiddenException("Platform admin access required")
            call.respond(HttpStatusCode.OK, settingsService.getGemhResponse())
        }

        put("/gemh") {
            if (call.userRole() != UserRole.PLATFORM_ADMIN) throw ForbiddenException("Platform admin access required")
            val actorId = call.principal<JWTPrincipal>()?.subject ?: ""
            val request = call.receive<UpdateGemhSettingsRequest>()
            call.respond(HttpStatusCode.OK, settingsService.updateGemh(request, actorId, call.userRole().name))
        }
    }
}
