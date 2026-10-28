package com.longyannfc.app.data.ble

import com.longyannfc.app.utils.ChameleonCommand
import com.longyannfc.app.utils.HexUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class BlockInfo(
    var data: IntArray = IntArray(16)
) {
    fun toByteArray(): ByteArray = HexUtils.intsToBytes(data)
}

class SectorInfo(
    var keyA: IntArray? = null,
    var keyB: IntArray? = null,
    var isKnownA: Boolean = false,
    var isKnownB: Boolean = false
) {
    val blocks: MutableMap<Int, BlockInfo> = mutableMapOf(
        0 to BlockInfo(),
        1 to BlockInfo(),
        2 to BlockInfo(),
        3 to BlockInfo()
    )

    fun updateKeyA(key: IntArray) {
        keyA = key.copyOf()
        isKnownA = true
    }

    fun updateKeyB(key: IntArray) {
        keyB = key.copyOf()
        isKnownB = true
    }

    fun writeBlock(index: Int, data: IntArray) {
        blocks[index] = BlockInfo(data.copyOf(16))
        if (index == 3 && data.size >= 16) {
            keyA = data.copyOfRange(0, 6)
            keyB = data.copyOfRange(10, 16)
            isKnownA = true
            isKnownB = true
        }
    }

    fun readBlock(index: Int): IntArray? = blocks[index]?.data
}

data class CardInfo(
    var uid: IntArray = IntArray(4),
    var atqa: IntArray = IntArray(2),
    var sak: Int = 0,
    var size: Int = 1024
)

data class NtagType(
    val type: String,
    val blocks: Int
)

data class KnownSector(
    val sectorNumber: Int,
    val keyArray: IntArray,
    val keyType: Int
)

data class ReadDumpResult(
    val data: IntArray,
    val failIndex: List<Int>
)

data class WriteNtagResult(
    val authResult: Boolean,
    val message: String,
    val failIndex: List<Int>
)

data class WriteDumpResult(
    val success: Boolean,
    val writtenBlocks: Int,
    val failBlocks: List<Int>,
    val skippedBlocks: List<Int>,
    val authFailedSectors: List<Int>,
    val message: String
)

data class ChameleonResponse(
    val success: Boolean,
    val response: String
)

