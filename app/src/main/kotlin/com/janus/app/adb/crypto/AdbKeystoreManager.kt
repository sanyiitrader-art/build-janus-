package com.janus.app.adb.crypto

import android.content.Context
import java.io.File
import java.security.KeyFactory
import java.security.KeyPair
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

/**
 * Persists the Controller's ADB RSA identity keypair across app restarts
 * (spec #48, #50).
 *
 * The keypair must remain stable across restarts — every Target the user
 * has ever paired with recognizes THIS specific public key as authorized;
 * regenerating it on every launch would force the user to re-approve
 * authorization on every Target, every time. Stored as raw DER bytes
 * (PKCS8 for the private key, X.509 SubjectPublicKeyInfo for the public
 * key) under a dedicated "adb_keystore" subdirectory of app-private
 * storage, matching the path already excluded from cloud backup in
 * backup_rules.xml / data_extraction_rules.xml.
 *
 * This does NOT use Android Keystore's hardware-backed key storage. That
 * would be more secure in principle, but Android Keystore-backed RSA keys
 * do not straightforwardly support the exact raw NONEwithRSA / PKCS#1 v1.5
 * signing operation ADB's AUTH step requires. File-based storage in
 * app-private, non-backed-up storage is judged an acceptable tradeoff: the
 * file is inaccessible to other apps under Android's standard sandboxing.
 */
class AdbKeystoreManager(private val context: Context) {

    private val keystoreDir: File by lazy {
        File(context.filesDir, "adb_keystore").apply { mkdirs() }
    }
    private val privateKeyFile: File by lazy { File(keystoreDir, "adbkey") }
    private val publicKeyFile: File by lazy { File(keystoreDir, "adbkey.pub") }

    @Volatile
    private var cachedKeyPair: KeyPair? = null

    fun getOrCreateKeyPair(): KeyPair {
        cachedKeyPair?.let { return it }

        synchronized(this) {
            cachedKeyPair?.let { return it }

            val loaded = loadKeyPair()
            val keyPair = loaded ?: AdbRsaKeyPair.generate().also { saveKeyPair(it) }
            cachedKeyPair = keyPair
            return keyPair
        }
    }

    fun getAdbFormattedPublicKey(): String {
        val publicKey = getOrCreateKeyPair().public as RSAPublicKey
        return AdbRsaKeyPair.encodePublicKeyAdbFormat(publicKey)
    }

    fun signAuthToken(token: ByteArray): ByteArray {
        val privateKey = getOrCreateKeyPair().private as RSAPrivateKey
        return AdbRsaKeyPair.sign(privateKey, token)
    }

    private fun loadKeyPair(): KeyPair? {
        if (!privateKeyFile.exists() || !publicKeyFile.exists()) return null

        return runCatching {
            val keyFactory = KeyFactory.getInstance("RSA")

            val privateKeyBytes = privateKeyFile.readBytes()
            val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateKeyBytes))

            val publicKeyBytes = publicKeyFile.readBytes()
            val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes))

            KeyPair(publicKey, privateKey)
        }.getOrNull()
    }

    private fun saveKeyPair(keyPair: KeyPair) {
        privateKeyFile.writeBytes(keyPair.private.encoded)
        publicKeyFile.writeBytes(keyPair.public.encoded)
    }
}