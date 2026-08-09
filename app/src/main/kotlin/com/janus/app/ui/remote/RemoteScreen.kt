package com.janus.app.ui.remote

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.weight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.janus.app.ui.devicetabs.DeviceTabBar

/**
 * Project Janus main screen (spec #6, #8).
 *
 * Structure: the remote video/empty-state region dominates the screen
 * (weight(1f)) with the connected-device tab bar pinned below it — matching
 * the conceptual layout from the spec:
 *
 *   ┌─────────────────────────────┐
 *   │      TARGET REMOTE SCREEN    │
 *   ├─────────────────────────────┤
 *   │ Device A │ Device B │ ... → │
 *   └─────────────────────────────┘
 *
 * Phase 1 has no live connections yet, so the top region always shows
 * EmptyStateView and the tab bar always shows zero tabs — both wired to
 * real state (RemoteScreenViewModel + DeviceListViewModel) starting in
 * Phase 2 (device persistence) and Phase 9 (multi-device tabs). The
 * swipe-right navigation drawer (spec #7) attaches to this screen starting
 * in Phase 10 — RemoteSurfaceView (the actual live video surface, spec #28)
 * replaces EmptyStateView's role here once video lands in Phase 6.
 */
@Composable
fun RemoteScreen() {
    Column(modifier = Modifier.fillMaxSize()) {
        EmptyStateView(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        )

        DeviceTabBar(
            devices = emptyList(),
            activeDeviceId = null,
            onTabSelected = {},
            onTabLongPressed = {}
        )
    }
}