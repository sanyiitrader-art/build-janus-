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
 * Note: Modifier.weight() below is a MEMBER function of ColumnScope
 * (declared inside the ColumnScope interface), not a top-level extension —
 * it is automatically available inside this Column's content lambda with
 * no import required. Do not add an explicit import for "weight"; doing so
 * resolves to an unrelated internal Compose Foundation symbol and breaks
 * compilation.
 *
 * TEMPORARY (Phase 3): [onOpenDiscovery] adds a plain button to reach the
 * Discovery screen for testing, since the real swipe-right navigation
 * drawer (spec #7) isn't built until Phase 10. This button and its call
 * site in JanusNavGraph should be removed once the drawer exists — it is
 * not part of the final UI design.
 *
 * Phase 1 has no live connections yet, so the top region always shows
 * EmptyStateView and the tab bar always shows zero tabs — wired to real
 * state starting Phase 2 (persistence) and Phase 9 (multi-device tabs).
 * The swipe-right navigation drawer (spec #7) attaches starting Phase 10.
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

            // TEMPORARY — remove once the swipe-right drawer (Phase 10)
            // provides real navigation to Discovery.
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