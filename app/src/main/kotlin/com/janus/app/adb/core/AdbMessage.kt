package com.janus.app.adb.core

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A single ADB protocol message: a fixed 24-byte header followed by an
 * optional variable-length payload (spec #48).
 *
 * Header layout (all fields little-endian uint32):
 *   command | arg0 | arg1 | data_length | data_checksum | magic
 *
 * [magic] is always `command xor 0xFFFFFFFF` — a simple integrity check
 * that the header wasn't corrupted/misaligned, verified on decode.
 *
 * The checksum is NOT a real CRC32 despite ADB's historical field name
 * ("data_crc32") — it's the additive sum of all payload bytes (each byte
 * treated as unsigned 0-255), truncated to 32 bits. This is a well-known
 * quirk of the ADB protocol's naming versus actual behavior.
 */
data class AdbMessage(
    val command: Int,
    val arg0: Int,
    val arg1: Int,
    val payload: ByteArray = ByteArray(0)
) {
    fun encode(): ByteArray {
        val header = ByteBuffer.allocate(AdbProtocol.MESSAGE_HEADER_SIZE)
            .order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(command)
        header.putInt(arg0)
        header.putInt(arg1)
        header.putInt(payload.size)
        header.putInt(computeChecksum(payload))
        header.putInt(command xor -1) // magic = command xor 0xFFFFFFFF

        return header.array() + payload
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AdbMessage) return false
        return command == other.command &&
            arg0 == other.arg0 &&
            arg1 == other.arg1 &&
            payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = command
        result = 31 * result + arg0
        result = 31 * result + arg1
        result = 31 * result + payload.contentHashCode()
        return result
    }

    companion object {
        fun decodeHeader(headerBytes: ByteArray): DecodedHeader {
            require(headerBytes.size == AdbProtocol.MESSAGE_HEADER_SIZE) {
                "Expected ${AdbProtocol.MESSAGE_HEADER_SIZE}-byte header, got ${headerBytes.size}"
            }
            val buffer = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
            val command = buffer.int
            val arg0 = buffer.int
            val arg1 = buffer.int
            val dataLength = buffer.int
            val dataChecksum = buffer.int
            val magic = buffer.int

            val expectedMagic = command xor -1
            require(magic == expectedMagic) {
                "ADB message magic mismatch: expected $expectedMagic, got $magic"
            }

            return DecodedHeader(command, arg0, arg1, dataLength, dataChecksum)
        }

        fun computeChecksum(payload: ByteArray): Int {
            var sum = 0
            for (byte in payload) {
                sum += byte.toInt() and 0xFF
            }
            return sum
        }
    }

    data class DecodedHeader(
        val command: Int,
        val arg0: Int,
        val arg1: Int,
        val dataLength: Int,
        val dataChecksum: Int
    )
}