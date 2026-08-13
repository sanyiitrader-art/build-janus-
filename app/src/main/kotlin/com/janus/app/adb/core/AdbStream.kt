package com.janus.app.adb.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A single multiplexed ADB stream (spec #48) — e.g. one shell session or
 * one sync/push transfer. Multiple AdbStreams share ONE underlying
 * AdbConnection's TCP socket; [localId]/[remoteId] are how OPEN/WRTE/OKAY/
 * CLSE messages are routed to the correct stream on both ends.
 *
 * IMPORTANT: this class does NOT hold a reference to the raw Socket, and
 * does NOT spin up its own reader thread. ADB multiplexes many streams
 * over one TCP connection, so there must be exactly ONE reader for that
 * socket at any time — that single read loop lives in AdbConnection and
 * dispatches parsed messages to the correct AdbStream (by matching the
 * message's arg1 against this stream's localId). If this stream needs to
 * read its own socket independently, two streams opened at once will race
 * reading the same underlying byte stream and corrupt each other's framed
 * messages — this exact bug shipped once already; do not reintroduce it.
 *
 * Implements the classic ADB flow-control rule: after sending a WRTE, the
 * sender must wait for a matching OKAY before sending the next WRTE on
 * that stream. [write] suspends until that OKAY arrives.
 */
class AdbStream(
    val localId: Int,
    private val sendMessage: suspend (AdbMessage) -> Unit
) {
    @Volatile
    var remoteId: Int = 0
        private set

    private val opened = CompletableDeferred<Unit>()
    private val writeAck = Channel<Unit>(Channel.CONFLATED)
    private val incoming = Channel<ByteArray>(Channel.UNLIMITED)
    private val closed = AtomicBoolean(false)

    /** Suspends until the Target has responded OKAY to our OPEN. */
    suspend fun awaitOpen() {
        opened.await()
    }

    suspend fun write(data: ByteArray) {
        check(!closed.get()) { "Cannot write to a closed AdbStream" }
        sendMessage(AdbMessage(AdbProtocol.CMD_WRTE, localId, remoteId, data))
        writeAck.receive() // suspend until the Target OKAYs this WRTE
    }

    /**
     * Suspends until the next chunk of data arrives from the Target, or
     * returns null once the stream has been closed with no more data
     * pending.
     */
    suspend fun readIncoming(): ByteArray? = incoming.receiveCatching().getOrNull()

    suspend fun close() {
        if (closed.compareAndSet(false, true)) {
            runCatching { sendMessage(AdbMessage(AdbProtocol.CMD_CLSE, localId, remoteId)) }
            incoming.close()
        }
    }

    // --- Called only by AdbConnection's shared read loop, never by external callers. ---

    internal fun onRemoteOpened(remoteStreamId: Int) {
        remoteId = remoteStreamId
        opened.complete(Unit)
    }

    internal fun onOkayReceived() {
        writeAck.trySend(Unit)
    }

    internal suspend fun onDataReceived(payload: ByteArray) {
        incoming.send(payload)
    }

    internal fun onRemoteClosed() {
        closed.set(true)
        incoming.close()
        opened.complete(Unit) // unblock any awaiter if closed before ever opening
    }
}