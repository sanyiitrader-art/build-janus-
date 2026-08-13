package com.janus.app.adb.server

import android.content.Context
import android.util.Log
import com.janus.app.adb.core.AdbConnection
import com.janus.app.adb.shell.AdbShellSession
import com.janus.app.adb.sync.AdbSyncService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Launches the Janus Target server on a remote device via ADB (spec #19, #48).
 *
 * The Target server is NOT an installed APK — it runs as a temporary
 * process via `app_process`, under the shell UID's existing trust level.
 *
 * Each shell command opens its OWN stream via
 * `adbConnection.openStream("shell:$command")` — matching real one-shot
 * `adb shell <command>` semantics. Multiple commands that must run
 * together are joined with `&&` into a single command string.
 */
class AdbTargetServerLauncher(
    private val context: Context,
    private val adbConnection: AdbConnection
) {
    private val tag = "AdbTargetServerLauncher"
    private val remoteDir = "/data/local/tmp/janus-server"
    private val remoteZip = "$remoteDir/server.zip"
    private val remoteExecutable = "$remoteDir/targetserver.jar"

    suspend fun launch(
        targetServerBytes: ByteArray,
        port: Int = 8080
    ): Int = withContext(Dispatchers.IO) {
        try {
            val zipFile = createServerZip(targetServerBytes)
            Log.d(tag, "Server ZIP created: ${zipFile.absolutePath}")

            pushServerZip(zipFile)
            Log.d(tag, "Server ZIP pushed to $remoteZip")

            extractServerZip()
            Log.d(tag, "Server ZIP extracted to $remoteDir")

            val pid = executeServer(port)
            Log.d(tag, "Server launched (PID=$pid)")
            pid
        } catch (e: Exception) {
            throw TargetServerException("Failed to launch server", e)
        }
    }

    private fun createServerZip(targetServerBytes: ByteArray): File {
        val zipFile = File.createTempFile("janus-server", ".zip", context.cacheDir)
        ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
            zip.putNextEntry(ZipEntry("targetserver.jar"))
            zip.write(targetServerBytes)
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("launch.sh"))
            zip.write(
                """
                #!/system/bin/sh
                exec app_process -Djava.class.path=$remoteExecutable / com.janus.targetserver.Main "$@"
                """.trimIndent().toByteArray(StandardCharsets.UTF_8)
            )
            zip.closeEntry()
        }
        return zipFile
    }

    private suspend fun runShellCommand(command: String): String {
        val shell = AdbShellSession(adbConnection.openStream("shell:$command"))
        try {
            return shell.readAll()
        } finally {
            shell.close()
        }
    }

    private suspend fun pushServerZip(zipFile: File) {
        val syncStream = adbConnection.openStream("sync:")
        val syncService = AdbSyncService(syncStream)
        try {
            runShellCommand("mkdir -p $remoteDir")
            syncService.push(zipFile, remoteZip, "0644")
        } finally {
            syncService.close()
        }
    }

    private suspend fun extractServerZip() {
        runShellCommand("unzip -o $remoteZip -d $remoteDir && chmod 755 $remoteDir/launch.sh")
    }

    private suspend fun executeServer(port: Int): Int {
        val output = runShellCommand(
            "app_process -Djava.class.path=$remoteExecutable / com.janus.targetserver.Main $port & echo $!"
        )
        return output.trim().toIntOrNull()
            ?: throw TargetServerException("Failed to parse PID from output: $output")
    }

    suspend fun kill(pid: Int) {
        runShellCommand("kill $pid")
        Log.d(tag, "Server killed (PID=$pid)")
    }
}

class TargetServerException(message: String, cause: Throwable? = null) : Exception(message, cause)