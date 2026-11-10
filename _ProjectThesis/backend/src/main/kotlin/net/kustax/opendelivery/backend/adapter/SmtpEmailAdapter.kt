package net.kustax.opendelivery.backend.adapter

import jakarta.mail.Message
import jakarta.mail.MessagingException
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.kustax.opendelivery.backend.service.ExternalServiceException
import net.kustax.opendelivery.backend.service.SmtpSecurity
import net.kustax.opendelivery.backend.service.SmtpSettings
import net.kustax.opendelivery.domain.entity.tenant.OrderItem
import net.kustax.opendelivery.domain.port.NotificationService
import org.slf4j.LoggerFactory
import java.util.Properties

// SMTP settings are read from platform_settings on every send, so changes made on the admin Settings
// page apply immediately without a backend restart.
class SmtpEmailAdapter(private val loadSettings: suspend () -> SmtpSettings?) : NotificationService {

    private val logger = LoggerFactory.getLogger(SmtpEmailAdapter::class.java)

    override suspend fun sendReceipt(toEmail: String, orderId: String, amount: Long, deliveryFee: Long, items: List<OrderItem>) {
        try {
            send(toEmail, "Receipt for order $orderId", buildBody(orderId, amount, deliveryFee, items))
            logger.info("[EMAIL] Receipt for order $orderId sent to ${maskEmail(toEmail)}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: ExternalServiceException) {
            // SMTP is not configured yet (normal on a fresh install): expected, so no stack trace
            logger.warn("[EMAIL] Receipt for order $orderId not sent: ${e.message}")
        } catch (e: Exception) {
            logger.error("[EMAIL] Failed to send receipt for order $orderId to ${maskEmail(toEmail)}: ${e.message}", e)
        }
    }

    override suspend fun sendOwnerWelcome(toEmail: String, temporaryPassword: String, loginUrl: String) {
        try {
            send(toEmail, "Welcome to Guest Desk", buildWelcomeBody(toEmail, temporaryPassword, loginUrl))
            logger.info("[EMAIL] Owner welcome email sent to ${maskEmail(toEmail)}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: ExternalServiceException) {
            logger.warn("[EMAIL] Owner welcome email to ${maskEmail(toEmail)} not sent: ${e.message}")
        } catch (e: Exception) {
            logger.error("[EMAIL] Failed to send owner welcome email to ${maskEmail(toEmail)}: ${e.message}", e)
        }
    }

    override suspend fun sendPasswordReset(toEmail: String, temporaryPassword: String, loginUrl: String) {
        try {
            send(toEmail, "Your Guest Desk password was reset", buildResetBody(toEmail, temporaryPassword, loginUrl))
            logger.info("[EMAIL] Password reset email sent to ${maskEmail(toEmail)}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: ExternalServiceException) {
            logger.warn("[EMAIL] Password reset email to ${maskEmail(toEmail)} not sent: ${e.message}")
        } catch (e: Exception) {
            logger.error("[EMAIL] Failed to send password reset email to ${maskEmail(toEmail)}: ${e.message}", e)
        }
    }

    // Used by the Settings page — unlike the notifications above, failures are surfaced to the admin.
    suspend fun sendTestEmail(toEmail: String) {
        try {
            send(toEmail, "Guest Desk SMTP test", "This is a test email from Guest Desk. Your SMTP settings are working.")
        } catch (e: MessagingException) {
            throw ExternalServiceException("SMTP error: ${e.message}")
        }
    }

    private suspend fun send(toEmail: String, subject: String, body: String) {
        val settings = loadSettings() ?: throw ExternalServiceException("SMTP is not configured. Set it up in Settings.")
        val message = MimeMessage(createSession(settings)).apply {
            setFrom(InternetAddress(settings.fromAddress, settings.fromName, "UTF-8"))
            setRecipients(Message.RecipientType.TO, toEmail)
            setSubject(subject, "UTF-8")
            setText(body, "UTF-8")
        }
        withContext(Dispatchers.IO) { Transport.send(message) }
    }

    private fun createSession(settings: SmtpSettings): Session {
        val auth = settings.username.isNotBlank()
        val props = Properties().apply {
            put("mail.smtp.host", settings.host)
            put("mail.smtp.port", settings.port.toString())
            put("mail.smtp.auth", auth.toString())
            put("mail.smtp.connectiontimeout", "10000")
            put("mail.smtp.timeout", "10000")
            put("mail.smtp.writetimeout", "10000")
            when (settings.security) {
                SmtpSecurity.STARTTLS -> {
                    put("mail.smtp.starttls.enable", "true")
                    put("mail.smtp.starttls.required", "true")
                }
                SmtpSecurity.SSL -> put("mail.smtp.ssl.enable", "true")
                SmtpSecurity.NONE -> Unit
            }
        }
        if (!auth) return Session.getInstance(props)
        return Session.getInstance(props, object : jakarta.mail.Authenticator() {
            override fun getPasswordAuthentication() = PasswordAuthentication(settings.username, settings.password)
        })
    }

    private fun buildWelcomeBody(toEmail: String, temporaryPassword: String, loginUrl: String): String {
        return buildString {
            appendLine("Welcome to Guest Desk!")
            appendLine()
            appendLine("Your owner account has been created. You can log in with the following credentials:")
            appendLine()
            appendLine("Login: $toEmail")
            appendLine("Temporary password: $temporaryPassword")
            appendLine()
            appendLine("Login URL: $loginUrl")
            appendLine()
            appendLine("Please log in and change your password as soon as possible.")
        }
    }

    private fun buildResetBody(toEmail: String, temporaryPassword: String, loginUrl: String): String {
        return buildString {
            appendLine("Your Guest Desk password was reset by the platform administrator.")
            appendLine()
            appendLine("Login: $toEmail")
            appendLine("Temporary password: $temporaryPassword")
            appendLine()
            appendLine("Login URL: $loginUrl")
            appendLine()
            appendLine("Please log in and change your password as soon as possible.")
        }
    }

    private fun buildBody(orderId: String, amount: Long, deliveryFee: Long, items: List<OrderItem>): String {
        val lines = items.joinToString("\n") { item ->
            "  ${item.quantity}x ${item.productName} - ${formatCents(item.subtotal)}"
        }
        return buildString {
            appendLine("Thank you for your order!")
            appendLine()
            appendLine("Order: $orderId")
            appendLine()
            appendLine("Items:")
            appendLine(lines)
            if (deliveryFee > 0) appendLine("  Delivery fee - ${formatCents(deliveryFee)}")
            appendLine()
            appendLine("Total: ${formatCents(amount)}")
        }
    }

    private fun formatCents(cents: Long): String {
        val euros = cents / 100
        val remainder = (cents % 100).let { if (it < 0) -it else it }
        return "%d.%02d EUR".format(euros, remainder)
    }
}

// "john.doe@example.com" -> "j***@example.com": enough to correlate a log line, not enough to contact the guest
private fun maskEmail(email: String): String {
    val at = email.indexOf('@')
    return if (at <= 0) "***" else "${email[0]}***${email.substring(at)}"
}
