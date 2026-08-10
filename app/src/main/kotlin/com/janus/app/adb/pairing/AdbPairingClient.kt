package com.janus.app.adb.pairing

import android.util.Log
import com.janus.app.adb.crypto.AdbKeystoreManager
import com.janus.app.adb.pairing.SpakeHandshake.SpakeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import javax.net.ssl.SSLSocket

/**
 * Coordinates the Wireless Debugging pairing workflow (spec #15):
 *   1. User enters IP, port, and pairing code (from Target's Developer Options).
 *   2. Perform SPAKE2 key exchange over a plain socket.
 *   3. Upgrade to TLS 1.3 using the derived key.
 *   4. Persist the Target's public key as authorized.
 *
 * The pairing port is distinct from the normal ADB-over-TCP port (5555) and is
 * randomly assigned per session (visible in the Target's "Wireless Debugging"
 * screen). This client does NOT guess ports; the user must supply it.
 *
 * Throws:
 *   - [PairingException] on protocol errors (invalid code, TLS failure).
 *   - [java.io.IOException] on network errors (unreachable host, timeout).
 */
class AdbPairingClient(
    private val keystoreManager: AdbKeystoreManager
) {
    private val tag = "AdbPairingClient"

    /**
     * Pairs with a Target device.
     *
     * @param ip Target IP address (e.g., "192.168.1.100").
     * @param port Pairing port (from Target's Developer Options).
     * @param pairingCode 6-digit code (from Target's Developer Options).
     * @param timeoutMillis Socket timeout (default: 5000ms).
     * @return [Result<Unit>] with success/failure.
     */
    suspend fun pair(
        ip: String,
        port: Int,
        pairingCode: String,
        timeoutMillis: Int = 5000
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(pairingCode.matches(Regex("\\d{6}"))) {
                "Pairing code must be 6 digits"
            }

            // Step 1: SPAKE2 key exchange
            val spakeResult = performSpakeHandshake(ip, port, pairingCode, timeoutMillis)
            Log.d(tag, "SPAKE2 handshake succeeded")

            // Step 2: TLS upgrade
            val tlsSocket = PairingTlsSocket.create(
                ip = ip,
                port = port,
                spakeKey = spakeResult.sharedKey,
                timeoutMillis = timeoutMillis
            )
            Log.d(tag, "TLS handshake succeeded")

            // Step 3: Persist the Target's public key as authorized
            val targetPublicKey = tlsSocket.peerCertificate.publicKey.encoded
            keystoreManager.addAuthorizedKey(targetPublicKey)
            Log.d(tag, "Target public key persisted as authorized")

            // Step 4: Close the TLS socket
            tlsSocket.close()
        }.onFailure { e ->
            Log.e(tag, "Pairing failed", e)
            throw PairingException("Pairing failed: ${e.message}", e)
        }
    }

    /**
     * Performs SPAKE2 key exchange over a plain socket.
     *
     * @param ip Target IP address.
     * @param port Pairing port.
     * @param pairingCode 6-digit pairing code.
     * @param timeoutMillis Socket timeout.
     * @return [SpakeResult] containing the shared key.
     */
    private suspend fun performSpakeHandshake(
        ip: String,
        port: Int,
        pairingCode: String,
        timeoutMillis: Int
    ): SpakeResult {
        val socket = java.net.Socket().apply {
            connect(InetSocketAddress(ip, port), timeoutMillis)
            soTimeout = timeoutMillis
        }

        return try {
            SpakeHandshake.perform(
                socket = socket,
                pairingCode = pairingCode,
                isClient = true
            )
        } finally {
            socket.close()
        }
    }
}

/**
 * Pairing-specific errors.
 */
class PairingException(message: String, cause: Throwable? = null) : Exception(message, cause)
