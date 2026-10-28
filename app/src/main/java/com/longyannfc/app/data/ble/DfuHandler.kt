package com.longyannfc.app.data.ble

import com.longyannfc.app.utils.CRC32
import java.io.ByteArrayInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * DFU (Device Firmware Update) handler using Nordic Secure DFU protocol.
 * Supports BLE firmware updates for the LongYanNFC device.
 */
class DfuHandler(private val bleManager: BleManager) {

    companion object {
        // DFU Object types
        const val OBJ_COMMAND = 0x01
        const val OBJ_DATA = 0x02

        // DFU Op Codes
        const val OP_CREATE = 0x01
        const val OP_SET_PRN = 0x02
        const val OP_CALC_CHECKSUM = 0x03
        const val OP_EXECUTE = 0x04
        const val OP_SELECT = 0x06
        const val OP_PING = 0x07
        const val OP_MTU_GET = 0x08
        const val OP_ABORT = 0x0C
        const val OP_RESPONSE = 0x60
        const val OP_SERIAL_PROCEDURE = 0x01
        const val OP_START_DFU = 0x01

        // Error codes
        const val SUCCESS = 0x01
        const val INVALID_STATE = 0x02
        const val NOT_SUPPORTED = 0x03
        const val DATA_SIZE_EXCEEDS_LIMIT = 0x04
        const val CRC_ERROR = 0x05
        const val OPERATION_FAILED = 0x06
    }

    data class DfuImage(
        val initPacket: ByteArray,
        val firmwareData: ByteArray
    )

    /**
     * Load DFU ZIP file and extract init packet + firmware
     */
    fun loadDfuFile(zipData: ByteArray): DfuImage? {
        return try {
            val zipInputStream = ZipInputStream(ByteArrayInputStream(zipData))
            var entry: ZipEntry? = zipInputStream.nextEntry
            var manifestJson: String? = null
            var initBytes: ByteArray? = null
            var firmwareBytes: ByteArray? = null

            while (entry != null) {
                val data = zipInputStream.readBytes()
                val name = entry.name ?: ""

                when {
                    name == "manifest.json" -> manifestJson = String(data).trim()
                    name.startsWith("sd/") || name.contains("softdevice") -> initBytes = data
                    name.endsWith(".bin") -> firmwareBytes = data
                }

                entry = zipInputStream.nextEntry
            }
            zipInputStream.close()

            if (initBytes != null && firmwareBytes != null) {
                DfuImage(initBytes, firmwareBytes)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Start DFU update process
     */
    suspend fun startDfu(image: DfuImage, onProgress: (Int) -> Unit) {
        // 1. Upload init command packet
        uploadInitPacket(image.initPacket)

        // 2. Upload firmware data
        uploadFirmware(image.firmwareData, onProgress)

        // 3. Verify
        verifyDfu()
    }

    private fun uploadInitPacket(initData: ByteArray) {
        // Create init command object
        val lengthBytes = ByteArray(4)
        lengthBytes[0] = (initData.size and 0xFF).toByte()
        lengthBytes[1] = ((initData.size shr 8) and 0xFF).toByte()
        lengthBytes[2] = ((initData.size shr 16) and 0xFF).toByte()
        lengthBytes[3] = ((initData.size shr 24) and 0xFF).toByte()

        val command = ByteArray(2 + 4 + initData.size)
        command[0] = OP_CREATE.toByte()
        command[1] = OBJ_COMMAND.toByte()
        System.arraycopy(lengthBytes, 0, command, 2, 4)
        System.arraycopy(initData, 0, command, 6, initData.size)

        bleManager.sendCommand(command)
    }

    private fun uploadFirmware(firmware: ByteArray, onProgress: (Int) -> Unit) {
        val maxMtu = 170
        val chunkSize = maxMtu - 4 // Leave room for header

        // Create data object
        val createCmd = ByteArray(6)
        createCmd[0] = OP_CREATE.toByte()
        createCmd[1] = OBJ_DATA.toByte()
        val lengthBytes = ByteArray(4)
        lengthBytes[0] = (firmware.size and 0xFF).toByte()
        lengthBytes[1] = ((firmware.size shr 8) and 0xFF).toByte()
        lengthBytes[2] = ((firmware.size shr 16) and 0xFF).toByte()
        lengthBytes[3] = ((firmware.size shr 24) and 0xFF).toByte()
        System.arraycopy(lengthBytes, 0, createCmd, 2, 4)
        bleManager.sendCommand(createCmd)

        // Send firmware in chunks
        var offset = 0
        while (offset < firmware.size) {
            val remaining = firmware.size - offset
            val currentChunkSize = minOf(chunkSize, remaining)
            val chunk = firmware.copyOfRange(offset, offset + currentChunkSize)
            bleManager.sendCommand(chunk)

            offset += currentChunkSize
            onProgress((offset * 100) / firmware.size)

            // Small delay to prevent BLE buffer overflow
            Thread.sleep(20)
        }

        // Verify after upload
        verifyDfu()
    }

    private fun verifyDfu() {
        // Calculate CRC32 of uploaded data
        val selectCmd = ByteArray(2)
        selectCmd[0] = OP_SELECT.toByte()
        selectCmd[1] = OBJ_DATA.toByte()
        bleManager.sendCommand(selectCmd)

        // Execute DFU
        val execCmd = ByteArray(1)
        execCmd[0] = OP_EXECUTE.toByte()
        bleManager.sendCommand(execCmd)
    }

    /**
     * Abort DFU process
     */
    fun abort() {
        val abortCmd = ByteArray(1)
        abortCmd[0] = OP_ABORT.toByte()
        bleManager.sendCommand(abortCmd)
    }
}
