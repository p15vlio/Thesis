package net.kustax.opendelivery.backend.routing

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.kustax.opendelivery.backend.service.DashboardService
import net.kustax.opendelivery.backend.service.ForbiddenException
import net.kustax.opendelivery.domain.enum.UserRole

fun Route.dashboardRoutes(dashboardService: DashboardService) {
    get("/dashboard/summary") {
        if (call.userRole() != UserRole.OWNER) throw ForbiddenException("Owner access required")
        call.withTenant {
            val response = dashboardService.getSummary()
            call.respond(HttpStatusCode.OK, response)
        }
    }
}
