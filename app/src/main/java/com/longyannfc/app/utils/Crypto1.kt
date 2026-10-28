package com.longyannfc.app.utils

/**
 * Crypto1 stream cipher, ported from utils/crypto1.js.
 * odd/even are 24-bit LFSR halves; filter/lfsrBit/prngSuccessor match the original.
 */
class Crypto1 {
    var odd: Int = 0
    var even: Int = 0

    fun oddParity8(t: Int): Int = ODD_PARITY[t and 0xFF]

    fun evenParity8(value: Int): Int {
        var t = value
        t = t xor (t ushr 4)
        t = t xor (t ushr 2)
        return toBit(t xor (t ushr 1))
    }

    fun oddParity16(value: Int): Int {
        var t = value
        t = t xor (t shr 8)
        return oddParity8(t)
    }

    fun evenParity16(value: Int): Int {
        var t = value
        t = t xor (t shr 8)
        return evenParity8(t)
    }

    fun oddParity32(value: Int): Int {
        var t = value
        t = t xor (t shr 16)
        t = t xor (t shr 8)
        return oddParity8(t)
    }

    fun evenParity32(value: Int): Int {
        var t = value
        t = t xor (t shr 16)
        t = t xor (t shr 8)
        return evenParity8(t)
    }

    fun toBit(t: Int): Int = t and 1

    fun bit(t: Int, e: Int): Int = toBit(t ushr e)

    fun toBool(t: Int): Int = if (t != 0) 1 else 0

    fun toBool(t: Boolean): Int = if (t) 1 else 0

    fun toUint24(t: Int): Int = t and 0xFFFFFF

    fun toUint32(t: Int): Int = t

    fun toUint8(t: Int): Int = t and 0xFF

    fun beBit(t: Int, e: Int): Int = bit(t, 24 xor e)

    fun setLfsr(packet: Packet) {
        odd = 0
        even = 0
        var e = 47
        while (e >= 0) {
            odd = (odd shl 1) or packet.getBit(e - 1 xor 7)
            even = (even shl 1) or packet.getBit(7 xor e)
            e -= 2
        }
    }

    fun setLfsr(hexKey: String) {
        setLfsr(Packet.fromHex(hexKey))
    }

    fun setLfsr(key: Long) {
        setLfsr(key.toString(16).padStart(12, '0'))
    }

    fun setLfsr(key: Int) {
        setLfsr(key.toLong() and 0xFFFFFFFFL)
    }

    fun getState(): Long {
        val bytes = getLfsr().toByteArray()
        var value = 0L
        for (b in bytes) {
            value = (value shl 8) or (b.toLong() and 0xFF)
        }
        return value
    }

    fun lfsrBit(input: Int, isEncrypted: Boolean): Int = lfsrBit(input, if (isEncrypted) 1 else 0)

    fun lfsrByte(t: Int, e: Boolean): Int = lfsrByte(t, if (e) 1 else 0)

    fun lfsrWord(t: Int, e: Boolean): Int = lfsrWord(t, if (e) 1 else 0)

    fun lfsrRollbackBit(): Int = lfsrRollbackBit(0, 0)

    fun lfsrRollbackBit(input: Int, isEncrypted: Boolean): Int =
        lfsrRollbackBit(input, if (isEncrypted) 1 else 0)

    fun lfsrRollbackByte(t: Int, e: Boolean): Int = lfsrRollbackByte(t, if (e) 1 else 0)

    fun lfsrRollbackWord(t: Int, e: Boolean): Int = lfsrRollbackWord(t, if (e) 1 else 0)

    fun getLfsr(): Packet {
        val t = Packet(6)
        for (e in 23 downTo 0) {
            t.setBit(46 - 2 * e, bit(odd, 3 xor e) != 0, true)
            t.setBit(47 - 2 * e, bit(even, 3 xor e) != 0, true)
        }
        return t
    }

    fun getStateHex(): String {
        return getLfsr().toByteArray().joinToString("") { "%02X".format(it) }
    }

    fun filter(t: Int): Int {
        var e = 0
        e = e or ((991936 ushr (t and 15)) and 16)
        e = e or ((444864 ushr (t ushr 4 and 15)) and 8)
        e = e or ((247984 ushr (t ushr 8 and 15)) and 4)
        e = e or ((123992 ushr (t ushr 12 and 15)) and 2)
        e = e or ((55608 ushr (t ushr 16 and 15)) and 1)
        return bit(FILTER_TABLE, e)
    }

    fun lfsrBit(input: Int, isEncrypted: Int): Int {
        val i = filter(odd)
        val r = (i and toBool(isEncrypted)) xor toBool(input) xor (0x29CE5C and odd) xor (0x870804 and even)
        val newOdd = toUint32((even shl 1) or evenParity32(r))
        val newEven = odd
        odd = newOdd
        even = newEven
        return i
    }

