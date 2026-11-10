package net.kustax.opendelivery.ui.screen.store

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.kustax.opendelivery.model.Cart
import net.kustax.opendelivery.model.FoodCartLine
import net.kustax.opendelivery.model.UiProduct
import net.kustax.opendelivery.ui.components.PlatformBadge
import net.kustax.opendelivery.ui.components.QuantitySelector
import net.kustax.opendelivery.ui.components.formatEuro
import net.kustax.opendelivery.ui.theme.AmberPrimary
import net.kustax.opendelivery.ui.theme.ErrorRed
import net.kustax.opendelivery.ui.theme.OnBackground
import net.kustax.opendelivery.ui.theme.OnBackgroundMuted
import net.kustax.opendelivery.ui.theme.OnBackgroundSubtle
import net.kustax.opendelivery.ui.theme.SurfaceDark
import net.kustax.opendelivery.ui.theme.SurfaceVariant

val CART_PANEL_WIDTH: Dp = 300.dp

// The guest's one cart, always visible next to the store's products.
@Composable
fun CartPanel(
    cart: Cart?,
    onAddStore: (UiProduct) -> Unit,
    onDecrementStore: (String) -> Unit,
    onAddDish: (FoodCartLine) -> Unit,
    onDecrementDish: (FoodCartLine) -> Unit,
    onCheckout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.background(SurfaceDark)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Your order",
                style = MaterialTheme.typography.titleLarge,
                color = OnBackground
            )
            if (cart != null) {
                Box(
                    modifier = Modifier
                        .background(AmberPrimary, CircleShape)
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    Text(text = cart.itemCount.toString(), style = MaterialTheme.typography.labelLarge, color = Color.Black)
                }
            }
        }
        HorizontalDivider(color = SurfaceVariant)

        if (cart == null) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "Your order is empty.\nAdd items from the menu.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = OnBackgroundSubtle,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            StoreLabel(cart)
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
            ) {
                when (cart) {
                    is Cart.InRoom -> items(cart.items, key = { it.product.id }) { item ->
                        CartLineRow(
                            name = item.product.name,
                            quantity = item.quantity,
                            subtotalCents = item.subtotalCents,
                            onAdd = { onAddStore(item.product) },
                            onDecrement = { onDecrementStore(item.product.id) }
                        )
                    }

                    is Cart.Restaurant -> items(cart.lines, key = { it.key }) { line ->
                        CartLineRow(
                            name = line.item.name,
                            quantity = line.quantity,
                            subtotalCents = line.subtotalCents,
                            onAdd = { onAddDish(line) },
                            onDecrement = { onDecrementDish(line) }
                        )
                    }
                }
            }
            CheckoutFooter(cart = cart, onCheckout = onCheckout)
        }
    }
}

@Composable
private fun StoreLabel(cart: Cart) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (cart) {
            is Cart.InRoom -> Icon(
                Icons.Default.Storefront,
                contentDescription = null,
                tint = AmberPrimary,
                modifier = Modifier.size(22.dp)
            )

            is Cart.Restaurant -> PlatformBadge(cart.restaurant.platform)
        }
        Text(
            text = cart.storeName,
            style = MaterialTheme.typography.titleSmall,
            color = OnBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CheckoutFooter(cart: Cart, onCheckout: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        HorizontalDivider(color = SurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
        if (cart is Cart.Restaurant) {
            TotalRow("Items", formatEuro(cart.itemsCents))
            TotalRow(
                "Delivery",
                if (cart.deliveryFeeCents == 0L) "Free" else formatEuro(cart.deliveryFeeCents)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Total", style = MaterialTheme.typography.titleMedium, color = OnBackground)
            Text(formatEuro(cart.totalCents), style = MaterialTheme.typography.titleMedium, color = AmberPrimary)
        }

        if (!cart.isPayable) {
            Text(
                text = "Minimum order is ${formatEuro(cart.minOrderCents)} - add ${formatEuro(cart.minOrderCents - cart.itemsCents)} more to check out.",
                style = MaterialTheme.typography.bodySmall,
                color = ErrorRed,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        Button(
            onClick = onCheckout,
            enabled = cart.isPayable,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AmberPrimary,
                disabledContainerColor = SurfaceVariant
            )
        ) {
            Text(
                text = "Checkout",
                style = MaterialTheme.typography.titleMedium,
                color = if (cart.isPayable) Color.Black else OnBackgroundSubtle
            )
        }
    }
}

@Composable
private fun TotalRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = OnBackgroundMuted)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = OnBackgroundMuted)
    }
}

@Composable
private fun CartLineRow(
    name: String,
    quantity: Int,
    subtotalCents: Long,
    onAdd: () -> Unit,
    onDecrement: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                color = OnBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatEuro(subtotalCents),
                style = MaterialTheme.typography.bodyMedium,
                color = AmberPrimary
            )
        }
        QuantitySelector(
            quantity = quantity,
            onIncrement = onAdd,
            onDecrement = onDecrement,
            buttonSize = 32.dp
        )
    }
}
