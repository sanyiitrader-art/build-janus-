package com.janus.app.adb.core

/**
 * ADB wire protocol constants (spec #48).
 *
 * Command IDs are the well-documented ADB protocol "magic" values: each
 * command is 4 ASCII characters packed as a little-endian uint32 — e.g.
 * "CNXN" -> bytes ['C','N','X','N'] -> little-endian 0x4E584E43. These
 * values are stable, published parts of the ADB protocol (unlike the RSA
 * public key encoding in AdbRsaKeyPair.kt, which is comparatively obscure
 * and the higher-risk piece to get exactly right).
 */
object AdbProtocol {

    // Command identifiers ("CNXN", "AUTH", "OPEN", "OKAY", "CLSE", "WRTE",
    // "SYNC" as little-endian packed ASCII).
    const val CMD_SYNC = 0x434E5953
    const val CMD_CNXN = 0x4E584E43
    const val CMD_AUTH = 0x48545541
    const val CMD_OPEN = 0x4E45504F
    const val CMD_OKAY = 0x59414B4F
    const val CMD_CLSE = 0x45534C43
    const val CMD_WRTE = 0x45545257

    // CNXN handshake parameters.
    const val CONNECT_VERSION = 0x01000000
    const val CONNECT_MAX_DATA = 1 * 1024 * 1024

    /**
     * CNXN payload identifying this Controller to the Target. Must be
     * null-terminated per protocol convention (adbd reads it as a C
     * string). "host::" is the conventional prefix ADB clients use.
     */
    const val CONNECT_PAYLOAD = "host::\u0000"

    // AUTH message sub-types (the `arg0` field of an AUTH message).
    const val AUTH_TYPE_TOKEN = 1
    const val AUTH_TYPE_SIGNATURE = 2
    const val AUTH_TYPE_RSA_PUBLIC_KEY = 3

    /** Fixed header size: 6 uint32 fields (command, arg0, arg1, data_length, data_crc32, magic). */
    const val MESSAGE_HEADER_SIZE = 24
}