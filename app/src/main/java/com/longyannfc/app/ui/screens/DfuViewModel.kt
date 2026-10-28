package com.longyannfc.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.longyannfc.app.data.ble.BleManager
import com.longyannfc.app.data.ble.DfuHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.CancellationException
import javax.inject.Inject

/**
 * DFU 固件更新。使用仓库中已有的 [DfuHandler] 完成 Nordic Secure DFU 上传。
 */
@HiltViewModel
class DfuViewModel @Inject constructor(
    bleManager: BleManager
) : ViewModel() {

    private val dfuHandler = DfuHandler(bleManager)

    private val _isUpdating = MutableStateFlow(false)
    val isUpdating: StateFlow<Boolean> = _isUpdating

    private val _progress = MutableStateFlow(0)
    val progress: StateFlow<Int> = _progress

    private val _selectedFileName = MutableStateFlow("")
    val selectedFileName: StateFlow<String> = _selectedFileName

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    fun onDeviceStateChanged(connected: Boolean) {
        _isConnected.value = connected
    }

    /** 解析用户选择的 DFU 包，失败时给出明确原因 */
    fun onFirmwareSelected(fileName: String, bytes: ByteArray) {
        _selectedFileName.value = fileName
        _errorMessage.value = null
        val image = dfuHandler.loadDfuFile(bytes)
        if (image == null) {
            _errorMessage.value = "无法解析固件包，请选择 Nordic DFU 格式的 .zip 文件"
        }
    }

    fun startUpdate(bytes: ByteArray) {
        if (_isUpdating.value) return
        viewModelScope.launch {
            _isUpdating.value = true
            _errorMessage.value = null
            _progress.value = 0
            try {
                val image = dfuHandler.loadDfuFile(bytes)
                    ?: throw IllegalStateException("无法解析固件包，请选择 Nordic DFU 格式的 .zip 文件")
                withContext(Dispatchers.IO) {
                    dfuHandler.startDfu(image) { pct -> _progress.value = pct }
                }
                _progress.value = 100
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                Timber.e(e, "DFU update failed")
                _errorMessage.value = e.message ?: "固件更新失败"
            } finally {
                _isUpdating.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}

