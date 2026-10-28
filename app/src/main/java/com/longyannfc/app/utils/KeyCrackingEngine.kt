package com.longyannfc.app.utils

/**
 * Mifare Classic key cracking engine.
 * Implements brute force, nested attack, darkside attack, and static nested attack.
 */
class KeyCrackingEngine(private val crypto1: Crypto1) {

    companion object {
        // Common default keys used in Mifare cards
        val COMMON_KEYS = listOf(
            "FFFFFFFFFFFF", "000000000000", "A0A1A2A3A4A5", "B0B1B2B3B4B5",
            "D3F7D3F7D3F7", "AABBCCDDEEFF", "1A2B3C4D5E6F", "123456789ABC",
            "ABCDEF123456", "4D3A99C35DD8", "6A6B6C6D6E6F", "7A7B7C7D7E7F",
            "8A8B8C8D8E8F", "9A9B9C9D9E9F", "AAABACADAEAF", "BABBBDBEBFC0",
            "CACBCCCDCECF", "DADBDCDDDEDF", "EAEBECEDEEEF", "FAFBFCFDFEFF",
            "010203040506", "0708090A0B0C", "0D0E0F101112", "131415161718",
            "191A1B1C1D1E", "1F2021222324", "25262728292A", "2B2C2D2E2F30",
            "313233343536", "3738393A3B3C", "3D3E3F404142", "434445464748",
            "494A4B4C4D4E", "4F5051525354", "55565758595A", "5B5C5D5E5F60",
            "616263646566", "6768696A6B6C", "6D6E6F707172", "737475767778",
            "797A7B7C7D7E", "7F8081828384", "85868788898A", "8B8C8D8E8F90"
        )

        // Known weak keys
        val WEAK_KEYS = listOf(
            "FFFFFFFFFFFF", "000000000000", "A0A1A2A3A4A5", "B0B1B2B3B4B5",
            "D3F7D3F7D3F7", "AABBCCDDEEFF", "1A2B3C4D5E6F", "123456789ABC",
            "ABCDEF123456", "4D3A99C35DD8"
        )
    }

    data class CrackResult(
        val success: Boolean,
        val key: String?,
        val sector: Int?,
        val attackType: AttackType,
        val attempts: Long,
        val elapsedMs: Long
    )

    enum class AttackType {
        BRUTE_FORCE,
        NESTED,
        DARKSIDE,
        STATIC_NESTED,
        HARDNESTED
    }

    /**
     * Brute force attack - try all common keys
     */
    fun bruteForce(
        uid: String,
        sector: Int,
        onProgress: (Int) -> Unit
    ): CrackResult {
        val startTime = System.currentTimeMillis()
        var attempts = 0L

        for (key in COMMON_KEYS) {
            attempts++
            if (verifyKey(uid, sector, key)) {
                return CrackResult(
                    success = true,
                    key = key,
                    sector = sector,
                    attackType = AttackType.BRUTE_FORCE,
                    attempts = attempts,
                    elapsedMs = System.currentTimeMillis() - startTime
                )
            }
            onProgress((attempts * 100 / COMMON_KEYS.size).toInt())
        }

        return CrackResult(
            success = false,
            key = null,
            sector = sector,
            attackType = AttackType.BRUTE_FORCE,
            attempts = attempts,
            elapsedMs = System.currentTimeMillis() - startTime
        )
    }

    /**
     * Nested attack - recover unknown key from known key
     */
    fun nestedAttack(
        uid: String,
        knownSector: Int,
        knownKey: String,
        targetSector: Int,
        onProgress: (Int) -> Unit
    ): CrackResult {
        val startTime = System.currentTimeMillis()

        // Step 1: Authenticate to known sector
        crypto1.setLfsr(knownKey.toLong(16))

        // Step 2: Get nonce from target sector
        val nonce = collectNonce(uid, targetSector)

        // Step 3: Analyze nonce to recover key
        val recoveredKey = recoverKeyFromNonce(nonce, uid)

        return if (recoveredKey != null) {
            CrackResult(
                success = true,
                key = recoveredKey,
                sector = targetSector,
                attackType = AttackType.NESTED,
                attempts = 1,
                elapsedMs = System.currentTimeMillis() - startTime
            )
        } else {
            CrackResult(
                success = false,
                key = null,
                sector = targetSector,
                attackType = AttackType.NESTED,
                attempts = 1,
                elapsedMs = System.currentTimeMillis() - startTime
            )
        }
    }

