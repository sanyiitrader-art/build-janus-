package com.janus.app.adb.core

import com.janus.app.adb.crypto.AdbKeystoreManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Owns a single ADB-over-TCP connection: the socket, the CNXN/AUTH
 * handshake, and the ONE shared read loop that demultiplexes incoming
 * messages to the correct [AdbStream] (spec #48).
 *
 * IMPORTANT — single reader invariant: only this class ever reads from the
 * underlying socket, both during handshake and for the connection's entire
 * lifetime afterward. AdbStream never touches the socket directly. This is
 * required by the protocol: many streams are multiplexed over one TCP
 * connection, so two independent readers on that same socket would race
 * and corrupt framed messages for both streams.
 *
 * Routing rule: for WRTE/OKAY/CLSE messages, `message.arg1` identifies
 * which of OUR streams (by localId) the message is destined for — this
 * holds uniformly whether it's the OKAY response to our OPEN or a later
 * WRTE/OKAY/CLSE on an already-open stream.
 */
class AdbConnection(
    private val keystoreManager: AdbKeystoreManager,
    private val host: String,
    private val port: Int,
    private val connectTimeoutMillis: Int = 5_000,
    private val authorizationWaitTimeoutMillis: Int = 60_000
) {
    private var socket: Socket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    private val writeMutex = Mutex()
    private val streams = ConcurrentHashMap<Int, AdbStream>()
    private val nextLocalId = AtomicInteger(1)

    private val connectionJob = SupervisorJob()
    private val connectionScope = CoroutineScope(Dispatchers.IO + connectionJob)

    /**
     * Establishes the TCP connection and performs the full CNXN/AUTH
     * handshake. Suspends for up to [authorizationWaitTimeoutMillis] if the
     * Target requires manual authorization (spec #18) — the caller should
     * show a "Waiting for authorization on Target device..." state while
     * this suspends.
     */
    suspend fun connect() = withContext(Dispatchers.IO) {
        val sock = Socket().apply {
            connect(InetSocketAddress(host, port), connectTimeoutMillis)
            soTimeout = connectTimeoutMillis
        }
        socket = sock
        input = sock.getInputStream()
        output = sock.getOutputStream()

        performHandshake()

        // Handshake complete — from here on, reads may legitimately block
        // for a long time waiting on the next message (shell output,
        // etc.), so remove the short handshake timeout.
        sock.soTimeout = 0

        startReadLoop()
    }

    /**
     * Opens a new multiplexed stream for [service] (e.g. "shell:" or
     * "sync:"), suspending until the Target acknowledges the OPEN.
     */
    suspend fun openStream(service: String): AdbStream {
        val localId = nextLocalId.getAndIncrement()
        val stream = AdbStream(localId) { message -> sendMessage(message) }
        streams[localId] = stream

        sendMessage(
            AdbMessage(
                command = AdbProtocol.CMD_OPEN,
                arg0 = localId,
                arg1 = 0,
                payload = "$service\u0000".toByteArray(Charsets.UTF_8)
            )
        )
        stream.awaitOpen()
        return stream
    }

    fun close() {
        connectionJob.cancel()
        streams.clear()
        runCatching { socket?.close() }
        socket = null
        input = null
        output = null
    }

    // --- Handshake ---

    private suspend fun performHandshake() {
        sendMessage(
            AdbMessage(
                command = AdbProtocol.CMD_CNXN,
                arg0 = AdbProtocol.CONNECT_VERSION,
                arg1 = AdbProtocol.CONNECT_MAX_DATA,
                payload = AdbProtocol.CONNECT_PAYLOAD.toByteArray(Charsets.UTF_8)
            )
        )

        val response = readMessage()
        when (response.command) {
            AdbProtocol.CMD_CNXN -> return // already authorized from a prior pairing

            AdbProtocol.CMD_AUTH -> {
                if (response.arg0 != AdbProtocol.AUTH_TYPE_TOKEN) {
                    throw AdbConnectionException("Unexpected AUTH sub-type: ${response.arg0}")
                }
                handleAuthToken(response.payload)
            }

            else -> throw AdbConnectionException("Expected CNXN or AUTH, got ${response.command}")
        }
    }

    /**
     * ADB's real client behavior: try signing the token with our stored
     * key FIRST (matches a previously-authorized key without requiring
     * re-approval). Only if the Target rejects that (responds with another
     * AUTH rather than CNXN) do we send our public key and wait for the
     * user's manual approval (spec #18).
     */
    private suspend fun handleAuthToken(token: ByteArray) {
        val signature = keystoreManager.signAuthToken(token)
        sendMessage(
            AdbMessage(
                command = AdbProtocol.CMD_AUTH,
                arg0 = AdbProtocol.AUTH_TYPE_SIGNATURE,
                arg1 = 0,
                payload = signature
            )
        )

        val afterSignature = readMessage()
        when (afterSignature.command) {
            AdbProtocol.CMD_CNXN -> return // signature already recognized

            AdbProtocol.CMD_AUTH -> {
                // Not yet authorized -- send our public key, then wait for
                // the Target owner to manually approve (spec #18). This is
                // the step that can legitimately take a while.
                val publicKeyBytes = keystoreManager.getAdbFormattedPublicKey()
                    .toByteArray(Charsets.UTF_8)
                sendMessage(
                    AdbMessage(
                        command = AdbProtocol.CMD_AUTH,
                        arg0 = AdbProtocol.AUTH_TYPE_RSA_PUBLIC_KEY,
                        arg1 = 0,
                        payload = publicKeyBytes
                    )
                )

                val approved = readMessage(timeoutOverrideMillis = authorizationWaitTimeoutMillis)
                if (approved.command != AdbProtocol.CMD_CNXN) {
                    throw AdbConnectionException(
                        "Authorization was not granted on the Target (expected CNXN, got ${approved.command})"
                    )
                }
            }

            else -> throw AdbConnectionException("Unexpected response after AUTH signature: ${afterSignature.command}")
        }
    }

    // --- Send / receive plumbing ---

    private suspend fun sendMessage(message: AdbMessage) {
        writeMutex.withLock {
            val out = output ?: throw IOException("Connection not open")
            out.write(message.encode())
            out.flush()
        }
    }

    private fun readMessage(timeoutOverrideMillis: Int? = null): AdbMessage {
        val sock = socket ?: throw IOException("Connection not open")
        val previousTimeout = sock.soTimeout
        if (timeoutOverrideMillis != null) sock.soTimeout = timeoutOverrideMillis

        try {
            val headerBytes = readExactly(AdbProtocol.MESSAGE_HEADER_SIZE)
            val header = AdbMessage.decodeHeader(headerBytes)
            val payload = if (header.dataLength > 0) readExactly(header.dataLength) else ByteArray(0)

            val actualChecksum = AdbMessage.computeChecksum(payload)
            if (actualChecksum != header.dataChecksum) {
                throw AdbConnectionException(
                    "Payload checksum mismatch: expected ${header.dataChecksum}, got $actualChecksum"
                )
            }

            return AdbMessage(header.command, header.arg0, header.arg1, payload)
        } finally {
            if (timeoutOverrideMillis != null) sock.soTimeout = previousTimeout
        }
    }

    private fun readExactly(byteCount: Int): ByteArray {
        val inStream = input ?: throw IOException("Connection not open")
        val buffer = ByteArray(byteCount)
        var offset = 0
        while (offset < byteCount) {
            val read = inStream.read(buffer, offset, byteCount - offset)
            if (read == -1) throw IOException("Socket closed while reading (got $offset of $byteCount bytes)")
            offset += read
        }
        return buffer
    }

    // --- Steady-state read loop (post-handshake) ---

    private fun startReadLoop() {
        connectionScope.launch {
            try {
                while (isActive) {
                    val message = readMessage()
                    dispatch(message)
                }
            } catch (e: Exception) {
                streams.values.forEach { it.onRemoteClosed() }
                streams.clear()
            }
        }
    }

    private suspend fun dispatch(message: AdbMessage) {
        val stream = streams[message.arg1] ?: return // unknown/already-closed stream; ignore

        when (message.command) {
            AdbProtocol.CMD_OKAY -> {
                if (stream.remoteId == 0) {
                    stream.onRemoteOpened(message.arg0)
                } else {
                    stream.onOkayReceived()
                }
            }

            AdbProtocol.CMD_WRTE -> {
                // Protocol requires acking every WRTE with OKAY.
                sendMessage(
                    AdbMessage(AdbProtocol.CMD_OKAY, stream.localId, stream.remoteId)
                )
                stream.onDataReceived(message.payload)
            }

            AdbProtocol.CMD_CLSE -> {
                stream.onRemoteClosed()
                streams.remove(message.arg1)
            }

            else -> {
                // CNXN/AUTH/SYNC are not expected post-handshake; ignore
                // rather than crash the whole connection over one
                // unexpected message.
            }
        }
    }
}

class AdbConnectionException(message: String, cause: Throwable? = null) : Exception(message, cause)