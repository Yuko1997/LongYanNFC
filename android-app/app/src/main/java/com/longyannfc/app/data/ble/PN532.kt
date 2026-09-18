package com.longyannfc.app.data.ble

import com.longyannfc.app.utils.Crypto1
import com.longyannfc.app.utils.HexUtils
import com.longyannfc.app.utils.Iso14443
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class PassiveTarget(
    val tags: Int,
    val uidLen: Int,
    val atqa: IntArray,
    val sak: Int,
    val uid: IntArray
)

class Pn532Exception(message: String, val code: Int = 2) : Exception(message)

@Singleton
class PN532 @Inject constructor(
    private val bleManager: BleManager
) {
    val crypto1 = Crypto1()
    var gen3KeyHex: String = "A396EFA4E24F"
    var parityEnable: Boolean = true

    private val rxBuffer = mutableListOf<Int>()
    private var fragmentedFrame = false
    private var lastFrameRcvLen = 0
    private var frameRecvFinish = false
    private var currentFrameCmdLen = 0
    private val frameReady = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val commandMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            bleManager.incoming.collect { data ->
                runCatching { handleIncoming(data) }
                    .onFailure {
                        Timber.w(it, "Ignored malformed PN532 BLE frame")
                        clearRxBuffer()
                    }
            }
        }
    }

    fun handleIncoming(data: ByteArray) {
        handlePn53xFrame(data.map { it.toInt() and 0xFF }.toIntArray())
    }

    companion object {
        fun buildFrame(payload: IntArray): IntArray {
            val s = payload.size + 1
            var checksum = -1
            val frame = mutableListOf(0, 0, 255, s, (255 - s + 1) and 0xFF, 0xD4)
            checksum += 0xD4
            for (b in payload) {
                frame.add(b and 0xFF)
                checksum += b and 0xFF
            }
            checksum %= 256
            frame.add((255 - checksum) and 0xFF)
            frame.add(0)
            return frame.toIntArray()
        }

        val WAKEUP_FRAME = intArrayOf(
            0x55, 0x55, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0xFF, 5, 0xFB, 0xD4, 20, 1, 2, 0
        )
    }

    fun clearRxBuffer() {
        rxBuffer.clear()
        frameRecvFinish = false
        lastFrameRcvLen = 0
        fragmentedFrame = false
        currentFrameCmdLen = 0
    }

    private fun handlePn53xFrame(r: IntArray) {
        val t = r.size
        if (t == 0) return
        if (r[0] != 0 || r.getOrNull(1) != 0 || r.getOrNull(2) != 255) {
            if (fragmentedFrame) {
                if (t + lastFrameRcvLen > 262) {
                    clearRxBuffer()
                } else if (t + lastFrameRcvLen == 6 + currentFrameCmdLen + 7) {
                    rxBuffer.addAll(r.toList())
                    frameRecvFinish = true
                    fragmentedFrame = false
                    frameReady.tryEmit(Unit)
                } else {
                    lastFrameRcvLen += t
                    rxBuffer.addAll(r.toList())
                }
            } else {
                clearRxBuffer()
            }
            return
        }
        rxBuffer.addAll(r.toList())
        Timber.d("PN532 RX ${HexUtils.toHex(r)}")
        if (t < 6) {
            clearRxBuffer()
            return
        }
        if (t == r[3] + 7 && fragmentedFrame) {
            fragmentedFrame = false
            frameRecvFinish = true
            frameReady.tryEmit(Unit)
            return
        }
        if (t == 6) {
            fragmentedFrame = true
            return
        }
        if (t > 6 && t < 9) {
            clearRxBuffer()
            return
        }
        if (t > 9) {
            if (t == r[9] + 7 + 6) {
                frameRecvFinish = true
                fragmentedFrame = false
                frameReady.tryEmit(Unit)
            } else {
                currentFrameCmdLen = r[9]
                fragmentedFrame = true
                lastFrameRcvLen += t
            }
        }
    }

    suspend fun sendCommand(payload: IntArray) {
        val frame = buildFrame(payload)
        Timber.d("PN532 TX ${HexUtils.toHex(frame)}")
        clearRxBuffer()
        bleManager.writeRaw(HexUtils.intsToBytes(frame))
    }

    private suspend fun waitForFrame(timeoutMs: Long): List<Int> {
        val start = System.currentTimeMillis()
        while (true) {
            if (frameRecvFinish && rxBuffer.isNotEmpty()) return rxBuffer.toList()
            if (System.currentTimeMillis() - start >= timeoutMs) {
                clearRxBuffer()
                throw Pn532Exception("Resp Timeout exceeded", 1)
            }
            delay(1)
        }
    }

    suspend fun readResponseWithTime(cmd: Int, timeoutMs: Long = 500): IntArray {
        val r = waitForFrame(timeoutMs)
        if (r.size < 6) throw Pn532Exception("ACK Fail.", 1)
        if (r.size <= 12) throw Pn532Exception("Frame LostData.", 2)
        if (r[12] != cmd + 1 || r[11] != 0xD5) throw Pn532Exception("CMD OR TFI Error.", 2)
        val n = r[9]
        if (n < 1) throw Pn532Exception("Frame Decode Error.", 2)
        if (n + 6 + 7 != r.size) throw Pn532Exception("Frame LostData.", 2)
        return r.subList(12, 12 + n - 1).toIntArray()
    }

    private suspend fun <T> withCommand(block: suspend () -> T): T = commandMutex.withLock { block() }

    suspend fun wakeup() {
        Timber.d("PN532 wakeup")
        bleManager.writeRaw(HexUtils.intsToBytes(WAKEUP_FRAME))
        delay(200)
    }

    suspend fun samConfiguration(timeoutMs: Long = 500): Boolean = withCommand {
        sendCommand(intArrayOf(20, 1, 20, 1))
        val resp = readResponseWithTime(20, timeoutMs)
        if (resp.isEmpty()) throw Pn532Exception("Frame Decode Error.")
        true
    }

    suspend fun getFirmwareVersion(timeoutMs: Long = 500): IntArray = withCommand {
        sendCommand(intArrayOf(2))
        val s = readResponseWithTime(2, timeoutMs)
        if (s.size < 5) throw Pn532Exception("Frame Decode Error.")
        intArrayOf(s[1], s[2], s[3], s[4])
    }

    suspend fun inListPassiveTarget(
        maxTargets: Int = 1,
        baud: Int = 0,
        extra: IntArray = intArrayOf(),
        timeoutMs: Long = 500
    ): PassiveTarget = withCommand {
        val cmd = mutableListOf(74, maxTargets, baud)
        cmd.addAll(extra.toList())
        sendCommand(cmd.toIntArray())
        val i = try {
            readResponseWithTime(74, timeoutMs)
        } catch (e: Exception) {
            throw Pn532Exception("未发现卡片.", 0)
        }
        if (i.size < 7) throw Pn532Exception("Frame Decode Error.")
        val uidLen = i[6]
        if (i.size < 7 + uidLen) throw Pn532Exception("Frame Decode Error.")
        PassiveTarget(
            tags = i[1],
            uidLen = uidLen,
            atqa = i.sliceArray(3 until 5),
            sak = i[5],
            uid = i.sliceArray(7 until 7 + uidLen)
        )
    }

    suspend fun inDataExchange(tg: Int = 1, data: IntArray, timeoutMs: Long = 500): IntArray = withCommand {
        val cmd = mutableListOf(64, tg)
        cmd.addAll(data.toList())
        sendCommand(cmd.toIntArray())
        val o = readResponseWithTime(64, timeoutMs)
        if (o.isEmpty()) throw Pn532Exception("Frame Decode Error.")
        o.copyOfRange(1, o.size)
    }

    suspend fun inCommunicateThru(data: IntArray, timeoutMs: Long = 500): IntArray = withCommand {
        val cmd = mutableListOf(66)
        cmd.addAll(data.toList())
        sendCommand(cmd.toIntArray())
        val o = readResponseWithTime(66, timeoutMs)
        if (o.isEmpty()) throw Pn532Exception("Frame Decode Error.")
        o.copyOfRange(1, o.size)
    }

    suspend fun rfConfiguration(cfg: Int, value: Any, timeoutMs: Long = 500): Boolean = withCommand {
        val cmd = mutableListOf(50, cfg)
        when (value) {
            is Int -> cmd.add(value)
            is IntArray -> cmd.addAll(value.toList())
            is List<*> -> cmd.addAll(value.map { (it as Number).toInt() })
        }
        sendCommand(cmd.toIntArray())
        val resp = readResponseWithTime(50, timeoutMs)
        if (resp.isEmpty()) throw Pn532Exception("Frame Decode Error.")
        true
    }

    suspend fun setParameter(value: Int, timeoutMs: Long = 500): Boolean = withCommand {
        sendCommand(intArrayOf(18, value))
        val resp = readResponseWithTime(18, timeoutMs)
        if (resp.isEmpty()) throw Pn532Exception("Frame Decode Error.")
        true
    }

    suspend fun inRelease(tg: Int = 1, timeoutMs: Long = 500): IntArray = withCommand {
        sendCommand(intArrayOf(82, tg))
        val c = readResponseWithTime(82, timeoutMs)
        if (c.isEmpty()) throw Pn532Exception("Frame Decode Error.")
        c.copyOfRange(1, c.size)
    }

    suspend fun inDeSelect(tg: Int = 1, timeoutMs: Long = 500): IntArray = withCommand {
        sendCommand(intArrayOf(68, tg))
        val c = readResponseWithTime(68, timeoutMs)
        if (c.isEmpty()) throw Pn532Exception("Frame Decode Error.")
        c.copyOfRange(1, c.size)
    }

    suspend fun readRegister(addrs: IntArray, timeoutMs: Long = 500): IntArray = withCommand {
        val s = mutableListOf(6)
        for (addr in addrs) {
            s.add((addr shr 8) and 0xFF)
            s.add(addr and 0xFF)
        }
        sendCommand(s.toIntArray())
        val u = readResponseWithTime(6, timeoutMs)
        if (u.size < 2) throw Pn532Exception("Frame Decode Error.")
        u.copyOfRange(1, u.size)
    }

    suspend fun writeRegister(addrs: IntArray, values: IntArray, timeoutMs: Long = 500): Boolean = withCommand {
        if (addrs.size != values.size) return@withCommand false
        val c = mutableListOf(8)
        for (i in addrs.indices) {
            c.add((addrs[i] shr 8) and 0xFF)
            c.add(addrs[i] and 0xFF)
            c.add(values[i] and 0xFF)
        }
        sendCommand(c.toIntArray())
        val resp = readResponseWithTime(8, timeoutMs)
        if (resp.isEmpty()) throw Pn532Exception("Frame Decode Error.")
        true
    }

    suspend fun updateRegisterWithMask(rows: List<IntArray>, timeoutMs: Long = 500): Boolean {
        val addrs = rows.map { it[0] }.toIntArray()
        val masks = rows.map { it[1] }.toIntArray()
        val values = rows.map { it[2] }.toIntArray()
        val current = readRegister(addrs, timeoutMs)
        val next = IntArray(addrs.size) { p ->
            (values[p] and masks[p]) or (current[p] and masks[p].inv())
        }
        return writeRegister(addrs, next, timeoutMs)
    }

    suspend fun pn532SetTxRxCrc(tx: Boolean, rx: Boolean): Boolean {
        return updateRegisterWithMask(
            listOf(
                intArrayOf(25347, 128, if (rx) 128 else 0),
                intArrayOf(25346, 128, if (tx) 128 else 0)
            )
        )
    }

    suspend fun pn532SetFrameTxParity(enable: Boolean): Boolean {
        parityEnable = enable
        return updateRegisterWithMask(listOf(intArrayOf(25357, 16, if (enable) 0 else 16)))
    }

    suspend fun pn532SetTxBits(bits: Int) {
        writeRegister(intArrayOf(25405), intArrayOf(bits))
    }

    suspend fun initAsInitiator(): Boolean {
        return updateRegisterWithMask(
            listOf(
                intArrayOf(25349, 64, 64),
                intArrayOf(25404, 16, 16)
            )
        )
    }

    suspend fun prepareForTransBits(): Boolean {
        return try {
            initAsInitiator()
            rfConfiguration(1, 0)
            rfConfiguration(5, intArrayOf(0, 1, 2))
            setParameter(4)
            rfConfiguration(1, 1)
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun mifareAuthBlock(uid: IntArray, key: IntArray, keyType: Int, block: Int, timeoutMs: Long = 500): IntArray {
        val trailer = block + 4 - block % 4 - 1
        val payload = mutableListOf(if (keyType == 1) 0x60 else 0x61, trailer)
        payload.addAll(key.toList())
        payload.addAll(uid.toList())
        return inDataExchange(1, payload.toIntArray(), timeoutMs)
    }

    suspend fun mifareReadBlock(block: Int, timeoutMs: Long = 500): IntArray {
        return inDataExchange(1, intArrayOf(0x30, block), timeoutMs)
    }

    suspend fun mifareWriteBlock(block: Int, data: IntArray, timeoutMs: Long = 500): IntArray {
        val payload = mutableListOf(0xA0, block)
        payload.addAll(data.toList())
        return inDataExchange(1, payload.toIntArray(), timeoutMs)
    }

    suspend fun ntagWriteBlock(page: Int, data: IntArray, timeoutMs: Long = 500): IntArray {
        val payload = mutableListOf(0xA2, page)
        payload.addAll(data.toList())
        return inCommunicateThru(payload.toIntArray(), timeoutMs)
    }

    suspend fun ntagAuthPwd(pwd: IntArray, timeoutMs: Long = 500): IntArray {
        val payload = mutableListOf(0x1B)
        payload.addAll(pwd.toList())
        return inCommunicateThru(payload.toIntArray(), timeoutMs)
    }

    suspend fun getNtagVersion(timeoutMs: Long = 500): IntArray {
        return inCommunicateThru(intArrayOf(0x60), timeoutMs)
    }

    suspend fun readNtagSig(timeoutMs: Long = 500): IntArray {
        return inCommunicateThru(intArrayOf(0x3C, 0), timeoutMs)
    }

    suspend fun unlockChineseCard(): Boolean {
        val a = mutableListOf<Int>()
        val s = mutableListOf<Int>()
        return try {
            pn532SetTxRxCrc(false, false)
            initorTransceiveBits(intArrayOf(80, 0, 87, 205), 32, intArrayOf(), a, s, 5200)
            var n = initorTransceiveBits(intArrayOf(64), 7, intArrayOf(), a, s, 300)
            if (a.getOrNull(1) == 10) {
                a.clear(); s.clear()
                n = initorTransceiveBits(intArrayOf(67), 8, intArrayOf(), a, s, 300)
                return n != 0 && a.getOrNull(1) == 10
            }
            initorTransceiveBits(intArrayOf(80, 0, 87, 205), 32, intArrayOf(), a, s, 5200)
            n = initorTransceiveBits(intArrayOf(32), 7, intArrayOf(), a, s, 300)
            if (a.getOrNull(1) == 10) {
                a.clear(); s.clear()
                n = initorTransceiveBits(intArrayOf(35), 8, intArrayOf(), a, s, 300)
                return n != 0 && a.getOrNull(1) == 10
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    suspend fun unlockFuidCard(): Boolean {
        val a = mutableListOf<Int>()
        val s = mutableListOf<Int>()
        return try {
            pn532SetTxRxCrc(false, false)
            initorTransceiveBits(intArrayOf(80, 0, 87, 205), 32, intArrayOf(), a, s, 5200)
            var n = initorTransceiveBits(intArrayOf(32), 7, intArrayOf(), a, s, 300)
            if (n == 0 || a.getOrNull(1) != 10) return false
            a.clear(); s.clear()
            n = initorTransceiveBits(intArrayOf(35), 8, intArrayOf(), a, s, 300)
            n != 0 && a.getOrNull(1) == 10
        } catch (_: Exception) {
            false
        }
    }

    suspend fun mifareCheckDarkSide(): Boolean {
        return try {
            inListPassiveTarget()
            pn532SetTxRxCrc(false, false)
            val n = inCommunicateThru(intArrayOf(96, 0, 245, 123))
            if (n.getOrNull(0) != 0 || n.size < 5) return false
            val a = mutableListOf<Int>()
            val s = mutableListOf<Int>()
            pn532SetFrameTxParity(false)
            val bits = initorTransceiveBits(IntArray(8), 64, IntArray(8) { 1 }, a, s)
            bits == 4
        } catch (_: Exception) {
            false
        }
    }

    suspend fun initorTransceiveBits(
        data: IntArray,
        bitLen: Int,
        parity: IntArray,
        outData: MutableList<Int>,
        outParity: MutableList<Int>,
        timeoutMs: Long = 500
    ): Int {
        if (bitLen == 0) throw IllegalArgumentException("invalid input")
        val payload = data.toMutableList()
        val txBits = bitLen % 8
        pn532SetTxBits(txBits)
        val l = inCommunicateThru(payload.toIntArray(), timeoutMs)
        if (l.isEmpty()) return 0
        if (l[0] != 0) return 0
        outData.clear()
        outData.addAll(l.toList())
        val m = readRegister(intArrayOf(25404))
        val b = m[0] and 7
        val v = l.copyOfRange(1, l.size)
        return 8 * (v.size - if (b == 0) 0 else 1) + b
    }

    suspend fun fastCheckKeys(uid: IntArray, key: IntArray, info: IntArray, timeoutMs: Long = 500): IntArray = withCommand {
        val i = mutableListOf(162)
        i.addAll(uid.toList())
        i.addAll(key.toList())
        i.add(info.size)
        i.addAll(info.toList())
        Iso14443.appendCrcA(i)
        sendCommand(i.toIntArray())
        val p = readResponseWithTime(162, timeoutMs)
        if (p.isEmpty()) throw Pn532Exception("Frame Decode Error.")
        p.copyOfRange(1, p.size)
    }

    suspend fun mifareFastGetNonce(
        uid: IntArray,
        key: IntArray,
        keyType: Int,
        sectorNum: Int,
        crackInfo: IntArray,
        attackCnt: Int,
        mode: Int,
        timeoutMs: Long = 500
    ): IntArray = withCommand {
        val c = mutableListOf(160)
        c.addAll(uid.toList())
        c.addAll(key.toList())
        c.add(keyType)
        c.add(sectorNum)
        c.addAll(crackInfo.toList())
        c.add(attackCnt)
        c.add(mode)
        Iso14443.appendCrcA(c)
        sendCommand(c.toIntArray())
        val o = readResponseWithTime(160, timeoutMs)
        if (o.size < 2) throw Pn532Exception("Frame Decode Error.")
        o.copyOfRange(1, o.size)
    }

    suspend fun getMcuUuid(timeoutMs: Long = 500): IntArray = withCommand {
        sendCommand(intArrayOf(0xAC, 0, 0, 0, 0))
        try {
            val s = readResponseWithTime(0xAC, timeoutMs)
            if (s.size >= 2) s.copyOfRange(1, s.size) else intArrayOf()
        } catch (_: Exception) {
            intArrayOf()
        }
    }

    fun iso14443AAppendCrc(data: MutableList<Int>) = Iso14443.appendCrcA(data)

    fun byte2Number(data: IntArray, length: Int = data.size): Int = HexUtils.byte2Number(data, length)
}
