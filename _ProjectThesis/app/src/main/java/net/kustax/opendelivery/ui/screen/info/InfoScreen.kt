package net.kustax.opendelivery.ui.screen.info

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import net.kustax.opendelivery.AppContainer
import net.kustax.opendelivery.kiosk.KioskManager
import net.kustax.opendelivery.model.EMERGENCY_SERVICES
import net.kustax.opendelivery.model.UiProperty
import net.kustax.opendelivery.navigation.navigateToIdle
import net.kustax.opendelivery.ui.components.AdminPinDialog
import net.kustax.opendelivery.ui.components.DialFallbackDialog
import net.kustax.opendelivery.ui.components.DialState
import net.kustax.opendelivery.ui.components.rememberDialState
import net.kustax.opendelivery.ui.components.GuestDeskTopBar
import net.kustax.opendelivery.ui.components.InactivityWrapper
import net.kustax.opendelivery.ui.theme.*

// The screen does not scroll, so longer house rules are cut off
private const val HOUSE_RULES_MAX_LINES = 9

@Composable
fun InfoScreen(navController: NavController) {
    val property by AppContainer.propertyRepository.property.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val dialer = rememberDialState()
    var showAdminDialog by remember { mutableStateOf(false) }

    DialFallbackDialog(dialer)

    if (showAdminDialog) {
        AdminPinDialog(
            onConfirm = {
                showAdminDialog = false
                (context as? Activity)?.let { activity ->
                    KioskManager(activity).stopKiosk()
                    activity.finish()
                }
            },
            onDismiss = { showAdminDialog = false }
        )
    }

    InactivityWrapper(
        onTimeout = { navController.navigateToIdle() }
    ) {
        Scaffold(
            topBar = {
                GuestDeskTopBar(
                    title = "Property Info",
                    showBack = true,
                    showCart = false,
                    onBack = { navController.popBackStack() },
                    onAdminGesture = { showAdminDialog = true }
                )
            },
            containerColor = Background
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        property?.let { PropertyCard(it, dialer) }

                        InfoCard(title = "Emergency Numbers", icon = Icons.Default.Emergency) {
                            EMERGENCY_SERVICES.forEach { EmergencyRow(it.label, it.number, dialer) }
                        }
                    }

                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        property?.let { WifiAndRules(it) }
                    }
                }

                Text(
                    text = "Powered by Guest Desk",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnBackgroundSubtle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentWidth(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
private fun PropertyCard(property: UiProperty, dialer: DialState) {
    InfoCard(title = "Property", icon = Icons.Default.Home) {
        InfoRow(label = "Name", value = property.name)
        if (property.address.isNotBlank()) InfoRow(label = "Address", value = property.address)
        if (property.contactPhone.isNotBlank()) {
            InfoRow(
                label = "Phone",
                value = property.contactPhone,
                clickable = true,
                onClick = { dialer.dial(property.contactPhone) }
            )
        }
        property.checkOutTime?.let { InfoRow(label = "Check-out", value = it) }
    }
}

@Composable
private fun ColumnScope.WifiAndRules(property: UiProperty) {
    var wifiPasswordVisible by remember { mutableStateOf(false) }

    if (property.wifiName != null || property.wifiPassword != null) {
        InfoCard(title = "WiFi", icon = Icons.Default.Wifi) {
            property.wifiName?.let { InfoRow(label = "Network", value = it) }
            property.wifiPassword?.let { password ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Password",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnBackgroundMuted
                        )
                        Text(
                            text = if (wifiPasswordVisible) password else "••••••••",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (wifiPasswordVisible) OnBackground else OnBackgroundSubtle
                        )
                    }
                    IconButton(onClick = { wifiPasswordVisible = !wifiPasswordVisible }) {
                        Icon(
                            imageVector = if (wifiPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (wifiPasswordVisible) "Hide password" else "Show password",
                            tint = AmberPrimary
                        )
                    }
                }
            }
        }
    }

    property.houseRules?.let { rules ->
        InfoCard(title = "House Rules", icon = Icons.AutoMirrored.Filled.Article) {
            Text(
                text = rules,
                style = MaterialTheme.typography.bodyMedium,
                color = OnBackgroundMuted,
                maxLines = HOUSE_RULES_MAX_LINES,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AmberPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Text(title, style = MaterialTheme.typography.titleMedium, color = OnBackground)
            }
            HorizontalDivider(color = SurfaceVariant)
            content()
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    clickable: Boolean = false,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = if (clickable) {
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
        } else {
            Modifier.fillMaxWidth()
        },
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = OnBackgroundMuted)
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = if (clickable) AmberPrimary else OnBackground,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun EmergencyRow(label: String, number: String, dialer: DialState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { dialer.dial(number) }
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = OnBackground)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(number, style = MaterialTheme.typography.titleMedium, color = AmberPrimary)
            Icon(
                Icons.Default.Phone,
                contentDescription = "Call $label",
                tint = AmberPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
