package com.janus.app.adb.shell

import android.util.Log
import com.janus.app.adb.core.AdbStream
import com.janus.app.adb.core.AdbStreamException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * Executes shell commands over an ADB stream.
 *
 * Usage:
 *   val session = AdbShellSession(stream)
 *   session.execute("ls -l").collect { output ->
 *       println(output)
 *   }
 *
 * The shell service ("shell:") must be opened by the caller via [AdbConnection].
 */
class AdbShellSession(
    private val stream: AdbStream
) {
    private val tag = "AdbShellSession"
    private val buffer = ByteArrayOutputStream()
    private val outputChannel = Channel<String>(Channel.UNLIMITED)

    /**
     * Executes a shell command and streams output.
     *
     * @param command Shell command (e.g., "ls -l").
     * @return [Channel] emitting output lines.
     */
    suspend fun execute(command: String): Channel<String> = withContext(Dispatchers.IO) {
        try {
            // Send the command
            stream.write("$command\n".toByteArray(StandardCharsets.UTF_8))
            Log.d(tag, "Command sent: $command")

            // Start a coroutine to read output
            Thread {
                try {
                    while (true) {
                        val data = stream.read() ?: break
                        buffer.write(data)
                        flushBuffer()
                    }
                } catch (e: AdbStreamException) {
                    Log.e(tag, "Stream error", e)
                } finally {
                    flushBuffer()
                    outputChannel.close()
                }
            }.start()
        } catch (e: IOException) {
            Log.e(tag, "Failed to execute command", e)
            outputChannel.close(e)
        }
        outputChannel
    }

    /**
     * Flushes the buffer to the output channel.
     */
    private fun flushBuffer() {
        if (buffer.size() > 0) {
            val output = buffer.toString(StandardCharsets.UTF_8.name())
            buffer.reset()
            output.split("\n").forEach { line ->
                if (line.isNotEmpty()) {
                    outputChannel.trySend(line).getOrThrow()
                }
            }
        }
    }

    /**
     * Closes the session.
     */
    fun close() {
        stream.close()
        outputChannel.close()
    }
}