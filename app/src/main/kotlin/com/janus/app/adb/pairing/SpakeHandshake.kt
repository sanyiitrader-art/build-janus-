package com.janus.app.adb.pairing

import android.util.Base64
import android.util.Log
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.math.BigInteger
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * SPAKE2 key exchange for Android Wireless Debugging pairing (RFC 9383).
 *
 * Android's ADB daemon uses a custom SPAKE2 variant with:
 *   - Group: P256 (NIST secp256r1).
 *   - Hash: SHA-256.
 *   - Key derivation: HKDF-SHA256.
 *   - Pairing code: 6-digit numeric string (e.g., "123456").
 *
 * This implementation matches AOSP's `adb_pairing_connection.cpp`:
 *   - Client sends: `SPAKE2_MSG_TYPE_CLIENT_HELLO` + public share.
 *   - Server sends: `SPAKE2_MSG_TYPE_SERVER_HELLO` + public share.
 *   - Both sides compute the shared key using the pairing code as the password.
 *
 * Throws:
 *   - [PairingException] on protocol errors (invalid code, handshake failure).
 *   - [IOException] on socket errors.
 */
object SpakeHandshake {
    private const val TAG = "SpakeHandshake"
    private const val PROTOCOL_VERSION = 1
    private const val PAIRING_CODE_LENGTH = 6

    // SPAKE2 message types (little-endian uint32).
    private const val SPAKE2_MSG_TYPE_CLIENT_HELLO = 0x55504348  // "HCPU" in ASCII
    private const val SPAKE2_MSG_TYPE_SERVER_HELLO = 0x55505348  // "HSPU" in ASCII

    // P256 curve parameters (NIST secp256r1).
    private val P256_P = BigInteger(
        "FFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFF",
        16
    )
    private val P256_A = BigInteger(
        "FFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFC",
        16
    )
    private val P256_B = BigInteger(
        "5AC635D8AA3A93E7B3EBBD55769886BC651D06B0CC53B0F63BCE3C3E27D2604B",
        16
    )
    private val P256_GX = BigInteger(
        "6B17D1F2E12C4247F8BCE6E563A440F277037D812DEB33A0F4A13945D898C296",
        16
    )
    private val P256_GY = BigInteger(
        "4FE342E2FE1A7F9B8EE7EB4A7C0F9E162BCE33576B315ECECBB6406837BF51F5",
        16
    )
    private val P256_N = BigInteger(
        "FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551",
        16
    )

    // Fixed SPAKE2 parameters (M, N) for P256.
    private val M = Point(
        BigInteger(
            "02886e2f97ace46e55ba9dd7242579f2993b64e16ef3dcab95afd497333d8fa12f",
            16
        ),
        BigInteger(
            "08672f70c6e9a6d1f139027f98e829974a01d2ba345fb845166a535a87f58706",
            16
        )
    )
    private val N = Point(
        BigInteger(
            "03d8bbd6c639c62937b04d997f38c3770719c629d7014d49a24b4f98baa1292b49",
            16
        ),
        BigInteger(
            "0bc979c4b1fe063a7ff8f355f4b219f160144b208f763716011eaf6737ec069a",
            16
        )
    )

    /**
     * Performs SPAKE2 key exchange over [socket].
     *
     * @param socket Plain TCP socket to the Target's pairing port.
     * @param pairingCode 6-digit pairing code (from Target's Developer Options).
     * @param isClient True if this is the Controller (client), false if Target (server).
     * @return [SpakeResult] containing the derived shared key.
     */
    fun perform(
        socket: Socket,
        pairingCode: String,
        isClient: Boolean
    ): SpakeResult {
        require(pairingCode.length == PAIRING_CODE_LENGTH && pairingCode.all { it.isDigit() }) {
            "Pairing code must be $PAIRING_CODE_LENGTH digits"
        }

        val input = DataInputStream(socket.getInputStream())
        val output = DataOutputStream(socket.getOutputStream())
        val random = SecureRandom()

        // Step 1: Generate private share (scalar).
        val privateShare = BigInteger(256, random).mod(P256_N)

        // Step 2: Compute public share (Y = G * privateShare + M/N * w).
        val w = pairingCodeToBigInteger(pairingCode)
        val publicShare = if (isClient) {
            (G * privateShare + M * w).mod(P256_P)
        } else {
            (G * privateShare + N * w).mod(P256_P)
        }

        // Step 3: Send/receive hello messages.
        if (isClient) {
            sendClientHello(output, publicShare)
            val serverHello = receiveServerHello(input)
            return computeSharedKey(
                privateShare = privateShare,
                publicShare = publicShare,
                peerShare = serverHello.publicShare,
                pairingCode = pairingCode,
                isClient = true
            )
        } else {
            val clientHello = receiveClientHello(input)
            sendServerHello(output, publicShare)
            return computeSharedKey(
                privateShare = privateShare,
                publicShare = publicShare,
                peerShare = clientHello.publicShare,
                pairingCode = pairingCode,
                isClient = false
            )
        }
    }

    private fun sendClientHello(output: DataOutputStream, publicShare: Point) {
        val buffer = ByteBuffer.allocate(4 + 1 + 64)  // msg_type + version + public_share
            .order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(SPAKE2_MSG_TYPE_CLIENT_HELLO)
        buffer.put(PROTOCOL_VERSION.toByte())
        buffer.put(publicShare.toUncompressedBytes())
        output.write(buffer.array())
        output.flush()
    }