@Singleton
class Chameleon @Inject constructor(
    private val bleManager: BleManager
) {
    private val rxBuffer = mutableListOf<String>()
    private var frameRecvFinish = false
    private var isInDetectionCmd = false
    private var detectionDeferred: CompletableDeferred<ByteArray>? = null
    private val detectionBuffer = mutableListOf<Byte>()
    private val detectionFrameBuffer = mutableListOf<Byte>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            bleManager.incoming.collect { data ->
                runCatching { handleFrame(data) }
                    .onFailure {
                        Timber.w(it, "Ignored malformed Chameleon BLE frame")
                        clearRxBuffer()
                    }
            }
        }
    }

    fun handleFrame(data: ByteArray) {
        if (isInDetectionCmd) {
            handleDetectionFrame(data)
            return
        }
        val text = data.toString(Charsets.US_ASCII)
        if (HEADER.matches(text)) {
            rxBuffer.add(text)
            frameRecvFinish = true
        }
    }

    fun clearRxBuffer() {
        rxBuffer.clear()
        frameRecvFinish = false
    }

    private suspend fun sendCmd(cmd: String) {
        val payload = (ChameleonCommand.sanitize(cmd) + "\r\n").toByteArray()
        clearRxBuffer()
        bleManager.writeRaw(payload)
    }

    private suspend fun readResponse(timeoutMs: Long): String {
        val start = System.currentTimeMillis()
        while (true) {
            if (frameRecvFinish && rxBuffer.isNotEmpty()) return rxBuffer.first()
            if (System.currentTimeMillis() - start >= timeoutMs) {
                clearRxBuffer()
                throw IllegalStateException("Chameleon Resp Timeout exceeded")
            }
            delay(1)
        }
    }

    suspend fun sendNormalCmd(cmd: String, timeoutMs: Long = 2000): Boolean {
        if (cmd.isBlank()) return false
        return try {
            sendCmd(cmd)
            val a = readResponse(timeoutMs)
            a.contains("100:OK")
        } catch (e: Exception) {
            Timber.w(e, "chameleon cmd failed: $cmd")
            false
        }
    }

    suspend fun sendCmdWithResponse(cmd: String, timeoutMs: Long = 2000): ChameleonResponse {
        if (cmd.isBlank()) return ChameleonResponse(false, "")
        return try {
            sendCmd(cmd)
            var a = readResponse(timeoutMs)
            val success = a.contains("100:OK") || a.contains("101:OK WITH TEXT")
            a = a.replace("101:OK WITH TEXT", "").replace("100:OK", "")
            ChameleonResponse(success, a)
        } catch (_: Exception) {
            ChameleonResponse(false, "")
        }
    }

    suspend fun getVersion(timeoutMs: Long = 2000): ChameleonResponse =
        sendCmdWithResponse("VERSION?", timeoutMs)

    suspend fun setConfig(config: String): Boolean {
        val normalized = config.trim()
        if (!ChameleonCommand.isValidConfig(normalized)) {
            Timber.w("拒绝非法的 CONFIG 值")
            return false
        }
        return sendNormalCmd("CONFIG=$normalized")
    }

    suspend fun setUid(uidHex: String): Boolean {
        val normalized = uidHex.trim()
        if (!ChameleonCommand.isValidUid(normalized)) {
            Timber.w("拒绝非法的 UID 值")
            return false
        }
        return sendNormalCmd("UID=$normalized")
    }

    suspend fun setSetting(slot: Int): Boolean = sendNormalCmd("SETTING=$slot")

    suspend fun switchToPcReaderMode(): Boolean = setSetting(9)

    suspend fun downloadPcrDetection(timeoutMs: Long = 20_000): ByteArray {
        if (!bleManager.isConnected()) throw IllegalStateException("请先连接侦测设备")
        detectionBuffer.clear()
        detectionFrameBuffer.clear()
        isInDetectionCmd = true
        val deferred = CompletableDeferred<ByteArray>()
        detectionDeferred = deferred
        bleManager.writeRaw("PCRDETECTION?\r\n".toByteArray())
        val start = System.currentTimeMillis()
        while (!deferred.isCompleted) {
            if (System.currentTimeMillis() - start >= timeoutMs) {
                cancelDetection()
                throw IllegalStateException("侦测数据下载超时")
            }
            delay(20)
        }
        return deferred.await()
    }

    suspend fun clearPcrDetection(state: Int = 0): Boolean = sendNormalCmd("PCRDETECTION=$state")

    private fun cancelDetection() {
        detectionDeferred?.cancel()
        detectionDeferred = null
        detectionBuffer.clear()
        detectionFrameBuffer.clear()
        isInDetectionCmd = false
    }

    private fun handleDetectionFrame(data: ByteArray) {
        if (data.isEmpty()) return
        val first = data[0].toInt() and 0xFF
        if (first != XMODEM_SOH && first != XMODEM_EOT && first != XMODEM_CAN) {
            val text = data.toString(Charsets.US_ASCII)
            if (text.startsWith("110") || text.contains("110:")) {
                scope.launch { bleManager.writeRaw(byteArrayOf(XMODEM_NAK.toByte())) }
            }
            return
        }
        detectionFrameBuffer.addAll(data.toList())
        parseDetectionXmodem()
    }

    private fun parseDetectionXmodem() {
        while (detectionFrameBuffer.isNotEmpty()) {
            when (detectionFrameBuffer.first().toInt() and 0xFF) {
                XMODEM_EOT -> {
                    detectionFrameBuffer.removeAt(0)
                    val result = detectionBuffer.toByteArray().trimTrailingPadding()
                    detectionDeferred?.complete(result)
                    detectionDeferred = null
                    isInDetectionCmd = false
                    scope.launch { bleManager.writeRaw(byteArrayOf(XMODEM_ACK.toByte())) }
                    return
                }
                XMODEM_CAN -> {
                    detectionDeferred?.completeExceptionally(IllegalStateException("侦测设备取消传输"))
                    detectionDeferred = null
                    isInDetectionCmd = false
                    detectionFrameBuffer.clear()
                    detectionBuffer.clear()
                    return
                }
                XMODEM_SOH -> {
                    if (detectionFrameBuffer.size < XMODEM_PACKET_SIZE) return
                    val packet = detectionFrameBuffer.take(XMODEM_PACKET_SIZE).map { it.toInt() and 0xFF }
                    repeat(XMODEM_PACKET_SIZE) { detectionFrameBuffer.removeAt(0) }
                    val checksum = packet.take(XMODEM_PACKET_SIZE - 1).sum() and 0xFF
                    if (checksum != packet.last()) {
                        scope.launch { bleManager.writeRaw(byteArrayOf(XMODEM_CAN.toByte())) }
                        detectionDeferred?.completeExceptionally(IllegalStateException("侦测数据校验失败"))
                        detectionDeferred = null
                        isInDetectionCmd = false
                        detectionBuffer.clear()
                        return
                    }
                    for (i in 3 until 131) detectionBuffer.add(packet[i].toByte())
                    scope.launch { bleManager.writeRaw(byteArrayOf(XMODEM_ACK.toByte())) }
                }
                else -> detectionFrameBuffer.removeAt(0)
            }
        }
    }

    private fun ByteArray.trimTrailingPadding(): ByteArray {
        var end = size
        while (end > 0 && (this[end - 1].toInt() and 0xFF) in setOf(0x00, 0x1A, 0xFF)) end--
        return copyOf(end)
    }

    companion object {
        private val HEADER = Regex("^(1[0-9]{2}|200|201|202|203):")
        private const val XMODEM_SOH = 0x01
        private const val XMODEM_EOT = 0x04
        private const val XMODEM_ACK = 0x06
        private const val XMODEM_NAK = 0x15
        private const val XMODEM_CAN = 0x18
        private const val XMODEM_PACKET_SIZE = 132
    }
}
