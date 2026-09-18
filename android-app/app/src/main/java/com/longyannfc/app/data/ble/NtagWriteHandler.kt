package com.longyannfc.app.data.ble

import com.longyannfc.app.utils.AmiiboUtils

/**
 * NTAG215 write handler for Amiibo data.
 * Handles the complete write sequence including password protection.
 */
class NtagWriteHandler(private val bleManager: BleManager) {

    companion object {
        // NTAG215 constants
        const val NTAG215_SIZE = 540
        const val NTAG213_SIZE = 192
        const val NTAG216_SIZE = 231

        // Page size for NTAG series
        const val PAGE_SIZE = 4
        const val PAGES_PER_READ = 4 // Read 4 pages at a time

        // Special pages
        const val UID_PAGE = 0
        const val LOCK_PAGE = 2
        const val CAPABILITY_CONTAINER_PAGE = 3
        const val PASSWORD_PAGE = 0xE9
        const val PACK_PAGE = 0xEA
        const val DYNAMIC_LOCK_PAGE = 0xE4
        const val CONFIG_PAGE_1 = 0xE5
        const val CONFIG_PAGE_2 = 0xE6
        const val SIGNATURE_PAGE = 0xAC

        // Write commands
        const val CMD_READ = 0x30
        const val CMD_WRITE = 0xA2
        const val CMD_FAST_READ = 0x3A
        const val CMD_COMP_WRITE = 0xA0
        const val CMD_AUTH0 = 0x1B
        const val CMD_PWD_AUTH = 0x1B
        const val CMD_VERSION = 0x60
    }

    enum class WriteStep {
        PREPARE_DATA,
        WRITE_HEADER,
        WRITE_DATA,
        WRITE_PASSWORD,
        WRITE_LOCK,
        VERIFY,
        COMPLETE
    }

    data class WriteProgress(
        val step: WriteStep,
        val progress: Int,
        val message: String
    )

    /**
     * Write amiibo data to NTAG215
     */
    suspend fun writeAmiibo(
        data: ByteArray,
        onProgress: (WriteProgress) -> Unit
    ): Boolean {
        if (data.size < AmiiboUtils.AMIIBO_SIZE) {
            onProgress(WriteProgress(WriteStep.PREPARE_DATA, 0, "Data too small"))
            return false
        }

        onProgress(WriteProgress(WriteStep.PREPARE_DATA, 10, "Preparing data..."))

        // Step 1: Prepare data with password and lock bytes
        val preparedData = AmiiboUtils.prepareNtagWrite(data)

        onProgress(WriteProgress(WriteStep.WRITE_HEADER, 20, "Writing header..."))

        // Step 2: Write header (pages 0-3)
        // Note: Page 0-1 contains UID, usually read-only
        // Page 2 contains lock bytes
        // Page 3 contains Capability Container

        onProgress(WriteProgress(WriteStep.WRITE_DATA, 30, "Writing data..."))

        // Step 3: Write main data (pages 4 onwards)
        // NTAG215 has pages 0-143 (576 bytes)
        val dataPages = 140 // Pages 4-143 for user data
        for (page in 4 until dataPages + 4) {
            val offset = page * PAGE_SIZE
            if (offset + PAGE_SIZE > preparedData.size) break

            val pageData = preparedData.copyOfRange(offset, offset + PAGE_SIZE)
            writePage(page, pageData)

            val progress = 30 + (page * 50 / dataPages)
            onProgress(WriteProgress(WriteStep.WRITE_DATA, progress, "Writing page $page..."))

            // Small delay to prevent buffer overflow
            kotlinx.coroutines.delay(10)
        }

        onProgress(WriteProgress(WriteStep.WRITE_PASSWORD, 80, "Setting password..."))

        // Step 4: Write password
        val password = AmiiboUtils.calculatePassword(data)
        writePage(PASSWORD_PAGE, password)

        // Step 5: Write PACK
        val pack = AmiiboUtils.calculatePack()
        writePage(PACK_PAGE, pack)

        onProgress(WriteProgress(WriteStep.WRITE_LOCK, 85, "Setting lock bytes..."))

        // Step 6: Write lock bytes (protect pages)
        val lockBytes = byteArrayOf(0x0F, 0xE0.toByte(), 0x00, 0x00)
        writePage(DYNAMIC_LOCK_PAGE, lockBytes)

        onProgress(WriteProgress(WriteStep.VERIFY, 90, "Verifying..."))

        // Step 7: Verify write
        val verified = verifyWrite(data)

        if (verified) {
            onProgress(WriteProgress(WriteStep.COMPLETE, 100, "Write complete!"))
        } else {
            onProgress(WriteProgress(WriteStep.COMPLETE, 100, "Write completed with warnings"))
        }

        return verified
    }

    /**
     * Write a single page to NTAG
     */
    private fun writePage(page: Int, data: ByteArray): Boolean {
        if (data.size != PAGE_SIZE) return false

        // Build write command: [CMD_WRITE, page, data[0], data[1], data[2], data[3]]
        val command = ByteArray(6)
        command[0] = CMD_WRITE.toByte()
        command[1] = page.toByte()
        System.arraycopy(data, 0, command, 2, PAGE_SIZE)

        bleManager.sendCommand(command)
        return true
    }

    /**
     * Verify written data by reading it back
     */
    private suspend fun verifyWrite(original: ByteArray): Boolean {
        // Read back key pages and compare
        val pagesToCheck = intArrayOf(4, 5, 6, 7, PASSWORD_PAGE, PACK_PAGE, DYNAMIC_LOCK_PAGE)

        for (page in pagesToCheck) {
            val readData = readPage(page)
            if (readData == null || readData.size != PAGE_SIZE) {
                return false
            }

            val offset = page * PAGE_SIZE
            for (i in 0 until PAGE_SIZE) {
                if (offset + i < original.size && readData[i] != original[offset + i]) {
                    return false
                }
            }
            kotlinx.coroutines.delay(5)
        }
        return true
    }

    /**
     * Read a single page from NTAG
     */
    private fun readPage(page: Int): ByteArray? {
        val command = byteArrayOf(CMD_READ.toByte(), page.toByte())
        bleManager.sendCommand(command)
        // In real implementation, wait for response
        return null
    }

    /**
     * Get NTAG version info
     */
    fun getVersion(): ByteArray? {
        val command = byteArrayOf(CMD_VERSION.toByte())
        bleManager.sendCommand(command)
        return null
    }

    /**
     * Authenticate with password
     */
    fun authenticate(password: ByteArray): Boolean {
        val command = ByteArray(5)
        command[0] = CMD_PWD_AUTH.toByte()
        System.arraycopy(password, 0, command, 1, 4)
        bleManager.sendCommand(command)
        return true
    }
}
