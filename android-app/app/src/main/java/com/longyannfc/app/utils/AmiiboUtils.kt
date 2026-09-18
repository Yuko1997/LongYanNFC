package com.longyannfc.app.utils

/**
 * Amiibo data parser and utilities.
 * Handles amiibo binary format (540 bytes for NTAG215).
 */
object AmiiboUtils {

    // Amiibo binary format constants
    const val AMIIBO_SIZE = 540
    const val AMIIBO_ID_OFFSET = 0x054
    const val AMIIBO_ID_SIZE = 0x08
    const val AMIIBO_NAME_OFFSET = 0x038
    const val AMIIBO_NAME_SIZE = 0x14 // 20 bytes (UTF-16LE)
    const val AMIIBO_DATA_OFFSET = 0x0DC
    const val AMIIBO_SETTINGS_OFFSET = 0x100

    // Password for NTAG215
    const val NTAG_PASSWORD_OFFSET = 0x0E9
    const val NTAG_PACK_OFFSET = 0x0EA
    const val NTAG_SIGNATURE_OFFSET = 0x0AC
    const val NTAG_LOCK_BYTES_OFFSET = 0x0E4

    /**
     * Extract amiibo ID from binary data
     */
    fun extractId(data: ByteArray): String {
        if (data.size < AMIIBO_ID_OFFSET + AMIIBO_ID_SIZE) return ""
        return data.copyOfRange(AMIIBO_ID_OFFSET, AMIIBO_ID_OFFSET + AMIIBO_ID_SIZE)
            .joinToString("") { "%02X".format(it) }
    }

    /**
     * Extract amiibo name from binary data
     */
    fun extractName(data: ByteArray): String {
        if (data.size < AMIIBO_NAME_OFFSET + AMIIBO_NAME_SIZE) return ""
        val nameBytes = data.copyOfRange(AMIIBO_NAME_OFFSET, AMIIBO_NAME_OFFSET + AMIIBO_NAME_SIZE)
        return try {
            String(nameBytes, Charsets.UTF_16LE).trim('\u0000', ' ')
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Extract character info from amiibo ID
     */
    fun extractCharacterInfo(amiiboId: String): CharacterInfo {
        if (amiiboId.length < 4) return CharacterInfo("Unknown", "Unknown")
        val gameId = amiiboId.substring(0, 2)
        val characterId = amiiboId.substring(2, 4)
        // Simplified - full implementation would have complete amiibo database
        return CharacterInfo(getGameName(gameId), getCharacterName(characterId))
    }

    /**
     * Original amiiboBinCheck password:
     * t[532]=t[1]^t[4]^0xAA, t[533]=t[2]^t[5]^0x55, t[534]=t[3]^t[6]^0xAA, t[535]=t[4]^t[7]^0x55
     */
    fun calculatePassword(data: ByteArray): ByteArray {
        if (data.size < 8) return byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        return byteArrayOf(
            (data[1].toInt() xor data[4].toInt() xor 0xAA).toByte(),
            (data[2].toInt() xor data[5].toInt() xor 0x55).toByte(),
            (data[3].toInt() xor data[6].toInt() xor 0xAA).toByte(),
            (data[4].toInt() xor data[7].toInt() xor 0x55).toByte()
        )
    }

    fun amiiboBinCheck(raw: ByteArray): ByteArray? {
        if (raw.size < 530 || raw.size > 580) return null
        val data = if (raw.size == AMIIBO_SIZE) raw.copyOf() else {
            val extra = ByteArray(8)
            extra[0] = (raw[1].toInt() xor raw[4].toInt() xor 0xAA).toByte()
            extra[1] = (raw[2].toInt() xor raw[5].toInt() xor 0x55).toByte()
            extra[2] = (raw[3].toInt() xor raw[6].toInt() xor 0xAA).toByte()
            extra[3] = (raw[4].toInt() xor raw[7].toInt() xor 0x55).toByte()
            extra[4] = 0x80.toByte()
            extra[5] = 0x80.toByte()
            extra[6] = 0
            extra[7] = 0
            raw + extra
        }
        if (data.size >= AMIIBO_SIZE) {
            val pwd = calculatePassword(data)
            System.arraycopy(pwd, 0, data, 532, 4)
            data[536] = 0x80.toByte()
            data[537] = 0x80.toByte()
            data[538] = 0
            data[539] = 0
        }
        return data
    }

    /**
     * Calculate PACK (Password ACK) for NTAG215
     */
    fun calculatePack(): ByteArray {
        return byteArrayOf(0x80.toByte(), 0x80.toByte(), 0x00.toByte(), 0x00.toByte())
    }

    /**
     * Prepare data for writing to NTAG215
     */
    fun prepareNtagWrite(original: ByteArray): ByteArray {
        if (original.size < AMIIBO_SIZE) return ByteArray(AMIIBO_SIZE) { 0 }
        
        val data = original.copyOf()
        
        // Set password
        val password = calculatePassword(data)
        System.arraycopy(password, 0, data, NTAG_PASSWORD_OFFSET, 4)
        
        // Set PACK
        val pack = calculatePack()
        System.arraycopy(pack, 0, data, NTAG_PACK_OFFSET, 2)
        
        // Set lock bytes (if needed)
        data[NTAG_LOCK_BYTES_OFFSET] = 0x0F
        data[NTAG_LOCK_BYTES_OFFSET + 1] = 0xE0.toByte()
        data[NTAG_LOCK_BYTES_OFFSET + 2] = 0x00
        data[NTAG_LOCK_BYTES_OFFSET + 3] = 0x00
        
        return data
    }

    private fun getGameName(gameId: String): String {
        return when (gameId) {
            "01" -> "Super Smash Bros."
            "02" -> "Mario Bros."
            "03" -> "Animal Crossing"
            "04" -> "Splatoon"
            "05" -> "Yoshi"
            "06" -> "Kirby"
            "07" -> "Zelda"
            "08" -> "Pokemon"
            else -> "Unknown"
        }
    }

    private fun getCharacterName(characterId: String): String {
        return when (characterId) {
            "00" -> "Mario"
            "01" -> "Luigi"
            "02" -> "Peach"
            "03" -> "Bowser"
            "04" -> "Yoshi"
            "05" -> "Donkey Kong"
            "06" -> "Link"
            "07" -> "Zelda"
            "08" -> "Ganondorf"
            "09" -> "Samus"
            else -> "Unknown"
        }
    }
}

data class CharacterInfo(
    val game: String,
    val character: String
)
