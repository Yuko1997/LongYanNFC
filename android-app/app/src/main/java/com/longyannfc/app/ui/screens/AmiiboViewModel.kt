package com.longyannfc.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.longyannfc.app.data.remote.AmiiboInfo
import com.longyannfc.app.data.repository.CardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.util.concurrent.CancellationException
import javax.inject.Inject

@HiltViewModel
class AmiiboViewModel @Inject constructor(
    private val repository: CardRepository
) : ViewModel() {

    private val _amiiboList = MutableStateFlow<List<AmiiboInfo>>(emptyList())
    val amiiboList: StateFlow<List<AmiiboInfo>> = _amiiboList

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _downloading = MutableStateFlow<Set<String>>(emptySet())
    val downloading: StateFlow<Set<String>> = _downloading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    fun loadAmiiboList() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _amiiboList.value = repository.downloadAmiiboList()
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                Timber.e(e, "Failed to load amiibo list")
                _errorMessage.value = "读取本地 Amiibo 列表失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun downloadAmiibo(amiibo: AmiiboInfo, cacheDir: File) {
        viewModelScope.launch {
            val id = amiibo.id.ifBlank { amiibo.name }
            _downloading.value = _downloading.value + id
            try {
                val bytes = repository.downloadAmiiboBin(amiibo.downloadUrl)
                File(cacheDir, "$id.bin").writeBytes(bytes)
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                Timber.e(e, "Download failed for $id")
                _errorMessage.value = e.message ?: "本地 Bin 文件读取失败"
            } finally {
                _downloading.value = _downloading.value - id
            }
        }
    }

    fun writeAmiibo(amiiboId: String, cacheDir: File) {
        viewModelScope.launch {
            try {
                val file = File(cacheDir, "$amiiboId.bin")
                if (!file.exists()) {
                    _errorMessage.value = "Amiibo file not found. Please download first."
                    return@launch
                }
                repository.detectCard()
                val result = repository.writeAmiibo(file.readBytes())
                if (!result.authResult) {
                    _errorMessage.value = result.message
                }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                Timber.e(e, "Write failed")
                _errorMessage.value = e.message ?: "写卡失败"
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
