package com.longyannfc.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.longyannfc.app.data.repository.CardRepository
import com.longyannfc.app.data.repository.DetectionResult
import com.longyannfc.app.data.repository.DistanceResult
import com.longyannfc.app.data.repository.NestedAttackResult
import com.longyannfc.app.data.repository.SmartDetectionPhase
import com.longyannfc.app.data.repository.SmartDetectionResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.CancellationException
import javax.inject.Inject

@HiltViewModel
class DetectionViewModel @Inject constructor(
    private val repository: CardRepository
) : ViewModel() {
    private val _isDetecting = MutableStateFlow(false)
    val isDetecting: StateFlow<Boolean> = _isDetecting

    private val _progress = MutableStateFlow("")
    val progress: StateFlow<String> = _progress

    private val _progressPercent = MutableStateFlow(0)
    val progressPercent: StateFlow<Int> = _progressPercent

    private val _phase = MutableStateFlow(SmartDetectionPhase.IDLE)
    val phase: StateFlow<SmartDetectionPhase> = _phase

    private val _phaseLabel = MutableStateFlow("")
    val phaseLabel: StateFlow<String> = _phaseLabel

    private val _smartResult = MutableStateFlow<SmartDetectionResult?>(null)
    val smartResult: StateFlow<SmartDetectionResult?> = _smartResult

    private val _result = MutableStateFlow<DetectionResult?>(null)
    val result: StateFlow<DetectionResult?> = _result

    private val _distanceResult = MutableStateFlow<DistanceResult?>(null)
    val distanceResult: StateFlow<DistanceResult?> = _distanceResult

    private val _nestedResult = MutableStateFlow<NestedAttackResult?>(null)
    val nestedResult: StateFlow<NestedAttackResult?> = _nestedResult

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    /**
     * 统一智能侦测入口
     * 自动完成: 检测卡片 → 扫描密钥 → 读卡 → 安全分析 → (必要时)嵌套攻击
     */
    fun startSmartDetection() {
        viewModelScope.launch {
            _isDetecting.value = true
            _errorMessage.value = null
            _smartResult.value = null
            _result.value = null
            _distanceResult.value = null
            _nestedResult.value = null
            _phase.value = SmartDetectionPhase.IDLE
            _phaseLabel.value = "准备开始..."
            _progressPercent.value = 0
            try {
                _smartResult.value = repository.runSmartDetection(
                    onPhaseChange = { phase, label ->
                        _phase.value = phase
                        _phaseLabel.value = label
                    },
                    onProgress = { msg, pct ->
                        _progress.value = msg
                        _progressPercent.value = pct
                    }
                )
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                Timber.e(e, "Smart detection failed")
                _errorMessage.value = e.message ?: "侦测失败"
            } finally {
                _isDetecting.value = false
                _progress.value = ""
            }
        }
    }

    /**
     * Mode A: Chameleon device detection (XMODEM download + local Crypto1 analysis)
     */
    fun startDetection() {
        viewModelScope.launch {
            _isDetecting.value = true
            _errorMessage.value = null
            _result.value = null
            _distanceResult.value = null
            try {
                _result.value = repository.runAdvancedDetection { msg, pct ->
                    _progress.value = "$msg $pct%"
                }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                Timber.e(e, "Advanced detection failed")
                _errorMessage.value = e.message ?: "侦测失败"
            } finally {
                _isDetecting.value = false
                _progress.value = ""
            }
        }
    }

    /**
     * Mode B: PN532 nonce collection + local distance calculation
     * No Chameleon device needed, just a Mifare Classic card on the reader.
     */
    fun startDistanceDetection() {
        viewModelScope.launch {
            _isDetecting.value = true
            _errorMessage.value = null
            _result.value = null
            _distanceResult.value = null
            try {
                _distanceResult.value = repository.runDistanceDetection { msg, pct ->
                    _progress.value = "$msg $pct%"
                }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                Timber.e(e, "Distance detection failed")
                _errorMessage.value = e.message ?: "侦测失败"
            } finally {
                _isDetecting.value = false
                _progress.value = ""
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Mode C: Crypto1-PRS nested attack.
     * Can break Gen3 "no-vulnerability" cards using parity-based candidate filtering.
     */
    fun startNestedAttack() {
        viewModelScope.launch {
            _isDetecting.value = true
            _errorMessage.value = null
            _result.value = null
            _distanceResult.value = null
            _nestedResult.value = null
            try {
                _nestedResult.value = repository.runNestedAttack { msg, pct ->
                    _progress.value = "$msg $pct%"
                }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                Timber.e(e, "Nested attack failed")
                _errorMessage.value = e.message ?: "攻击失败"
            } finally {
                _isDetecting.value = false
                _progress.value = ""
            }
        }
    }
}