package net.kustax.opendelivery.backend.service

import net.kustax.opendelivery.backend.security.SecretCipher
import net.kustax.opendelivery.data.request.UpdateGemhSettingsRequest
import net.kustax.opendelivery.data.request.UpdateSmtpSettingsRequest
import net.kustax.opendelivery.data.response.GemhSettingsResponse
import net.kustax.opendelivery.data.response.SmtpSettingsResponse
import net.kustax.opendelivery.domain.repository.PlatformSettingsRepository

enum class SmtpSecurity { STARTTLS, SSL, NONE }

data class SmtpSettings(
    val host: String,
    val port: Int,
    val security: SmtpSecurity,
    val username: String,
    val password: String,
    val fromAddress: String,
    val fromName: String
)

class PlatformSettingsService(
    private val repo: PlatformSettingsRepository,
    private val auditLogService: AuditLogService
) {

    private companion object {
        const val SMTP_HOST = "smtp.host"
        const val SMTP_PORT = "smtp.port"
        const val SMTP_SECURITY = "smtp.security"
        const val SMTP_USERNAME = "smtp.username"
        const val SMTP_PASSWORD = "smtp.password"
        const val SMTP_FROM_ADDRESS = "smtp.from_address"
        const val SMTP_FROM_NAME = "smtp.from_name"
        const val GEMH_API_KEY = "gemh.api_key"
        val SMTP_KEYS = listOf(SMTP_HOST, SMTP_PORT, SMTP_SECURITY, SMTP_USERNAME, SMTP_PASSWORD, SMTP_FROM_ADDRESS, SMTP_FROM_NAME)

        // Shown pre-filled on a fresh install — the platform's intended provider is Gmail with an app password.
        const val DEFAULT_HOST = "smtp.gmail.com"
        const val DEFAULT_PORT = 587
        const val DEFAULT_FROM_NAME = "Guest Desk"
    }

    // null until an admin has saved SMTP settings — email sending is skipped until then.
    suspend fun getSmtpSettings(): SmtpSettings? {
        val values = repo.findAll(SMTP_KEYS)
        val host = values[SMTP_HOST] ?: return null
        val fromAddress = values[SMTP_FROM_ADDRESS] ?: return null
        return SmtpSettings(
            host = host,
            port = values[SMTP_PORT]?.toIntOrNull() ?: DEFAULT_PORT,
            security = values[SMTP_SECURITY]?.let { SmtpSecurity.valueOf(it) } ?: SmtpSecurity.STARTTLS,
            username = values[SMTP_USERNAME].orEmpty(),
            password = values[SMTP_PASSWORD]?.let { SecretCipher.decrypt(it) }.orEmpty(),
            fromAddress = fromAddress,
            fromName = values[SMTP_FROM_NAME] ?: DEFAULT_FROM_NAME
        )
    }

    suspend fun getSmtpResponse(): SmtpSettingsResponse {
        val values = repo.findAll(SMTP_KEYS)
        return SmtpSettingsResponse(
            host = values[SMTP_HOST] ?: DEFAULT_HOST,
            port = values[SMTP_PORT]?.toIntOrNull() ?: DEFAULT_PORT,
            security = values[SMTP_SECURITY] ?: SmtpSecurity.STARTTLS.name,
            username = values[SMTP_USERNAME].orEmpty(),
            passwordSet = values[SMTP_PASSWORD] != null,
            fromAddress = values[SMTP_FROM_ADDRESS].orEmpty(),
            fromName = values[SMTP_FROM_NAME] ?: DEFAULT_FROM_NAME
        )
    }

    suspend fun updateSmtp(request: UpdateSmtpSettingsRequest, actorId: String, actorRole: String): SmtpSettingsResponse {
        require(request.host.isNotBlank()) { "SMTP host is required" }
        require(request.port in 1..65535) { "SMTP port must be between 1 and 65535" }
        val security = SmtpSecurity.entries.firstOrNull { it.name == request.security }
            ?: throw IllegalArgumentException("Security must be one of ${SmtpSecurity.entries.joinToString()}")
        require(EMAIL_REGEX.matches(request.fromAddress.trim())) { "A valid sender address is required" }
        require(request.fromName.isNotBlank()) { "Sender name is required" }

        val values = mutableMapOf(
            SMTP_HOST to request.host.trim(),
            SMTP_PORT to request.port.toString(),
            SMTP_SECURITY to security.name,
            SMTP_USERNAME to request.username.trim(),
            SMTP_FROM_ADDRESS to request.fromAddress.trim(),
            SMTP_FROM_NAME to request.fromName.trim()
        )
        // Gmail shows app passwords in groups of four ("abcd efgh ijkl mnop") — the spaces are not part of it.
        request.password?.takeIf { it.isNotBlank() }?.let { values[SMTP_PASSWORD] = SecretCipher.encrypt(it.replace(" ", "")) }
        repo.saveAll(values)

        auditLogService.log(
            actorId = actorId,
            actorRole = actorRole,
            action = "UPDATE_SMTP_SETTINGS",
            targetType = "PLATFORM_SETTINGS",
            details = "host=${request.host.trim()}, port=${request.port}, security=${security.name}" +
                if (request.password.isNullOrBlank()) "" else ", password changed"
        )
        return getSmtpResponse()
    }

    // null until an admin has saved a GEMH API key — company lookups fail with a clear message until then.
    suspend fun getGemhApiKey(): String? =
        repo.findAll(listOf(GEMH_API_KEY))[GEMH_API_KEY]?.let { SecretCipher.decrypt(it) }

    suspend fun getGemhResponse() = GemhSettingsResponse(apiKeySet = repo.findAll(listOf(GEMH_API_KEY))[GEMH_API_KEY] != null)

    suspend fun updateGemh(request: UpdateGemhSettingsRequest, actorId: String, actorRole: String): GemhSettingsResponse {
        require(request.apiKey.isNotBlank()) { "GEMH API key is required" }
        repo.saveAll(mapOf(GEMH_API_KEY to SecretCipher.encrypt(request.apiKey.trim())))
        auditLogService.log(
            actorId = actorId,
            actorRole = actorRole,
            action = "UPDATE_GEMH_SETTINGS",
            targetType = "PLATFORM_SETTINGS",
            details = "api key changed"
        )
        return getGemhResponse()
    }
}

internal val EMAIL_REGEX = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""")
