package net.kustax.opendelivery.backend.routing

import com.auth0.jwt.interfaces.Payload
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.util.AttributeKey
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.withContext
import net.kustax.opendelivery.backend.database.TenantContext
import net.kustax.opendelivery.backend.security.JwtConfig
import net.kustax.opendelivery.backend.service.AuthenticationException
import net.kustax.opendelivery.backend.service.ForbiddenException
import net.kustax.opendelivery.domain.entity.tenant.Device
import net.kustax.opendelivery.domain.enum.UserRole
import net.kustax.opendelivery.domain.repository.AuthRepository
import net.kustax.opendelivery.domain.repository.DeviceRepository

// Carries the device resolved by withTenant's revocation check so route handlers for UserRole.DEVICE
// callers (e.g. the kiosk product list, which needs the device's propertyId) don't have to re-query it.
private class TenantDeviceElement(val device: Device) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<TenantDeviceElement>
    override val key: CoroutineContext.Key<*> get() = Key
}

// Set once in configureRouting; lets withTenant look the calling device up without a global singleton
val DeviceRepositoryKey = AttributeKey<DeviceRepository>("DeviceRepository")

// Set once in configureRouting; lets withTenant check that the calling owner is still active
val AuthRepositoryKey = AttributeKey<AuthRepository>("AuthRepository")

suspend fun currentTenantDevice(): Device? = coroutineContext[TenantDeviceElement.Key]?.device

private fun ApplicationCall.jwtPayload(): Payload =
    principal<JWTPrincipal>()?.payload ?: throw AuthenticationException("Missing authorization token")

fun ApplicationCall.userRole(): UserRole {
    val raw = jwtPayload().getClaim(JwtConfig.CLAIM_ROLE).asString()
        ?: throw AuthenticationException("Missing role claim in token")
    return enumValueOf(raw)
}

fun ApplicationCall.tenantSchema(): String? {
    return try {
        val claimSchema = jwtPayload().getClaim(JwtConfig.CLAIM_SCHEMA).asString()?.ifBlank { null }
        // PLATFORM_ADMIN JWTs carry no schemaName claim — allow override via query param so
        // admins can browse any tenant's data from the panel.
        if (claimSchema == null && userRole() == UserRole.PLATFORM_ADMIN) {
            request.queryParameters["schemaName"]?.ifBlank { null }
        } else {
            claimSchema
        }
    } catch (_: AuthenticationException) {
        null
    }
}

suspend fun ApplicationCall.withTenant(block: suspend () -> Unit) {
    val schema = tenantSchema() ?: throw AuthenticationException("No tenant schema in token")
    withContext(TenantContext(schema)) {
        if (userRole() == UserRole.DEVICE) {
            val deviceId = principal<JWTPrincipal>()?.subject
                ?: throw AuthenticationException("Missing device identity")
            val device = application.attributes[DeviceRepositoryKey].findById(deviceId)
                ?: throw AuthenticationException("Device not found")
            // 401 makes the kiosk clear its registration and return to the registration screen. That is right for a
            // device that was disconnected by the owner (unregistered: no activation time, new activation code).
            if (device.activatedAt == null) throw AuthenticationException("Device has been disconnected")
            // A deactivated device, like the devices of a deactivated owner, is answered with 403 instead: the kiosk
            // keeps its registration and resumes as soon as the device or the owner is reactivated.
            if (!device.isActive) throw ForbiddenException("Device is deactivated")
            if (!application.attributes[AuthRepositoryKey].isOwnerActiveBySchema(schema)) {
                throw ForbiddenException("The owner account of this device is deactivated")
            }
            withContext(TenantDeviceElement(device)) { block() }
        } else {
            // Deactivation takes effect at once: an owner's still-valid access token stops working on the next
            // request instead of when it expires
            if (userRole() == UserRole.OWNER) {
                val ownerId = principal<JWTPrincipal>()?.subject ?: throw AuthenticationException("Missing user identity")
                if (!application.attributes[AuthRepositoryKey].isOwnerActive(ownerId)) {
                    throw AuthenticationException("This account has been deactivated. Contact the platform administrator.")
                }
            }
            block()
        }
    }
}
