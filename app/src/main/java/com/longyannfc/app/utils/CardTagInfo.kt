package com.longyannfc.app.utils

data class GenericTagInfo(
    val rows: List<Pair<String, String>>,
    val warnings: List<String>
)

object CardTagInfo {
    fun fromDump(uid: String, cardType: String, size: Int, data: ByteArray): GenericTagInfo {
        val block0 = data.take(16)
        val uidFromDump = uid.ifBlank { block0.take(4).toByteArray().let(HexUtils::toHexCompact) }
        val uidLength = (uidFromDump.length / 2).takeIf { it > 0 } ?: 4
        val bcc = block0.getOrNull(4)?.toInt()?.and(0xFF)
        val calculatedBcc = block0.take(4).fold(0) { acc, byte -> acc xor (byte.toInt() and 0xFF) }
        val sak = block0.getOrNull(5)?.toInt()?.and(0xFF)
        val atqa = if (block0.size >= 8) "%02X%02X".format(block0[7].toInt() and 0xFF, block0[6].toInt() and 0xFF) else "未知"
        val blockCount = if (data.isNotEmpty()) data.size / 16 else size / 16
        val sectorCount = when {
            blockCount > 128 -> 40
            blockCount > 0 -> blockCount / 4
            size >= 4096 -> 40
            size > 0 -> size / 64
            else -> 0
        }
        val type = identifyType(cardType, sak, atqa, size)
        val uidCascade = when (uidLength) {
            7 -> "CL2"
            10 -> "CL3"
            else -> "single"
        }
        val techList = when {
            type.contains("Classic", ignoreCase = true) -> "NfcA, MifareClassic"
            type.contains("Ultralight", ignoreCase = true) || type.contains("NTAG", ignoreCase = true) -> "NfcA, MifareUltralight"
            else -> "NfcA"
        }
        val rows = listOf(
            "UID" to "$uidFromDump ($uidLength byte, $uidCascade)",
            "类型" to type,
            "技术" to techList,
            "ATQA" to atqa,
            "SAK" to sak?.let { "%02X".format(it) }.orEmpty().ifBlank { "未知" },
            "ATS" to "未从 dump 中保存",
            "容量" to "${data.size.takeIf { it > 0 } ?: size} byte",
            "Block 大小" to "16 byte",
            "扇区数" to sectorCount.toString(),
            "块数" to blockCount.toString(),
            "Manufacturer Block" to block0.toByteArray().let(HexUtils::toHex)
        )
        val warnings = buildList {
            if (bcc != null && bcc != calculatedBcc) {
                add("Block 0 BCC 不匹配: dump=%02X calc=%02X".format(bcc, calculatedBcc))
            }
            if (data.isEmpty()) add("没有可分析的 dump 数据")
        }
        return GenericTagInfo(rows, warnings)
    }

    fun fromLiveInfo(uid: String, atqa: ByteArray?, sak: Byte?, size: Int, cardType: String, block0: ByteArray = ByteArray(0)): GenericTagInfo {
        val uidHex = uid.replace(":", "").replace(" ", "")
        val uidBytes = HexUtils.fromHex(uidHex)
        val uidLength = uidBytes.size.takeIf { it > 0 } ?: 4
        val atqaHex = if (atqa != null && atqa.size >= 2) {
            "%02X%02X".format(atqa[1].toInt() and 0xFF, atqa[0].toInt() and 0xFF)
        } else "未知"
        val sakInt = sak?.toInt()?.and(0xFF)
        val type = identifyType(cardType, sakInt, atqaHex, size)
        val uidCascade = when (uidLength) {
            7 -> "CL2"
            10 -> "CL3"
            else -> "single"
        }
        val techList = when {
            type.contains("Classic", ignoreCase = true) -> "NfcA, MifareClassic"
            type.contains("Ultralight", ignoreCase = true) || type.contains("NTAG", ignoreCase = true) -> "NfcA, MifareUltralight"
            else -> "NfcA"
        }
        val blockCount = size / 16
        val sectorCount = when {
            blockCount > 128 -> 40
            blockCount > 0 -> blockCount / 4
            size >= 4096 -> 40
            size > 0 -> size / 64
            else -> 0
        }
        // Always ensure we have a usable block0: prefer real read, fallback to anti-collision data
        val effectiveBlock0 = if (block0.size >= 16) {
            block0
        } else {
            // Construct synthetic block0 from anti-collision data
            val synthetic = ByteArray(16)
            val uid4 = if (uidBytes.size >= 4) uidBytes.copyOfRange(0, 4) else ByteArray(4)
            uid4.copyInto(synthetic, 0)
            synthetic[4] = (uid4[0].toInt() xor uid4[1].toInt() xor uid4[2].toInt() xor uid4[3].toInt()).toByte()
            synthetic[5] = sak ?: 0
            if (atqa != null && atqa.size >= 2) {
                synthetic[6] = atqa[1]
                synthetic[7] = atqa[0]
            }
            // Manufacturer bytes: industry code (0x04 = NXP) + rest zeros
            synthetic[8] = if (uidLength == 4) 0x04.toByte() else 0x00
            synthetic
        }
        val bcc = effectiveBlock0[4].toInt() and 0xFF
        val calculatedBcc = effectiveBlock0[0].toInt() xor effectiveBlock0[1].toInt() xor effectiveBlock0[2].toInt() xor effectiveBlock0[3].toInt()
        val atqaFromBlock = "%02X%02X".format(effectiveBlock0[7].toInt() and 0xFF, effectiveBlock0[6].toInt() and 0xFF)
        val sakFromBlock = effectiveBlock0[5].toInt() and 0xFF
        val manufacturerBytes = HexUtils.toHex(effectiveBlock0.sliceArray(8 until 16).map { it.toInt() and 0xFF }.toIntArray(), " ")
        val rows = listOf(
            "UID" to "$uid ($uidLength byte, $uidCascade)",
            "类型" to type,
            "技术" to techList,
            "ATQA" to atqaFromBlock,
            "SAK" to "%02X".format(sakFromBlock),
            "ATS" to "未检测",
            "容量" to "$size byte",
            "Block 大小" to "16 byte",
            "扇区数" to sectorCount.toString(),
            "块数" to blockCount.toString(),
            "Manufacturer Block" to HexUtils.toHex(effectiveBlock0.map { it.toInt() and 0xFF }.toIntArray()),
            "Manufacturer Bytes" to manufacturerBytes
        )
        val warnings = buildList {
            if (bcc != (calculatedBcc and 0xFF)) {
                add("Block 0 BCC 不匹配: 读取=%02X 计算=%02X".format(bcc, calculatedBcc and 0xFF))
            }
        }
        return GenericTagInfo(rows, warnings)
    }

    private fun identifyType(cardType: String, sak: Int?, atqa: String, size: Int): String {
        if (cardType.isNotBlank() && cardType != "UNKNOWN") return cardType
        return when (sak) {
            0x08 -> "MIFARE Classic 1K"
            0x18, 0x38 -> "MIFARE Classic 4K"
            0x00 -> if (atqa == "0044") "MIFARE Ultralight / NTAG" else "ISO14443A"
            else -> when {
                size >= 4096 -> "MIFARE Classic 4K"
                size >= 1024 -> "MIFARE Classic 1K"
                else -> "未知标签"
            }
        }
    }
}