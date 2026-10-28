package com.longyannfc.app.utils

/**
 * CRC32 implementation for DFU packet verification.
 * Uses the standard polynomial 0xEDB88320 (reflected form of 0x04C11DB7).
 */
object CRC32 {
    private val table: IntArray = IntArray(256) { i ->
        var crc = i
        repeat(8) {
            crc = if ((crc and 1) != 0) {
                (crc ushr 1) xor 0xEDB88320.toInt()
            } else {
                crc ushr 1
            }
        }
        crc
    }

    /**
     * Calculate CRC32 checksum of byte array
     */
    fun calculate(data: ByteArray, offset: Int = 0, length: Int = data.size): Long {
        var crc = 0xFFFFFFFF.toInt()
        for (i in offset until offset + length) {
            val idx = (crc xor data[i].toInt()) and 0xFF
            crc = (crc ushr 8) xor table[idx]
        }
        return (crc xor 0xFFFFFFFF.toInt()).toLong() and 0xFFFFFFFF
    }

    /**
     * Calculate CRC32 of byte array (returns Int)
     */
    fun calculateInt(data: ByteArray): Int {
        return calculate(data).toInt()
    }
}
