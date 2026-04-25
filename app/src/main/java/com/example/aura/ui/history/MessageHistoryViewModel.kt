package com.example.aura.ui.history

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.aura.data.local.MessageHistoryEntryEntity
import com.example.aura.data.local.MessageHistoryGroupEntity
import com.example.aura.history.MessageHistoryRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MessageHistoryDaySection(
    val date: LocalDate,
    val items: List<MessageHistoryEntryEntity>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class MessageHistoryViewModel(
    private val repository: MessageHistoryRepository,
) : ViewModel() {

    private val _selectedGroupId = MutableStateFlow<String?>(null)
    val selectedGroupId: StateFlow<String?> = _selectedGroupId.asStateFlow()

    val groups: StateFlow<List<MessageHistoryGroupEntity>> =
        repository.observeGroups().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList(),
        )

    val daySections: StateFlow<List<MessageHistoryDaySection>> =
        _selectedGroupId.flatMapLatest { gid ->
            if (gid == null) {
                flowOf(emptyList())
            } else {
                repository.observeMessages(gid).map { list -> groupByDay(list) }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectGroup(groupId: String?) {
        _selectedGroupId.value = groupId
    }

    fun exportHistory(onUri: (Uri) -> Unit, onError: (Throwable) -> Unit) {
        viewModelScope.launch {
            try {
                val uri = repository.exportHistory()
                onUri(uri)
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    fun clearHistory(groupId: String? = null, onDone: () -> Unit = {}, onError: (Throwable) -> Unit = {}) {
        viewModelScope.launch {
            try {
                repository.clearHistory(groupId)
                if (groupId != null) _selectedGroupId.value = null
                onDone()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    companion object {
        fun groupByDay(entries: List<MessageHistoryEntryEntity>): List<MessageHistoryDaySection> {
            val zone = ZoneId.systemDefault()
            return entries
                .groupBy { e ->
                    Instant.ofEpochMilli(e.createdAtEpochMs).atZone(zone).toLocalDate()
                }
                .toList()
                .sortedBy { it.first }
                .map { (d, items) -> MessageHistoryDaySection(d, items.sortedBy { it.createdAtEpochMs }) }
        }
    }

    class Factory(
        private val repository: MessageHistoryRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MessageHistoryViewModel(repository) as T
    }
}
