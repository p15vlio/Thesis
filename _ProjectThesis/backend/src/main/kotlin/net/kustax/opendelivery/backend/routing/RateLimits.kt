package net.kustax.opendelivery.backend.routing

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import kotlin.time.Duration.Companion.minutes

// Unauthenticated endpoints that take a secret (login, token refresh, activation code), limited per client IP
val PublicRateLimit = RateLimitName("public")

// Address search: every call is forwarded to the public Nominatim service, limited per signed-in user
val GeocodeRateLimit = RateLimitName("geocode")

fun Application.configureRateLimits() {
    install(RateLimit) {
        register(PublicRateLimit) {
            rateLimiter(limit = 20, refillPeriod = 1.minutes)
            // origin reflects X-Forwarded-For, so a browser behind the nginx proxy is limited by its own address
            requestKey { call -> call.request.origin.remoteHost }
        }
        register(GeocodeRateLimit) {
            rateLimiter(limit = 30, refillPeriod = 1.minutes)
            requestKey { call -> call.principal<JWTPrincipal>()?.subject ?: call.request.origin.remoteHost }
        }
    }
}