    /**
     * Darkside attack - exploit weak PRNG
     */
    fun darksideAttack(
        uid: String,
        sector: Int,
        onProgress: (Int) -> Unit
    ): CrackResult {
        val startTime = System.currentTimeMillis()

        // Collect multiple nonces
        val nonces = mutableListOf<NonceData>()
        repeat(10) { i ->
            val nonce = collectNonce(uid, sector)
            if (nonce != null) {
                nonces.add(nonce)
            }
            onProgress((i * 100 / 10))
        }

        // Analyze nonces for patterns
        val recoveredKey = analyzeNonces(nonces, uid)

        return if (recoveredKey != null) {
            CrackResult(
                success = true,
                key = recoveredKey,
                sector = sector,
                attackType = AttackType.DARKSIDE,
                attempts = nonces.size.toLong(),
                elapsedMs = System.currentTimeMillis() - startTime
            )
        } else {
            CrackResult(
                success = false,
                key = null,
                sector = sector,
                attackType = AttackType.DARKSIDE,
                attempts = nonces.size.toLong(),
                elapsedMs = System.currentTimeMillis() - startTime
            )
        }
    }

    /**
     * Static nested attack - for Gen3/Gen4 cards with predictable nonces
     */
    fun staticNestedAttack(
        uid: String,
        sector: Int,
        onProgress: (Int) -> Unit
    ): CrackResult {
        val startTime = System.currentTimeMillis()

        // Collect nonce
        val nonce = collectNonce(uid, sector)
        if (nonce == null) {
            return CrackResult(
                success = false,
                key = null,
                sector = sector,
                attackType = AttackType.STATIC_NESTED,
                attempts = 0,
                elapsedMs = System.currentTimeMillis() - startTime
            )
        }

        // For static nested, nonce is predictable
        val predictedKey = predictKeyFromStaticNonce(nonce, uid)

        return CrackResult(
            success = predictedKey != null,
            key = predictedKey,
            sector = sector,
            attackType = AttackType.STATIC_NESTED,
            attempts = 1,
            elapsedMs = System.currentTimeMillis() - startTime
        )
    }

    /**
     * Hardnested attack - cloud-assisted key recovery
     */
    fun hardnestedAttack(
        uid: String,
        sector: Int,
        knownKey: String,
        onProgress: (Int) -> Unit
    ): CrackResult {
        val startTime = System.currentTimeMillis()

        // This would typically call cloud API
        // For now, return failure
        return CrackResult(
            success = false,
            key = null,
            sector = sector,
            attackType = AttackType.HARDNESTED,
            attempts = 0,
            elapsedMs = System.currentTimeMillis() - startTime
        )
    }

    /**
     * Verify if a key is correct for a sector
     */
    private fun verifyKey(uid: String, sector: Int, key: String): Boolean {
        // In real implementation, this would send auth command to device
        return false
    }

    /**
     * Collect nonce from card
     */
    private fun collectNonce(uid: String, sector: Int): NonceData? {
        // In real implementation, this would interact with the card
        return null
    }

    /**
     * Recover key from nonce analysis
     */
    private fun recoverKeyFromNonce(nonce: NonceData?, uid: String): String? {
        if (nonce == null) return null

        // LFSR rollback attack
        crypto1.setLfsr(0)

        // Rollback LFSR by 48 bits
        repeat(48) {
            crypto1.lfsrRollbackBit()
        }

        // Verify by re-encrypting
        val testNt = crypto1.lfsrWord(nonce.nt, true)
        return if (testNt == (nonce.nt xor (nonce.uid xor nonce.nr))) {
            crypto1.getState().toString(16).uppercase()
        } else {
            null
        }
    }

    /**
     * Analyze multiple nonces for patterns
     */
    private fun analyzeNonces(nonces: List<NonceData>, uid: String): String? {
        if (nonces.isEmpty()) return null

        // Look for repeated nonces (weak PRNG)
        val nonceGroups = nonces.groupBy { it.nt }
        val repeatedNonce = nonceGroups.entries.find { it.value.size > 1 }

        if (repeatedNonce != null) {
            // Use repeated nonce to recover key
            return recoverKeyFromNonce(repeatedNonce.value.first(), uid)
        }

        return null
    }

    /**
     * Predict key from static nonce (Gen3/Gen4)
     */
    private fun predictKeyFromStaticNonce(nonce: NonceData, uid: String): String? {
        // For cards with predictable nonces, key can be derived directly
        val predictedKey = (nonce.nt xor nonce.uid).toLong()
        return predictedKey.toString(16).uppercase().padStart(12, '0')
    }

    data class NonceData(
        val uid: Int,
        val nt: Int,
        val nr: Int,
        val ar: Int,
        val at: Int
    )
}
