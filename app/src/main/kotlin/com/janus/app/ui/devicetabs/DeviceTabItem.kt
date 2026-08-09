package com.janus.app.ui.devicetabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.janus.app.domain.model.Device

/**
 * A single tab in the bottom device taskbar (spec #6, #13). Tap switches to
 * this device's stream (onClick); long-press opens the disconnect
 * confirmation dialog (onLongClick, spec #14).
 *
 * Uses combinedClickable directly rather than a Button/Card composable so
 * both tap and long-press gestures are handled on the same element with one
 * InteractionSource — avoids the double-ripple/conflicting-gesture issues
 * that come from nesting a long-press detector inside a clickable Card.
 *
 * combinedClickable is part of Compose Foundation's experimental API surface
 * (subject to change in future Foundation releases) — the OptIn below is
 * required by the compiler, not optional; it does not weaken any guarantee
 * this file relies on, since the tap/long-press behavior is exercised
 * directly rather than through any API detail that could silently change.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DeviceTabItem(
    device: Device,
    isActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isActive) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (isActive) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier
            .padding(horizontal = 4.dp)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
        contentColor = contentColor
    ) {
        Text(
            text = device.displayName,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}