package com.longyannfc.app.ui.screens

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.longyannfc.app.data.local.CardDumpEntity
import com.longyannfc.app.data.repository.CardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class CardDumpViewModel @Inject constructor(
    private val repository: CardRepository
) : ViewModel() {
    val dumps: StateFlow<List<CardDumpEntity>> = repository.observeCardDumps()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun observeDump(id: Long): Flow<CardDumpEntity?> = repository.observeCardDump(id)

    fun updateData(id: Long, data: ByteArray) {
        viewModelScope.launch { repository.updateCardDumpData(id, data) }
    }

    fun rename(id: Long, name: String) {
        viewModelScope.launch { repository.renameCardDump(id, name) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.deleteCardDump(id) }
    }

    fun exportDump(context: Context, dumpId: Long, uri: Uri) {
        viewModelScope.launch {
            try {
                val dump = repository.getCardDump(dumpId) ?: return@launch
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(dump.data)
                }
            } catch (e: Exception) {
                Timber.e(e, "Export failed")
            }
        }
    }
}