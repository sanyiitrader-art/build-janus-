package com.janus.app.adb.pairing

import com.janus.app.adb.crypto.AdbKeystoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets

/**
 * Coordinates the Wireless Debugging pairing workflow (spec #15):
 *   1. User enters IP, port, and pairing code (from Target's Developer Options).
 *   2. Perform SPAKE2 key exchange over a plain socket.
 *   3. Upgrade to TLS, authenticated by the SPAKE2-derived key (TLS-PSK).
 *   4. Send our ADB public key to the Target over that encrypted channel.
 *
 * REMOVED from an earlier version: a step that called a nonexistent
 * `keystoreManager.addAuthorizedKey(targetPublicKey)` using the peer's TLS
 * certificate. That was wrong on two counts -- there is no peer
 * certificate in a PSK handshake, and pairing's whole purpose is getting
 * OUR key accepted by the Target, not the reverse. The Controller never
 * "authorizes" anything locally; the Target does that on its own once it
 * receives our public key.
 *
 * UNVERIFIED: the exact wire format of the post-handshake "peer info"
 * exchange (real AOSP pairing sends more than just the raw key -- likely a
 * small structured message with a type/length header) could not be
 * confirmed against AOSP source in this session. What's sent below is our
 * best-effort framing (a 4-byte length-prefixed ADB-formatted public key
 * string) and is the most likely piece to need adjustment once tested
 * against a real device.
 */
class AdbPairingClient(
    private val keystoreManager: AdbKeystoreManager
) {

    suspend fun pair(
        ip: String,
        port: Int,
        pairingCode: String,
        timeoutMillis: Int = 5_000
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(pairingCode.matches(Regex("\\d{6}"))) {
                "Pairing code must be 6 digits"
            }

            val spakeResult = performSpakeHandshake(ip, port, pairingCode, timeoutMillis)

            val tlsSocket = PairingTlsSocket.create(
                ip = ip,
                port = port,
                spakeKey = spakeResult.sharedKey,
                timeoutMillis = timeoutMillis
            )

            try {
                sendOurPublicKey(tlsSocket)
            } finally {
                tlsSocket.close()
            }
        }.onFailure { e ->
            throw PairingException("Pairing failed: ${e.message}", e)
        }
    }

    private fun sendOurPublicKey(tlsSocket: javax.net.ssl.SSLSocket) {
        val publicKeyBytes = keystoreManager.getAdbFormattedPublicKey()
            .toByteArray(StandardCharsets.UTF_8)

        val out = DataOutputStream(tlsSocket.outputStream)
        out.writeInt(publicKeyBytes.size) // big-endian length prefix
        out.write(publicKeyBytes)
        out.flush()
    }

    private fun performSpakeHandshake(
        ip: String,
        port: Int,
        pairingCode: String,
        timeoutMillis: Int
    ): SpakeHandshake.SpakeResult {
        val socket = Socket().apply {
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