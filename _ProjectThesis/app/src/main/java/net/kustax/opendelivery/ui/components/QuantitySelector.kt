package net.kustax.opendelivery.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.kustax.opendelivery.ui.theme.AmberPrimary
import net.kustax.opendelivery.ui.theme.OnBackgroundMuted

@Composable
fun QuantitySelector(
    quantity: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    modifier: Modifier = Modifier,
    minQuantity: Int = 0,
    maxQuantity: Int = 99,
    buttonSize: Dp = 40.dp
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (buttonSize < 40.dp) 8.dp else 12.dp)
    ) {
        FilledIconButton(
            onClick = onDecrement,
            enabled = quantity > minQuantity,
            modifier = Modifier.size(buttonSize),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = AmberPrimary,
                disabledContainerColor = AmberPrimary.copy(alpha = 0.3f)
            )
        ) {
            Icon(Icons.Default.Remove, contentDescription = "Remove one", tint = Color.Black, modifier = Modifier.size(20.dp))
        }

        Text(
            text = quantity.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = if (quantity > minQuantity) OnBackgroundMuted else OnBackgroundMuted.copy(alpha = 0.4f)
        )

        FilledIconButton(
            onClick = onIncrement,
            enabled = quantity < maxQuantity,
            modifier = Modifier.size(buttonSize),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = AmberPrimary,
                disabledContainerColor = AmberPrimary.copy(alpha = 0.3f)
            )
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add one", tint = Color.Black, modifier = Modifier.size(20.dp))
        }
    }
}
