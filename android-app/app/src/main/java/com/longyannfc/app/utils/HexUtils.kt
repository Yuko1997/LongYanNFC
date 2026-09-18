package com.longyannfc.app.utils

/**
 * Hex data manipulation utilities for card data editing and analysis.
 */
object HexUtils {

    fun toHex(data: ByteArray, separator: String = " "): String {
        return data.joinToString(separator) { "%02X".format(it) }
    }

    fun toHex(data: IntArray, separator: String = ""): String {
        return data.joinToString(separator) { "%02X".format(it and 0xFF) }
    }

    fun toHex(data: List<Int>, separator: String = ""): String {
        return data.joinToString(separator) { "%02X".format(it and 0xFF) }
    }

    fun toHexCompact(data: ByteArray): String = toHex(data, "")

    fun fromHex(hex: String): ByteArray {
        val cleanHex = hex.replace(Regex("[^0-9A-Fa-f]"), "")
        if (cleanHex.isEmpty()) return ByteArray(0)
        return ByteArray(cleanHex.length / 2) { i ->
            cleanHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    fun fromHexInts(hex: String): IntArray {
        return fromHex(hex).map { it.toInt() and 0xFF }.toIntArray()
    }

    fun bytesToInts(data: ByteArray): IntArray = IntArray(data.size) { data[it].toInt() and 0xFF }

    fun intsToBytes(data: IntArray): ByteArray = data.map { it.toByte() }.toByteArray()

    fun intsToBytes(data: List<Int>): ByteArray = data.map { it.toByte() }.toByteArray()

    fun byte2Number(data: ByteArray, length: Int = data.size): Int {
        var value = 0
        for (i in 0 until length) {
            value = (value shl 8) + (data[i].toInt() and 0xFF)
        }
        return value
    }

    fun byte2Number(data: IntArray, length: Int = data.size): Int {
        var value = 0
        for (i in 0 until length) {
            value = (value shl 8) + (data[i] and 0xFF)
        }
        return value
    }

    fun number2Bytes(value: Int, length: Int): ByteArray {
        return ByteArray(length) { i ->
            ((value ushr ((length - 1 - i) * 8)) and 0xFF).toByte()
        }
    }

    fun keyToHex(key: IntArray): String = toHex(key)

    fun defaultKey(): IntArray = intArrayOf(0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF)

    /**
     * Format hex data for display with offset, hex, and ASCII
     */
    fun formatHexDump(data: ByteArray, bytesPerLine: Int = 16): List<HexLine> {
        val lines = mutableListOf<HexLine>()
        for (i in data.indices step bytesPerLine) {
            val chunk = data.copyOfRange(i, minOf(i + bytesPerLine, data.size))
            val hex = toHex(chunk)
            val ascii = chunk.map { if (it in 32..126) it.toChar() else '.' }.joinToString("")
            lines.add(HexLine(i, hex, ascii))
        }
        return lines
    }

    /**
     * Compare two byte arrays and return differences
     */
    fun diff(a: ByteArray, b: ByteArray): List<DiffEntry> {
        val diffs = mutableListOf<DiffEntry>()
        val size = maxOf(a.size, b.size)
        for (i in 0 until size) {
            val byteA = if (i < a.size) a[i] else 0
            val byteB = if (i < b.size) b[i] else 0
            if (byteA != byteB) {
                diffs.add(DiffEntry(i, byteA, byteB))
            }
        }
        return diffs
    }

    /**
     * XOR two byte arrays
     */
    fun xor(a: ByteArray, b: ByteArray): ByteArray {
        val size = minOf(a.size, b.size)
        return ByteArray(size) { i -> (a[i].toInt() xor b[i].toInt()).toByte() }
    }

    /**
     * Reverse bytes (for endianness conversion)
     */
    fun reverse(data: ByteArray): ByteArray {
        return data.reversedArray()
    }

    /**
     * Extract bits from byte
     */
    fun getBits(byte: Byte, offset: Int, length: Int): Int {
        val mask = (1 shl length) - 1
        return (byte.toInt() shr offset) and mask
    }

    /**
     * Set bits in byte
     */
    fun setBits(byte: Byte, offset: Int, length: Int, value: Int): Byte {
        val mask = (1 shl length) - 1
        val clearMask = (mask shl offset).inv()
        val newByte = (byte.toInt() and clearMask) or ((value and mask) shl offset)
        return newByte.toByte()
    }

    /**
     * Calculate parity of a byte
     */
    fun parity(byte: Byte): Int {
        var b = byte.toInt()
        var parity = 0
        repeat(8) {
            parity = parity xor (b and 1)
            b = b shr 1
        }
        return parity
    }

    /**
     * Convert UID to various formats
     */
    fun formatUid(uid: ByteArray): String {
        return when (uid.size) {
            4 -> uid.joinToString("") { "%02X".format(it) }
            7 -> uid.joinToString(":") { "%02X".format(it) }
            else -> toHex(uid)
        }
    }
}

data class HexLine(
    val offset: Int,
    val hex: String,
    val ascii: String
)

data class DiffEntry(
    val offset: Int,
    val oldValue: Byte,
    val newValue: Byte
)
