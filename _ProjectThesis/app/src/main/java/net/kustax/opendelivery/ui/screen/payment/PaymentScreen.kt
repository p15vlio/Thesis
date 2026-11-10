package net.kustax.opendelivery.ui.screen.payment

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import net.kustax.opendelivery.kiosk.KioskManager
import net.kustax.opendelivery.model.Cart
import net.kustax.opendelivery.navigation.Screen
import net.kustax.opendelivery.navigation.navigateToIdle
import net.kustax.opendelivery.navigation.returnToHome
import net.kustax.opendelivery.ui.components.AdminPinDialog
import net.kustax.opendelivery.ui.components.GuestDeskTopBar
import net.kustax.opendelivery.ui.components.InactivityWrapper
import net.kustax.opendelivery.ui.components.PlatformBadge
import net.kustax.opendelivery.ui.components.formatEuro
import net.kustax.opendelivery.ui.theme.AmberPrimary
import net.kustax.opendelivery.ui.theme.Background
import net.kustax.opendelivery.ui.theme.ErrorRed
import net.kustax.opendelivery.ui.theme.OnBackground
import net.kustax.opendelivery.ui.theme.OnBackgroundMuted
import net.kustax.opendelivery.ui.theme.OnBackgroundSubtle
import net.kustax.opendelivery.ui.theme.SuccessGreen
import net.kustax.opendelivery.ui.theme.SurfaceDark
import net.kustax.opendelivery.ui.theme.SurfaceElevated
import net.kustax.opendelivery.ui.theme.SurfaceVariant

private const val APPROVED_SHOWN_MS = 1_800L

@Composable
fun PaymentScreen(navController: NavController) {
    val vm: PaymentViewModel = viewModel()
    val cart by vm.cart.collectAsStateWithLifecycle()
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val guestEmail by vm.guestEmail.collectAsStateWithLifecycle()
    val guestNotes by vm.guestNotes.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showAdminDialog by remember { mutableStateOf(false) }

    // The reader only listens while the screen is visible
    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.resumeWaiting() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.stopWaiting() }

    // Once the card is being processed or approved there is no way back
    val locked = uiState is PaymentViewModel.UiState.Processing || uiState is PaymentViewModel.UiState.Approved
    BackHandler(enabled = locked) {}

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

    if (uiState is PaymentViewModel.UiState.PinEntry) {
        PinPadDialog(onSubmit = vm::submitPin, onCancel = vm::cancelPin)
    }

    LaunchedEffect(uiState) {
        if (uiState is PaymentViewModel.UiState.Approved) {
            delay(APPROVED_SHOWN_MS)
            navController.navigate(Screen.OrderConfirmation.route) {
                // The order is placed: drop Payment and the store so Back never returns to an empty cart
                popUpTo(Screen.Home.route)
            }
        }
    }

    InactivityWrapper(
        onTimeout = {
            navController.navigateToIdle()
        }
    ) {
        Scaffold(
            topBar = {
                GuestDeskTopBar(
                    title = "Payment",
                    showBack = true,
                    showCart = false,
                    onBack = { if (!locked) navController.popBackStack() },
                    onAdminGesture = { showAdminDialog = true }
                )
            },
            containerColor = Background
        ) { padding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Sized to its content; only a long order makes it scroll
                    cart?.let { OrderSummaryCard(cart = it, modifier = Modifier.weight(1f, fill = false)) }
                    GuestDetailsFields(
                        guestEmail = guestEmail,
                        guestNotes = guestNotes,
                        onEmailChange = { vm.guestEmail.value = it },
                        onNotesChange = { vm.guestNotes.value = it }
                    )
                }

                CardPaymentPanel(
                    amount = cart?.let { formatEuro(it.totalCents) } ?: "",
                    state = uiState,
                    onRetryReader = vm::resumeWaiting,
                    onBackToHome = navController::returnToHome,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        }
    }
}

