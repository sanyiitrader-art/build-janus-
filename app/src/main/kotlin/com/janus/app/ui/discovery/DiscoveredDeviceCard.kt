package com.janus.app.ui.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.janus.app.domain.model.DiscoveredDevice
import com.janus.app.domain.model.DiscoverySource

/**
 * A single row in the "Devices Found on Same Wi-Fi" list (spec #9).
 *
 * Shows the discovery source distinction from spec #9's confidence model:
 * NSD results (a real advertised service) are labeled plainly, while
 * subnet-scan results are labeled "possible device" — since an open port
 * alone does not confirm the device is actually Janus/ADB-compatible.
 */
@Composable
fun DiscoveredDeviceCard(
    device: DiscoveredDevice,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(10.dp)
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = device.serviceName ?: device.ipAddress,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${device.ipAddress}:${device.port}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = when (device.source) {
                    DiscoverySource.NSD -> "Wireless Debugging detected"
                    DiscoverySource.SUBNET_SCAN -> "Possible device (unconfirmed)"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}