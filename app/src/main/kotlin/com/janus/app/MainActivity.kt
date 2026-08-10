import android.util.Log
import com.janus.app.adb.core.AdbConnection
import com.janus.app.adb.pairing.AdbPairingClient
import com.janus.app.adb.shell.AdbShellSession
import com.janus.app.adb.sync.AdbSyncService
import kotlinx.coroutines.runBlocking
import java.io.File

class Phase4TestHarness(
    private val pairingClient: AdbPairingClient,
    private val adbConnection: AdbConnection
) {
    private val tag = "Phase4TestHarness"

    fun runAllTests() = runBlocking {
        try {
            testPairing()
            testConnection()
            testShell()
            testSync()
            Log.i(tag, "✅ All Phase 4 tests passed!")
        } catch (e: Exception) {
            Log.e(tag, "❌ Test failed", e)
        }
    }

    private suspend fun testPairing() {
        Log.i(tag, "🧪 Testing pairing...")
        pairingClient.pair(
            ip = "192.168.1.100",
            port = 35761,
            pairingCode = "123456"
        ).getOrThrow()
    }

    private suspend fun testConnection() {
        Log.i(tag, "🧪 Testing connection...")
        adbConnection.connect("shell:")
    }

    private suspend fun testShell() {
        Log.i(tag, "🧪 Testing shell...")
        val shell = AdbShellSession(adbConnection.connect("shell:"))
        val output = shell.execute("ls -l /sdcard")
        for (line in output) Log.d(tag, line)
        shell.close()
    }

    private suspend fun testSync() {
        Log.i(tag, "🧪 Testing sync...")
        val sync = AdbSyncService(adbConnection.openSyncStream())
        val testFile = File("/sdcard/test.txt").apply {
            writeText("Hello, Janus!")
        }
        sync.push(testFile, "/sdcard/remote.txt")
        sync.pull("/sdcard/remote.txt", File("/sdcard/test_copy.txt"))
        check(testFile.readText() == File("/sdcard/test_copy.txt").readText())
        sync.close()
    }
}
