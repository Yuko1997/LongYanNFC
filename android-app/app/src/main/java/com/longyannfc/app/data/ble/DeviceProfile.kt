package com.longyannfc.app.data.ble

import java.util.UUID

enum class DeviceType(val id: Int) {
    READER(0),
    PANDA(1),
    GP_GEN3(2),
    GP_GEN3_PRO(3),
    NFC_PRO(-1),
    PCR_PRO(-2),
    NFC_TOOLS(-3),
    SUPER_CARD(-200),
    NRF_DFU(-201),
    UNKNOWN(Int.MIN_VALUE);

    val isEmulator: Boolean get() = id > 0
}

data class BleProfile(
    val service: UUID,
    val write: UUID,
    val notify: UUID
)

object BleUuids {
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    val LONGYAN = BleProfile(
        service = UUID.fromString("0000ff00-0000-1000-8000-00805f9b34fb"),
        write = UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb"),
        notify = UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb")
    )

    val NORDIC_UART = BleProfile(
        service = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e"),
        write = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e"),
        notify = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
    )

    val NORDIC_DFU = BleProfile(
        service = UUID.fromString("0000fe59-0000-1000-8000-00805f9b34fb"),
        write = UUID.fromString("8ec90002-f315-4f60-9fb8-838830daea50"),
        notify = UUID.fromString("8ec90001-f315-4f60-9fb8-838830daea50")
    )

    val ALL = listOf(LONGYAN, NORDIC_UART, NORDIC_DFU)

    fun filterDevice(name: String?): Pair<Boolean, DeviceType> {
        val n = name?.trim().orEmpty()
        if (n.isEmpty() || n.equals("Unnamed", ignoreCase = true)) {
            return false to DeviceType.UNKNOWN
        }
        val lower = n.lowercase()
        return when {
            n.equals("DfuTarg", ignoreCase = true) || lower.contains("dfu") ->
                true to DeviceType.NRF_DFU
            lower.contains("supercard") -> true to DeviceType.SUPER_CARD
            n.equals("LongYan_V1.0", ignoreCase = true) ||
                n.equals("LongYan_V1.0(BLE)", ignoreCase = true) ||
                lower.contains("longyan") ||
                lower.contains("panda") ||
                lower.contains("chameleon") ||
                lower.contains("pcr") -> true to DeviceType.PANDA
            else -> false to DeviceType.UNKNOWN
        }
    }

    fun uuidEquals(a: UUID?, b: UUID): Boolean {
        return a != null && a.toString().equals(b.toString(), ignoreCase = true)
    }
}

data class BleDevice(
    val address: String,
    val name: String,
    val rssi: Int,
    val type: DeviceType
)
