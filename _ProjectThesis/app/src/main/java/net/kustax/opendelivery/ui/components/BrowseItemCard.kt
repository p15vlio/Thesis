package net.kustax.opendelivery.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.LocalPizza
import androidx.compose.material.icons.filled.LunchDining
import androidx.compose.material.icons.filled.OutdoorGrill
import androidx.compose.material.icons.filled.RamenDining
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SetMeal
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SportsBar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import net.kustax.opendelivery.model.BrowseItem
import net.kustax.opendelivery.ui.theme.AmberPrimary
import net.kustax.opendelivery.ui.theme.ErrorRed
import net.kustax.opendelivery.ui.theme.OnBackground
import net.kustax.opendelivery.ui.theme.OnBackgroundMuted
import net.kustax.opendelivery.ui.theme.SurfaceDark

val BrowseItemCardHeight = 112.dp

// The one card used for every store: in-room products and restaurant dishes look identical.
@Composable
fun BrowseItemCard(
    item: BrowseItem,
    quantity: Int,
    onAddOne: () -> Unit,
    onRemoveOne: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(BrowseItemCardHeight),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Row(modifier = Modifier.fillMaxHeight()) {
            ItemImage(
                item = item,
                modifier = Modifier
                    .width(BrowseItemCardHeight)
                    .fillMaxHeight()
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = OnBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val meta = metaLine(item)
                    if (meta != null) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = ErrorRed,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = OnBackgroundMuted,
                        maxLines = if (meta != null) 1 else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatEuro(item.priceCents),
                        style = MaterialTheme.typography.titleSmall,
                        color = AmberPrimary
                    )
                    if (quantity == 0) {
                        FilledIconButton(
                            onClick = onAddOne,
                            modifier = Modifier.size(36.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = AmberPrimary)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add ${item.name}",
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        QuantitySelector(
                            quantity = quantity,
                            onIncrement = onAddOne,
                            onDecrement = onRemoveOne,
                            buttonSize = 36.dp
                        )
                    }
                }
            }
        }
    }
}

// Allergens for store products that declare any
private fun metaLine(item: BrowseItem): String? =
    item.allergens.takeIf { it.isNotEmpty() }?.let { "Allergens: ${it.joinToString(", ")}" }

@Composable
private fun ItemImage(item: BrowseItem, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
    val placeholder: @Composable () -> Unit = { ItemPlaceholder(item) }
    Box(modifier = modifier.clip(shape)) {
        if (item.imageUrl.isNullOrBlank()) {
            placeholder()
        } else {
            SubcomposeAsyncImage(
                model = item.imageUrl,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                loading = { placeholder() },
                error = { placeholder() },
                modifier = Modifier.fillMaxHeight().fillMaxWidth()
            )
        }
    }
}

// Items without a photo get a soft tint and icon derived from their category, so every category
// of a menu is told apart at a glance.
@Composable
private fun ItemPlaceholder(item: BrowseItem) {
    val tint = PLACEHOLDER_TINTS[(item.category.hashCode() and Int.MAX_VALUE) % PLACEHOLDER_TINTS.size]
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = placeholderIcon(item),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(44.dp)
        )
    }
}

private val PLACEHOLDER_TINTS = listOf(
    Color(0xFFF59E0B), Color(0xFF3B82F6), Color(0xFF10B981), Color(0xFFEF4444),
    Color(0xFF8B5CF6), Color(0xFF06B6D4), Color(0xFFEC4899), Color(0xFF84CC16)
)

private val PLACEHOLDER_ICONS: List<Pair<List<String>, ImageVector>> = listOf(
    listOf("pizza") to Icons.Default.LocalPizza,
    listOf("souvlaki", "gyros", "grill", "kalamaki", "meat") to Icons.Default.OutdoorGrill,
    listOf("salad") to Icons.Default.Eco,
    listOf("sushi", "asian", "fish", "seafood") to Icons.Default.SetMeal,
    listOf("pasta", "noodle") to Icons.Default.RamenDining,
    listOf("burger", "sandwich", "wrap") to Icons.Default.LunchDining,
    listOf("bakery", "pie", "bread") to Icons.Default.BakeryDining,
    listOf("dessert", "sweet", "cake") to Icons.Default.Cake,
    listOf("hot_drink", "coffee", "tea") to Icons.Default.Coffee,
    listOf("alcohol", "beer", "wine") to Icons.Default.SportsBar,
    listOf("beverage", "drink", "juice", "water") to Icons.Default.LocalDrink,
    listOf("snack", "side", "fries", "starter") to Icons.Default.Fastfood,
    listOf("personal_care") to Icons.Default.Spa,
    listOf("pharmacy") to Icons.Default.LocalPharmacy
)

private fun placeholderIcon(item: BrowseItem): ImageVector {
    val text = "${item.category} ${item.name}".lowercase()
    return PLACEHOLDER_ICONS.firstOrNull { (keywords, _) -> keywords.any { it in text } }?.second
        ?: if (item is BrowseItem.Store) Icons.Default.ShoppingBag else Icons.Default.Restaurant
}
