package com.longyannfc.app.utils

/**
 * Bit-addressable byte buffer, ported from utils/crypto1.js Packet.
 */
class Packet(size: Int) : Iterable<Int> {
    private val data = ByteArray(size)

    constructor(bytes: ByteArray) : this(bytes.size) {
        bytes.copyInto(data)
    }

    val size: Int get() = data.size
    val length: Int get() = data.size

    operator fun get(index: Int): Int = data[index].toInt() and 0xFF

    operator fun set(index: Int, value: Int) {
        data[index] = value.toByte()
    }

    fun toByteArray(): ByteArray = data.copyOf()

    fun getBit(index: Int, lsbFirst: Boolean = false): Int {
        val bitIndex = 7 xor if (lsbFirst) index else 8 * length - index - 1
        return (this[bitIndex ushr 3] ushr (bitIndex and 7)) and 1
    }

    fun setBit(index: Int, value: Boolean, lsbFirst: Boolean = false): Packet {
        val bitIndex = 7 xor if (lsbFirst) index else 8 * length - index - 1
        val current = (this[bitIndex ushr 3] ushr (bitIndex and 7)) and 1
        val desired = if (value) 1 else 0
        if (current xor desired != 0) {
            data[bitIndex ushr 3] = (this[bitIndex ushr 3] xor (1 shl (bitIndex and 7))).toByte()
        }
        return this
    }

    fun reverse(): Packet {
        data.reverse()
        return this
    }

    override fun iterator(): Iterator<Int> = data.map { it.toInt() and 0xFF }.iterator()

    companion object {
        fun fromHex(hex: String, reverse: Boolean = false): Packet {
            val clean = hex.replace(Regex("[^0-9A-Fa-f]"), "")
            require(clean.length % 2 == 0) { "invalid hex string" }
            val packet = Packet(clean.length / 2)
            for (i in packet.data.indices) {
                packet[i] = clean.substring(i * 2, i * 2 + 2).toInt(16)
            }
            if (reverse) packet.reverse()
            return packet
        }
    }
}
