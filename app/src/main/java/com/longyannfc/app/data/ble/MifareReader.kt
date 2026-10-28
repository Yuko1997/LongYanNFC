package com.longyannfc.app.data.ble

import com.longyannfc.app.utils.HexUtils
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class MifareInfo(
    val uid: ByteArray,
    val atqa: ByteArray,
    val sak: Byte,
    val size: Int,
    val cardType: CardType,
    val dump: ByteArray = ByteArray(0),
    val isUidCard: Boolean = false,
    val isUltralight: Boolean = false,
    val manufacturerBlock: ByteArray = ByteArray(0)
) {
    enum class CardType {
        MIFARE_1K, MIFARE_4K, MIFARE_ULTRALIGHT, MTAG_MINI, NTAG213, NTAG215, NTAG216, UNKNOWN
    }

    val uidHex: String get() = HexUtils.toHexCompact(uid)
}

@Singleton
class MifareCard @Inject constructor(
    val hal: PN532
) {
    val cardInfo = CardInfo()
    var isUidCard = false
    var isMifareUltraLight = false
    var maxSectorsNum = 16
    val historyKeys = mutableListOf<IntArray>()
    val sectors: MutableMap<Int, SectorInfo> = mutableMapOf()

    init {
        resetSectors()
    }

    private fun resetSectors() {
        sectors.clear()
        for (i in 0 until 18) {
            sectors[i] = SectorInfo()
        }
    }

    fun calcUidBcc(): Int {
        return cardInfo.uid[0] xor cardInfo.uid[1] xor cardInfo.uid[2] xor cardInfo.uid[3]
    }

    fun setHistoryKeys(keys: List<IntArray>) {
        val merged = LinkedHashSet<String>()
        (keys + historyKeys).forEach { merged.add(HexUtils.toHex(it)) }
        historyKeys.clear()
        merged.forEach { historyKeys.add(HexUtils.fromHexInts(it)) }
    }

    fun addHistoryKeys(key: IntArray) {
        historyKeys.add(key.copyOf())
    }

    fun setMaxSectors(n: Int) {
        maxSectorsNum = n
    }

    fun isUltraLightEv1Card(): Boolean {
        val atqa = (cardInfo.atqa[0] shl 8) or cardInfo.atqa[1]
        return cardInfo.sak == 0 && atqa == 68
    }

    fun setMifareInfo(target: PassiveTarget) {
        var t = if (target.uidLen == 7) 3 else 0
        for (r in 0 until 4) {
            cardInfo.uid[r] = target.uid.getOrElse(t) { 0 }
            t++
        }
        cardInfo.sak = target.sak
        cardInfo.atqa = target.atqa.copyOf()
        val n = (cardInfo.atqa[0] shl 8) or cardInfo.atqa[1]
        when (target.sak) {
            8, 24 -> cardInfo.size = 1024
            0 -> if (n == 68) {
                cardInfo.size = 64
                isMifareUltraLight = true
            }
            else -> cardInfo.size = 1024
        }
        maxSectorsNum = if (cardInfo.size >= 4096) 40 else 16
    }

    fun toMifareInfo(dump: ByteArray = ByteArray(0), manufacturerBlock: ByteArray = ByteArray(0)): MifareInfo {
        val type = when {
            isMifareUltraLight || isUltraLightEv1Card() -> MifareInfo.CardType.NTAG215
            cardInfo.size == 4096 -> MifareInfo.CardType.MIFARE_4K
            else -> MifareInfo.CardType.MIFARE_1K
        }
        return MifareInfo(
            uid = HexUtils.intsToBytes(cardInfo.uid),
            atqa = HexUtils.intsToBytes(cardInfo.atqa),
            sak = cardInfo.sak.toByte(),
            size = cardInfo.size,
            cardType = type,
            dump = dump,
            isUidCard = isUidCard,
            isUltralight = isMifareUltraLight || isUltraLightEv1Card(),
            manufacturerBlock = manufacturerBlock
        )
    }

    fun getSectorInfo(sector: Int): SectorInfo = sectors.getOrPut(sector) { SectorInfo() }

    fun updateSectorKeyA(sector: Int, key: IntArray) {
        getSectorInfo(sector).updateKeyA(key)
    }

    fun updateSectorKeyB(sector: Int, key: IntArray) {
        getSectorInfo(sector).updateKeyB(key)
    }

    fun addSectorKey(sector: Int, keyA: IntArray?, keyB: IntArray?, knownA: Boolean, knownB: Boolean) {
        sectors[sector] = SectorInfo(keyA, keyB, knownA, knownB)
    }

    fun updateSectorKeys(keys: List<IntArray?>, swapped: Boolean, replace: Boolean = true) {
        val n = minOf(maxSectorsNum, keys.size / 2)
        for (a in 0 until 2 * n step 2) {
            val first = keys[if (swapped) a else a + 1]
            val second = keys[if (swapped) a + 1 else a]
            val knownFirst = first != null
            val knownSecond = second != null
            val sector = a / 2
            if (replace) {
                addSectorKey(sector, second, first, knownSecond, knownFirst)
            } else {
                if (second != null) updateSectorKeyA(sector, second)
                if (first != null) updateSectorKeyB(sector, first)
            }
        }
    }

    fun getFirstKnownSectorInfo(): KnownSector? {
        for (e in 0 until maxSectorsNum) {
            val t = sectors[e] ?: continue
            if (t.isKnownA) return KnownSector(e, t.keyA ?: return null, 0x60)
            if (t.isKnownB) return KnownSector(e, t.keyB ?: return null, 0x61)
        }
        return null
    }

    fun checkNtagType(e: IntArray): NtagType {
        var t = "Unknown"
        var r = 0
        if (e.size >= 8) {
            val n = e[1]
            val a = e[2]
            val s = e[3]
            val c = e[6]
            if (n == 4) {
                if (a == 3 && (s == 1 || s == 2)) {
                    if (c == 11) {
                        t = "MF0UL11"; r = 20
                    } else if (c == 14) {
                        t = "MF0UL21"; r = 40
                    }
                } else if (a == 4) {
                    when (c) {
                        11 -> { t = "NTAG210"; r = 20 }
                        14 -> { t = "NTAG212"; r = 40 }
                        15 -> { t = "NTAG213"; r = 45 }
                        17 -> { t = "NTAG215"; r = 135 }
                        19 -> { t = "NTAG216"; r = 231 }
                    }
                }
            }
        }
        return NtagType(t, r)
    }

    fun fillBlockWithDefaultData(block: Int, sector: SectorInfo): IntArray {
        val r = IntArray(16)
        if (block == 0) {
            cardInfo.uid.copyInto(r, 0, 0, 4)
            r[4] = calcUidBcc()
            r[5] = cardInfo.sak
            r[6] = cardInfo.atqa.getOrElse(1) { 0 }
            r[7] = cardInfo.atqa.getOrElse(0) { 0 }
            intArrayOf(98, 99, 100, 101, 102, 103, 104, 105).copyInto(r, 8)
            return r
        }
        if (block % 4 == 3) {
            val keyA = sector.keyA ?: IntArray(6) { 0xFF }
            val keyB = sector.keyB ?: IntArray(6) { 0xFF }
            val access = intArrayOf(255, 7, 128, 105)
            keyA.copyInto(r, 0)
            access.copyInto(r, 6)
            keyB.copyInto(r, 10)
            return r
        }
        return r
    }

    suspend fun initHalChip() {
        hal.wakeup()
        hal.samConfiguration()
        hal.pn532SetTxRxCrc(true, true)
        hal.pn532SetFrameTxParity(true)
    }

    suspend fun pollNfcCard(): PassiveTarget = hal.inListPassiveTarget(1, 0, intArrayOf())

    suspend fun deselectCard() {
        runCatching { hal.inDeSelect() }
    }

    suspend fun resetCard() {
        hal.rfConfiguration(1, 0)
        hal.rfConfiguration(1, 1)
    }

    suspend fun isChineseCard(): Boolean {
        val n = try {
            hal.prepareForTransBits()
            hal.unlockChineseCard()
        } finally {
            runCatching { hal.pn532SetTxRxCrc(true, true) }
        }
        return n
    }

    suspend fun isFuidCard(): Boolean {
        val n = try {
            hal.prepareForTransBits()
            hal.unlockFuidCard()
        } finally {
            runCatching { hal.pn532SetTxRxCrc(true, true) }
        }
        return n
    }

    suspend fun checkDarkside(): Boolean {
        val n = try {
            hal.mifareCheckDarkSide()
        } finally {
            runCatching { hal.pn532SetTxRxCrc(true, true) }
            runCatching { hal.pn532SetFrameTxParity(true) }
        }
        return n
    }

    suspend fun writeNtagMemory(data: ByteArray, pwd: IntArray = intArrayOf(0, 0, 0, 0)): WriteNtagResult {
        val s = data.map { it.toInt() and 0xFF }.toMutableList()
        val version = hal.getNtagVersion().let { if (it.size > 1) it.copyOfRange(1, it.size) else it }
        val i = checkNtagType(version)
        if (s.size != 4 * i.blocks) throw IllegalStateException("待写入数据长度与卡类型不符合.")
        val auth = hal.ntagAuthPwd(pwd)
        if (auth.getOrNull(1) != 0 || auth.getOrNull(2) != 0) {
            return WriteNtagResult(false, "auth fail", emptyList())
        }
        val fail = mutableListOf<Int>()
        for (h in 0 until i.blocks) {
            val k = s.subList(4 * h, 4 * h + 4).toIntArray()
            val resp = hal.ntagWriteBlock(h, k)
            if (resp.getOrNull(0) != 2) fail.add(h)
        }
        return WriteNtagResult(true, "写卡完成", fail)
    }

    /**
     * Quick read: only read block 0 (manufacturer block) without full dump.
     * For NTAG/Ultralight: read first 4 pages (16 bytes) - no auth needed.
     * For Mifare Classic: authenticate with default key FFFFFFFFFFFF, then read block 0.
     * Always returns 16 bytes (real data or constructed from anti-collision info).
     */
    suspend fun quickReadManufacturerBlock(): ByteArray {
        return try {
            if (isMifareUltraLight || isUltraLightEv1Card()) {
                // NTAG/Ultralight: first pages are freely readable
                val pageData = mutableListOf<Int>()
                for (page in 0 until 4) {
                    val resp = hal.mifareReadBlock(page)
                    if (resp.size >= 5 && resp[0] == 0x00) {
                        pageData.addAll(resp.slice(1 until 5))
                    } else {
                        pageData.addAll(listOf(0, 0, 0, 0))
                    }
                }
                val bytes = HexUtils.intsToBytes(pageData.toIntArray())
                if (bytes.size >= 16) bytes.copyOf(16) else fillBlock0FromAntiCollision()
            } else {
                // Mifare Classic: try default key FFFFFFFFFFFF on sector 0
                val defaultKey = DEFAULT_KEY
                val authResp = hal.mifareAuthBlock(cardInfo.uid, defaultKey, 0x60, 0)
                if (authResp.getOrNull(0) == 0) {
                    val readResp = hal.mifareReadBlock(0)
                    if (readResp.size >= 17 && readResp[0] == 0x00) {
                        HexUtils.intsToBytes(readResp.sliceArray(1 until 17))
                    } else {
                        runCatching { hal.inDeSelect() }
                        fillBlock0FromAntiCollision()
                    }
                } else {
                    runCatching { hal.inDeSelect() }
                    fillBlock0FromAntiCollision()
                }
            }
        } catch (_: Exception) {
            runCatching { hal.inDeSelect() }
            fillBlock0FromAntiCollision()
        }
    }

    /**
     * Construct block 0 from anti-collision data (UID, BCC, SAK, ATQA).
     * Used as fallback when actual block 0 read fails.
     */
    private fun fillBlock0FromAntiCollision(): ByteArray {
        val r = ByteArray(16)
        for (i in 0 until minOf(4, cardInfo.uid.size)) r[i] = cardInfo.uid[i].toByte()
        r[4] = (cardInfo.uid[0] xor cardInfo.uid[1] xor cardInfo.uid[2] xor cardInfo.uid[3]).toByte()
        r[5] = cardInfo.sak.toByte()
        r[6] = cardInfo.atqa.getOrElse(1) { 0 }.toByte()
        r[7] = cardInfo.atqa.getOrElse(0) { 0 }.toByte()
        // Bytes 8-15: manufacturer specific (0x04 = NXP for 4-byte UID)
        if (cardInfo.uid.size >= 4 && cardInfo.uid[0] != 0) r[8] = 0x04
        return r
    }

    /**
     * Quick read of block 0 (manufacturer block).
     * No authentication needed - block 0 is always readable on Mifare Classic.
     * For NTAG/Ultralight, first pages are also freely readable.
     * Returns 16 bytes of block 0, or empty array on failure.
     */
    suspend fun readManufacturerBlock(): ByteArray {
        return try {
            val resp = hal.mifareReadBlock(0)
            if (resp.getOrNull(0) == 0 && resp.size >= 17) {
                HexUtils.intsToBytes(resp.sliceArray(1 until 17))
            } else {
                ByteArray(0)
            }
        } catch (_: Exception) {
            ByteArray(0)
        }
    }

    suspend fun readNtagMemory(): ReadDumpResult {
        val data = mutableListOf<Int>()
        val fail = mutableListOf<Int>()
        var consecutiveFailures = 0
        val version = hal.getNtagVersion().let { if (it.size > 1) it.copyOfRange(1, it.size) else it }
        val o = checkNtagType(version)
        for (i in 0 until o.blocks) {
            val u = hal.mifareReadBlock(i)
            if (u.size >= 17) {
                consecutiveFailures = 0
                data.addAll(u.slice(1 until 5))
            } else {
                consecutiveFailures++
                data.addAll(listOf(0, 0, 0, 0))
                fail.add(i)
                if (consecutiveFailures >= 4) throw IllegalStateException("读卡中断，请保持卡片贴近天线")
            }
        }
        return ReadDumpResult(data.toIntArray(), fail)
    }

    suspend fun readAllBlocks(onProgress: ((String, Int) -> Unit)? = null): ReadDumpResult {
        val s = mutableListOf<Int>()
        val u = mutableListOf<Int>()
        var consecutiveFailures = 0
        var o = 30.0
        val i = 70.0 / (4 * maxSectorsNum)
        for (k in 0 until maxSectorsNum) {
            val l = getSectorInfo(k)
            var p = false
            var usedKeyA = true
            if (!isUidCard) {
                var y = try {
                    hal.mifareAuthBlock(cardInfo.uid, l.keyA ?: DEFAULT_KEY, 1, 4 * k)
                } catch (_: Exception) {
                    intArrayOf(1)
                }
                if (y.getOrNull(0) != 0) {
                    runCatching { hal.inDeSelect() }
                    y = try {
                        hal.mifareAuthBlock(cardInfo.uid, l.keyB ?: DEFAULT_KEY, 0, 4 * k)
                    } catch (_: Exception) {
                        intArrayOf(1)
                    }
                    if (y.getOrNull(0) == 0) {
                        usedKeyA = false
                        p = true
                    } else {
                        for (d in 0 until 4) {
                            u.add(4 * k + d)
                            s.addAll(fillBlockWithDefaultData(d + 4 * k, l).toList())
                        }
                        runCatching { hal.inDeSelect() }
                    }
                } else {
                    p = true
                }
                if (!p) continue
            }
            for (b in 0 until 4) {
                onProgress?.invoke("读取扇区中 Block$b/Sector$k", o.toInt())
                o += i
                var v = try {
                    hal.mifareReadBlock(b + 4 * k)
                } catch (_: Exception) {
                    intArrayOf(1)
                }
                if (v.getOrNull(0) == 0 && v.size >= 17) {
                    consecutiveFailures = 0
                    if (b == 3 && !isUidCard) {
                        val keyA = l.keyA ?: DEFAULT_KEY
                        val keyB = l.keyB ?: DEFAULT_KEY
                        keyA.copyInto(v, 1)
                        keyB.copyInto(v, 11)
                    }
                    s.addAll(v.slice(1 until 17))
                } else {
                    runCatching { hal.inDeSelect() }
                    if (usedKeyA && !isUidCard) {
                        val authB = try {
                            hal.mifareAuthBlock(cardInfo.uid, l.keyB ?: DEFAULT_KEY, 0, 4 * k)
                        } catch (_: Exception) {
                            intArrayOf(1)
                        }
                        if (authB.getOrNull(0) == 0) {
                            v = try {
                                hal.mifareReadBlock(b + 4 * k)
                            } catch (_: Exception) {
                                intArrayOf(1)
                            }
                            if (v.getOrNull(0) == 0 && v.size >= 17) {
                                consecutiveFailures = 0
                                if (b == 3) {
                                    val keyA = l.keyA ?: DEFAULT_KEY
                                    val keyB = l.keyB ?: DEFAULT_KEY
                                    keyA.copyInto(v, 1)
                                    keyB.copyInto(v, 11)
                                }
                                s.addAll(v.slice(1 until 17))
                            } else {
                                consecutiveFailures++
                                s.addAll(fillBlockWithDefaultData(b + 4 * k, l).toList())
                                u.add(b + 4 * k)
                            }
                        } else {
                            consecutiveFailures++
                            s.addAll(fillBlockWithDefaultData(b + 4 * k, l).toList())
                            u.add(b + 4 * k)
                        }
                    } else {
                        consecutiveFailures++
                        s.addAll(fillBlockWithDefaultData(b + 4 * k, l).toList())
                        u.add(b + 4 * k)
                    }
                    if (consecutiveFailures >= 8) throw IllegalStateException("读卡中断，请保持卡片贴近天线")
                }
            }
        }
        return ReadDumpResult(s.toIntArray(), u)
    }

    suspend fun scanDefaultKeys(onProgress: ((String, Int) -> Unit)? = null) {
        val found = Array<IntArray?>(2 * maxSectorsNum) { null }
        // 预填充已有密钥，跳过已知扇区
        for (l in 0 until maxSectorsNum) {
            val info = getSectorInfo(l)
            if (info.isKnownA && info.keyA != null) found[2 * l] = info.keyA
            if (info.isKnownB && info.keyB != null) found[2 * l + 1] = info.keyB
        }
        val keys = DEFAULT_KEYS + historyKeys
        val step = 30.0 / keys.size / maxSectorsNum
        var h = 0.0
        for (key in keys) {
            // 每个密钥独立计数失败次数，避免跨密钥累积导致误中断
            var consecutiveFailures = 0
            // 检查是否所有扇区都已找到密钥，提前终止
            val allFound = (0 until maxSectorsNum).all { l ->
                found[2 * l] != null || found[2 * l + 1] != null
            }
            if (allFound) break
            for (l in 0 until maxSectorsNum) {
                onProgress?.invoke(
                    "正在扫描 $l/${maxSectorsNum - 1}扇区 秘钥 ${HexUtils.toHex(key, "")}",
                    h.toInt().coerceAtMost(30)
                )
                for (v in 1 downTo 0) {
                    val idx = 2 * l + v
                    if (found[idx] != null) continue
                    val m = 4 * l + 3
                    try {
                        val b = hal.mifareAuthBlock(cardInfo.uid, key, v, m)
                        if (b.getOrNull(0) == 0) {
                            consecutiveFailures = 0
                            found[idx] = key
                            if (v == 1) {
                                val read = hal.mifareReadBlock(m)
                                if (read.size >= 17) {
                                    val g = read.copyOfRange(11, 17)
                                    val authB = hal.mifareAuthBlock(cardInfo.uid, g, 0, m)
                                    if (authB.getOrNull(0) == 0) {
                                        found[2 * l] = g
                                        break
                                    } else {
                                        runCatching { hal.inDeSelect() }
                                    }
                                } else {
                                    runCatching { hal.inDeSelect() }
                                }
                            }
                        } else {
                            consecutiveFailures++
                            runCatching { hal.inDeSelect() }
                        }
                    } catch (e: Exception) {
                        consecutiveFailures++
                        Timber.w(e, "key scan failed")
                        runCatching { hal.inDeSelect() }
                    }
                    if (consecutiveFailures >= 24) throw IllegalStateException("读卡中断，请保持卡片贴近天线")
                }
                h += step
            }
        }
        updateSectorKeys(found.toList(), swapped = true, replace = false)
    }

    /**
     * 写入全卡数据，参照小程序 writeAllBlocks 逻辑。
     * - 跳过 Block 0（UID 块），除非是 UID 可写卡
     * - Sector Trailer 块仅写入密钥部分，保留 Access Bits 不变
     * - 返回 WriteDumpResult 记录写入结果
     */
    suspend fun writeAllBlocks(data: ByteArray, onProgress: ((String, Int) -> Unit)? = null): WriteDumpResult {
        val c = data.map { it.toInt() and 0xFF }
        require(c.size >= 16 * 4 * maxSectorsNum) { "invalid input data: ${c.size} < ${16 * 4 * maxSectorsNum}" }
        val failBlocks = mutableListOf<Int>()
        val skippedBlocks = mutableListOf<Int>()
        val authFailedSectors = mutableListOf<Int>()
        var writtenBlocks = 0

        for (f in 0 until maxSectorsNum) {
            val sector = getSectorInfo(f)
            var authenticated = false
            var usedKeyA = true

            if (isUidCard) {
                authenticated = true
            } else {
                // 尝试 KeyA 认证
                val authA = try {
                    hal.mifareAuthBlock(cardInfo.uid, sector.keyA ?: DEFAULT_KEY, 0x60, 4 * f)
                } catch (_: Exception) { intArrayOf(1) }
                if (authA.getOrNull(0) == 0) {
                    authenticated = true
                    usedKeyA = true
                } else {
                    runCatching { hal.inDeSelect() }
                    // 尝试 KeyB 认证
                    val authB = try {
                        hal.mifareAuthBlock(cardInfo.uid, sector.keyB ?: DEFAULT_KEY, 0x61, 4 * f)
                    } catch (_: Exception) { intArrayOf(1) }
                    if (authB.getOrNull(0) == 0) {
                        authenticated = true
                        usedKeyA = false
                    }
                }
            }

            if (!authenticated) {
                authFailedSectors.add(f)
                for (d in 0 until 4) skippedBlocks.add(4 * f + d)
                onProgress?.invoke("扇区$f 认证失败，跳过", ((f * 4) * 100) / (maxSectorsNum * 4))
                continue
            }

            for (d in 0 until 4) {
                val blockNum = 4 * f + d
                // Block 0：UID 块，非 UID 可写卡跳过
                if (f == 0 && d == 0 && !isUidCard) {
                    skippedBlocks.add(blockNum)
                    continue
                }

                val progress = (blockNum * 100) / (maxSectorsNum * 4)
                onProgress?.invoke("写入 Block$d/Sector$f", progress)

                val blockData = c.subList(16 * blockNum, 16 * blockNum + 16).toIntArray()

                // Sector Trailer（每扇区第4块）：仅写入密钥，保留 Access Bits
                if (d == 3 && !isUidCard) {
                    val existingTrailer = sector.blocks[3]?.data
                    if (existingTrailer != null) {
                        // 保留原 Access Bits（字节6-9），只替换 KeyA(0-5) 和 KeyB(10-15)
                        blockData[6] = existingTrailer[6]
                        blockData[7] = existingTrailer[7]
                        blockData[8] = existingTrailer[8]
                        blockData[9] = existingTrailer[9]
                    }
                }

                try {
                    val resp = hal.mifareWriteBlock(blockNum, blockData)
                    if (resp.getOrNull(0) == 0) {
                        writtenBlocks++
                    } else {
                        failBlocks.add(blockNum)
                    }
                } catch (e: Exception) {
                    Timber.w(e, "write block$blockNum failed")
                    failBlocks.add(blockNum)
                    runCatching { hal.inDeSelect() }
                    // 重新认证以便继续写后续块
                    if (f < maxSectorsNum - 1) {
                        val reAuth = try {
                            if (usedKeyA) hal.mifareAuthBlock(cardInfo.uid, sector.keyA ?: DEFAULT_KEY, 0x60, 4 * f)
                            else hal.mifareAuthBlock(cardInfo.uid, sector.keyB ?: DEFAULT_KEY, 0x61, 4 * f)
                        } catch (_: Exception) { intArrayOf(1) }
                        if (reAuth.getOrNull(0) != 0) break // 认证失败，跳出当前扇区
                    }
                }
            }
        }

        runCatching { hal.inRelease() }

        val success = failBlocks.isEmpty() && authFailedSectors.isEmpty()
        val message = when {
            success -> "写卡完成，共写入 $writtenBlocks 块"
            authFailedSectors.isNotEmpty() -> "写卡完成，${authFailedSectors.size}个扇区认证失败"
            else -> "写卡完成，${failBlocks.size}个块写入失败"
        }
        return WriteDumpResult(success, writtenBlocks, failBlocks, skippedBlocks, authFailedSectors, message)
    }

    companion object {
        val DEFAULT_KEY = intArrayOf(0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF)
        val DEFAULT_KEYS = listOf(
            intArrayOf(0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF),
            intArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            intArrayOf(0xA0, 0xA1, 0xA2, 0xA3, 0xA4, 0xA5),
            intArrayOf(0xB0, 0xB1, 0xB2, 0xB3, 0xB4, 0xB5),
            intArrayOf(0xD3, 0xF7, 0xD3, 0xF7, 0xD3, 0xF7),
            intArrayOf(0xAA, 0xBB, 0xCC, 0xDD, 0xEE, 0xFF),
            intArrayOf(0x1A, 0x2B, 0x3C, 0x4D, 0x5E, 0x6F),
            intArrayOf(0x12, 0x34, 0x56, 0x78, 0x9A, 0xBC),
            intArrayOf(0xAB, 0xCD, 0xEF, 0x12, 0x34, 0x56),
            intArrayOf(0x4D, 0x3A, 0x99, 0xC3, 0x5D, 0xD8),
            intArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06),
            intArrayOf(0xA0, 0xA0, 0xA0, 0xA0, 0xA0, 0xA0)
        )
    }
}

typealias MifareReader = MifareCard
