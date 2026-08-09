package com.janus.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.janus.app.domain.model.DiscoveredDevice
import com.janus.app.domain.usecase.DiscoverDevicesUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn

/**
 * Backs the "Devices Found on Same Wi-Fi" drawer section (spec #9).
 *
 * [discoveredDevices] uses SharingStarted.WhileSubscribed: the underlying
 * NSD listener and subnet scan (both real, ongoing network activity) only
 * run while the Discovery screen is actually visible/collecting — they
 * stop automatically a few seconds after the screen is left, rather than
 * continuing to scan the network in the background indefinitely.
 *
 * Discovery failures (e.g. NSD start failure) are caught and surfaced as an
 * empty result rather than crashing the ViewModel — spec #9 explicitly
 * requires discovery to safely handle "no devices," "unreachable devices,"
 * and similar non-fatal conditions.
 */
class DeviceListViewModel(
    private val discoverDevicesUseCase: DiscoverDevicesUseCase
) : ViewModel() {

    val discoveredDevices: StateFlow<List<DiscoveredDevice>> =
        discoverDevicesUseCase()
            .catch { emit(emptyList()) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                initialValue = emptyList()
            )

    class Factory(
        private val discoverDevicesUseCase: DiscoverDevicesUseCase
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DeviceListViewModel(discoverDevicesUseCase) as T
        }
    }
}