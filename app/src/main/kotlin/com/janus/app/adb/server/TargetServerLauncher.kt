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
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Launches the Janus Target server on a remote device via ADB.
 *
 * Steps:
 *   1. Package the Target server (from `:targetserver` module) into a ZIP.
 *   2. Push the ZIP to the Target device (e.g., `/data/local/tmp/janus-server.zip`).
 *   3. Extract the ZIP on the Target.
 *   4. Execute the server via `app_process`.
 *
 * The Target server is **not** an installed APK. It runs as a temporary process
 * using Android's `app_process` tool.
 *
 * Throws:
 *   - [TargetServerException] on packaging/transfer/execution errors.
 *   - [IOException] on file I/O errors.
 */
class AdbTargetServerLauncher(
    private val context: Context,
    private val adbConnection: AdbConnection
) {
    private val tag = "AdbTargetServerLauncher"
    private val remoteDir = "/data/local/tmp/janus-server"
    private val remoteZip = "$remoteDir/server.zip"
    private val remoteExecutable = "$remoteDir/targetserver.jar"

    /**
     * Launches the Target server.
     *
     * @param targetServerBytes Prebuilt Target server JAR (from `:targetserver` module).
     * @param port Port for the server to listen on (e.g., 8080).
     * @return PID of the launched server process.
     */
    suspend fun launch(
        targetServerBytes: ByteArray,
        port: Int = 8080
    ): Int = withContext(Dispatchers.IO) {
        try {
            // Step 1: Package the server into a ZIP
            val zipFile = createServerZip(targetServerBytes)
            Log.d(tag, "Server ZIP created: ${zipFile.absolutePath}")

            // Step 2: Push the ZIP to the Target
            pushServerZip(zipFile)
            Log.d(tag, "Server ZIP pushed to $remoteZip")

            // Step 3: Extract the ZIP on the Target
            extractServerZip()
            Log.d(tag, "Server ZIP extracted to $remoteDir")

            // Step 4: Execute the server
            val pid = executeServer(port)
            Log.d(tag, "Server launched (PID=$pid)")
            pid
        } catch (e: Exception) {
            throw TargetServerException("Failed to launch server", e)
        }
    }

    /**
     * Creates a ZIP file containing the Target server.
     *
     * @param targetServerBytes Prebuilt Target server JAR.
     * @return ZIP file.
     */
    private fun createServerZip(targetServerBytes: ByteArray): File {
        val zipFile = File.createTempFile("janus-server", ".zip", context.cacheDir)
        ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
            // Add the JAR
            zip.putNextEntry(ZipEntry("targetserver.jar"))
            zip.write(targetServerBytes)
            zip.closeEntry()

            // Add a launch script (optional)
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

    /**
     * Pushes the server ZIP to the Target.
     *
     * @param zipFile Local ZIP file.
     */
    private suspend fun pushServerZip(zipFile: File) {
        val syncStream = adbConnection.openSyncStream()
        val syncService = AdbSyncService(syncStream)
        try {
            // Ensure remote directory exists
            val shell = AdbShellSession(adbConnection.connect("shell:"))
            shell.execute("mkdir -p $remoteDir").close()
            shell.close()

            // Push the ZIP
            syncService.push(zipFile, remoteZip, "0644")
        } finally {
            syncService.close()
        }
    }

    /**
     * Extracts the server ZIP on the Target.
     */
    private suspend fun extractServerZip() {
        val shell = AdbShellSession(adbConnection.connect("shell:"))
        try {
            // Unzip the file
            shell.execute("unzip -o $remoteZip -d $remoteDir").close()

            // Make launch script executable (if added)
            shell.execute("chmod 755 $remoteDir/launch.sh").close()
        } finally {
            shell.close()
        }
    }

    /**
     * Executes the server on the Target.
     *
     * @param port Port for the server to listen on.
     * @return PID of the server process.
     */
    private suspend fun executeServer(port: Int): Int {
        val shell = AdbShellSession(adbConnection.connect("shell:"))
        try {
            // Execute the server in the background and capture PID
            val output = shell.execute(
                "app_process -Djava.class.path=$remoteExecutable / com.janus.targetserver.Main $port & echo $!"
            )

            // Read the PID from output
            val pidLine = output.receive()
            return pidLine?.trim()?.toIntOrNull()
                ?: throw TargetServerException("Failed to parse PID")
        } finally {
            shell.close()
        }
    }

    /**
     * Kills the Target server.
     *
     * @param pid PID of the server process.
     */
    suspend fun kill(pid: Int) {
        val shell = AdbShellSession(adbConnection.connect("shell:"))
        try {
            shell.execute("kill $pid").close()
            Log.d(tag, "Server killed (PID=$pid)")
        } finally {
            shell.close()
        }
    }
}

/**
 * Target server errors.
 */
class TargetServerException(message: String, cause: Throwable? = null) : Exception(message, cause)
