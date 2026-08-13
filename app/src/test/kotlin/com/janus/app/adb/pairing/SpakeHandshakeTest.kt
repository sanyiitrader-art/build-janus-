package com.janus.app.adb.pairing

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

class SpakeHandshakeTest {

    @Test
    fun `client and server derive the same shared key from one joint handshake`() {
        val pairingCode = "123456"
        val serverSocket = ServerSocket(0)

        var serverResult: SpakeHandshake.SpakeResult? = null
        val serverThread = thread {
            val serverSideSocket = serverSocket.accept()
            serverSideSocket.use {
                serverResult = SpakeHandshake.perform(
                    socket = it,
                    pairingCode = pairingCode,
                    isClient = false
                )
            }
        }

        val clientResult = Socket("localhost", serverSocket.localPort).use { clientSocket ->
            SpakeHandshake.perform(
                socket = clientSocket,
                pairingCode = pairingCode,
                isClient = true
            )
        }

        serverThread.join()
        serverSocket.close()

        assertArrayEquals(
            "Client and server must derive identical shared keys from one joint handshake",
            clientResult.sharedKey,
            requireNotNull(serverResult) { "Server thread did not produce a result" }.sharedKey
        )
    }

    @Test
    fun `mismatched pairing codes produce different keys, not an exception`() {
        val serverSocket = ServerSocket(0)

        var serverResult: SpakeHandshake.SpakeResult? = null
        val serverThread = thread {
            val serverSideSocket = serverSocket.accept()
            serverSideSocket.use {
                serverResult = SpakeHandshake.perform(
                    socket = it,
                    pairingCode = "123456",
                    isClient = false
                )
            }
        }

        val clientResult = Socket("localhost", serverSocket.localPort).use { clientSocket ->
            SpakeHandshake.perform(
                socket = clientSocket,
                pairingCode = "654321",
                isClient = true
            )
        }

        serverThread.join()
        serverSocket.close()

        assertFalse(
            "Mismatched pairing codes must NOT produce matching keys",
            clientResult.sharedKey.contentEquals(
                requireNotNull(serverResult) { "Server thread did not produce a result" }.sharedKey
            )
        )
    }
}