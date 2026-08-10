package com.janus.app.media.video

import android.view.Surface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Renders video frames to a Surface.
 *
 * Usage:
 *   val renderer = RemoteRenderer(surface, h264Decoder, transportClient)
 *   renderer.start()
 */
class RemoteRenderer(
    private val surface: Surface,
    private val decoder: H264Decoder,
    private val transportClient: VideoTransportClient,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private var isRunning = false

    /**
     * Starts the rendering loop.
     */
    fun start() {
        isRunning = true
        scope.launch {
            decoder.configure()
            while (isRunning) {
                val frame = transportClient.receiveFrame()
                decoder.decode(frame)
            }
        }
    }

    /**
     * Stops the rendering loop.
     */
    fun stop() {
        isRunning = false
        decoder.release()
    }
}
