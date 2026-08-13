package com.janus.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.janus.app.adb.crypto.AdbKeystoreManager
import com.janus.app.adb.pairing.AdbPairingClient
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Drives the pairing workflow UI (spec #15): sequential IP -> port -> code
 * dialogs, then a pairing attempt, then a success/failure banner.
 */
class PairingViewModel(
    private val adbPairingClient: AdbPairingClient
) : ViewModel() {

    private val _uiState = MutableStateFlow<PairingUiState>(PairingUiState.EnterIp())
    val uiState: StateFlow<PairingUiState> = _uiState.asStateFlow()

    fun onIpEntered(ip: String) {
        val trimmed = ip.trim()
        if (trimmed.isEmpty()) return
        _uiState.value = PairingUiState.EnterPort(ip = trimmed)
    }

    fun onPortEntered(port: String) {
        val current = _uiState.value as? PairingUiState.EnterPort ?: return
        val portInt = port.trim().toIntOrNull()
        if (portInt == null || portInt !in 1..65535) {
            _uiState.value = current.copy(error = "Enter a valid port number")
            return
        }
        _uiState.value = PairingUiState.EnterCode(ip = current.ip, port = portInt)
    }

    fun onCodeEntered(code: String) {
        val current = _uiState.value as? PairingUiState.EnterCode ?: return
        val trimmed = code.trim()
        if (!trimmed.matches(Regex("\\d{6}"))) {
            _uiState.value = current.copy(error = "Pairing code must be 6 digits")
            return
        }

        _uiState.value = PairingUiState.Pairing(ip = current.ip, port = current.port)

        viewModelScope.launch {
            val result = adbPairingClient.pair(
                ip = current.ip,
                port = current.port,
                pairingCode = trimmed
            )
            _uiState.value = result.fold(
                onSuccess = { PairingUiState.Success },
                onFailure = { PairingUiState.Failure(it.message ?: "Pairing failed") }
            )
        }
    }

    fun reset() {
        _uiState.value = PairingUiState.EnterIp()
    }

    class Factory(
        private val keystoreManager: AdbKeystoreManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PairingViewModel(AdbPairingClient(keystoreManager)) as T
        }
    }
}

sealed interface PairingUiState {
    data class EnterIp(val error: String? = null) : PairingUiState
    data class EnterPort(val ip: String, val error: String? = null) : PairingUiState
    data class EnterCode(val ip: String, val port: Int, val error: String? = null) : PairingUiState
    data class Pairing(val ip: String, val port: Int) : PairingUiState
    data object Success : PairingUiState
    data class Failure(val message: String) : PairingUiState
}