package com.janus.app.adb.pairing

import org.junit.Assert.assertArrayEquals
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

class SpakeHandshakeTest {
    @Test
    fun testSpake2Handshake() {
        // Test vector: pairing code "123456".
        val pairingCode = "123456"

        // Start a mock server in a background thread.
        val serverSocket = ServerSocket(0)  // Random port
        val serverThread = thread {
            val clientSocket = serverSocket.accept()
            SpakeHandshake.perform(
                socket = clientSocket,
                pairingCode = pairingCode,
                isClient = false
            )
            clientSocket.close()
        }

        // Client connects to the mock server.
        val clientSocket = Socket("localhost", serverSocket.localPort)
        val clientResult = SpakeHandshake.perform(
            socket = clientSocket,
            pairingCode = pairingCode,
            isClient = true
        )
        clientSocket.close()

        // Wait for server to finish.
        serverThread.join()
        serverSocket.close()

        // Both sides should derive the same shared key.
        assertArrayEquals(
            "Client and server shared keys must match",
            clientResult.sharedKey,
            SpakeHandshake.perform(
                socket = Socket("localhost", serverSocket.localPort).apply { close() },
                pairingCode = pairingCode,
                isClient = false
            ).sharedKey
        )
    }

    @Test(expected = PairingException::class)
    fun testInvalidPairingCode() {
        val serverSocket = ServerSocket(0)
        thread {
            val clientSocket = serverSocket.accept()
            SpakeHandshake.perform(
                socket = clientSocket,
                pairingCode = "123456",  // Server expects this
                isClient = false
            )
            clientSocket.close()
        }

        val clientSocket = Socket("localhost", serverSocket.localPort)
        SpakeHandshake.perform(
            socket = clientSocket,
            pairingCode = "654321",  // Client sends wrong code
            isClient = true
        )
    }
}
