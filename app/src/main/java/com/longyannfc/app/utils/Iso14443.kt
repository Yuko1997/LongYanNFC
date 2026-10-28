package com.longyannfc.app.utils

/**
 * ISO14443-A CRC, ported from pn532.js Iso14443ACRC.
 * Polynomial equivalent of initial value 0x6363 (25443).
 */
object Iso14443 {
    fun crcA(data: IntArray, initial: Int = 25443, xorOut: Int = 0): IntArray {
        var crc = initial
        for (byte in data) {
            var r = (crc xor byte) and 0xFF
            r = r xor (r shl 4)
            r = r and 0xFF
            crc = (crc ushr 8) xor (r shl 8) xor (r ushr 4) xor (r shl 3)
            crc = crc and 0xFFFF
        }
        val out = crc xor xorOut
        return intArrayOf(out and 0xFF, (out ushr 8) and 0xFF)
    }

    fun appendCrcA(data: MutableList<Int>) {
        val crc = crcA(data.toIntArray())
        data.add(crc[0])
        data.add(crc[1])
    }

    fun appendCrcA(data: ByteArray): ByteArray {
        val ints = data.map { it.toInt() and 0xFF }.toMutableList()
        appendCrcA(ints)
        return ints.map { it.toByte() }.toByteArray()
    }
}
