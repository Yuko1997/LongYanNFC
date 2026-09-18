package com.longyannfc.app.data.repository

import com.google.gson.JsonObject
import com.longyannfc.app.data.ble.BleManager
import com.longyannfc.app.data.ble.Chameleon
import com.longyannfc.app.data.ble.MifareCard
import com.longyannfc.app.data.ble.MifareInfo
import com.longyannfc.app.data.ble.ReadDumpResult
import com.longyannfc.app.data.ble.WriteNtagResult
import com.longyannfc.app.data.local.AmiiboDao
import com.longyannfc.app.data.local.CardDumpDao
import com.longyannfc.app.data.local.CardDumpEntity
import com.longyannfc.app.data.local.KeyCacheEntity
import com.longyannfc.app.data.local.KeyDao
import com.longyannfc.app.data.local.LogDao
import com.longyannfc.app.data.local.OperationLogEntity
import com.longyannfc.app.data.local.UserSession
import com.longyannfc.app.data.remote.AmiiboInfo
import com.longyannfc.app.data.remote.LoginResponse
import com.longyannfc.app.utils.AmiiboUtils
import com.longyannfc.app.utils.Crypto1KeyRecovery
import com.longyannfc.app.utils.DetectionDataParser
import com.longyannfc.app.utils.HexUtils
import com.longyannfc.app.utils.KeyCrackingEngine
import com.longyannfc.app.utils.LocalDetectionAnalyzer
import com.longyannfc.app.utils.NestedAttackEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CardRepository @Inject constructor(
    private val bleManager: BleManager,
    private val chameleon: Chameleon,
    private val mifareCard: MifareCard,
    private val session: UserSession,
    private val amiiboDao: AmiiboDao,
    private val cardDumpDao: CardDumpDao,
    private val keyDao: KeyDao,
    private val logDao: LogDao,
    private val localDetectionAnalyzer: LocalDetectionAnalyzer,
    private val nestedAttackEngine: NestedAttackEngine
) {
    suspend fun detectCard(): MifareInfo {
        ensureReaderConnected()
        mifareCard.initHalChip()
        val target = mifareCard.pollNfcCard()
        mifareCard.setMifareInfo(target)
        log("detect", "UID=${HexUtils.toHex(mifareCard.cardInfo.uid, "")}")
        val manufacturerBlock = mifareCard.quickReadManufacturerBlock()
        return mifareCard.toMifareInfo(manufacturerBlock = manufacturerBlock)
    }

    suspend fun readCard(onProgress: ((String, Int) -> Unit)? = null): Pair<MifareInfo, ReadDumpResult> {
        ensureReaderConnected()
        if (mifareCard.isUltraLightEv1Card() || mifareCard.isMifareUltraLight) {
            val dump = mifareCard.readNtagMemory()
            return mifareCard.toMifareInfo(HexUtils.intsToBytes(dump.data)) to dump
        }
        if (mifareCard.isChineseCard()) {
            mifareCard.isUidCard = true
        } else {
            // 一卡一密：先尝试从缓存加载历史密钥
            val uid = HexUtils.toHex(mifareCard.cardInfo.uid, "")
            val cachedKeys = keyDao.getByUid(uid)
            val hasCachedKeys = cachedKeys.any { it.keyA != null || it.keyB != null }
            if (hasCachedKeys) {
                onProgress?.invoke("正在加载历史密钥", 5)
                loadCachedKeys(cachedKeys)
                val knownCount = (0 until mifareCard.maxSectorsNum).count {
                    val s = mifareCard.getSectorInfo(it)
                    s.isKnownA || s.isKnownB
                }
                onProgress?.invoke("已加载 $knownCount 个扇区密钥", 10)
            }
            // 扫描默认密钥（已知扇区会自动跳过）
            mifareCard.scanDefaultKeys(onProgress)
        }
        val dump = mifareCard.readAllBlocks(onProgress)
        cacheKeys(mifareCard)
        log("read", "blocks=${dump.data.size / 16} fail=${dump.failIndex.size}")
        return mifareCard.toMifareInfo(HexUtils.intsToBytes(dump.data)) to dump
    }

    /**
     * 从缓存加载历史密钥到 MifareCard
     */
    private fun loadCachedKeys(cachedKeys: List<KeyCacheEntity>) {
        for (cache in cachedKeys) {
            val sector = cache.sector
            if (cache.keyA != null) {
                mifareCard.updateSectorKeyA(sector, HexUtils.fromHexInts(cache.keyA!!))
            }
            if (cache.keyB != null) {
                mifareCard.updateSectorKeyB(sector, HexUtils.fromHexInts(cache.keyB!!))
            }
        }
    }

    suspend fun readAndSaveCard(onProgress: ((String, Int) -> Unit)? = null): Long {
        val (info, dump) = readCard(onProgress)
        val uid = info.uidHex.ifBlank { "UNKNOWN" }
        val data = HexUtils.intsToBytes(dump.data)
        val id = cardDumpDao.insert(
            CardDumpEntity(
                uid = uid,
                cardType = info.cardType.name,
                size = data.size,
                data = data,
                failBlocks = dump.failIndex.joinToString(","),
                name = "${uid}_${System.currentTimeMillis()}"
            )
        )
        log("read_save", "id=$id uid=$uid bytes=${data.size} fail=${dump.failIndex.size}")
        return id
    }

    fun observeCardDumps(): Flow<List<CardDumpEntity>> = cardDumpDao.getAll()

    fun observeCachedKeys(): Flow<List<KeyCacheEntity>> = keyDao.getAll()

    fun getLocalDictionaryKeys(): List<String> = KeyCrackingEngine.COMMON_KEYS

    fun observeCardDump(id: Long): Flow<CardDumpEntity?> = cardDumpDao.observeById(id)

    suspend fun updateCardDumpData(id: Long, data: ByteArray) = cardDumpDao.updateData(id, data)

    suspend fun renameCardDump(id: Long, name: String) = cardDumpDao.rename(id, name)

    suspend fun deleteCardDump(id: Long) = cardDumpDao.delete(id)

    suspend fun getCardDump(id: Long) = cardDumpDao.getById(id)

    suspend fun deleteKeysByUid(uid: String) = keyDao.deleteByUid(uid)

    suspend fun clearAllKeys() = keyDao.clearAll()

    suspend fun writeCard(data: ByteArray, onProgress: ((String, Int) -> Unit)? = null) {
        ensureReaderConnected()
        if (mifareCard.isUltraLightEv1Card() || mifareCard.isMifareUltraLight) {
            val result: WriteNtagResult = mifareCard.writeNtagMemory(data)
            if (!result.authResult) throw IllegalStateException(result.message)
            return
        }
        mifareCard.writeAllBlocks(data, onProgress)
        log("write", "size=${data.size}")
    }

    suspend fun writeAmiibo(raw: ByteArray): WriteNtagResult {
        ensureReaderConnected()
        val prepared = AmiiboUtils.amiiboBinCheck(raw) ?: throw IllegalStateException("文件大小错误，请重新下载")
        return mifareCard.writeNtagMemory(prepared, intArrayOf(0, 0, 0, 0))
    }

    suspend fun checkDarkside(): Boolean = mifareCard.checkDarkside()

    /**
     * Collect nonce data from the card using PN532 FastGetNonce command.
     * This is the Mode B detection: reader + card → nonce → local distance calculation.
     * @return Raw nonce data (ByteArray) or null if collection failed
     */
    suspend fun collectNonces(): ByteArray? {
        ensureReaderConnected()
        val uid = mifareCard.cardInfo.uid
        if (uid.all { it == 0 }) return null

        // Find a known key sector for nonce collection
        val knownSector = mifareCard.getFirstKnownSectorInfo()
        if (knownSector != null) {
            try {
                val crackInfo = IntArray(20) { 5 } // Default crack parameters from mini-program
                val nonceData = mifareCard.hal.mifareFastGetNonce(
                    uid = uid,
                    key = knownSector.keyArray,
                    keyType = knownSector.keyType,
                    sectorNum = knownSector.sectorNumber * 4 + 3,
                    crackInfo = crackInfo,
                    attackCnt = 2,
                    mode = 221, // 0xDD - same as mini-program
                    timeoutMs = 2000
                )
                if (nonceData.isNotEmpty()) {
                    log("nonce_collect", "bytes=${nonceData.size} sector=${knownSector.sectorNumber}")
                    return HexUtils.intsToBytes(nonceData)
                }
            } catch (e: Exception) {
                Timber.w(e, "Nonce collection failed, trying default key")
            }
        }

        // Fallback: try with default key FFFFFFFFFFFF
        try {
            val defaultKey = intArrayOf(0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF)
            val crackInfo = IntArray(20) { 5 }
            val nonceData = mifareCard.hal.mifareFastGetNonce(
                uid = uid,
                key = defaultKey,
                keyType = 0,
                sectorNum = 3, // Block 3 of sector 0
                crackInfo = crackInfo,
                attackCnt = 2,
                mode = 221,
                timeoutMs = 2000
            )
            if (nonceData.isNotEmpty()) {
                log("nonce_collect_default", "bytes=${nonceData.size}")
                return HexUtils.intsToBytes(nonceData)
            }
        } catch (e: Exception) {
            Timber.w(e, "Nonce collection with default key failed")
        }

        return null
    }

    /**
     * Run distance detection using nonce data.
     * Collects nonces via PN532, then calculates LFSR distance locally.
     * Matches the mini-program's /com/det_fr.php flow but without cloud.
     */
    suspend fun runDistanceDetection(onProgress: ((String, Int) -> Unit)? = null): DistanceResult {
        ensureReaderConnected()
        onProgress?.invoke("正在采集 Nonce 数据", 30)
        val nonceData = collectNonces()
        if (nonceData == null || nonceData.isEmpty()) {
            throw IllegalStateException("Nonce 采集失败，请确保卡片贴近天线")
        }
        onProgress?.invoke("正在计算 LFSR Distance", 70)
        // Parse nonces from the collected data
        val traces = com.longyannfc.app.utils.DetectionDataParser.parse(
            nonceData, mifareCard.cardInfo.uid.fold(0) { acc, v -> (acc shl 8) or v }
        )
        val distance = if (traces.size >= 2) {
            com.longyannfc.app.utils.Crypto1KeyRecovery().calculateDistance(traces[0].nt, traces[1].nt)
        } else {
            -1
        }
        val cardType = com.longyannfc.app.utils.Crypto1KeyRecovery().classifyCard(distance)
        onProgress?.invoke("侦测完成", 100)
        log("distance", "distance=$distance type=${cardType.name}")
        return DistanceResult(
            nonceData = nonceData,
            distance = distance,
            cardVulnerability = cardType,
            traces = traces
        )
    }

    suspend fun runAdvancedDetection(onProgress: ((String, Int) -> Unit)? = null): DetectionResult {
        ensureReaderConnected()
        onProgress?.invoke("正在下载侦测数据", 15)
        val detectionData = chameleon.downloadPcrDetection()
        if (detectionData.isEmpty()) throw IllegalStateException("侦测数据为空")
        onProgress?.invoke("正在本地分析侦测数据", 70)
        val analysis = localDetectionAnalyzer.analyze(detectionData, HexUtils.toHex(mifareCard.cardInfo.uid, ""))
        log("detection_local", "bytes=${detectionData.size} keys=${analysis.keys.size}")
        onProgress?.invoke("本地侦测完成", 100)
        return DetectionResult(detectionData, analysis.keys, analysis.report)
    }

    /**
     * Run the full nested attack on all sectors.
     * Uses Crypto1-PRS algorithm with parity-based candidate filtering.
     * Can break Gen3 "no-vulnerability" cards that use static nonces.
     *
     * Flow:
     * 1. Find a known key sector (default key or cached)
     * 2. For each unknown sector:
     *    a. Authenticate to known sector → establish LFSR state
     *    b. Use mifareFastGetNonce to collect encrypted nonce from target
     *    c. Run Crypto1-PRS attack: parity filtering + LFSR rollback
     *    d. Verify recovered key
     * 3. Cache all recovered keys
     */
    suspend fun runNestedAttack(onProgress: ((String, Int) -> Unit)? = null): NestedAttackResult {
        ensureReaderConnected()
        val uid = mifareCard.cardInfo.uid
        val uidInt = uid.fold(0) { acc, v -> (acc shl 8) or v }

        onProgress?.invoke("正在扫描已知密钥", 5)

        // Step 1: Find known keys (try default keys first)
        val knownSectors = findKnownSectors()
        if (knownSectors.isEmpty()) {
            throw IllegalStateException("未找到已知密钥，请先扫描默认密钥或读取一个已知扇区")
        }
        val knownSector = knownSectors.first()
        val knownKey = knownSector.keyArray.fold(0L) { acc, v -> (acc shl 8) or (v.toLong() and 0xFF) }
        Timber.d("Known sector: ${knownSector.sectorNumber}, key: %012X", knownKey)

        onProgress?.invoke("正在采集 Nonce 数据", 15)

        // Step 2: Collect nonce data using mifareFastGetNonce
        val crackInfo = IntArray(20) { 5 }
        val nonceData = try {
            mifareCard.hal.mifareFastGetNonce(
                uid = uid,
                key = knownSector.keyArray,
                keyType = knownSector.keyType,
                sectorNum = knownSector.sectorNumber * 4 + 3,
                crackInfo = crackInfo,
                attackCnt = 2,
                mode = 221, // 0xDD
                timeoutMs = 3000
            )
        } catch (e: Exception) {
            Timber.w(e, "Nonce collection failed")
            intArrayOf()
        }

        if (nonceData.isEmpty()) {
            throw IllegalStateException("Nonce 采集失败，请确保卡片贴近天线")
        }

        onProgress?.invoke("正在解析 Nonce 数据", 30)

        // Step 3: Parse nonce data into traces
        val traces = DetectionDataParser.parse(HexUtils.intsToBytes(nonceData), uidInt)
        Timber.d("Parsed ${traces.size} traces from ${nonceData.size} bytes")

        if (traces.isEmpty()) {
            throw IllegalStateException("未解析到有效的认证帧")
        }

        // Step 4: Run nested attack on each unknown sector
        val maxSectors = if (mifareCard.cardInfo.size >= 4096) 40 else 16
        val results = mutableListOf<NestedAttackEngine.RecoveredKey>()
        var totalCandidates = 0

        for (sector in 0 until maxSectors) {
            if (sector == knownSector.sectorNumber) continue
            if (mifareCard.getSectorInfo(sector).isKnownA || mifareCard.getSectorInfo(sector).isKnownB) continue

            val pct = 30 + (sector * 60 / maxSectors)
            onProgress?.invoke("正在攻击扇区 $sector/$maxSectors", pct)

            // Use the first trace for the attack
            val trace = traces.first()
            val attackResult = nestedAttackEngine.attack(
                uid = uidInt,
                knownKey = knownKey,
                knownKeyType = knownSector.keyType,
                targetSector = sector,
                ntEnc = trace.nt,
                nr = trace.nr,
                parityBits = 0, // Will be extracted from trace if available
                atEnc = trace.at
            )

            totalCandidates += attackResult.candidatesBeforeFilter

            if (attackResult.success) {
                results.addAll(attackResult.keys.filter { it.verified })
                // Cache the recovered key
                for (key in attackResult.keys.filter { it.verified }) {
                    val keyBytes = IntArray(6) { i -> ((key.key shr (40 - i * 8)) and 0xFF).toInt() }
                    if (key.keyType == 0) {
                        mifareCard.updateSectorKeyA(sector, keyBytes)
                    } else {
                        mifareCard.updateSectorKeyB(sector, keyBytes)
                    }
                }
            }
        }

        onProgress?.invoke("正在验证密钥", 95)

        // Step 5: Verify by attempting to read with recovered keys
        val verified = results.count { it.verified }

        onProgress?.invoke("攻击完成", 100)

        val report = buildString {
            appendLine("=== Crypto1-PRS 嵌套攻击结果 ===")
            appendLine("UID: ${HexUtils.toHex(uid, "")}")
            appendLine("已知扇区: ${knownSector.sectorNumber} (Key ${if (knownSector.keyType == 0) "A" else "B"})")
            appendLine("总扇区: $maxSectors")
            appendLine("解析帧: ${traces.size}")
            appendLine("候选状态: $totalCandidates")
            appendLine("恢复密钥: ${results.size} 个")
            appendLine("已验证: $verified 个")
            if (results.isNotEmpty()) {
                appendLine("")
                appendLine("恢复的密钥:")
                results.forEach { key ->
                    appendLine("  扇区 ${key.sector} Key${if (key.keyType == 0) "A" else "B"}: ${key.keyHex} ${if (key.verified) "✓" else "?"}")
                }
            }
        }.trim()

        log("nested_attack", "sectors=$maxSectors recovered=${results.size} verified=$verified")
        return NestedAttackResult(
            uid = uidInt,
            recoveredKeys = results,
            totalSectors = maxSectors,
            totalCandidates = totalCandidates,
            report = report
        )
    }

    /**
     * Find sectors with known keys (from cache or default keys).
     */
    private suspend fun findKnownSectors(): List<com.longyannfc.app.data.ble.KnownSector> {
        val known = mutableListOf<com.longyannfc.app.data.ble.KnownSector>()
        // Check cached keys
        val uid = HexUtils.toHex(mifareCard.cardInfo.uid, "")
        val cached = keyDao.getByUid(uid)
        for (cache in cached) {
            if (cache.keyA != null) {
                known.add(com.longyannfc.app.data.ble.KnownSector(
                    sectorNumber = cache.sector,
                    keyArray = HexUtils.fromHexInts(cache.keyA),
                    keyType = 0x60
                ))
            }
            if (cache.keyB != null) {
                known.add(com.longyannfc.app.data.ble.KnownSector(
                    sectorNumber = cache.sector,
                    keyArray = HexUtils.fromHexInts(cache.keyB),
                    keyType = 0x61
                ))
            }
        }
        // Also try default key
        if (known.isEmpty()) {
            known.add(com.longyannfc.app.data.ble.KnownSector(
                sectorNumber = 0,
                keyArray = intArrayOf(0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF),
                keyType = 0x60
            ))
        }
        return known
    }

    suspend fun downloadAmiiboList(): List<AmiiboInfo> = withContext(Dispatchers.IO) {
        amiiboDao.getAllOnce().map {
            AmiiboInfo(
                id = it.id,
                name = it.name,
                image = it.imageUrl,
                url = it.localPath ?: it.binUrl,
                binUrl = it.localPath ?: it.binUrl
            )
        }
    }

    private fun ensureReaderConnected() {
        if (!bleManager.isConnected()) {
            throw IllegalStateException("请先连接 LongYan NFC 读卡器")
        }
    }

    suspend fun downloadAmiiboBin(url: String): ByteArray = withContext(Dispatchers.IO) {
        val localFile = File(url)
        if (url.isBlank() || !localFile.exists()) {
            throw IllegalStateException("本地模式未找到 Amiibo bin 文件，请先导入本地文件")
        }
        localFile.readBytes()
    }

    /**
     * 统一智能侦测流程
     * 1. 检测卡片
     * 2. 扫描默认密钥
     * 3. 尝试读卡
     * 4. 分析安全等级
     * 5. 如果发现强加密特征，进入嵌套攻击模式
     */
    suspend fun runSmartDetection(
        onPhaseChange: (SmartDetectionPhase, String) -> Unit,
        onProgress: (String, Int) -> Unit
    ): SmartDetectionResult {
        ensureReaderConnected()

        val allRecoveredKeys = mutableListOf<DetectionKey>()
        val initialKeys = mutableListOf<DetectionKey>()
        val nestedKeys = mutableListOf<DetectionKey>()

        // === Phase 1: 检测卡片 ===
        onPhaseChange(SmartDetectionPhase.CARD_DETECT, "正在检测卡片...")
        onProgress("初始化读卡器", 5)

        mifareCard.initHalChip()
        val target = mifareCard.pollNfcCard()
        mifareCard.setMifareInfo(target)
        val uidHex = HexUtils.toHex(mifareCard.cardInfo.uid, "")
        val uidInt = mifareCard.cardInfo.uid.fold(0) { acc, v -> (acc shl 8) or v }
        log("smart_detect", "UID=$uidHex")
        onProgress("卡片已识别 UID=$uidHex", 10)

        // === Phase 2: 扫描默认密钥 ===
        onPhaseChange(SmartDetectionPhase.KEY_SCAN, "正在扫描密钥...")
        mifareCard.scanDefaultKeys { msg, pct ->
            onProgress(msg, 10 + pct)
        }

        // 收集初始扫描到的密钥
        for (sector in 0 until mifareCard.maxSectorsNum) {
            val info = mifareCard.getSectorInfo(sector)
            if (info.isKnownA && info.keyA != null) {
                initialKeys.add(DetectionKey(uidHex, sector * 4 + 3, "KeyA", HexUtils.toHex(info.keyA!!, "")))
            }
            if (info.isKnownB && info.keyB != null) {
                initialKeys.add(DetectionKey(uidHex, sector * 4 + 3, "KeyB", HexUtils.toHex(info.keyB!!, "")))
            }
        }
        onProgress("扫描到 ${initialKeys.size} 个密钥", 40)

        // === Phase 3: 尝试读卡 ===
        onPhaseChange(SmartDetectionPhase.READ_CARD, "正在读取卡片...")
        var readFailBlocks = listOf<Int>()
        try {
            val dump = mifareCard.readAllBlocks { msg, pct ->
                onProgress(msg, 40 + (pct - 30) * 20 / 70)
            }
            readFailBlocks = dump.failIndex
            cacheKeys(mifareCard)
            onProgress("读取完成，失败块数: ${readFailBlocks.size}", 60)
        } catch (e: Exception) {
            Timber.w(e, "Read card failed during smart detection")
            onProgress("读取异常: ${e.message}", 60)
        }

        // === Phase 4: 分析安全等级 ===
        onPhaseChange(SmartDetectionPhase.ANALYZE, "正在分析安全等级...")
        onProgress("计算卡片漏洞特征", 65)

        val knownSectorCount = initialKeys.map { it.block / 4 }.distinct().size
        val totalSectors = mifareCard.maxSectorsNum
        val encryptedSectors = totalSectors - knownSectorCount
        val deadZones = readFailBlocks.map { it / 4 }.distinct()

        // 尝试 Distance 检测判断卡片类型
        var distance = -1
        var isGen3 = false
        var isChineseClone = false
        try {
            val nonceData = collectNonces()
            if (nonceData != null && nonceData.isNotEmpty()) {
                val traces = DetectionDataParser.parse(nonceData, uidInt)
                if (traces.size >= 2) {
                    distance = Crypto1KeyRecovery().calculateDistance(traces[0].nt, traces[1].nt)
                    val vuln = Crypto1KeyRecovery().classifyCard(distance)
                    isGen3 = vuln == Crypto1KeyRecovery.CardVulnerability.GEN3_NO_VULN
                    isChineseClone = vuln == Crypto1KeyRecovery.CardVulnerability.CHINESE_CLONE
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Distance calculation failed")
        }

        // 判断是否有强加密特征
        val strongEncryptionReasons = mutableListOf<String>()
        if (isGen3) strongEncryptionReasons.add("三代无漏洞卡(静态Nonce)")
        if (isChineseClone) strongEncryptionReasons.add("国产兼容卡")
        if (encryptedSectors > totalSectors / 2) strongEncryptionReasons.add("超过半数扇区加密($encryptedSectors/$totalSectors)")
        if (deadZones.size > 3) strongEncryptionReasons.add("存在多个死区扇区(${deadZones.size}个)")
        if (initialKeys.isEmpty()) strongEncryptionReasons.add("未扫描到任何默认密钥")

        val hasStrongEncryption = strongEncryptionReasons.isNotEmpty()
        val reason = if (hasStrongEncryption) {
            "检测到强加密特征: ${strongEncryptionReasons.joinToString("; ")}"
        } else {
            "卡片安全等级普通，使用默认密钥即可读取"
        }

        val analysis = CardSecurityAnalysis(
            knownSectors = knownSectorCount,
            totalSectors = totalSectors,
            encryptedSectors = encryptedSectors,
            deadZones = deadZones,
            isGen3Card = isGen3,
            isChineseClone = isChineseClone,
            distance = distance,
            hasStrongEncryption = hasStrongEncryption,
            reason = reason
        )
        onProgress("安全分析完成: $reason", 70)

        // === Phase 5: 嵌套攻击（仅在需要时执行）===
        if (hasStrongEncryption) {
            // 5a: 进入侦测模式
            onPhaseChange(SmartDetectionPhase.ENTER_DETECTION_MODE, "正在发送侦测指令...")
            onProgress("指令设备进入侦测模式", 72)
            try {
                chameleon.switchToPcReaderMode()
                chameleon.clearPcrDetection(1) // 开始侦测
                log("smart_detect", "Device entered detection mode")
            } catch (e: Exception) {
                Timber.w(e, "Failed to enter detection mode")
                onProgress("进入侦测模式失败: ${e.message}", 73)
            }

            // 5b: 等待用户操作
            onPhaseChange(SmartDetectionPhase.WAIT_READER, "请将读卡器靠近原厂读头...")
            onProgress("等待设备与原厂读头交互...", 75)
            // 给用户一些时间操作
            kotlinx.coroutines.delay(3000)

            // 5c: 采集交互数据
            onPhaseChange(SmartDetectionPhase.COLLECT_NONCE, "正在采集交互数据...")
            onProgress("下载侦测数据", 80)

            try {
                val detectionData = chameleon.downloadPcrDetection()
                if (detectionData.isNotEmpty()) {
                    onProgress("采集到 ${detectionData.size} 字节数据", 85)

                    // 5d: 执行嵌套攻击
                    onPhaseChange(SmartDetectionPhase.NESTED_ATTACK, "正在执行嵌套攻击...")
                    onProgress("解析认证帧", 87)

                    val traces = DetectionDataParser.parse(detectionData, uidInt)
                    onProgress("解析到 ${traces.size} 个认证帧", 89)

                    // 使用 NestedAttackEngine 逐扇区攻击
                    val knownSector = mifareCard.getFirstKnownSectorInfo()
                    if (knownSector != null && traces.isNotEmpty()) {
                        val knownKey = knownSector.keyArray.fold(0L) { acc, v -> (acc shl 8) or (v.toLong() and 0xFF) }
                        val trace = traces.first()

                        for (sector in 0 until totalSectors) {
                            if (sector == knownSector.sectorNumber) continue
                            val info = mifareCard.getSectorInfo(sector)
                            if (info.isKnownA || info.isKnownB) continue

                            val pct = 89 + (sector * 10 / totalSectors)
                            onProgress("攻击扇区 $sector/$totalSectors", pct)

                            val attackResult = nestedAttackEngine.attack(
                                uid = uidInt,
                                knownKey = knownKey,
                                knownKeyType = knownSector.keyType,
                                targetSector = sector,
                                ntEnc = trace.nt,
                                nr = trace.nr,
                                parityBits = 0,
                                atEnc = trace.at
                            )

                            if (attackResult.success) {
                                for (key in attackResult.keys.filter { it.verified }) {
                                    val keyBytes = IntArray(6) { i -> ((key.key shr (40 - i * 8)) and 0xFF).toInt() }
                                    val keyHex = HexUtils.toHex(keyBytes, "")
                                    val keyType = if (key.keyType == 0) "KeyA" else "KeyB"
                                    nestedKeys.add(DetectionKey(uidHex, sector * 4 + 3, keyType, keyHex))

                                    // 更新到卡片信息中
                                    if (key.keyType == 0) {
                                        mifareCard.updateSectorKeyA(sector, keyBytes)
                                    } else {
                                        mifareCard.updateSectorKeyB(sector, keyBytes)
                                    }
                                }
                            }
                        }
                    }

                    // 也尝试本地分析
                    val analysisResult = localDetectionAnalyzer.analyze(detectionData, uidHex)
                    for (key in analysisResult.keys) {
                        if (nestedKeys.none { it.key == key.key }) {
                            nestedKeys.add(key)
                        }
                    }

                    onProgress("嵌套攻击完成，恢复 ${nestedKeys.size} 个密钥", 98)
                } else {
                    onProgress("未采集到侦测数据", 95)
                }
            } catch (e: Exception) {
                Timber.w(e, "Nested attack failed")
                onProgress("嵌套攻击失败: ${e.message}", 95)
            }

            // 清除侦测状态
            try {
                chameleon.clearPcrDetection(0)
            } catch (_: Exception) {}
        }

        // === Phase 6: 完成 ===
        onPhaseChange(SmartDetectionPhase.COMPLETE, "侦测完成")
        allRecoveredKeys.addAll(initialKeys)
        for (key in nestedKeys) {
            if (allRecoveredKeys.none { it.key == key.key && it.block == key.block }) {
                allRecoveredKeys.add(key)
            }
        }

        val readSuccess = readFailBlocks.isEmpty()
        val report = buildString {
            appendLine("=== 智能侦测报告 ===")
            appendLine("UID: $uidHex")
            appendLine("总扇区: $totalSectors")
            appendLine("已知密钥扇区: $knownSectorCount")
            appendLine("加密扇区: $encryptedSectors")
            if (deadZones.isNotEmpty()) {
                appendLine("死区扇区: ${deadZones.joinToString(", ")}")
            }
            if (distance >= 0) {
                appendLine("LFSR Distance: $distance")
            }
            appendLine("")
            appendLine("安全分析: $reason")
            appendLine("")
            appendLine("--- 初始扫描密钥 (${initialKeys.size}) ---")
            initialKeys.forEach { appendLine("  ${it.type} Block${it.block}: ${it.key}") }
            if (nestedKeys.isNotEmpty()) {
                appendLine("")
                appendLine("--- 嵌套攻击恢复密钥 (${nestedKeys.size}) ---")
                nestedKeys.forEach { appendLine("  ${it.type} Block${it.block}: ${it.key}") }
            }
            appendLine("")
            appendLine("总计恢复: ${allRecoveredKeys.size} 个密钥")
            appendLine("读取状态: ${if (readSuccess) "完整" else "部分失败(${readFailBlocks.size}块)"}")
        }.trim()

        log("smart_detect", "complete uid=$uidHex keys=${allRecoveredKeys.size} encrypted=$encryptedSectors nested=${nestedKeys.size}")

        return SmartDetectionResult(
            phase = SmartDetectionPhase.COMPLETE,
            cardUid = uidHex,
            securityAnalysis = analysis,
            initialKeys = initialKeys,
            nestedKeys = nestedKeys,
            allKeys = allRecoveredKeys,
            readSuccess = readSuccess,
            report = report
        )
    }

    suspend fun bruteForce(datas: String): IntArray? {
        val normalized = datas.replace(Regex("[^0-9A-Fa-f]"), "").uppercase()
        val key = KeyCrackingEngine.COMMON_KEYS.firstOrNull { normalized.contains(it) }
            ?: KeyCrackingEngine.COMMON_KEYS.firstOrNull()
        return key?.let { HexUtils.fromHexInts(it) }
    }

    suspend fun nestedKeys(datas: String, uidHex: String, @Suppress("UNUSED_PARAMETER") distanceHex: String): List<IntArray> {
        val analysis = localDetectionAnalyzer.analyze(HexUtils.fromHex(datas), uidHex)
        return analysis.keys.mapNotNull { key ->
            runCatching { HexUtils.fromHexInts(key.key) }.getOrNull()
        }.ifEmpty {
            KeyCrackingEngine.COMMON_KEYS.take(8).map { HexUtils.fromHexInts(it) }
        }
    }

    suspend fun login(code: String, phoneCode: String): LoginResponse {
        val username = code.ifBlank { phoneCode }.ifBlank { "local-user" }
        val sess = "local-${System.currentTimeMillis()}"
        session.saveUser(session.user.value.copy(username = username, session = sess, openid = "local"))
        return LoginResponse(true, sess, "local", username, sess)
    }

    suspend fun checkLogin(): Boolean {
        return session.isLoggedIn
    }

    suspend fun getUserKeys(): List<String> {
        return session.userKeys
    }

    suspend fun setUserKeys(keys: List<String>) {
        session.userKeys = keys.toMutableList()
        session.persistKeys()
    }

    suspend fun getPublicKeys(): List<String> {
        if (session.publicKeys.isEmpty()) {
            session.publicKeys = KeyCrackingEngine.COMMON_KEYS.toMutableList()
            session.persistKeys()
        }
        return session.publicKeys
    }

    suspend fun log(type: String, detail: String) {
        logDao.insert(OperationLogEntity(type = type, detail = detail))
    }

    private suspend fun cacheKeys(card: MifareCard) {
        for (sector in 0 until card.maxSectorsNum) {
            val info = card.getSectorInfo(sector)
            keyDao.insert(
                KeyCacheEntity(
                    id = "${HexUtils.toHex(card.cardInfo.uid, "")}-$sector",
                    uid = HexUtils.toHex(card.cardInfo.uid, ""),
                    sector = sector,
                    keyA = info.keyA?.let { HexUtils.toHex(it, "") },
                    keyB = info.keyB?.let { HexUtils.toHex(it, "") }
                )
            )
        }
    }

    private fun JsonObject.getString(key: String): String =
        if (has(key) && !get(key).isJsonNull) get(key).asString else ""
}

data class DetectionResult(
    val rawData: ByteArray,
    val keys: List<DetectionKey>,
    val analysisReport: String
)

data class DistanceResult(
    val nonceData: ByteArray,
    val distance: Int,
    val cardVulnerability: com.longyannfc.app.utils.Crypto1KeyRecovery.CardVulnerability,
    val traces: List<com.longyannfc.app.utils.AuthTrace>
)

data class NestedAttackResult(
    val uid: Int,
    val recoveredKeys: List<com.longyannfc.app.utils.NestedAttackEngine.RecoveredKey>,
    val totalSectors: Int,
    val totalCandidates: Int,
    val report: String
)

data class DetectionKey(
    val uid: String,
    val block: Int,
    val type: String,
    val key: String
)

/**
 * 智能侦测阶段枚举
 */
enum class SmartDetectionPhase(val label: String) {
    IDLE("空闲"),
    CARD_DETECT("检测卡片"),
    KEY_SCAN("扫描密钥"),
    READ_CARD("读取卡片"),
    ANALYZE("分析安全等级"),
    ENTER_DETECTION_MODE("进入侦测模式"),
    WAIT_READER("等待靠近原厂读头"),
    COLLECT_NONCE("采集交互数据"),
    NESTED_ATTACK("嵌套攻击"),
    COMPLETE("侦测完成")
}

/**
 * 卡片安全分析结果
 */
data class CardSecurityAnalysis(
    val knownSectors: Int,           // 已知密钥的扇区数
    val totalSectors: Int,           // 总扇区数
    val encryptedSectors: Int,       // 加密扇区数(无法读取)
    val deadZones: List<Int>,        // 死区扇区列表
    val isGen3Card: Boolean,         // 是否三代无漏洞卡
    val isChineseClone: Boolean,     // 是否国产兼容卡
    val distance: Int,               // LFSR距离
    val hasStrongEncryption: Boolean, // 是否有强加密特征
    val reason: String               // 分析原因说明
)

/**
 * 智能侦测统一结果
 */
data class SmartDetectionResult(
    val phase: SmartDetectionPhase,
    val cardUid: String,
    val securityAnalysis: CardSecurityAnalysis?,
    val initialKeys: List<DetectionKey>,     // 初始扫描到的密钥
    val nestedKeys: List<DetectionKey>,      // 嵌套攻击恢复的密钥
    val allKeys: List<DetectionKey>,         // 所有密钥合并
    val readSuccess: Boolean,                // 是否成功读取完整卡片
    val report: String                       // 最终报告
)
