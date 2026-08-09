package com.janus.app.ui.devicetabs

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.janus.app.domain.model.Device

/**
 * Bottom connected-device taskbar (spec #6, #13). Horizontally scrollable
 * row of DeviceTabItem entries — one per currently connected (not merely
 * known/paired) device. Always visible regardless of what's shown in the
 * main remote-screen area above it, including the empty state (spec #8).
 *
 * Phase 1: renders correctly with zero tabs (RemoteScreen currently passes
 * emptyList()). Wired to live connected-device state in Phase 9, at which
 * point onTabSelected switches the active stream (without a full ADB
 * reconnect — spec #13) and onTabLongPressed triggers the disconnect
 * confirmation dialog (spec #14).
 */
@Composable
fun DeviceTabBar(
    devices: List<Device>,
    activeDeviceId: String?,
    onTabSelected: (String) -> Unit,
    onTabLongPressed: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(MaterialTheme.colorScheme.surface)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        devices.forEach { device ->
            DeviceTabItem(
                device = device,
                isActive = device.id == activeDeviceId,
                onClick = { onTabSelected(device.id) },
                onLongClick = { onTabLongPressed(device.id) }
            )
        }
    }
}