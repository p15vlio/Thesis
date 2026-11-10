package net.kustax.opendelivery.web.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.kustax.opendelivery.data.response.PaymentResponse
import net.kustax.opendelivery.web.util.entryModeLabel
import net.kustax.opendelivery.web.util.toLabel

// Card payment attempts of an order, oldest first; null while still loading
@Composable
fun PaymentSection(payments: List<PaymentResponse>?) {
    when {
        payments == null -> Text(
            "Loading payment…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        payments.isEmpty() -> Text(
            "No payment recorded",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        else -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            payments.forEach { payment ->
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        buildString {
                            append("Payment · ${payment.entryMode.entryModeLabel()}")
                            payment.cardLast4?.let { append(" · •••• $it") }
                            payment.declineReason?.let { append(" · $it") }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (payment.status == "FAILED") "Declined" else payment.status.toLabel(),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = when (payment.status) {
                            "COMPLETED" -> MaterialTheme.colorScheme.tertiary
                            "FAILED" -> MaterialTheme.colorScheme.error
                            "REFUNDED" -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            if (payments.any { it.status == "REFUNDED" }) {
                Text(
                    "The order was cancelled and the payment was refunded to the card.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}
