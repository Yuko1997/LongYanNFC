package com.longyannfc.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.longyannfc.app.data.ble.BleDevice
import com.longyannfc.app.data.ble.BleManager
import com.longyannfc.app.data.ble.MifareInfo
import com.longyannfc.app.data.ble.WriteDumpResult
import com.longyannfc.app.data.repository.CardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.CancellationException
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val bleManager: BleManager,
    private val repository: CardRepository
) : ViewModel() {

    val connectionState: StateFlow<BleManager.ConnectionState> = bleManager.connectionState
    val devices: StateFlow<List<BleDevice>> = bleManager.devices
    val lastConnectedAddress: StateFlow<String> = bleManager.lastConnectedAddress

    private val _isDetecting = MutableStateFlow(false)
    val isDetecting: StateFlow<Boolean> = _isDetecting

    private val _isReading = MutableStateFlow(false)
    val isReading: StateFlow<Boolean> = _isReading

    private val _isWriting = MutableStateFlow(false)
    val isWriting: StateFlow<Boolean> = _isWriting

    private val _progress = MutableStateFlow("")
    val progress: StateFlow<String> = _progress

    private val _cardInfo = MutableStateFlow<MifareInfo?>(null)
    val cardInfo: StateFlow<MifareInfo?> = _cardInfo

    private val _dump = MutableStateFlow<ByteArray>(ByteArray(0))
    val dump: StateFlow<ByteArray> = _dump

    private val _lastDumpId = MutableStateFlow<Long?>(null)
    val lastDumpId: StateFlow<Long?> = _lastDumpId

    private val _writeResult = MutableStateFlow<WriteDumpResult?>(null)
    val writeResult: StateFlow<WriteDumpResult?> = _writeResult

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    fun startScan() {
        bleManager.startScan()
    }

    fun stopScan() {
        bleManager.stopScan()
    }

    fun connect(address: String) {
        bleManager.connect(address)
    }

    fun disconnect() {
        bleManager.disconnect()
    }

    fun refreshDeviceList() {
        bleManager.refreshDeviceList()
    }

    fun detectCard() {
        viewModelScope.launch {
            _isDetecting.value = true
            _errorMessage.value = null
            _cardInfo.value = null
            try {
                _cardInfo.value = repository.detectCard()
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                Timber.e(e, "Card detection failed")
                _errorMessage.value = e.message ?: "选卡失败，请将卡片放置在天线中心位置"
            } finally {
                _isDetecting.value = false
            }
        }
    }

    fun readCard() {
        readCard(null)
    }

    fun readCard(onSaved: ((Long) -> Unit)?) {
        viewModelScope.launch {
            _isReading.value = true
            try {
                if (_cardInfo.value == null) {
                    _cardInfo.value = repository.detectCard()
                }
                val id = repository.readAndSaveCard { msg, pct ->
                    if (!bleManager.isConnected()) throw IllegalStateException("读卡中断，设备已断开")
                    _progress.value = "$msg $pct%"
                }
                _lastDumpId.value = id
                onSaved?.invoke(id)
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                Timber.e(e, "Read failed")
                _errorMessage.value = e.message ?: "读卡失败"
            } finally {
                _isReading.value = false
                _progress.value = ""
            }
        }
    }

    fun detectAndShowDetails(onNavigate: (MifareInfo) -> Unit) {
        viewModelScope.launch {
            _isDetecting.value = true
            _errorMessage.value = null
            try {
                if (_cardInfo.value == null) {
                    _cardInfo.value = repository.detectCard()
                }
                _cardInfo.value?.let { onNavigate(it) }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                Timber.e(e, "Card detection failed")
                _errorMessage.value = e.message ?: "选卡失败，请将卡片放置在天线中心位置"
            } finally {
                _isDetecting.value = false
            }
        }
    }

    fun writeCard(data: ByteArray, onComplete: ((WriteDumpResult) -> Unit)? = null) {
        viewModelScope.launch {
            _isWriting.value = true
            _writeResult.value = null
            _errorMessage.value = null
            try {
                if (!bleManager.isConnected()) throw IllegalStateException("请先连接读卡器")
                val result = repository.writeCard(data) { msg, pct ->
                    _progress.value = "$msg $pct%"
                }
                _writeResult.value = result
                onComplete?.invoke(result)
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                Timber.e(e, "Write failed")
                _errorMessage.value = e.message ?: "写卡失败"
            } finally {
                _isWriting.value = false
                _progress.value = ""
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearWriteResult() {
        _writeResult.value = null
    }
}
