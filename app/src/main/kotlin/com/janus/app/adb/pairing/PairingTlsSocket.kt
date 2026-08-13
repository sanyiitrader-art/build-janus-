package com.janus.app.adb.pairing

import android.net.PskKeyManager
import java.net.Socket
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager

/**
 * TLS wrapper for the Wireless Debugging pairing channel, authenticated by
 * the SPAKE2-derived shared key via a genuine TLS-PSK cipher suite.
 *
 * REWRITTEN from an earlier version that used a normal X.509 TrustManager
 * comparing a peer certificate's public key bytes directly against the raw
 * SPAKE2 key -- that can never succeed, since an X.509 SubjectPublicKeyInfo
 * encoding and a raw 32-byte symmetric key are structurally incompatible
 * data.
 *
 * The correct mechanism is TLS-PSK: the shared secret participates
 * directly in the handshake's key derivation, with no certificate
 * validation involved. Uses Android's public, documented
 * `android.net.PskKeyManager` (API 21+).
 *
 * UNVERIFIED: the exact TLS protocol version / cipher suite the real ADB
 * pairing service negotiates could not be confirmed against AOSP source in
 * this session -- this enables whatever PSK cipher suites the device's TLS
 * provider supports rather than hardcoding one exact suite name. A real
 * pairing attempt is the only way to confirm this negotiates correctly
 * against a real Target.
 */
object PairingTlsSocket {

    fun create(ip: String, port: Int, spakeKey: ByteArray, timeoutMillis: Int): SSLSocket {
        try {
            val pskKeyManager = SpakeDerivedPskKeyManager(spakeKey)

            val sslContext = SSLContext.getInstance("TLS").apply {
                init(
                    arrayOf<KeyManager>(pskKeyManager),
                    arrayOf<TrustManager>(), // No TrustManager needed for TLS-PSK.
                    null
                )
            }

            val plainSocket = Socket(ip, port).apply { soTimeout = timeoutMillis }
            val sslSocket = sslContext.socketFactory
                .createSocket(plainSocket, ip, port, true) as SSLSocket

            val pskCipherSuites = sslSocket.supportedCipherSuites.filter { it.contains("PSK") }
            require(pskCipherSuites.isNotEmpty()) {
                "No TLS-PSK cipher suites supported by this device's TLS provider"
            }
            sslSocket.enabledCipherSuites = pskCipherSuites.toTypedArray()

            sslSocket.startHandshake()
            return sslSocket
        } catch (e: Exception) {
            throw PairingException("TLS-PSK pairing handshake failed: ${e.message}", e)
        }
    }

    private class SpakeDerivedPskKeyManager(private val key: ByteArray) : PskKeyManager() {
        override fun chooseServerKeyIdentityHint(socket: Socket?): String? = null
        override fun chooseServerKeyIdentityHint(engine: SSLEngine?): String? = null

        override fun chooseClientKeyIdentity(identityHint: String?, socket: Socket?): String = ""
        override fun chooseClientKeyIdentity(identityHint: String?, engine: SSLEngine?): String = ""

        override fun getKey(identityHint: String?, identity: String?, socket: Socket?): SecretKeySpec =
            SecretKeySpec(key, "RAW")

        override fun getKey(identityHint: String?, identity: String?, engine: SSLEngine?): SecretKeySpec =
            SecretKeySpec(key, "RAW")
    }
}

class PairingException(message: String, cause: Throwable? = null) : Exception(message, cause)