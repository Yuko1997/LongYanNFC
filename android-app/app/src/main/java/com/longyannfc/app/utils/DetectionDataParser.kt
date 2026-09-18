package com.longyannfc.app.utils

import timber.log.Timber

/**
 * Mifare Classic authentication trace from detection data.
 * Each trace captures one authentication exchange:
 *   Reader → Tag: {NT_enc, NR_enc}
 *   Tag → Reader: {AR_enc, AT_enc}
 */
data class AuthTrace(
    val uid: Int,
    val nt: Int,        // Tag nonce (4 bytes)
    val nr: Int,        // Reader nonce (4 bytes)
    val ar: Int,        // Reader answer (4 bytes)
    val at: Int,        // Tag answer (4 bytes)
    val sector: Int = -1,
    val keyType: Int = 0 // 0=A, 1=B
) {
    val uidHex: String get() = "%08X".format(uid)
    val ntHex: String get() = "%08X".format(nt)
    val nrHex: String get() = "%08X".format(nr)
    val arHex: String get() = "%08X".format(ar)
    val atHex: String get() = "%08X".format(at)
}

/**
 * Result from Crypto1 key recovery attempt.
 */
data class RecoveryResult(
    val key: Long,
    val uid: Int,
    val sector: Int,
    val keyType: Int,
    val verified: Boolean = false
) {
    val keyHex: String get() = "%012X".format(key)
    val uidHex: String get() = "%08X".format(uid)
}

/**
 * Parses Chameleon detection data (XMODEM download) into authentication traces.
 *
 * Chameleon device in MF_DETECTION_1K/4K mode records all authentication attempts.
 * The binary data format (per 16-byte frame):
 *   Bytes 0-3:  NT (tag nonce) or UID depending on frame type
 *   Bytes 4-7:  NR (reader nonce) or NT
 *   Bytes 8-11: AR (reader answer) or NR
 *   Bytes 12-15: AT (tag answer) or AR
 *
 * The actual format depends on the Chameleon firmware version.
 * We try multiple parsing strategies.
 */
object DetectionDataParser {

    /**
     * Parse raw detection data into authentication traces.
     * Tries multiple frame sizes and offsets.
     */
    fun parse(rawData: ByteArray, fallbackUid: Int = 0): List<AuthTrace> {
        if (rawData.size < 16) return emptyList()

        // Strategy 1: Parse as 16-byte frames (standard Chameleon format)
        val traces16 = parseAs16ByteFrames(rawData, fallbackUid)
        if (traces16.isNotEmpty()) return traces16

        // Strategy 2: Parse as 18-byte frames (with sector/type info)
        val traces18 = parseAs18ByteFrames(rawData, fallbackUid)
        if (traces18.isNotEmpty()) return traces18

        // Strategy 3: Scan for nonce patterns
        return scanForNoncePatterns(rawData, fallbackUid)
    }

    /**
     * Parse as 16-byte frames. Each frame is one authentication trace.
     * Format: [NT(4)] [NR(4)] [AR(4)] [AT(4)]
     */
    private fun parseAs16ByteFrames(data: ByteArray, fallbackUid: Int): List<AuthTrace> {
        val traces = mutableListOf<AuthTrace>()
        val frameSize = 16
        val numFrames = data.size / frameSize

        // First pass: try to find UID from first few frames
        var uid = fallbackUid
        for (i in 0 until minOf(numFrames, 4)) {
            val offset = i * frameSize
            val candidate = readInt32(data, offset)
            // UID is typically the same across all traces and has non-zero BCC
            if (candidate != 0 && isValidUid(candidate)) {
                uid = candidate
                break
            }
        }

        for (i in 0 until numFrames) {
            val offset = i * frameSize
            if (offset + frameSize > data.size) break

            val b0 = readInt32(data, offset)
            val b1 = readInt32(data, offset + 4)
            val b2 = readInt32(data, offset + 8)
            val b3 = readInt32(data, offset + 12)

            // Skip all-zero frames
            if (b0 == 0 && b1 == 0 && b2 == 0 && b3 == 0) continue

            // Heuristic: NT is usually different each time, NR/AR/AT are derived
            // The first 4 bytes could be NT or UID
            val trace = if (i == 0 && isValidUid(b0)) {
                // First frame might be UID + NT + NR + AR
                AuthTrace(uid = b0, nt = b1, nr = b2, ar = b3, at = 0)
            } else {
                AuthTrace(uid = uid, nt = b0, nr = b1, ar = b2, at = b3)
            }
            traces.add(trace)
        }

        return traces
    }

    /**
     * Parse as 18-byte frames (some Chameleon versions include sector/type).
     * Format: [NT(4)] [NR(4)] [AR(4)] [AT(4)] [Sector(1)] [KeyType(1)] [Padding(2)]
     */
    private fun parseAs18ByteFrames(data: ByteArray, fallbackUid: Int): List<AuthTrace> {
        val traces = mutableListOf<AuthTrace>()
        val frameSize = 18
        val numFrames = data.size / frameSize
        if (numFrames < 2) return emptyList()

        var uid = fallbackUid
        for (i in 0 until numFrames) {
            val offset = i * frameSize
            if (offset + frameSize > data.size) break

            val nt = readInt32(data, offset)
            val nr = readInt32(data, offset + 4)
            val ar = readInt32(data, offset + 8)
            val at = readInt32(data, offset + 12)
            val sector = data[offset + 16].toInt() and 0xFF
            val keyType = data[offset + 17].toInt() and 0xFF

            if (nt == 0 && nr == 0) continue

            traces.add(AuthTrace(uid = uid, nt = nt, nr = nr, ar = ar, at = at,
                sector = sector, keyType = keyType))
        }
        return traces
    }

    /**
     * Scan raw data for nonce-like patterns (fallback).
     * Looks for 4-byte values that appear in NT/NR/AR/AT positions.
     */
    private fun scanForNoncePatterns(data: ByteArray, fallbackUid: Int): List<AuthTrace> {
        val traces = mutableListOf<AuthTrace>()
        // Try every 16-byte alignment
        var i = 0
        while (i + 16 <= data.size) {
            val b0 = readInt32(data, i)
            val b1 = readInt32(data, i + 4)
            val b2 = readInt32(data, i + 8)
            val b3 = readInt32(data, i + 12)

            // At least two non-zero values needed
            val nonZero = listOf(b0, b1, b2, b3).count { it != 0 }
            if (nonZero >= 2) {
                traces.add(AuthTrace(uid = fallbackUid, nt = b0, nr = b1, ar = b2, at = b3))
            }
            i += 16
        }
        return traces
    }

    private fun readInt32(data: ByteArray, offset: Int): Int {
        if (offset + 4 > data.size) return 0
        return ((data[offset].toInt() and 0xFF) shl 24) or
                ((data[offset + 1].toInt() and 0xFF) shl 16) or
                ((data[offset + 2].toInt() and 0xFF) shl 8) or
                (data[offset + 3].toInt() and 0xFF)
    }

    private fun isValidUid(uid: Int): Boolean {
        if (uid == 0) return false
        val b0 = (uid shr 24) and 0xFF
        val b1 = (uid shr 16) and 0xFF
        val b2 = (uid shr 8) and 0xFF
        val b3 = uid and 0xFF
        val bcc = b0 xor b1 xor b2 xor b3
        // BCC check is not always valid for detection data, just check non-zero
        return true
    }
}