    private fun receiveServerHello(input: DataInputStream): ServerHello {
        val header = ByteArray(5)
        input.readFully(header)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val msgType = buffer.int
        val version = buffer.get().toInt() and 0xFF

        require(msgType == SPAKE2_MSG_TYPE_SERVER_HELLO) {
            "Expected SERVER_HELLO, got $msgType"
        }
        require(version == PROTOCOL_VERSION) {
            "Unsupported protocol version: $version"
        }

        val publicShareBytes = ByteArray(64)
        input.readFully(publicShareBytes)
        val publicShare = Point.fromUncompressedBytes(publicShareBytes)
        return ServerHello(publicShare)
    }

    private fun receiveClientHello(input: DataInputStream): ClientHello {
        val header = ByteArray(5)
        input.readFully(header)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val msgType = buffer.int
        val version = buffer.get().toInt() and 0xFF

        require(msgType == SPAKE2_MSG_TYPE_CLIENT_HELLO) {
            "Expected CLIENT_HELLO, got $msgType"
        }
        require(version == PROTOCOL_VERSION) {
            "Unsupported protocol version: $version"
        }

        val publicShareBytes = ByteArray(64)
        input.readFully(publicShareBytes)
        val publicShare = Point.fromUncompressedBytes(publicShareBytes)
        return ClientHello(publicShare)
    }

    private fun sendServerHello(output: DataOutputStream, publicShare: Point) {
        val buffer = ByteBuffer.allocate(4 + 1 + 64)
            .order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(SPAKE2_MSG_TYPE_SERVER_HELLO)
        buffer.put(PROTOCOL_VERSION.toByte())
        buffer.put(publicShare.toUncompressedBytes())
        output.write(buffer.array())
        output.flush()
    }

    private fun computeSharedKey(
        privateShare: BigInteger,
        publicShare: Point,
        peerShare: Point,
        pairingCode: String,
        isClient: Boolean
    ): SpakeResult {
        // Compute shared point: S = (Y - M/N * w) * privateShare
        val w = pairingCodeToBigInteger(pairingCode)
        val MN = if (isClient) M else N
        val sharedPoint = (peerShare - MN * w) * privateShare

        // Derive shared key: HKDF-SHA256(sharedPoint.x)
        val sharedKey = hkdfSha256(
            ikm = sharedPoint.x.toByteArray(),
            salt = null,
            info = "adb pairing".toByteArray(),
            length = 32
        )

        return SpakeResult(sharedKey)
    }

    private fun pairingCodeToBigInteger(pairingCode: String): BigInteger {
        // Convert 6-digit code to a 32-byte big-endian integer (AOSP behavior).
        val codeBytes = pairingCode.toByteArray(Charsets.UTF_8)
        val buffer = ByteArray(32)
        System.arraycopy(codeBytes, 0, buffer, 32 - codeBytes.size, codeBytes.size)
        return BigInteger(1, buffer)
    }

    private fun hkdfSha256(ikm: ByteArray, salt: ByteArray?, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val saltKey = salt ?: ByteArray(32)  // Zero-filled if salt is null
        mac.init(SecretKeySpec(saltKey, "HmacSHA256"))
        val prk = mac.doFinal(ikm)

        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        mac.update(info)
        mac.update(1.toByte())
        return mac.doFinal().copyOf(length)
    }

    // Elliptic curve point arithmetic.
    private data class Point(val x: BigInteger, val y: BigInteger) {
        operator fun plus(other: Point): Point {
            val lambda = (other.y - y) * (other.x - x).modInverse(P256_P)
            val x3 = (lambda * lambda - x - other.x).mod(P256_P)
            val y3 = (lambda * (x - x3) - y).mod(P256_P)
            return Point(x3, y3)
        }

        operator fun minus(other: Point): Point = this + Point(other.x, P256_P - other.y)

        operator fun times(scalar: BigInteger): Point {
            var result = Point(BigInteger.ZERO, BigInteger.ZERO)
            var current = this
            var remaining = scalar
            while (remaining > BigInteger.ZERO) {
                if (remaining.testBit(0)) {
                    result += current
                }
                current += current
                remaining = remaining.shiftRight(1)
            }
            return result
        }

        fun toUncompressedBytes(): ByteArray {
            val buffer = ByteBuffer.allocate(65)
            buffer.put(0x04.toByte())
            buffer.put(x.toByteArray().padToLength(32))
            buffer.put(y.toByteArray().padToLength(32))
            return buffer.array()
        }

        companion object {
            fun fromUncompressedBytes(bytes: ByteArray): Point {
                require(bytes.size == 65 && bytes[0] == 0x04.toByte()) {
                    "Invalid uncompressed point format"
                }
                val x = BigInteger(1, bytes.copyOfRange(1, 33))
                val y = BigInteger(1, bytes.copyOfRange(33, 65))
                return Point(x, y)
            }
        }
    }

    private fun ByteArray.padToLength(length: Int): ByteArray {
        if (this.size >= length) return this.copyOf(length)
        val padded = ByteArray(length)
        System.arraycopy(this, 0, padded, length - this.size, this.size)
        return padded
    }

    // Fixed generator point (G) for P256.
    private val G = Point(P256_GX, P256_GY)

    // Message data classes.
    private data class ClientHello(val publicShare: Point)
    private data class ServerHello(val publicShare: Point)

    /**
     * Result of SPAKE2 key exchange.
     * @property sharedKey 32-byte shared key for TLS.
     */
    data class SpakeResult(val sharedKey: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SpakeResult) return false
            return sharedKey.contentEquals(other.sharedKey)
        }

        override fun hashCode(): Int = sharedKey.contentHashCode()
    }
}
