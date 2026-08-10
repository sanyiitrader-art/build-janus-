package com.janus.app.adb.sync

import android.util.Log
import com.janus.app.adb.core.AdbStream
import com.janus.app.adb.core.AdbStreamException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * ADB sync service for file transfers (push/pull).
 *
 * Protocol:
 *   - Commands: 4-byte ASCII (e.g., "SEND", "RECV", "DATA", "DONE", "FAIL").
 *   - Each command is followed by a 4-byte length and payload.
 *
 * Example (push):
 *   1. Send "SEND" + remote path (e.g., "/sdcard/file.txt,0644").
 *   2. Send "DATA" chunks for file content.
 *   3. Send "DONE" + modification time.
 */
class AdbSyncService(
    private val stream: AdbStream
) {
    private val tag = "AdbSyncService"

    // ADB sync commands (4-byte ASCII)
    private object Command {
        const val SEND = "SEND"
        const val RECV = "RECV"
        const val STAT = "STAT"
        const val DENT = "DENT"
        const val DATA = "DATA"
        const val DONE = "DONE"
        const val FAIL = "FAIL"
    }

    /**
     * Pushes a file to the Target.
     *
     * @param localFile Local file to push.
     * @param remotePath Remote path (e.g., "/sdcard/file.txt").
     * @param mode File permissions (e.g., "0644").
     */
    suspend fun push(localFile: File, remotePath: String, mode: String = "0644") {
        withContext(Dispatchers.IO) {
            require(localFile.exists()) { "Local file does not exist" }
            require(remotePath.isNotEmpty()) { "Remote path cannot be empty" }

            try {
                // Step 1: Send SEND command
                val remotePathWithMode = "$remotePath,$mode"
                sendCommand(Command.SEND, remotePathWithMode)
                Log.d(tag, "SEND command sent: $remotePathWithMode")

                // Step 2: Send file data in chunks
                FileInputStream(localFile).use { input ->
                    val buffer = ByteArray(64 * 1024) // 64KB chunks
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        sendDataChunk(buffer, bytesRead)
                    }
                }

                // Step 3: Send DONE command
                val modTime = localFile.lastModified() / 1000 // Unix timestamp
                sendCommand(Command.DONE, "", modTime.toInt())
                Log.d(tag, "DONE command sent (modTime=$modTime)")

                // Step 4: Verify success
                val response = receiveCommand()
                if (response.command == Command.FAIL) {
                    throw AdbSyncException("Push failed: ${String(response.payload)}")
                }
            } catch (e: IOException) {
                throw AdbSyncException("Push failed", e)
            }
        }
    }

    /**
     * Pulls a file from the Target.
     *
     * @param remotePath Remote path (e.g., "/sdcard/file.txt").
     * @param localFile Local file to save.
     */
    suspend fun pull(remotePath: String, localFile: File) {
        withContext(Dispatchers.IO) {
            require(remotePath.isNotEmpty()) { "Remote path cannot be empty" }

            try {
                // Step 1: Send RECV command
                sendCommand(Command.RECV, remotePath)
                Log.d(tag, "RECV command sent: $remotePath")

                // Step 2: Receive file data
                FileOutputStream(localFile).use { output ->
                    while (true) {
                        val response = receiveCommand()
                        when (response.command) {
                            Command.DATA -> {
                                output.write(response.payload)
                                Log.d(tag, "Received DATA chunk (size=${response.payload.size})")
                            }
                            Command.DONE -> {
                                Log.d(tag, "DONE command received")
                                break
                            }
                            Command.FAIL -> {
                                throw AdbSyncException("Pull failed: ${String(response.payload)}")
                            }
                            else -> throw AdbSyncException("Unexpected command: ${response.command}")
                        }
                    }
                }
            } catch (e: IOException) {
                throw AdbSyncException("Pull failed", e)
            }
        }
    }

    /**
     * Sends a sync command.
     *
     * @param command 4-byte ASCII command (e.g., "SEND").
     * @param path Remote path (for SEND/RECV/STAT).
     * @param arg Additional argument (e.g., mode for SEND, modTime for DONE).
     */
    private suspend fun sendCommand(command: String, path: String, arg: Int = 0) {
        require(command.length == 4) { "Command must be 4 bytes" }

        val pathBytes = path.toByteArray(StandardCharsets.UTF_8)
        val buffer = ByteBuffer.allocate(8 + pathBytes.size)
            .order(ByteOrder.LITTLE_ENDIAN)

        buffer.put(command.toByteArray(StandardCharsets.UTF_8))
        buffer.putInt(arg)
        buffer.putInt(pathBytes.size)
        buffer.put(pathBytes)

        stream.write(buffer.array())
    }

    /**
     * Sends a DATA chunk.
     *
     * @param data Data to send.
     * @param length Number of bytes to send.
     */
    private suspend fun sendDataChunk(data: ByteArray, length: Int) {
        val buffer = ByteBuffer.allocate(8 + length)
            .order(ByteOrder.LITTLE_ENDIAN)

        buffer.put(Command.DATA.toByteArray(StandardCharsets.UTF_8))
        buffer.putInt(length)
        buffer.put(data, 0, length)

        stream.write(buffer.array())
    }

    /**
     * Receives a sync command.
     *
     * @return [SyncResponse] containing the command and payload.
     */
    private suspend fun receiveCommand(): SyncResponse {
        // Read command header (8 bytes: command + arg + length)
        val header = ByteArray(8)
        var bytesRead = 0
        while (bytesRead < 8) {
            val read = stream.read()?.let { header.copyInto(it, bytesRead) } ?: 0
            if (read == 0) throw AdbStreamException("Stream closed")
            bytesRead += read
        }

        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val command = String(header.copyOfRange(0, 4), StandardCharsets.UTF_8)
        val arg = buffer.getInt(4)
        val length = buffer.getInt(8)

        // Read payload
        val payload = if (length > 0) {
            val payloadBuffer = ByteArray(length)
            bytesRead = 0
            while (bytesRead < length) {
                val read = stream.read()?.let { payloadBuffer.copyInto(it, bytesRead) } ?: 0
                if (read == 0) throw AdbStreamException("Stream closed")
                bytesRead += read
            }
            payloadBuffer
        } else {
            ByteArray(0)
        }

        return SyncResponse(command, arg, payload)
    }

    /**
     * Closes the sync service.
     */
    fun close() {
        stream.close()
    }

    /**
     * Sync command response.
     */
    private data class SyncResponse(
        val command: String,
        val arg: Int,
        val payload: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SyncResponse) return false
            return command == other.command &&
                   arg == other.arg &&
                   payload.contentEquals(other.payload)
        }

        override fun hashCode(): Int {
            var result = command.hashCode()
            result = 31 * result + arg
            result = 31 * result + payload.contentHashCode()
            return result
        }
    }
}

/**
 * ADB sync errors.
 */
class AdbSyncException(message: String, cause: Throwable? = null) : Exception(message, cause)
