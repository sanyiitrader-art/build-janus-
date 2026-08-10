package com.janus.app.adb.crypto

import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.Base64

/**
 * Generates and encodes an RSA identity keypair for ADB authentication
 * (spec #48, #50).
 *
 * ADB does NOT use standard PEM/X.509 encoding for the public key it sends
 * during CNXN/AUTH — the Android platform (system/core/libcrypto_utils in
 * AOSP) defines a custom fixed-layout binary structure that adbd on the
 * Target parses directly. [encodePublicKeyAdbFormat] reproduces that
 * layout from the documented AOSP algorithm:
 *
 *   struct RSAPublicKey {
 *     uint32_t len;          // = RSANUMWORDS (64 for a 2048-bit key)
 *     uint32_t n0inv;        // = -1 / N[0] mod 2^32  (Montgomery parameter)
 *     uint8_t  modulus[256]; // N, as 64 little-endian 32-bit words
 *     uint8_t  rr[256];      // R^2 mod N, same word layout (R = 2^2048)
 *     uint32_t exponent;     // public exponent, typically 65537
 *   }
 *
 * ...base64-encoded, with a trailing " comment" (arbitrary, for the
 * Target's authorized-keys display only — not parsed structurally).
 *
 * NOTE: this encoding is the single highest-risk piece of Phase 4 to get
 * byte-for-byte correct without a live device to validate a real pairing/
 * connect handshake against. If pairing fails at the AUTH step against a
 * real Target despite correct IP/port/pairing-code entry, this function is
 * the first place to re-verify against AOSP's android_pubkey_encode
 * reference implementation.
 */
object AdbRsaKeyPair {

    private const val KEY_SIZE_BITS = 2048
    private const val RSA_NUM_WORDS = KEY_SIZE_BITS / 32 // 64
    private const val RSA_NUM_BYTES = KEY_SIZE_BITS / 8 // 256
    private val TWO_32 = BigInteger.ONE.shiftLeft(32)

    fun generate(): KeyPair {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(KEY_SIZE_BITS)
        return generator.generateKeyPair()
    }

    /**
     * Signs [token] (the 20-byte token adbd sends in its AUTH message) using
     * raw RSA with PKCS#1 v1.5 padding and no digest algorithm wrapper —
     * ADB's AUTH signature step signs the token bytes directly. NONEwithRSA
     * matches this: it applies PKCS#1 v1.5 padding around the raw input
     * without adding a digest algorithm identifier.
     */
    fun sign(privateKey: RSAPrivateKey, token: ByteArray): ByteArray {
        val signature = Signature.getInstance("NONEwithRSA")
        signature.initSign(privateKey)
        signature.update(token)
        return signature.sign()
    }

    fun encodePublicKeyAdbFormat(publicKey: RSAPublicKey, comment: String = "janus@controller"): String {
        val modulus = publicKey.modulus
        val exponent = publicKey.publicExponent.toInt()

        val n0 = modulus.mod(TWO_32)
        val n0Inverse = n0.modInverse(TWO_32)
        val n0inv = TWO_32.subtract(n0Inverse).mod(TWO_32)

        val r = BigInteger.ONE.shiftLeft(RSA_NUM_WORDS * 32)
        val rr = r.multiply(r).mod(modulus)

        val buffer = java.io.ByteArrayOutputStream(4 + 4 + RSA_NUM_BYTES + RSA_NUM_BYTES + 4)
        writeUInt32LE(buffer, RSA_NUM_WORDS.toLong())
        writeUInt32LE(buffer, n0inv.toLong())
        buffer.write(bigIntegerToFixedLengthLittleEndian(modulus, RSA_NUM_BYTES))
        buffer.write(bigIntegerToFixedLengthLittleEndian(rr, RSA_NUM_BYTES))
        writeUInt32LE(buffer, exponent.toLong())

        val base64Key = Base64.getEncoder().encodeToString(buffer.toByteArray())
        return "$base64Key $comment"
    }

    private fun writeUInt32LE(buffer: java.io.ByteArrayOutputStream, value: Long) {
        buffer.write((value and 0xFF).toInt())
        buffer.write(((value shr 8) and 0xFF).toInt())
        buffer.write(((value shr 16) and 0xFF).toInt())
        buffer.write(((value shr 24) and 0xFF).toInt())
    }

    /**
     * Encodes [value] as exactly [length] bytes, little-endian — NOT the
     * same byte order as BigInteger.toByteArray(), which is big-endian and
     * may include a leading sign byte.
     */
    private fun bigIntegerToFixedLengthLittleEndian(value: BigInteger, length: Int): ByteArray {
        val bigEndian = value.toByteArray().let { bytes ->
            if (bytes.size > 1 && bytes[0] == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
        }
        val result = ByteArray(length)
        for (i in bigEndian.indices) {
            val targetIndex = i - (bigEndian.size - length)
            if (targetIndex in 0 until length) {
                result[length - 1 - targetIndex] = bigEndian[i]
            }
        }
        return result
    }
}