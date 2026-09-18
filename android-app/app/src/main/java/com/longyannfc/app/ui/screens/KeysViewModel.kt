package com.longyannfc.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.longyannfc.app.data.local.KeyCacheEntity
import com.longyannfc.app.data.repository.CardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class KeysViewModel @Inject constructor(
    private val repository: CardRepository
) : ViewModel() {
    val cachedKeys: StateFlow<List<String>> = repository.observeCachedKeys()
        .map { rows ->
            rows.flatMap { row ->
                listOfNotNull(
                    row.keyA?.let { "${row.uid} S${row.sector} A $it" },
                    row.keyB?.let { "${row.uid} S${row.sector} B $it" }
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // 按卡号分组的密钥
    val keysByUid: StateFlow<Map<String, List<KeyCacheEntity>>> = repository.observeCachedKeys()
        .map { rows -> rows.groupBy { it.uid } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _dictionaryKeys = MutableStateFlow(repository.getLocalDictionaryKeys())
    val dictionaryKeys: StateFlow<List<String>> = _dictionaryKeys

    fun deleteKeysByUid(uid: String) {
        viewModelScope.launch { repository.deleteKeysByUid(uid) }
    }

    fun clearAllKeys() {
        viewModelScope.launch { repository.clearAllKeys() }
    }
}