package net.kustax.opendelivery.backend.routing

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.kustax.opendelivery.backend.service.ForbiddenException
import net.kustax.opendelivery.data.response.AuditLogResponse
import net.kustax.opendelivery.domain.enum.UserRole
import net.kustax.opendelivery.domain.repository.AuditLogRepository

fun Route.auditLogRoutes(auditLogRepository: AuditLogRepository) {
    get("/audit-logs") {
        if (call.userRole() != UserRole.PLATFORM_ADMIN) throw ForbiddenException("Platform admin access required")
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
        val offset = call.request.queryParameters["offset"]?.toLongOrNull() ?: 0L
        val entries = auditLogRepository.findAll(limit, offset)
        call.respond(HttpStatusCode.OK, entries.map { entry ->
            AuditLogResponse(
                id = entry.id,
                actorId = entry.actorId,
                actorRole = entry.actorRole,
                action = entry.action,
                targetType = entry.targetType,
                targetId = entry.targetId,
                schemaName = entry.schemaName,
                details = entry.details,
                createdAt = entry.createdAt
            )
        })
    }
}
