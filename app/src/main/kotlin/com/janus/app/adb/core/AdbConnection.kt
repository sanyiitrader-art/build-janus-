package com.janus.app.adb.core

import android.util.Log
import com.janus.app.adb.crypto.AdbKeystoreManager
import com.janus.app.adb.core.AdbProtocol.AUTH_TYPE_RSA_PUBLIC
import com.janus.app.adb.core.AdbProtocol.AUTH_TYPE_SIGNATURE
import com.janus.app.adb.core.AdbProtocol.CMD_CNXN
import com.janus.app.adb.core.AdbProtocol.CMD_AUTH
import com.janus.app.adb.core.AdbProtocol.CMD_OPEN
import com.janus.app.adb.core.AdbProtocol.CMD_OKAY
import com.janus.app.adb.core.AdbProtocol.MAX_PAYLOAD
import com.janus.app.adb.core.AdbProtocol.VERSION
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Manages an ADB-over-TCP connection (CNXN → AUTH → OPEN).
 *
 * Steps:
 *   1. CNXN: Connect to the Target's ADB daemon (port 5555).
 *   2. AUTH: Send RSA public key or signature (if previously authorized).
 *   3. OPEN: Open streams for shell/file push.
 *
 * Throws:
 *   - [AdbConnectionException] on protocol errors (CNXN/AUTH failure).
 *   - [IOException] on network errors.
 */
class AdbConnection(
    private val keystoreManager: AdbKeystoreManager,
    private val host: String,
    private val port: Int = 5555,
    private val timeoutMillis: Int = 5000
) {
    private val tag = "AdbConnection"
    private var socket: Socket? = null
    private var nextLocalId = 1
    private val streams = mutableMapOf<Int, AdbStream>()

    /**
     * Connects to the Target and performs CNXN/AUTH handshake.
     *
     * @return [AdbStream] for the "shell:" service (or other services).
     */
    suspend fun connect(service: String): AdbStream = withContext(Dispatchers.IO) {
        try {
            // Step 1: Establish TCP socket
            socket = Socket().apply {
                connect(InetSocketAddress(host, port), timeoutMillis)
                soTimeout = timeoutMillis
            }
            Log.d(tag, "Connected to $host:$port")

            // Step 2: CNXN handshake
            performCnxnHandshake()
            Log.d(tag, "CNXN handshake succeeded")

            // Step 3: AUTH handshake
            performAuthHandshake()
            Log.d(tag, "AUTH handshake succeeded")

            // Step 4: OPEN stream for the requested service
            val localId = nextLocalId++
            val remoteId = performOpenHandshake(localId, service)
            Log.d(tag, "OPEN handshake succeeded (localId=$localId, remoteId=$remoteId)")

            AdbStream(
                socket = socket!!,
                localId = localId,
                remoteId = remoteId,
                onClose = { streams.remove(localId) }
            ).also { streams[localId] = it }
        } catch (e: Exception) {
            close()
            throw AdbConnectionException("Connection failed: ${e.message}", e)
        }
    }

    /**
     * Performs CNXN handshake (ADB protocol version + max payload).
     */
    private fun performCnxnHandshake() {
        val cnxnMessage = AdbMessage(
            command = CMD_CNXN,
            arg0 = VERSION,
            arg1 = MAX_PAYLOAD,
            payload = "host::\u0000".toByteArray(Charsets.UTF_8)
        )
        sendMessage(cnxnMessage)

        val response = receiveMessage()
        if (response.command != CMD_CNXN) {
            throw AdbConnectionException("Expected CNXN, got ${response.command}")
        }
    }

    /**
     * Performs AUTH handshake (RSA public key or signature).
     */
    private fun performAuthHandshake() {
        // Try RSA public key first
        val publicKey = keystoreManager.publicKeyBytes
        val authMessage = AdbMessage(
            command = CMD_AUTH,
            arg0 = AUTH_TYPE_RSA_PUBLIC,
            arg1 = 0,
            payload = publicKey
        )
        sendMessage(authMessage)

        val response = receiveMessage()
        when (response.command) {
            CMD_AUTH -> {
                // Target requests a signature (we're already authorized)
                if (response.arg0 != AUTH_TYPE_SIGNATURE) {
                    throw AdbConnectionException("Unsupported AUTH type: ${response.arg0}")
                }
                val signature = keystoreManager.sign(response.payload)
                sendMessage(
                    AdbMessage(
                        command = CMD_AUTH,
                        arg0 = AUTH_TYPE_SIGNATURE,
                        arg1 = 0,
                        payload = signature
                    )
                )
                val finalResponse = receiveMessage()
                if (finalResponse.command != CMD_CNXN) {
                    throw AdbConnectionException("Expected CNXN after AUTH, got ${finalResponse.command}")
                }
            }
            CMD_CNXN -> {
                // Target accepted our public key (no signature needed)
            }
            else -> throw AdbConnectionException("Expected AUTH or CNXN, got ${response.command}")
        }
    }

    /**
     * Performs OPEN handshake for a service (e.g., "shell:").
     *
     * @param localId Local stream ID.
     * @param service Service name (e.g., "shell:").
     * @return Remote stream ID.
     */
    private fun performOpenHandshake(localId: Int, service: String): Int {
        val openMessage = AdbMessage(
            command = CMD_OPEN,
            arg0 = localId,
            arg1 = 0,
            payload = "$service\u0000".toByteArray(Charsets.UTF_8)
        )
        sendMessage(openMessage)

        val response = receiveMessage()
        if (response.command != CMD_OKAY) {
            throw AdbConnectionException("OPEN failed: ${response.command}")
        }
        return response.arg0
    }

    /**
     * Sends an ADB message.
     */
    private fun sendMessage(message: AdbMessage) {
        val buffer = ByteBuffer.allocate(AdbMessage.HEADER_LENGTH + message.payload.size)
            .order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(message.command)
        buffer.putInt(message.arg0)
        buffer.putInt(message.arg1)
        buffer.putInt(message.payload.size)
        buffer.putInt(message.checksum)
        buffer.putInt(message.magic)
        buffer.put(message.payload)
        socket?.getOutputStream()?.write(buffer.array())
    }

    /**
     * Receives an ADB message.
     */
    private fun receiveMessage(): AdbMessage {
        val header = ByteArray(AdbMessage.HEADER_LENGTH)
        socket?.getInputStream()?.read(header) ?: throw IOException("Socket closed")
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        val command = buffer.int
        val arg0 = buffer.int
        val arg1 = buffer.int
        val payloadLength = buffer.int
        val checksum = buffer.int
        val magic = buffer.int

        val payload = ByteArray(payloadLength)
        if (payloadLength > 0) {
            socket?.getInputStream()?.read(payload) ?: throw IOException("Socket closed")
        }

        val message = AdbMessage(command, arg0, arg1, payload)
        if (message.checksum != checksum || message.magic != magic) {
            throw AdbConnectionException("Invalid checksum/magic")
        }
        return message
    }

    /**
     * Closes the connection and all streams.
     */
    fun close() {
        streams.values.forEach { it.close() }
        streams.clear()
        socket?.close()
        socket = null
    }
}

/**
 * ADB connection errors.
 */
class AdbConnectionException(message: String, cause: Throwable? = null) : Exception(message, cause)
