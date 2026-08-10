package com.janus.app.adb.core

import android.util.Log
import com.janus.app.adb.core.AdbProtocol.CMD_CLSE
import com.janus.app.adb.core.AdbProtocol.CMD_OKAY
import com.janus.app.adb.core.AdbProtocol.CMD_WRTE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages a bidirectional ADB stream (e.g., shell, file push).
 *
 * ADB streams use the following flow:
 *   - Controller sends `WRTE` (data) → Target sends `OKAY` (ack).
 *   - Target sends `WRTE` (data) → Controller sends `OKAY` (ack).
 *   - Either side sends `CLSE` to close the stream.
 *
 * This class handles:
 *   - Thread-safe read/write operations.
 *   - Automatic acknowledgment (`OKAY`).
 *   - Stream closure (`CLSE`).
 *
 * Throws:
 *   - [AdbStreamException] on protocol errors (e.g., unexpected `CLSE`).
 *   - [IOException] on socket errors.
 */
class AdbStream(
    private val socket: Socket,
    private val localId: Int,
    private val remoteId: Int,
    private val onClose: () -> Unit
) {
    private val tag = "AdbStream"
    private val inputStream = socket.getInputStream()
    private val outputStream = socket.getOutputStream()
    private val isClosed = AtomicBoolean(false)
    private val readChannel = Channel<ByteArray>(Channel.UNLIMITED)

    /**
     * Writes data to the stream.
     *
     * @param data Data to write.
     * @param offset Offset in the data array.
     * @param length Number of bytes to write.
     */
    suspend fun write(data: ByteArray, offset: Int = 0, length: Int = data.size) {
        check(!isClosed.get()) { "Stream is closed" }
        withContext(Dispatchers.IO) {
            val message = AdbMessage(
                command = CMD_WRTE,
                arg0 = localId,
                arg1 = remoteId,
                payload = data.copyOfRange(offset, offset + length)
            )
            sendMessage(message)
            Log.d(tag, "WRTE sent (localId=$localId, remoteId=$remoteId, length=$length)")
        }
    }

    /**
     * Reads data from the stream.
     *
     * @return Data read, or `null` if the stream is closed.
     */
    suspend fun read(): ByteArray? = withContext(Dispatchers.IO) {
        try {
            readChannel.receive()
        } catch (e: ClosedReceiveChannelException) {
            null
        }
    }

    /**
     * Closes the stream.
     */
    fun close() {
        if (isClosed.compareAndSet(false, true)) {
            withContext(Dispatchers.IO) {
                try {
                    sendMessage(
                        AdbMessage(
                            command = CMD_CLSE,
                            arg0 = localId,
                            arg1 = remoteId,
                            payload = ByteArray(0)
                        )
                    )
                    Log.d(tag, "CLSE sent (localId=$localId, remoteId=$remoteId)")
                } catch (e: IOException) {
                    Log.w(tag, "Failed to send CLSE", e)
                } finally {
                    readChannel.close()
                    onClose()
                }
            }
        }
    }

    /**
     * Starts the stream reader (called by `AdbConnection`).
     */
    internal fun startReader() {
        Thread {
            try {
                while (!isClosed.get()) {
                    val message = receiveMessage()
                    when (message.command) {
                        CMD_WRTE -> {
                            readChannel.trySend(message.payload).getOrThrow()
                            sendOkay()
                        }
                        CMD_OKAY -> {
                            // Remote acknowledged our WRTE; no action needed.
                        }
                        CMD_CLSE -> {
                            Log.d(tag, "CLSE received (localId=$localId, remoteId=$remoteId)")
                            close()
                        }
                        else -> throw AdbStreamException("Unexpected command: ${message.command}")
                    }
                }
            } catch (e: Exception) {
                if (!isClosed.get()) {
                    Log.e(tag, "Stream reader failed", e)
                    close()
                }
            }
        }.start()
    }

    /**
     * Sends an `OKAY` acknowledgment.
     */
    private fun sendOkay() {
        sendMessage(
            AdbMessage(
                command = CMD_OKAY,
                arg0 = localId,
                arg1 = remoteId,
                payload = ByteArray(0)
            )
        )
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
        outputStream.write(buffer.array())
        outputStream.flush()
    }

    /**
     * Receives an ADB message.
     */
    private fun receiveMessage(): AdbMessage {
        val header = ByteArray(AdbMessage.HEADER_LENGTH)
        inputStream.read(header)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        val command = buffer.int
        val arg0 = buffer.int
        val arg1 = buffer.int
        val payloadLength = buffer.int
        val checksum = buffer.int
        val magic = buffer.int

        val payload = ByteArray(payloadLength)
        if (payloadLength > 0) {
            inputStream.read(payload)
        }

        val message = AdbMessage(command, arg0, arg1, payload)
        if (message.checksum != checksum || message.magic != magic) {
            throw AdbStreamException("Invalid checksum/magic")
        }
        return message
    }
}

/**
 * ADB stream errors.
 */
class AdbStreamException(message: String, cause: Throwable? = null) : Exception(message, cause)