package com.janus.app.adb.pairing

import java.io.DataInputStream
import java.io.DataOutputStream
import java.math.BigInteger
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * SPAKE2 key exchange for Android Wireless Debugging pairing.
 *
 * ============================================================
 * KNOWN UNVERIFIED RISK — READ BEFORE TRUSTING THIS FILE
 * ============================================================
 * The M and N curve point constants below are carried over unchanged from
 * an earlier version of this file. Their hex string lengths are consistent
 * with SEC1 COMPRESSED point encoding (a 0x02/0x03 prefix byte + one
 * 32-byte coordinate) rather than a raw (x, y) pair, but this code uses
 * them directly as raw x/y values with no decompression step. If that
 * reading is correct, these are not valid points on the curve at all, and
 * the handshake will not produce a key the Target agrees on.
 *
 * This could not be verified against AOSP source in this session (no
 * network access to the relevant source file, and no real device to test
 * a handshake against). Do not trust this constant without one of:
 *   (a) cross-referencing the real AOSP pairing_auth source directly, or
 *   (b) a real-device pairing attempt failing specifically at key
 *       agreement (rather than at an earlier framing/parsing step) as
 *       confirming evidence this needs correcting.
 * ============================================================
 *
 * What WAS fixed in this pass (verified, general elliptic-curve math, not
 * AOSP-specific): the previous version's point-doubling case was entirely
 * missing (P + P used the distinct-points chord formula, which divides by
 * zero and throws on every doubling), the point-at-infinity identity
 * element was represented as the concrete point (0, 0) instead of a
 * genuine identity value, and the wire-format message framing allocated
 * 64 bytes for a point that is always actually 65 bytes (0x04 prefix +
 * two 32-byte coordinates), which would overflow on send and fail to
 * parse on receive. All three are fixed below.
 */
object SpakeHandshake {
    private const val PROTOCOL_VERSION = 1
    private const val PAIRING_CODE_LENGTH = 6

    private const val SPAKE2_MSG_TYPE_CLIENT_HELLO = 0x55504348
    private const val SPAKE2_MSG_TYPE_SERVER_HELLO = 0x55505348

    private val P256_P = BigInteger(
        "FFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFF", 16
    )
    private val P256_A = BigInteger(
        "FFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFC", 16
    )
    private val P256_N = BigInteger(
        "FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551", 16
    )
    private val P256_GX = BigInteger(
        "6B17D1F2E12C4247F8BCE6E563A440F277037D812DEB33A0F4A13945D898C296", 16
    )
    private val P256_GY = BigInteger(
        "4FE342E2FE1A7F9B8EE7EB4A7C0F9E162BCE33576B315ECECBB6406837BF51F5", 16
    )

    // See the KNOWN UNVERIFIED RISK notice above.
    private val M = Point(
        BigInteger("02886e2f97ace46e55ba9dd7242579f2993b64e16ef3dcab95afd497333d8fa12f", 16),
        BigInteger("08672f70c6e9a6d1f139027f98e829974a01d2ba345fb845166a535a87f58706", 16)
    )
    private val N = Point(
        BigInteger("03d8bbd6c639c62937b04d997f38c3770719c629d7014d49a24b4f98baa1292b49", 16),
        BigInteger("0bc979c4b1fe063a7ff8f355f4b219f160144b208f763716011eaf6737ec069a", 16)
    )

    private val G = Point(P256_GX, P256_GY)

    fun perform(socket: Socket, pairingCode: String, isClient: Boolean): SpakeResult {
        require(pairingCode.length == PAIRING_CODE_LENGTH && pairingCode.all { it.isDigit() }) {
            "Pairing code must be $PAIRING_CODE_LENGTH digits"
        }

        val input = DataInputStream(socket.getInputStream())
        val output = DataOutputStream(socket.getOutputStream())
        val random = SecureRandom()

        val privateShare = BigInteger(256, random).mod(P256_N)
        val w = pairingCodeToBigInteger(pairingCode)

        val publicShare = if (isClient) {
            pointAdd(scalarMultiply(G, privateShare), scalarMultiply(M, w))
        } else {
            pointAdd(scalarMultiply(G, privateShare), scalarMultiply(N, w))
        }
        requireNotNull(publicShare) { "Computed public share was the point at infinity" }

        return if (isClient) {
            sendClientHello(output, publicShare)
            val serverHello = receiveServerHello(input)
            computeSharedKey(privateShare, serverHello.publicShare, pairingCode, isClient = true)
        } else {
            val clientHello = receiveClientHello(input)
            sendServerHello(output, publicShare)
            computeSharedKey(privateShare, clientHello.publicShare, pairingCode, isClient = false)
        }
    }

    private fun sendClientHello(output: DataOutputStream, publicShare: Point) {
        val buffer = ByteBuffer.allocate(4 + 1 + 65).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(SPAKE2_MSG_TYPE_CLIENT_HELLO)
        buffer.put(PROTOCOL_VERSION.toByte())
        buffer.put(publicShare.toUncompressedBytes())
        output.write(buffer.array())
        output.flush()
    }

    private fun sendServerHello(output: DataOutputStream, publicShare: Point) {
        val buffer = ByteBuffer.allocate(4 + 1 + 65).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(SPAKE2_MSG_TYPE_SERVER_HELLO)
        buffer.put(PROTOCOL_VERSION.toByte())
        buffer.put(publicShare.toUncompressedBytes())
        output.write(buffer.array())
        output.flush()
    }

