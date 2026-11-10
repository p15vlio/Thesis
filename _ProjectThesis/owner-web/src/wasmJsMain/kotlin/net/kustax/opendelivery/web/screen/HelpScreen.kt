package net.kustax.opendelivery.web.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun HelpScreen() {
    val faqs = listOf(
        "How do I register a new kiosk device?" to """
            1. Go to Properties → select a property → Devices.
            2. Tap the + button and enter a device name.
            3. The activation code dialog will appear immediately after creation.
            4. On the kiosk Android device, open the Guest Desk app.
            5. Tap "Enter Activation Code" and type the code shown here.
            6. The device will activate and appear as Online within seconds.
            Tip: Use the "Show Code" button on any unactivated device to see its code again.
        """.trimIndent(),

        "How do I create an owner account?" to """
            1. Go to Owners (Platform Admin only).
            2. Tap "New Owner" and fill in the form.
            3. After creation, a temporary password dialog will appear — copy it immediately.
            4. The temporary password is shown only once and is never stored.
            5. Share the email and temporary password with the owner so they can log in.
        """.trimIndent(),

        "How does the order flow work?" to """
            Orders follow a fixed state machine:
            PENDING → CONFIRMED → PREPARING → OUT_FOR_DELIVERY → DELIVERED
            Any non-final state can transition to CANCELLED.

            - Guests place and pay for orders at the kiosk; a paid order starts as CONFIRMED.
            - In-room store orders are moved along by you from the Properties → Orders screen or the Orders sidebar item.
            - Restaurant (food delivery) orders are advanced automatically by the delivery platform. You can only cancel them.
            - Each status update is logged in the Audit Log.
        """.trimIndent(),

        "How do guests pay at the kiosk?" to """
            Guests pay by card at the kiosk with the built-in card reader: tap (contactless), insert the chip and enter the PIN, or swipe the magnetic stripe.
            The payment acquirer is emulated for demonstration and thesis purposes, so no real money is ever charged.
            Each payment attempt (entry mode, last four digits, declined or approved) is shown in the order details.
            If a restaurant order is rejected by the delivery platform after the card was charged, the order is cancelled and the payment is shown as Refunded.
        """.trimIndent(),

        "How do I offer food from efood, Wolt or Box?" to """
            1. Open Food Delivery in the sidebar and connect a platform with an API key (at least 8 characters).
            2. Edit a property and enable the platforms that property should offer to its guests.
            3. Guests then see the restaurants of those platforms on the kiosk and can order from them.
            The platforms are emulated: their restaurants and menus are simulated and orders advance on their own.
        """.trimIndent(),

        "How do I import products into my in-room store?" to """
            In Products, use "Import from platform" to pick items from the retail catalog of a connected food platform, choose a category and import them.
            Imported products behave like any other product: you can edit the price, set them unavailable per property or delete them.
        """.trimIndent(),

        "Where do I configure email and company lookup?" to """
            Platform Admins can open Settings:
            - Email (SMTP): the mail server used for welcome emails and guest receipts. Use "Send test email" to check it.
            - GEMH (Business Registry): the credentials used to look up a company by VAT number when creating an owner.
        """.trimIndent(),

        "What is the Audit Log for?" to """
            The Audit Log records every action taken by platform admins and owners:
            - Entity created, updated, or deleted
            - Which actor performed the action (role + ID)
            - Which tenant (schema) was affected
            - Timestamp of the action

            Only Platform Admins can view the full audit log.
            Use the Entity Type and Action filters to find specific events.
        """.trimIndent(),

        "How do I view orders for a specific property?" to """
            Option 1: Properties → tap a property card → View Orders button.
            Option 2: Use the Orders sidebar item (shows all orders across all your properties).
            In the Orders view, use the Property filter dropdown to narrow down to one property.
        """.trimIndent()
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                "Help & FAQ",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                "Common questions about the Guest Desk management console.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        faqs.forEach { (question, answer) ->
            item {
                FaqItem(question = question, answer = answer)
            }
        }
    }
}

@Composable
private fun FaqItem(question: String, answer: String) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    question,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = expanded) {
                Text(
                    answer,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}
