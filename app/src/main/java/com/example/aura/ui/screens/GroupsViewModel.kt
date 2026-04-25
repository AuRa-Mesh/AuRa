package com.example.aura.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aura.AuraApplication
import com.example.aura.data.local.FavGroupDao
import com.example.aura.data.local.FavGroupEntity
import com.example.aura.data.local.FavGroupMemberEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FavGroup(
    val id: Long,
    val name: String,
    val createdAt: Long,
)

class GroupsViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        /** Максимальное число избранных групп (папок) на устройстве. */
        const val MAX_FAV_GROUPS = 10
    }

    private val dao: FavGroupDao = (application as AuraApplication).favGroupDao

    val groups: StateFlow<List<FavGroup>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
        .let { flow ->
            MutableStateFlow<List<FavGroup>>(emptyList()).also { out ->
                viewModelScope.launch {
                    flow.collect { list -> out.value = list.map { it.toUi() } }
                }
            }
        }

    private val _selectedGroupId = MutableStateFlow<Long?>(null)
    val selectedGroupId: StateFlow<Long?> = _selectedGroupId.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedGroupMembers: StateFlow<List<Long>> = _selectedGroupId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else dao.observeMembers(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectGroup(id: Long?) { _selectedGroupId.value = id }

    fun createGroup(name: String) {
        viewModelScope.launch {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) return@launch
            if (dao.countGroups() >= MAX_FAV_GROUPS) return@launch
            dao.insertGroup(FavGroupEntity(name = trimmed))
        }
    }

    fun deleteGroup(id: Long) {
        viewModelScope.launch {
            dao.deleteGroup(id)
            if (_selectedGroupId.value == id) _selectedGroupId.value = null
        }
    }

    fun addMember(groupId: Long, nodeNum: Long) {
        viewModelScope.launch { dao.insertMember(FavGroupMemberEntity(groupId, nodeNum)) }
    }

    /**
     * Создать группу и сразу добавить в неё узел (для экрана профиля / избранного).
     * @return успех (false при пустом имени, лимите групп или ошибке вставки)
     */
    fun createGroupAndAddNode(name: String, nodeNum: Long, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = runCatching {
                val trimmed = name.trim()
                if (trimmed.isEmpty()) return@runCatching false
                if (dao.countGroups() >= MAX_FAV_GROUPS) return@runCatching false
                val id = dao.insertGroup(FavGroupEntity(name = trimmed))
                if (id <= 0L) return@runCatching false
                dao.insertMember(FavGroupMemberEntity(groupId = id, nodeNum = nodeNum))
                true
            }.getOrElse { false }
            onResult(ok)
        }
    }

    fun removeMember(groupId: Long, nodeNum: Long) {
        viewModelScope.launch { dao.deleteMember(groupId, nodeNum) }
    }
}

private fun FavGroupEntity.toUi() = FavGroup(id = id, name = name, createdAt = createdAt)