    fun lfsrByte(t: Int, e: Int): Int {
        var i = 0
        for (r in 0 until 8) {
            i = i or (lfsrBit(bit(t, r), e) shl r)
        }
        return i
    }

    fun lfsrWord(t: Int, e: Int): Int {
        var result = 0
        for (r in 0 until 32) {
            result = result or (lfsrBit(beBit(t, r), e) shl (24 xor r))
        }
        return result
    }

    fun swapEndian(t: Int): Int {
        return ((t and 0xFF) shl 24) or
            ((t and 0xFF00) shl 8) or
            ((t ushr 8) and 0xFF00) or
            ((t ushr 24) and 0xFF)
    }

    fun prngSuccessor(n: Int, bits: Int): Int {
        var t = swapEndian(n)
        repeat(bits) {
            t = (t ushr 1) or (((t ushr 16) xor (t ushr 18) xor (t ushr 19) xor (t ushr 21)) shl 31)
        }
        return swapEndian(t)
    }

    fun lfsrRollbackBit(input: Int, isEncrypted: Int): Int {
        val oldOdd = toUint24(odd)
        val oldEven = even
        even = oldOdd
        odd = oldEven
        val r = filter(odd)
        var n = toBit(even)
        even = even ushr 1
        n = n xor (0x870804 and even)
        n = n xor (0x29CE5C and odd)
        n = n xor toBool(input) xor (r and toBool(isEncrypted))
        even = toUint32(even or (evenParity32(n) shl 23))
        return r
    }

    fun lfsrRollbackByte(t: Int, e: Int): Int {
        var i = 0
        for (r in 7 downTo 0) {
            i = i or (lfsrRollbackBit(bit(t, r), e) shl r)
        }
        return i
    }

    fun lfsrRollbackWord(t: Int, e: Int): Int {
        var result = 0
        for (r in 31 downTo 0) {
            result = result or (lfsrRollbackBit(beBit(t, r), e) shl (24 xor r))
        }
        return result
    }

    fun updateContribution(t: Int, e: Int, i: Int): Int {
        var r = t ushr 25
        r = (r shl 1) or evenParity32(t and e)
        r = (r shl 1) or evenParity32(t and i)
        return toUint32((r shl 24) or (t and 0xFFFFFF))
    }

    fun extendTable(t: IntArray, eIn: Int, i: Int, r: Int, n: Int, sIn: Int): Int {
        val s = toUint32(sIn shl 24)
        var e = eIn
        var u = 0
        while (u < e) {
            t[u] = t[u] * 2
            val o = filter(t[u])
            if (o xor filter(1 or t[u]) != 0) {
                t[u] = updateContribution(t[u] + (o xor i), r, n) xor s
            } else if (o == i) {
                t[e++] = t[++u]
                t[u] = updateContribution(t[u - 1] + 1, r, n) xor s
                t[u - 1] = updateContribution(t[u - 1], r, n) xor s
            } else {
                t[u--] = t[--e]
            }
            u++
        }
        return e
    }

    fun reset(): Crypto1 {
        odd = 0
        even = 0
        return this
    }

    companion object {
        private const val FILTER_TABLE = -329783286 // 3965184010 as signed int

        private val ODD_PARITY = intArrayOf(
            1, 0, 0, 1, 0, 1, 1, 0, 0, 1, 1, 0, 1, 0, 0, 1,
            0, 1, 1, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0, 1, 1, 0,
            0, 1, 1, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0, 1, 1, 0,
            1, 0, 0, 1, 0, 1, 1, 0, 0, 1, 1, 0, 1, 0, 0, 1,
            0, 1, 1, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0, 1, 1, 0,
            1, 0, 0, 1, 0, 1, 1, 0, 0, 1, 1, 0, 1, 0, 0, 1,
            1, 0, 0, 1, 0, 1, 1, 0, 0, 1, 1, 0, 1, 0, 0, 1,
            0, 1, 1, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0, 1, 1, 0,
            0, 1, 1, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0, 1, 1, 0,
            1, 0, 0, 1, 0, 1, 1, 0, 0, 1, 1, 0, 1, 0, 0, 1,
            1, 0, 0, 1, 0, 1, 1, 0, 0, 1, 1, 0, 1, 0, 0, 1,
            0, 1, 1, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0, 1, 1, 0,
            1, 0, 0, 1, 0, 1, 1, 0, 0, 1, 1, 0, 1, 0, 0, 1,
            0, 1, 1, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0, 1, 1, 0,
            0, 1, 1, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0, 1, 1, 0,
            1, 0, 0, 1, 0, 1, 1, 0, 0, 1, 1, 0, 1, 0, 0, 1
        )
    }
}