@Composable
private fun CardPaymentPanel(
    amount: String,
    state: PaymentViewModel.UiState,
    onRetryReader: () -> Unit,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        // PinEntry keeps the waiting layout behind its dialog
        Crossfade(
            targetState = if (state is PaymentViewModel.UiState.PinEntry) PaymentViewModel.UiState.Waiting else state,
            label = "paymentState",
            modifier = Modifier.fillMaxSize()
        ) { shown ->
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                when (shown) {
                    PaymentViewModel.UiState.Waiting, PaymentViewModel.UiState.PinEntry -> WaitingContent(amount)
                    PaymentViewModel.UiState.Processing -> ProcessingContent(amount)
                    PaymentViewModel.UiState.Approved -> ApprovedContent(amount)
                    is PaymentViewModel.UiState.Declined -> DeclinedContent(shown.reason, shown.technical)
                    PaymentViewModel.UiState.ReaderUnavailable -> ReaderUnavailableContent(onRetryReader)
                    is PaymentViewModel.UiState.OrderCancelled -> OrderCancelledContent(shown.message, onBackToHome)
                }
            }
        }
    }
}

@Composable
private fun WaitingContent(amount: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        Text(
            text = "Pay $amount",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = AmberPrimary
        )
        WaitingForCard()
        Text(
            text = "Present your card to the reader",
            style = MaterialTheme.typography.titleLarge,
            color = OnBackground
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            CardWayTile(Icons.Default.Contactless, "Tap", "Hold your card near the reader", Modifier.weight(1f))
            CardWayTile(Icons.Default.CreditCard, "Insert", "Put the chip in and enter your PIN", Modifier.weight(1f))
            CardWayTile(Icons.Default.Swipe, "Swipe", "Slide the stripe through the reader", Modifier.weight(1f))
        }
    }
}

// Rings that expand from the reader icon while the terminal listens for a card
@Composable
private fun WaitingForCard() {
    val transition = rememberInfiniteTransition(label = "waiting")
    val ring by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "ring"
    )
    Box(modifier = Modifier.size(150.dp), contentAlignment = Alignment.Center) {
        listOf(0f, 0.5f).forEach { offset ->
            val progress = (ring + offset) % 1f
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .scale(1f + progress * 0.55f)
                    .alpha(1f - progress)
                    .border(3.dp, AmberPrimary, CircleShape)
            )
        }
        Box(
            modifier = Modifier.size(96.dp).background(AmberPrimary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Contactless, contentDescription = null, tint = Color.Black, modifier = Modifier.size(54.dp))
        }
    }
}

@Composable
private fun CardWayTile(icon: ImageVector, title: String, hint: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(SurfaceElevated, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, contentDescription = null, tint = AmberPrimary, modifier = Modifier.size(36.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = OnBackground)
        Text(
            hint,
            style = MaterialTheme.typography.bodyMedium,
            color = OnBackgroundMuted,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ProcessingContent(amount: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(72.dp), color = AmberPrimary, strokeWidth = 6.dp)
        Text("Processing payment…", style = MaterialTheme.typography.headlineSmall, color = OnBackground)
        Text(amount, style = MaterialTheme.typography.titleLarge, color = OnBackgroundMuted)
    }
}

@Composable
private fun ApprovedContent(amount: String) {
    val scale = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(
            modifier = Modifier.size(132.dp).scale(scale.value).background(SuccessGreen, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(84.dp))
        }
        Text("Payment approved", style = MaterialTheme.typography.headlineMedium, color = OnBackground)
        Text(amount, style = MaterialTheme.typography.titleLarge, color = SuccessGreen)
    }
}

@Composable
private fun DeclinedContent(reason: String, technical: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .background(ErrorRed.copy(alpha = 0.15f), CircleShape)
                .border(3.dp, ErrorRed, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Close, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(64.dp))
        }
        Text(if (technical) "Payment failed" else "Payment declined", style = MaterialTheme.typography.headlineMedium, color = OnBackground)
        Text(reason, style = MaterialTheme.typography.titleLarge, color = ErrorRed, textAlign = TextAlign.Center)
        Text("You can try again in a moment", style = MaterialTheme.typography.bodyLarge, color = OnBackgroundMuted)
    }
}

