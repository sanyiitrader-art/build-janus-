package com.janus.app.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.janus.app.adb.core.AdbConnection
import com.janus.app.adb.pairing.AdbPairingClient
import com.janus.app.adb.shell.AdbShellSession
import com.janus.app.adb.sync.AdbSyncService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class RemoteScreenViewModel(
    private val pairingClient: AdbPairingClient,
    private val adbConnection: AdbConnection
) : ViewModel() {
    private val tag = "RemoteScreenViewModel"

    sealed class UiState {
        object Idle : UiState()
        data class Success(val message: String) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState

    fun runPhase4Tests() {
        viewModelScope.launch {
            _uiState.value = UiState.Idle
            try {
                testPairing()
                testConnection()
                testShell()
                testSync()
                _uiState.value = UiState.Success("✅ All Phase 4 tests passed!")
            } catch (e: Exception) {
                _uiState.value = UiState.Error("❌ Test failed: ${e.message}")
                Log.e(tag, "Phase 4 test failed", e)
            }
        }
    }

    private suspend fun testPairing() {
        Log.i(tag, "🧪 Testing pairing...")
        pairingClient.pair(
            ip = "192.168.1.100",  // Replace with Target IP
            port = 35761,          // Replace with Target pairing port
            pairingCode = "123456" // Replace with Target code
        ).getOrThrow()
    }

    private suspend fun testConnection() {
        Log.i(tag, "🧪 Testing connection...")
        adbConnection.connect("shell:")
    }

    private suspend fun testShell() {
        Log.i(tag, "🧪 Testing shell...")
        val shell = AdbShellSession(adbConnection.connect("shell:"))
        val output = shell.execute("ls -l /sdcard")
        for (line in output) Log.d(tag, line)
        shell.close()
    }

    private suspend fun testSync() {
        Log.i(tag, "🧪 Testing sync...")
        val sync = AdbSyncService(adbConnection.openSyncStream())
        val testFile = File.createTempFile("test", ".txt").apply {
            writeText("Hello, Janus!")
        }
        sync.push(testFile, "/sdcard/remote.txt")
        sync.pull("/sdcard/remote.txt", File.createTempFile("test_copy", ".txt"))
        sync.close()
    }
}
