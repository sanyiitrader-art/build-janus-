package com.janus.app.adb.shell

import com.janus.app.adb.core.AdbStream
import java.nio.charset.StandardCharsets

/**
 * Wraps an [AdbStream] already opened for a specific one-shot shell command
 * (spec #48) — i.e. the caller must open the stream as
 * `adbConnection.openStream("shell:$command")`, with the command embedded
 * in the OPEN service string, matching how `adb shell <command>` works:
 * one stream = one command = its full output, and the stream closes on
 * its own once the command exits on the Target.
 *
 * This does NOT support sending multiple sequential commands down one
 * already-open session — open a new AdbShellSession per command instead,
 * or join multiple commands with `&&` into a single command string before
 * opening the stream.
 */
class AdbShellSession(private val stream: AdbStream) {

    /**
     * Suspends until the command's stream closes, returning everything it
     * wrote to stdout/stderr as one decoded UTF-8 string. Adequate for the
     * short-lived admin commands this is used for (mkdir, unzip, chmod,
     * launching/killing the target-server process) — not intended for
     * commands with unbounded or very large output.
     */
    suspend fun readAll(): String {
        val buffer = StringBuilder()
        while (true) {
            val chunk = stream.readIncoming() ?: break
            buffer.append(String(chunk, StandardCharsets.UTF_8))
        }
        return buffer.toString()
    }

    suspend fun close() {
        stream.close()
    }
}