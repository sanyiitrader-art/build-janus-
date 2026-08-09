package com.janus.app.ui.remote

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.janus.app.R

/**
 * Empty state shown in the main remote-screen area when no Target device is
 * currently connected (spec #8). Intentionally minimal — a single centered
 * message, no icon clutter, no permanent controls — so it doesn't compete
 * visually with the live video surface it will be replaced by once a device
 * connects (RemoteSurfaceView, Phase 6).
 */
@Composable
fun EmptyStateView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.no_connected_device),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}