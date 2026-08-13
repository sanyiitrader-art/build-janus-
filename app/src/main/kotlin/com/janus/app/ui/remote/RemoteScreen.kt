package com.janus.app.ui.remote

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.janus.app.ui.devicetabs.DeviceTabBar

/**
 * Project Janus main screen (spec #6, #8).
 *
 * RESTORED to the working Phase 3 version after a sync issue between this
 * file and JanusNavGraph.kt. Do not change this composable's signature
 * without also updating its call site in JanusNavGraph.kt in the same
 * change — that mismatch is what caused the last build break.
 *
 * TEMPORARY (Phase 3): [onOpenDiscovery] adds a plain button to reach the
 * Discovery screen for testing, since the real swipe-right navigation
 * drawer (spec #7) isn't built until Phase 10.
 */
@Composable
fun RemoteScreen(
    onOpenDiscovery: () -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            EmptyStateView(modifier = Modifier.fillMaxSize().weight(1f))

            Button(
                onClick = onOpenDiscovery,
                modifier = Modifier.padding(16.dp)
            ) {
                Text("Devices Found on Same Wi-Fi (temp)")
            }
        }

        DeviceTabBar(
            devices = emptyList(),
            activeDeviceId = null,
            onTabSelected = {},
            onTabLongPressed = {}
        )
    }
}