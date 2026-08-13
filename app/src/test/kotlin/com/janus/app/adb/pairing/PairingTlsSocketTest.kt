package com.janus.app.adb.pairing

import android.net.PskKeyManager
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.Socket
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import kotlin.concurrent.thread

/**
 * Runs under Robolectric (@RunWith(RobolectricTestRunner)) rather than as a
 * plain host-JVM unit test. The correct implementation of PairingTlsSocket
 * depends on android.net.PskKeyManager, a real Android framework class --
 * plain unit tests run against a stub android.jar where every framework
 * method throws, so this could never have run there. Robolectric
 * substitutes real (AOSP-derived) framework implementations.
 *
 * NOTE: whether TLS-PSK cipher suite negotiation itself is fully
 * functional under Robolectric's host-JVM environment (versus a real
 * device's Conscrypt provider) could not be confirmed ahead of time -- if
 * this test fails specifically with "no PSK cipher suites supported" or a
 * provider-related error rather than a logic assertion failure, that
 * points to a Robolectric/JVM SSL provider limitation rather than a bug in
 * PairingTlsSocket itself.
 *
 * Sets up a real mock TLS-PSK SERVER (using the same PskKeyManager
 * mechanism) so the client under test has something real to handshake
 * against with matching/mismatching keys.
 */
@RunWith(RobolectricTestRunner::class)
class PairingTlsSocketTest {

    private lateinit var serverSocket: SSLServerSocket
    private lateinit var serverThread: Thread

    private fun startMockPskServer(serverKey: ByteArray) {
        val pskKeyManager = object : PskKeyManager() {
            override fun chooseServerKeyIdentityHint(socket: Socket?): String? = null
            override fun chooseServerKeyIdentityHint(engine: SSLEngine?): String? = null
            override fun chooseClientKeyIdentity(identityHint: String?, socket: Socket?): String = ""
            override fun chooseClientKeyIdentity(identityHint: String?, engine: SSLEngine?): String = ""
            override fun getKey(identityHint: String?, identity: String?, socket: Socket?): SecretKeySpec =
                SecretKeySpec(serverKey, "RAW")
            override fun getKey(identityHint: String?, identity: String?, engine: SSLEngine?): SecretKeySpec =
                SecretKeySpec(serverKey, "RAW")
        }

        val sslContext = SSLContext.getInstance("TLS").apply {
            init(arrayOf<KeyManager>(pskKeyManager), arrayOf<TrustManager>(), null)
        }

        serverSocket = sslContext.serverSocketFactory.createServerSocket(0) as SSLServerSocket
        val pskCipherSuites = serverSocket.supportedCipherSuites.filter { it.contains("PSK") }
        serverSocket.enabledCipherSuites = pskCipherSuites.toTypedArray()

        serverThread = thread {
            runCatching {
                val clientConn = serverSocket.accept() as SSLSocket
                clientConn.startHandshake()
                clientConn.close()
            }
        }
    }

    @After
    fun teardown() {
        runCatching { serverSocket.close() }
    }

    @Test
    fun `matching PSK completes the handshake successfully`() {
        val sharedKey = ByteArray(32) { it.toByte() }
        startMockPskServer(sharedKey)

        val clientSocket = PairingTlsSocket.create(
            ip = "localhost",
            port = serverSocket.localPort,
            spakeKey = sharedKey,
            timeoutMillis = 5000
        )

        assertTrue(clientSocket.isConnected)
        clientSocket.close()
        serverThread.join()
    }

    @Test(expected = PairingException::class)
    fun `mismatched PSK fails the handshake`() {
        val serverKey = ByteArray(32) { it.toByte() }
        val wrongClientKey = ByteArray(32) { 0 }
        startMockPskServer(serverKey)

        PairingTlsSocket.create(
            ip = "localhost",
            port = serverSocket.localPort,
            spakeKey = wrongClientKey,
            timeoutMillis = 5000
        )
    }
}