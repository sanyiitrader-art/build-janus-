package com.janus.app.media.video

import android.util.Log
import com.janus.app.adb.core.AdbConnection
import com.janus.app.adb.core.AdbStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Transports video frames over ADB streams.
 *
 * Protocol:
 *   - Each frame is prefixed with a 12-byte header:
 *       - 4 bytes: Frame size (little-endian).
 *       - 8 bytes: Timestamp (microseconds, little-endian).
 */
class VideoTransportClient(
    private val adbConnection: AdbConnection
) {
    private val tag = "VideoTransportClient"
    private var stream: AdbStream? = null
    private val headerBuffer = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)

    /**
     * Opens the video transport stream.
     */
    suspend fun open() = withContext(Dispatchers.IO) {
        try {
            stream = adbConnection.connect("janus:video")
            Log.d(tag, "Video transport stream opened")
        } catch (e: Exception) {
            Log.e(tag, "Failed to open video stream", e)
            throw VideoTransportException("Stream open failed", e)
        }
    }

    /**
     * Sends a video frame.
     *
     * @param frame Video frame (H.264 NAL unit + timestamp).
     */
    suspend fun sendFrame(frame: VideoFrame) = withContext(Dispatchers.IO) {
        try {
            val stream = stream ?: throw VideoTransportException("Stream not open")
            headerBuffer.clear()
            headerBuffer.putInt(frame.data.size)
            headerBuffer.putLong(frame.timestampUs)
            stream.write(headerBuffer.array())
            stream.write(frame.data)
        } catch (e: Exception) {
            Log.e(tag, "Failed to send frame", e)
            throw VideoTransportException("Frame send failed", e)
        }
    }

    /**
     * Receives a video frame.
     *
     * @return Video frame (H.264 NAL unit + timestamp).
     */
    suspend fun receiveFrame(): VideoFrame = withContext(Dispatchers.IO) {
        try {
            val stream = stream ?: throw VideoTransportException("Stream not open")
            val header = ByteArray(12)
            var bytesRead = 0
            while (bytesRead < 12) {
                val read = stream.read()?.let { header.copyInto(it, bytesRead) } ?: 0
                if (read == 0) throw VideoTransportException("Stream closed")
                bytesRead += read
            }

            val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            val size = buffer.int
            val timestampUs = buffer.long
            val data = ByteArray(size)
            bytesRead = 0
            while (bytesRead < size) {
                val read = stream.read()?.let { data.copyInto(it, bytesRead) } ?: 0
                if (read == 0) throw VideoTransportException("Stream closed")
                bytesRead += read
            }
            VideoFrame(data, timestampUs)
        } catch (e: Exception) {
            Log.e(tag, "Failed to receive frame", e)
            throw VideoTransportException("Frame receive failed", e)
        }
    }

    /**
     * Closes the transport stream.
     */
    fun close() {
        stream?.close()
        stream = null
        Log.d(tag, "Video transport stream closed")
    }
}

/**
 * Video transport errors.
 */
class VideoTransportException(message: String, cause: Throwable? = null) : Exception(message, cause)
