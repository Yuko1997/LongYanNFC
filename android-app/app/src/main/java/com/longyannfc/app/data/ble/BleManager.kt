package com.longyannfc.app.data.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val prefs = context.getSharedPreferences("ble_prefs", Context.MODE_PRIVATE)

    private var bluetoothGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null
    private var activeProfile: BleProfile = BleUuids.LONGYAN
    private var mtuPayload = 20
    private var writeNoResponse = true

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _devices = MutableStateFlow<List<BleDevice>>(emptyList())
    val devices: StateFlow<List<BleDevice>> = _devices

    private val _lastConnectedAddress = MutableStateFlow(prefs.getString(KEY_LAST_ADDRESS, "") ?: "")
    val lastConnectedAddress: StateFlow<String> = _lastConnectedAddress

    private val _deviceType = MutableStateFlow(DeviceType.UNKNOWN)
    val deviceType: StateFlow<DeviceType> = _deviceType

    private val _incoming = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    val incoming: SharedFlow<ByteArray> = _incoming

    private val writeMutex = Mutex()
    private var writeAck: CompletableDeferred<Boolean>? = null
    private val foundByAddress = ConcurrentHashMap<String, BleDevice>()
    private var publishJob: kotlinx.coroutines.Job? = null
    private var lastPublishTime = 0L
    private val deviceDebounceMs = 350L

    sealed class ConnectionState {
        data object Disconnected : ConnectionState()
        data object Scanning : ConnectionState()
        data object Connecting : ConnectionState()
        data class Connected(val name: String, val address: String, val type: DeviceType) : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            addScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach(::addScanResult)
        }

        override fun onScanFailed(errorCode: Int) {
            Timber.e("BLE scan failed: $errorCode")
            _connectionState.value = ConnectionState.Error("扫描失败: $errorCode")
        }
    }

    @SuppressLint("MissingPermission")
    private fun addScanResult(result: ScanResult) {
        if (result.rssi == 127) return
        val device = result.device
        val name = device.name ?: result.scanRecord?.deviceName
        if (name.isNullOrBlank()) return
        val (matched, type) = BleUuids.filterDevice(name)
        val previous = foundByAddress[device.address]
        // RSSI 滑动平均，减少信号波动导致的列表跳动
        val smoothedRssi = if (previous != null) {
            ((previous.rssi * 3 + result.rssi) / 4)
        } else {
            result.rssi
        }
        val item = BleDevice(
            address = device.address,
            name = name,
            rssi = smoothedRssi,
            type = if (matched) type else DeviceType.UNKNOWN
        )
        if (previous == item) return
        foundByAddress[device.address] = item
        // 首个设备立即发布；后续用节流（每350ms最多发布一次），确保扫描期间持续更新列表
        val now = System.currentTimeMillis()
        if (_devices.value.isEmpty()) {
            lastPublishTime = now
            publishDeviceList()
        } else if (now - lastPublishTime >= deviceDebounceMs) {
            lastPublishTime = now
            publishDeviceList()
        } else if (publishJob?.isActive != true) {
            // 安排一次延迟发布，确保未达节流间隔的设备也能在短时间内显示
            publishJob = scope.launch {
                delay(deviceDebounceMs - (now - lastPublishTime))
                lastPublishTime = System.currentTimeMillis()
                publishDeviceList()
            }
        }
        Timber.d("Found device: $name ${device.address} rssi=${result.rssi} smoothed=$smoothedRssi matched=$matched")
    }

    private fun publishDeviceList() {
        val lastAddr = _lastConnectedAddress.value
        _devices.value = foundByAddress.values.sortedWith(
            compareByDescending<BleDevice> { it.address == lastAddr }
                .thenByDescending { it.type != DeviceType.UNKNOWN }
                .thenByDescending { it.rssi }
        )
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Timber.d("GATT connected, discovering services")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        gatt.requestMtu(170)
                    }
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Timber.d("GATT disconnected")
                    cleanupGatt()
                    _connectionState.value = ConnectionState.Disconnected
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                mtuPayload = (mtu - 3).coerceAtLeast(20)
                Timber.d("MTU=$mtu payload=$mtuPayload")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _connectionState.value = ConnectionState.Error("服务发现失败")
                gatt.disconnect()
                return
            }
            val profile = resolveProfile(gatt) ?: run {
                _connectionState.value = ConnectionState.Error("当前的蓝牙设备不合法")
                gatt.disconnect()
                return
            }
            activeProfile = profile
            val service = gatt.getService(profile.service)
            writeCharacteristic = service?.getCharacteristic(profile.write)
            notifyCharacteristic = service?.getCharacteristic(profile.notify)
            val notify = notifyCharacteristic
            if (writeCharacteristic == null || notify == null) {
                _connectionState.value = ConnectionState.Error("特征值缺失")
                gatt.disconnect()
                return
            }
            writeNoResponse =
                (writeCharacteristic!!.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
            gatt.setCharacteristicNotification(notify, true)
            val descriptor = notify.getDescriptor(BleUuids.CCCD)
            if (descriptor != null) {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
            } else {
                markConnected(gatt)
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                markConnected(gatt)
            } else {
                _connectionState.value = ConnectionState.Error("开启通知失败")
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val value = characteristic.value ?: return
            Timber.d("BLE RX ${value.size} bytes")
            _incoming.tryEmit(value)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            writeAck?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }
    }

    private fun resolveProfile(gatt: BluetoothGatt): BleProfile? {
        for (profile in BleUuids.ALL) {
            val service = gatt.getService(profile.service) ?: continue
            val write = service.getCharacteristic(profile.write)
            val notify = service.getCharacteristic(profile.notify)
            val writeOk = write != null && (
                (write.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
                    (write.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
                )
            val notifyOk = notify != null && (
                (notify.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 ||
                    (notify.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                )
            if (writeOk && notifyOk) return profile
        }
        return null
    }

    @SuppressLint("MissingPermission")
    private fun markConnected(gatt: BluetoothGatt) {
        val name = gatt.device.name ?: "LongYan NFC"
        val address = gatt.device.address
        val type = BleUuids.filterDevice(name).second
        _deviceType.value = type
        _connectionState.value = ConnectionState.Connected(name, address, type)
        _lastConnectedAddress.value = address
        prefs.edit().putString(KEY_LAST_ADDRESS, address).apply()
    }

    private fun hasScanPermission(): Boolean {
        val needed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return needed.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            _connectionState.value = ConnectionState.Error("设备不支持蓝牙")
            return
        }
        if (!adapter.isEnabled) {
            _connectionState.value = ConnectionState.Error("请先打开蓝牙")
            return
        }
        if (!hasScanPermission()) {
            _connectionState.value = ConnectionState.Error("缺少蓝牙扫描权限")
            return
        }
        foundByAddress.clear()
        _devices.value = emptyList()
        _connectionState.value = ConnectionState.Scanning
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()
        adapter.bluetoothLeScanner?.startScan(null, settings, scanCallback)
            ?: run { _connectionState.value = ConnectionState.Error("无法启动 BLE 扫描") }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        if (_connectionState.value is ConnectionState.Scanning) {
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        val adapter = bluetoothAdapter ?: run {
            _connectionState.value = ConnectionState.Error("蓝牙不可用")
            return
        }
        stopScan()
        val device = adapter.getRemoteDevice(address)
        _connectionState.value = ConnectionState.Connecting
        bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        cleanupGatt()
        _connectionState.value = ConnectionState.Disconnected
    }

    @SuppressLint("MissingPermission")
    private fun cleanupGatt() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        writeCharacteristic = null
        notifyCharacteristic = null
    }

    fun isConnected(): Boolean = _connectionState.value is ConnectionState.Connected

    fun refreshDeviceList() {
        publishJob?.cancel()
        publishDeviceList()
    }

    suspend fun writeRaw(data: ByteArray) {
        writeMutex.withLock {
            var offset = 0
            while (offset < data.size) {
                val end = minOf(offset + mtuPayload, data.size)
                val chunk = data.copyOfRange(offset, end)
                writeChunk(chunk)
                offset = end
                delay(1)
            }
            delay(40)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun writeChunk(chunk: ByteArray) {
        val char = writeCharacteristic ?: throw IllegalStateException("设备未连接")
        val gatt = bluetoothGatt ?: throw IllegalStateException("设备未连接")
        char.value = chunk
        char.writeType = if (writeNoResponse) {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }
        if (!writeNoResponse) {
            writeAck = CompletableDeferred()
        }
        val ok = gatt.writeCharacteristic(char)
        if (!ok) throw IllegalStateException("BLE 写入失败")
        if (!writeNoResponse) {
            val success = writeAck?.await() == true
            writeAck = null
            if (!success) throw IllegalStateException("BLE 写入被拒绝")
        }
    }

    fun sendCommand(command: ByteArray) {
        scope.launch { runCatching { writeRaw(command) } }
    }

    fun sendTextCommand(text: String) {
        scope.launch { runCatching { writeRaw(text.toByteArray()) } }
    }

    companion object {
        private const val KEY_LAST_ADDRESS = "last_connected_address"
    }
}
