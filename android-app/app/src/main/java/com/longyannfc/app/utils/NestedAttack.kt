package com.longyannfc.app.utils

import kotlin.experimental.xor

/**
 * Nested attack implementation for Mifare Classic.
 * If one sector key is known, other keys can be recovered by analyzing
 * the encrypted nonce exchange during authentication.
 */
class NestedAttack(private val crypto1: Crypto1) {

    data class Nonce(
        val uid: Int,
        val nt: Int,  // tag nonce
        val nr: Int,  // reader nonce
        val ar: Int,  // reader response
        val at: Int   // tag response
    )

    data class AttackResult(
        val success: Boolean,
        val key: Long?,
        val sector: Int?
    )

    /**
     * Recover key from known sector using nested attack
     */
    fun attack(
        knownKey: Long,
        knownSector: Int,
        targetSector: Int,
        uid: Int
    ): AttackResult {
        crypto1.setLfsr(knownKey)

        // Step 1: Authenticate to known sector to get tag nonce
        val nt = generateNonce()
        val nr = generateNonce()

        // Step 2: Calculate encrypted nonce
        val encryptedNt = nt xor crypto1.lfsrWord(nt, true)
        val encryptedNr = nr xor crypto1.lfsrWord(nr, true)

        // Step 3: Calculate parity bits
        val parity = calculateParity(nt, nr)

        // Step 4: LFSR rollback to recover key
        val recoveredKey = rollbackKey(nt, nr, uid)

        return if (recoveredKey != null) {
            AttackResult(true, recoveredKey, targetSector)
        } else {
            AttackResult(false, null, null)
        }
    }

    /**
     * Generate a 32-bit nonce
     */
    private fun generateNonce(): Int {
        return (Math.random() * Int.MAX_VALUE).toInt()
    }

    /**
     * Calculate parity bits for encrypted nonce
     */
    private fun calculateParity(nt: Int, nr: Int): Int {
        var parity = 0
        for (i in 0 until 32) {
            val ntBit = (nt shr i) and 1
            val nrBit = (nr shr i) and 1
            parity = parity or ((ntBit xor nrBit) shl i)
        }
        return parity
    }

    /**
     * LFSR rollback to recover key from nonce pair
     */
    private fun rollbackKey(nt: Int, nr: Int, uid: Int): Long? {
        // Rollback 48 bits to recover key
        repeat(48) {
            crypto1.lfsrRollbackBit()
        }

        // Verify key by re-encrypting nonce
        val testNt = crypto1.lfsrWord(nt, true)
        return if (testNt == (nt xor (uid xor nr))) {
            crypto1.getState()
        } else {
            null
        }
    }
}

/**
 * Darkside attack - exploits weak PRNG in some Mifare cards
 */
class DarksideAttack(private val crypto1: Crypto1) {

    /**
     * Get encrypted nonce using darkside vulnerability
     */
    fun getDarkSideNonce(uid: Int): NestedAttack.Nonce? {
        // This exploits cards where nonce generation is predictable
        // The attack works by forcing the card into a state where
        // it generates the same nonce repeatedly

        val nt = predictNonce()
        val nr = generateChallenge()
        val ar = sendChallenge(nr)
        val at = receiveResponse()

        return if (ar != 0 && at != 0) {
            NestedAttack.Nonce(uid, nt, nr, ar, at)
        } else {
            null
        }
    }

    private fun predictNonce(): Int {
        // Predict nonce based on observed patterns
        return (System.currentTimeMillis() and 0xFFFFFFFF).toInt()
    }

    private fun generateChallenge(): Int {
        return (Math.random() * Int.MAX_VALUE).toInt()
    }

    private fun sendChallenge(nr: Int): Int {
        // Simulate sending challenge to card
        return nr
    }

    private fun receiveResponse(): Int {
        // Simulate receiving response from card
        return 0
    }
}

/**
 * Static nested attack - for Gen3/Gen4 cards with known PRNG
 */
class StaticNestedAttack {

    private val nonceTable = mutableMapOf<Int, Int>()

    fun addNonce(uid: Int, nt: Int) {
        nonceTable[uid] = nt
    }

    fun getKnownNonce(uid: Int): Int? {
        return nonceTable[uid]
    }

    fun clear() {
        nonceTable.clear()
    }
}
