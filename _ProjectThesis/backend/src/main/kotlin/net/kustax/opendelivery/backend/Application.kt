package net.kustax.opendelivery.backend

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import net.kustax.opendelivery.backend.database.DatabaseFactory
import net.kustax.opendelivery.backend.database.SchemaManager
import net.kustax.opendelivery.backend.database.SqlState
import net.kustax.opendelivery.backend.repository.platform.ExposedOwnerRepository
import net.kustax.opendelivery.backend.routing.configureRateLimits
import net.kustax.opendelivery.backend.routing.configureRouting
import net.kustax.opendelivery.backend.security.JwtConfig
import net.kustax.opendelivery.backend.security.SecretCipher
import net.kustax.opendelivery.backend.service.AuthenticationException
import net.kustax.opendelivery.backend.service.ConflictException
import net.kustax.opendelivery.backend.service.ExternalServiceException
import net.kustax.opendelivery.backend.service.ForbiddenException
import net.kustax.opendelivery.backend.service.NotFoundException
import net.kustax.opendelivery.data.response.ErrorResponse
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.slf4j.LoggerFactory

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

private val unhandledExceptionLog = LoggerFactory.getLogger("UnhandledException")

private suspend fun respondMalformedRequest(call: ApplicationCall) {
    call.respond(HttpStatusCode.BadRequest, ErrorResponse(code = "BAD_REQUEST", message = "Malformed request"))
}

private suspend fun respondInternalError(call: ApplicationCall, cause: Throwable) {
    // Never leak cause.message here — it can carry raw SQL/schema/driver detail.
    // Full detail goes to the server log only; the client gets a stable, generic
    // message plus a code it can key UI copy off, not the underlying exception text.
    unhandledExceptionLog.error("Unhandled exception on {} {}", call.request.httpMethod.value, call.request.path(), cause)
    call.respond(
        HttpStatusCode.InternalServerError,
        ErrorResponse(code = "INTERNAL_SERVER_ERROR", message = "An unexpected error occurred. Please try again.")
    )
}

fun Application.module() {
    // Fail fast: both secrets are mandatory and have no defaults
    JwtConfig.requireConfigured()
    SecretCipher.requireConfigured()
    DatabaseFactory.init()
    if (System.getenv("RESET_DB") == "true") {
        SchemaManager.dropAndRecreate()
    } else {
        SchemaManager.createPlatformTables()
        // Reconcile every already-provisioned tenant schema against the current tenant model. Provisioning
        // at owner creation only happens once — a tenant created before a new table, column or constraint
        // was added would otherwise never get it. provisionTenant is idempotent, so this is safe to run on
        // every startup, self-healing this exact class of bug for any future tenant schema change too.
        val owners = runBlocking { ExposedOwnerRepository().findAll() }
        owners.forEach { owner -> SchemaManager.provisionTenant(owner.schemaName) }
    }

    install(ContentNegotiation) { json() }
    install(CallLogging)
    install(DefaultHeaders)
    // The web panel reaches the backend through the nginx proxy; take the client address from X-Forwarded-For
    install(XForwardedHeaders)
    configureRateLimits()

    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Options)
    }

    install(StatusPages) {
        exception<AuthenticationException> { call, cause ->
            call.respond(
                HttpStatusCode.Unauthorized,
                ErrorResponse(code = "UNAUTHORIZED", message = cause.message ?: "Unauthorized")
            )
        }
        exception<NotFoundException> { call, cause ->
            call.respond(HttpStatusCode.NotFound, ErrorResponse(code = "NOT_FOUND", message = cause.message ?: "Not found"))
        }
        exception<ConflictException> { call, cause ->
            call.respond(HttpStatusCode.Conflict, ErrorResponse(code = "CONFLICT", message = cause.message ?: "Conflict"))
        }
        exception<ForbiddenException> { call, cause ->
            call.respond(HttpStatusCode.Forbidden, ErrorResponse(code = "FORBIDDEN", message = cause.message ?: "Forbidden"))
        }
        exception<ExternalServiceException> { call, cause ->
            call.respond(HttpStatusCode.BadGateway, ErrorResponse(code = "EXTERNAL_SERVICE_ERROR", message = cause.message ?: "External service error"))
        }
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(code = "BAD_REQUEST", message = cause.message ?: "Invalid parameter value"))
        }
        // Malformed request bodies: bad JSON, wrong field types, missing required fields. The exception text
        // names internal classes and fields, so the client only gets a fixed message.
        exception<BadRequestException> { call, _ -> respondMalformedRequest(call) }
        exception<ContentTransformationException> { call, _ -> respondMalformedRequest(call) }
        exception<SerializationException> { call, _ -> respondMalformedRequest(call) }
        exception<ExposedSQLException> { call, cause ->
            // A constraint the database enforces was hit by a request that the service layer didn't catch first
            // (a concurrent duplicate, deleting something that is still referenced).
            when (cause.sqlState) {
                SqlState.UNIQUE_VIOLATION ->
                    call.respond(HttpStatusCode.Conflict, ErrorResponse(code = "CONFLICT", message = "A record with these details already exists"))
                SqlState.FOREIGN_KEY_VIOLATION ->
                    call.respond(HttpStatusCode.Conflict, ErrorResponse(code = "CONFLICT", message = "The operation conflicts with related records"))
                else -> respondInternalError(call, cause)
            }
        }
        exception<Exception> { call, cause -> respondInternalError(call, cause) }
        status(HttpStatusCode.TooManyRequests) { call, status ->
            call.respond(status, ErrorResponse(code = "TOO_MANY_REQUESTS", message = "Too many requests. Please wait a minute and try again."))
        }
    }

    install(Authentication) {
        jwt("jwt") {
            realm = "opendelivery"
            verifier(JwtConfig.verifier())
            validate { credential ->
                if (credential.payload.getClaim(JwtConfig.CLAIM_ROLE).asString() != null)
                    JWTPrincipal(credential.payload)
                else null
            }
        }
    }

    configureRouting()
}
