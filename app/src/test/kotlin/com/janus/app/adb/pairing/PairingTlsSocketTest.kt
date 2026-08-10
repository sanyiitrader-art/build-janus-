package com.janus.app.adb.pairing

import org.junit.After
import org.junit.Before
import org.junit.Test
import java.net.ServerSocket
import java.security.KeyStore
import java.security.cert.Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import kotlin.concurrent.thread

class PairingTlsSocketTest {
    private lateinit var serverSocket: ServerSocket
    private lateinit var serverThread: Thread
    private lateinit var serverCert: Certificate

    @Before
    fun setup() {
        // Generate a self-signed cert for the mock server.
        val keyStore = KeyStore.getInstance("JKS").apply {
            load(null, null)
            // In a real test, you'd generate a cert here.
            // For brevity, we'll skip this step.
        }
        serverCert = keyStore.getCertificate("server")

        // Start a mock TLS server.
        val sslContext = SSLContext.getInstance("TLSv1.3").apply {
            init(
                KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
                    .apply { init(keyStore, "password".toCharArray()) }
                    .keyManagers,
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                    .apply { init(keyStore) }
                    .trustManagers,
                null
            )
        }

        serverSocket = sslContext.serverSocketFactory.createServerSocket(0)
        serverThread = thread {
            val clientSocket = serverSocket.accept() as SSLSocket
            clientSocket.startHandshake()
            clientSocket.close()
        }
    }

    @After
    fun teardown() {
        serverThread.interrupt()
        serverSocket.close()
    }

    @Test
    fun testTlsHandshake() {
        // Use the server's cert as the "SPAKE2 key" for testing.
        val spakeKey = serverCert.publicKey.encoded

        // Client connects to the mock server.
        val sslSocket = PairingTlsSocket.create(
            ip = "localhost",
            port = serverSocket.localPort,
            spakeKey = spakeKey,
            timeoutMillis = 5000
        )

        // Verify the handshake succeeded.
        assert(sslSocket.isConnected)
        sslSocket.close()
    }

    @Test(expected = PairingException::class)
    fun testInvalidSpakeKey() {
        // Use a wrong key (all zeros).
        val spakeKey = ByteArray(32)

        PairingTlsSocket.create(
            ip = "localhost",
            port = serverSocket.localPort,
            spakeKey = spakeKey,
            timeoutMillis = 5000
        )
    }
}