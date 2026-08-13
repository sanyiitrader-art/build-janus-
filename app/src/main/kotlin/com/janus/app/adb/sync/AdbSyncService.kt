package com.janus.app.adb.sync

import com.janus.app.adb.core.AdbStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * ADB sync service for file transfers (push/pull) — spec #19, #48 (used to
 * push the target-server payload to the Target).
 *
 * Real ADB sync wire format: each request/response is an 8-byte header —
 * 4-byte ASCII command id + 4-byte little-endian length — followed by
 * exactly that many payload bytes. There is no separate "arg" field.
 * DONE is a special case: the modification timestamp goes directly in the
 * length field, with zero payload bytes.
 *
 * Runs entirely over one [AdbStream] (opened by the caller via
 * `adbConnection.openStream("sync:")`), NOT a raw InputStream/OutputStream
 * — AdbStream's readIncoming() delivers one WRTE payload chunk at a time,
 * which does not necessarily align with sync-protocol header/payload
 * boundaries, so this class buffers leftover bytes across reads via
 * [readExactly].
 */
class AdbSyncService(private val stream: AdbStream) {

    private object Command {
        const val SEND = "SEND"
        const val RECV = "RECV"
        const val DATA = "DATA"
        const val DONE = "DONE"
        const val FAIL = "FAIL"
        const val OKAY = "OKAY"
    }

    private var leftover: ByteArray = ByteArray(0)
    private var leftoverOffset: Int = 0

    /** Pushes [localFile] to [remotePath] on the Target with the given [mode] (e.g. "0644"). */
    suspend fun push(localFile: File, remotePath: String, mode: String = "0644") {
        require(localFile.exists()) { "Local file does not exist: ${localFile.path}" }
        require(remotePath.isNotEmpty()) { "Remote path cannot be empty" }

        sendHeaderAndPayload(Command.SEND, "$remotePath,$mode".toByteArray(StandardCharsets.UTF_8))

        FileInputStream(localFile).use { input ->
            val buffer = ByteArray(SYNC_DATA_MAX)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                sendHeaderAndPayload(Command.DATA, buffer.copyOf(bytesRead))
            }
        }

        val modTimeSeconds = (localFile.lastModified() / 1000).toInt()
        sendDoneWithTimestamp(modTimeSeconds)

        val response = receiveHeader()
        if (response.command == Command.FAIL) {
            val message = String(readExactly(response.length), StandardCharsets.UTF_8)
            throw AdbSyncException("Push failed: $message")
        }
    }

    /** Pulls [remotePath] from the Target into [localFile]. */
    suspend fun pull(remotePath: String, localFile: File) {
        require(remotePath.isNotEmpty()) { "Remote path cannot be empty" }

        sendHeaderAndPayload(Command.RECV, remotePath.toByteArray(StandardCharsets.UTF_8))

        FileOutputStream(localFile).use { output ->
            while (true) {
                val header = receiveHeader()
                when (header.command) {
                    Command.DATA -> {
                        val chunk = readExactly(header.length)
                        output.write(chunk)
                    }
                    Command.DONE -> break
                    Command.FAIL -> {
                        val message = String(readExactly(header.length), StandardCharsets.UTF_8)
                        throw AdbSyncException("Pull failed: $message")
                    }
                    else -> throw AdbSyncException("Unexpected sync response: ${header.command}")
                }
            }
        }
    }

    suspend fun close() {
        stream.close()
    }

    // --- Wire format helpers ---

    private suspend fun sendHeaderAndPayload(command: String, payload: ByteArray) {
        val buffer = ByteBuffer.allocate(SYNC_HEADER_SIZE + payload.size)
            .order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(command.toByteArray(StandardCharsets.UTF_8))
        buffer.putInt(payload.size)
        buffer.put(payload)
        stream.write(buffer.array())
    }

    private suspend fun sendDoneWithTimestamp(modTimeSeconds: Int) {
        val buffer = ByteBuffer.allocate(SYNC_HEADER_SIZE)
            .order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(Command.DONE.toByteArray(StandardCharsets.UTF_8))
        buffer.putInt(modTimeSeconds) // DONE's "length" field IS the timestamp, no payload follows
        stream.write(buffer.array())
    }

    private suspend fun receiveHeader(): SyncHeader {
        val headerBytes = readExactly(SYNC_HEADER_SIZE)
        val command = String(headerBytes.copyOfRange(0, 4), StandardCharsets.UTF_8)
        val length = ByteBuffer.wrap(headerBytes, 4, 4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .int
        return SyncHeader(command, length)
    }

    /**
     * Reads exactly [count] bytes, pulling from any leftover bytes from a
     * previous AdbStream chunk first, then requesting more chunks via
     * readIncoming() as needed.
     */
    private suspend fun readExactly(count: Int): ByteArray {
        val result = ByteArray(count)
        var filled = 0

        while (filled < count) {
            if (leftoverOffset >= leftover.size) {
                leftover = stream.readIncoming()
                    ?: throw AdbSyncException("Stream closed while expecting $count bytes (got $filled)")
                leftoverOffset = 0
            }

            val available = leftover.size - leftoverOffset
            val toCopy = minOf(available, count - filled)
            System.arraycopy(leftover, leftoverOffset, result, filled, toCopy)
            leftoverOffset += toCopy
            filled += toCopy
        }

        return result
    }

    private data class SyncHeader(val command: String, val length: Int)

    private companion object {
        const val SYNC_HEADER_SIZE = 8 // 4-byte command id + 4-byte length
        const val SYNC_DATA_MAX = 64 * 1024 // matches AOSP's SYNC_DATA_MAX
    }
}

class AdbSyncException(message: String, cause: Throwable? = null) : Exception(message, cause)