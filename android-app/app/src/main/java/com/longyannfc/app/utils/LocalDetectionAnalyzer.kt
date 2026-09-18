package com.longyannfc.app.utils

import com.longyannfc.app.data.repository.DetectionKey
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local detection analyzer that performs real Crypto1 key recovery.
 * Replaces the old heuristic-based analyzer with proper LFSR attack.
 */
@Singleton
class LocalDetectionAnalyzer @Inject constructor(
    private val keyRecovery: Crypto1KeyRecovery
) {
    data class Analysis(
        val keys: List<DetectionKey>,
        val report: String
    )

    fun analyze(rawData: ByteArray, fallbackUid: String = "UNKNOWN"): Analysis {
        // Parse UID from hex string
        val uidInt = try {
            fallbackUid.replace(":", "").replace(" ", "").toLong(16).toInt()
        } catch (_: Exception) { 0 }

        // Step 1: Parse detection data into authentication traces
        val traces = DetectionDataParser.parse(rawData, uidInt)
        Timber.d("Detection: parsed ${traces.size} traces from ${rawData.size} bytes")

        // Step 2: Recover keys using Crypto1 LFSR rollback
        val recoveredKeys = if (uidInt != 0) {
            keyRecovery.recoverFromDetectionData(rawData, uidInt)
        } else {
            // Try to extract UID from traces
            val detectedUid = traces.firstOrNull { it.uid != 0 }?.uid ?: 0
            if (detectedUid != 0) {
                keyRecovery.recoverFromDetectionData(rawData, detectedUid)
            } else {
                emptyList()
            }
        }

        // Step 3: Also try common keys as fallback
        val dictKeys = tryCommonKeys(traces, fallbackUid)

        // Step 4: Combine results
        val keys = buildList {
            // Verified recovered keys first
            addAll(recoveredKeys.filter { it.verified }.map { result ->
                DetectionKey(
                    uid = result.uidHex,
                    block = if (result.sector >= 0) result.sector * 4 + 3 else 0,
                    type = if (result.keyType == 0) "Crypto1_KeyA" else "Crypto1_KeyB",
                    key = result.keyHex
                )
            })
            // Unverified recovered keys
            addAll(recoveredKeys.filter { !it.verified }.map { result ->
                DetectionKey(
                    uid = result.uidHex,
                    block = if (result.sector >= 0) result.sector * 4 + 3 else 0,
                    type = "Candidate",
                    key = result.keyHex
                )
            })
            // Dictionary keys
            addAll(dictKeys)
        }.distinctBy { "${it.uid}:${it.block}:${it.type}:${it.key}" }

        val uid = traces.firstOrNull { it.uid != 0 }?.uidHex ?: fallbackUid

        val report = buildString {
            appendLine("=== 本地 Crypto1 侦测分析 ===")
            appendLine("侦测数据: ${rawData.size} bytes")
            appendLine("解析帧数: ${traces.size}")
            appendLine("UID: $uid")
            appendLine("恢复密钥: ${recoveredKeys.count { it.verified }} 个已验证")
            appendLine("候选密钥: ${recoveredKeys.count { !it.verified }} 个待验证")
            appendLine("字典密钥: ${dictKeys.size} 个")
            if (traces.isNotEmpty()) {
                val nonces = traces.take(3).map { "NT=%08X NR=%08X".format(it.nt, it.nr) }
                appendLine("样本Nonce: ${nonces.joinToString(", ")}")
            }
            if (recoveredKeys.isEmpty() && dictKeys.isEmpty()) {
                appendLine("提示: 未恢复到密钥，建议使用云端暴力破解或检查侦测数据质量。")
            }
        }.trim()

        return Analysis(keys, report)
    }

    /**
     * Try common/default keys against traces to see if any match.
     */
    private fun tryCommonKeys(traces: List<AuthTrace>, uid: String): List<DetectionKey> {
        if (traces.isEmpty()) return emptyList()
        val uidInt = try { uid.replace(":", "").toLong(16).toInt() } catch (_: Exception) { 0 }

        val results = mutableListOf<DetectionKey>()
        for (keyHex in KeyCrackingEngine.COMMON_KEYS.take(20)) {
            try {
                val keyValue = keyHex.toLong(16)
                // Check against first few traces
                val matchTrace = traces.take(5).firstOrNull { trace ->
                    keyRecovery.verifyKey(keyValue, trace.uid, trace.nt, trace.nr, trace.ar, trace.at)
                }
                if (matchTrace != null) {
                    results.add(DetectionKey(
                        uid = uid,
                        block = 3,
                        type = "Dictionary_Match",
                        key = keyHex
                    ))
                }
            } catch (_: Exception) { }
        }
        return results
    }
}