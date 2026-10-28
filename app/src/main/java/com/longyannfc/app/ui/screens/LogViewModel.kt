package com.longyannfc.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.longyannfc.app.data.local.OperationLogEntity
import com.longyannfc.app.data.repository.CardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * 操作日志。数据来自 Room 的 operation_log 表，由 Repository 写入。
 */
@HiltViewModel
class LogViewModel @Inject constructor(
    repository: CardRepository
) : ViewModel() {

    val logs: StateFlow<List<OperationLogEntity>> = repository.observeOperationLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

