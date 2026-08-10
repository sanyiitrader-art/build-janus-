package com.janus.app.media.video

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

/**
 * Hardware-accelerated H.264 decoder for video frames.
 *
 * Features:
 *   - Configurable resolution/bitrate.
 *   - Timestamp support for audio/video sync.
 *   - Surface-based rendering (low latency).
 */
class H264Decoder(
    private val width: Int,
    private val height: Int,
    private val surface: Surface
) {
    private val tag = "H264Decoder"
    private var decoder: MediaCodec? = null
    private var isRunning = false

    /**
     * Configures the decoder.
     */
    suspend fun configure() = withContext(Dispatchers.IO) {
        try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
            decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
                configure(format, surface, null, 0)
                start()
            }
            isRunning = true
            Log.d(tag, "Decoder configured for ${width}x$height")
        } catch (e: Exception) {
            Log.e(tag, "Failed to configure decoder", e)
            throw VideoDecoderException("Decoder configuration failed", e)
        }
    }

    /**
     * Decodes an H.264 frame.
     *
     * @param frame H.264 NAL unit (with timestamp).
     */
    suspend fun decode(frame: VideoFrame) = withContext(Dispatchers.IO) {
        check(isRunning) { "Decoder is not running" }
        try {
            val decoder = decoder ?: throw VideoDecoderException("Decoder not initialized")
            val inputBufferId = decoder.dequeueInputBuffer(10_000)
            if (inputBufferId >= 0) {
                val inputBuffer = decoder.getInputBuffer(inputBufferId) ?: return@withContext
                inputBuffer.put(frame.data)
                decoder.queueInputBuffer(
                    inputBufferId,
                    0,
                    frame.data.size,
                    frame.timestampUs,
                    0
                )
            }

            val bufferInfo = MediaCodec.BufferInfo()
            val outputBufferId = decoder.dequeueOutputBuffer(bufferInfo, 10_000)
            when {
                outputBufferId >= 0 -> decoder.releaseOutputBuffer(outputBufferId, true)
                outputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER -> return@withContext
                else -> throw VideoDecoderException("Unexpected output buffer status: $outputBufferId")
            }
        } catch (e: Exception) {
            Log.e(tag, "Decoding failed", e)
            throw VideoDecoderException("Decoding failed", e)
        }
    }

    /**
     * Releases the decoder.
     */
    fun release() {
        isRunning = false
        decoder?.stop()
        decoder?.release()
        decoder = null
        Log.d(tag, "Decoder released")
    }
}

/**
 * Video frame with timestamp.
 */
data class VideoFrame(
    val data: ByteArray,
    val timestampUs: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VideoFrame) return false
        return data.contentEquals(other.data) && timestampUs == other.timestampUs
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + timestampUs.hashCode()
        return result
    }
}

/**
 * Video decoding errors.
 */
class VideoDecoderException(message: String, cause: Throwable? = null) : Exception(message, cause)
