package com.janus.app.ui.discovery

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.janus.app.JanusApplication
import com.janus.app.R
import com.janus.app.domain.model.DiscoveredDevice
import com.janus.app.viewmodel.DeviceListViewModel

/**
 * "Devices Found on Same Wi-Fi" drawer section (spec #9).
 *
 * [onDeviceClick] is currently a no-op-friendly callback with no real
 * destination — tapping a discovered device is meant to begin the pairing
 * workflow (spec #15), which doesn't exist until Phase 4. The callback
 * shape is defined now so JanusNavGraph doesn't need to change again once
 * pairing lands; it just starts actually doing something.
 */
@Composable
fun DiscoveryScreen(
    onDeviceClick: (DiscoveredDevice) -> Unit = {}
) {
    val context = LocalContext.current
    val appModule = (context.applicationContext as JanusApplication).appModule
    val viewModel: DeviceListViewModel = viewModel(
        factory = DeviceListViewModel.Factory(
            discoverDevicesUseCase = appModule.discoverDevicesUseCase
        )
    )

    val discoveredDevices by viewModel.discoveredDevices.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        if (discoveredDevices.isEmpty()) {
            Text(
                text = stringResource(R.string.discovery_empty_state),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(discoveredDevices, key = { it.ipAddress }) { device ->
                    DiscoveredDeviceCard(
                        device = device,
                        onClick = { onDeviceClick(device) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}