@Composable
private fun OrderCancelledContent(message: String, onBackToHome: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .background(ErrorRed.copy(alpha = 0.15f), CircleShape)
                .border(3.dp, ErrorRed, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Close, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(64.dp))
        }
        Text("Order cancelled", style = MaterialTheme.typography.headlineMedium, color = OnBackground)
        Text(message, style = MaterialTheme.typography.titleLarge, color = OnBackgroundMuted, textAlign = TextAlign.Center)
        Button(
            onClick = onBackToHome,
            modifier = Modifier.height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary)
        ) {
            Text("Back to Home", style = MaterialTheme.typography.titleMedium, color = Color.Black)
        }
    }
}

@Composable
private fun ReaderUnavailableContent(onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Icon(Icons.Default.CreditCard, contentDescription = null, tint = OnBackgroundSubtle, modifier = Modifier.size(88.dp))
        Text("Card reader unavailable", style = MaterialTheme.typography.headlineMedium, color = OnBackground)
        Text(
            "Please ask the front desk for help.",
            style = MaterialTheme.typography.titleMedium,
            color = OnBackgroundMuted,
            textAlign = TextAlign.Center
        )
        Button(
            onClick = onRetry,
            modifier = Modifier.height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary)
        ) {
            Text("Try again", style = MaterialTheme.typography.titleMedium, color = Color.Black)
        }
    }
}

@Composable
private fun OrderSummaryCard(cart: Cart, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                when (cart) {
                    is Cart.InRoom -> Text(
                        text = "In-room store",
                        style = MaterialTheme.typography.titleLarge,
                        color = OnBackground
                    )

                    is Cart.Restaurant -> {
                        PlatformBadge(cart.restaurant.platform)
                        Text(
                            text = cart.restaurant.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = OnBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            HorizontalDivider(color = SurfaceVariant)

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                cart.summaryLines.forEach { line ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = "${line.quantity}× ${line.name}",
                            style = MaterialTheme.typography.titleMedium,
                            color = OnBackground,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        )
                        Text(
                            text = formatEuro(line.subtotalCents),
                            style = MaterialTheme.typography.titleMedium,
                            color = OnBackgroundMuted
                        )
                    }
                }
            }

            HorizontalDivider(color = SurfaceVariant)
            if (cart is Cart.Restaurant) {
                SummaryRow("Items", formatEuro(cart.itemsCents))
                SummaryRow(
                    "Delivery",
                    if (cart.deliveryFeeCents == 0L) "Free" else formatEuro(cart.deliveryFeeCents)
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total", style = MaterialTheme.typography.titleLarge, color = OnBackground)
                Text(formatEuro(cart.totalCents), style = MaterialTheme.typography.titleLarge, color = AmberPrimary)
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = OnBackgroundMuted)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = OnBackgroundMuted)
    }
}

@Composable
private fun GuestDetailsFields(
    guestEmail: String,
    guestNotes: String,
    onEmailChange: (String) -> Unit,
    onNotesChange: (String) -> Unit
) {
    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = AmberPrimary,
        unfocusedBorderColor = OnBackgroundSubtle,
        focusedLabelColor = AmberPrimary,
        unfocusedLabelColor = OnBackgroundMuted,
        focusedTextColor = OnBackground,
        unfocusedTextColor = OnBackground,
        cursorColor = AmberPrimary
    )

    OutlinedTextField(
        value = guestEmail,
        onValueChange = onEmailChange,
        label = { Text("Email for receipt (optional)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        colors = textFieldColors,
        modifier = Modifier.fillMaxWidth()
    )

    OutlinedTextField(
        value = guestNotes,
        onValueChange = onNotesChange,
        label = { Text("Notes: allergies, room number... (optional)") },
        minLines = 3,
        maxLines = 3,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        colors = textFieldColors,
        modifier = Modifier.fillMaxWidth()
    )
}