    private fun receiveServerHello(input: DataInputStream): ServerHello {
        val header = ByteArray(5)
        input.readFully(header)
        val msgType = ByteBuffer.wrap(header, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
        require(msgType == SPAKE2_MSG_TYPE_SERVER_HELLO) { "Expected SERVER_HELLO, got $msgType" }

        val publicShareBytes = ByteArray(65)
        input.readFully(publicShareBytes)
        return ServerHello(Point.fromUncompressedBytes(publicShareBytes))
    }

    private fun receiveClientHello(input: DataInputStream): ClientHello {
        val header = ByteArray(5)
        input.readFully(header)
        val msgType = ByteBuffer.wrap(header, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
        require(msgType == SPAKE2_MSG_TYPE_CLIENT_HELLO) { "Expected CLIENT_HELLO, got $msgType" }

        val publicShareBytes = ByteArray(65)
        input.readFully(publicShareBytes)
        return ClientHello(Point.fromUncompressedBytes(publicShareBytes))
    }

    private fun computeSharedKey(
        privateShare: BigInteger,
        peerShare: Point,
        pairingCode: String,
        isClient: Boolean
    ): SpakeResult {
        val w = pairingCodeToBigInteger(pairingCode)
        val correction = if (isClient) N else M
        val corrected = pointAdd(peerShare, pointNegate(scalarMultiply(correction, w)))
        val sharedPoint = requireNotNull(scalarMultiply(corrected, privateShare)) {
            "Shared point computation resulted in the point at infinity"
        }

        val sharedKey = hkdfSha256(
            ikm = sharedPoint.x.toByteArray(),
            salt = null,
            info = "adb pairing".toByteArray(),
            length = 32
        )
        return SpakeResult(sharedKey)
    }

    private fun pairingCodeToBigInteger(pairingCode: String): BigInteger {
        val codeBytes = pairingCode.toByteArray(Charsets.UTF_8)
        val buffer = ByteArray(32)
        System.arraycopy(codeBytes, 0, buffer, 32 - codeBytes.size, codeBytes.size)
        return BigInteger(1, buffer)
    }

    private fun hkdfSha256(ikm: ByteArray, salt: ByteArray?, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val saltKey = salt ?: ByteArray(32)
        mac.init(SecretKeySpec(saltKey, "HmacSHA256"))
        val prk = mac.doFinal(ikm)

        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        mac.update(info)
        mac.update(1.toByte())
        return mac.doFinal().copyOf(length)
    }

    // Point at infinity (the group identity) is represented as Kotlin `null`,
    // NOT as any concrete (x, y) pair -- affine coordinates cannot represent
    // infinity as a normal point, and using a placeholder like (0,0) silently
    // corrupts every computation that touches it.

    private data class Point(val x: BigInteger, val y: BigInteger) {
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

    private fun pointNegate(point: Point?): Point? {
        if (point == null) return null
        return Point(point.x, P256_P.subtract(point.y).mod(P256_P))
    }

    private fun pointAdd(p: Point?, q: Point?): Point? {
        if (p == null) return q
        if (q == null) return p

        if (p.x == q.x) {
            val ySum = p.y.add(q.y).mod(P256_P)
            if (ySum == BigInteger.ZERO) return null // p == -q -> identity
            val numerator = p.x.multiply(p.x).multiply(BigInteger.valueOf(3)).add(P256_A).mod(P256_P)
            val denominator = BigInteger.valueOf(2).multiply(p.y).mod(P256_P)
            val lambda = numerator.multiply(denominator.modInverse(P256_P)).mod(P256_P)
            return pointFromLambda(p, p, lambda)
        }

        val numerator = q.y.subtract(p.y).mod(P256_P)
        val denominator = q.x.subtract(p.x).mod(P256_P)
        val lambda = numerator.multiply(denominator.modInverse(P256_P)).mod(P256_P)
        return pointFromLambda(p, q, lambda)
    }

    private fun pointFromLambda(p: Point, q: Point, lambda: BigInteger): Point {
        val x3 = lambda.multiply(lambda).subtract(p.x).subtract(q.x).mod(P256_P)
        val y3 = lambda.multiply(p.x.subtract(x3)).subtract(p.y).mod(P256_P)
        return Point(x3, y3)
    }

    private fun scalarMultiply(point: Point, scalar: BigInteger): Point? {
        var result: Point? = null // identity
        var current: Point? = point
        var remaining = scalar
        while (remaining > BigInteger.ZERO) {
            if (remaining.testBit(0)) {
                result = pointAdd(result, current)
            }
            current = pointAdd(current, current)
            remaining = remaining.shiftRight(1)
        }
        return result
    }

    private fun ByteArray.padToLength(length: Int): ByteArray {
        if (this.size >= length) return this.copyOf(length)
        val padded = ByteArray(length)
        System.arraycopy(this, 0, padded, length - this.size, this.size)
        return padded
    }

    private data class ClientHello(val publicShare: Point)
    private data class ServerHello(val publicShare: Point)

    data class SpakeResult(val sharedKey: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SpakeResult) return false
            return sharedKey.contentEquals(other.sharedKey)
        }
        override fun hashCode(): Int = sharedKey.contentHashCode()
    }
}