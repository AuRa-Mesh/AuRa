package com.example.aura.mesh.nodedb

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aura.bluetooth.NodeConnectionState
import com.example.aura.bluetooth.NodeGattConnection
import com.example.aura.mesh.repository.MeshIncomingChatRepository
import com.example.aura.meshwire.MeshWireNodeSummary
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class NodesTabUiState(
    val nodes: List<MeshWireNodeSummary>,
    val rowUi: List<MeshNodeListRowUi>,
    val selfNode: MeshWireNodeSummary?,
    val showInitialDumpProgress: Boolean,
    val nowEpochSec: Long,
)

class NodesTabViewModel : ViewModel() {

    private val _meshChatIngressRevision = MutableStateFlow(0L)
    val meshChatIngressRevision: StateFlow<Long> = _meshChatIngressRevision.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _bleConnected = MutableStateFlow(false)
    fun setBleConnected(value: Boolean) {
        _bleConnected.value = value
    }

    private val nowEpochSec = MutableStateFlow(System.currentTimeMillis() / 1000L)

    init {
        viewModelScope.launch {
            MeshIncomingChatRepository.incomingMessages.collect {
                _meshChatIngressRevision.value = System.currentTimeMillis()
            }
        }
        viewModelScope.launch {
            while (isActive) {
                nowEpochSec.value = System.currentTimeMillis() / 1000L
                delay(60_000L)
            }
        }
    }

    fun setSearchQuery(q: String) {
        _searchQuery.value = q
    }

    private data class Mid(
        val nodes: List<MeshWireNodeSummary>,
        val query: String,
        val ack: Boolean,
        val conn: NodeConnectionState,
        val ble: Boolean,
        val myNum: UInt?,
    )

    val uiState: StateFlow<NodesTabUiState> = combine(
        combine(
            MeshNodeDbRepository.nodes,
            _searchQuery,
            NodeGattConnection.initialWantConfigAcknowledged,
            NodeGattConnection.connectionState,
            _bleConnected,
            NodeGattConnection.myNodeNum,
        ) { args: Array<*> ->
            @Suppress("UNCHECKED_CAST")
            Mid(
                args[0] as List<MeshWireNodeSummary>,
                args[1] as String,
                args[2] as Boolean,
                args[3] as NodeConnectionState,
                args[4] as Boolean,
                args[5] as UInt?,
            )
        },
        nowEpochSec,
    ) { mid, now ->
        toUi(mid, now)
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = NodesTabUiState(
                nodes = emptyList(),
                rowUi = emptyList(),
                selfNode = null,
                showInitialDumpProgress = false,
                nowEpochSec = System.currentTimeMillis() / 1000L,
            ),
        )

    private fun toUi(mid: Mid, now: Long): NodesTabUiState {
        val q = mid.query.trim().lowercase(Locale.ROOT)
        val filtered = if (q.isEmpty()) {
            mid.nodes
        } else {
            mid.nodes.filter { n ->
                val hex = "!%08x".format(n.nodeNum).lowercase(Locale.ROOT)
                n.displayLongName().lowercase(Locale.ROOT).contains(q) ||
                    n.longName.lowercase(Locale.ROOT).contains(q) ||
                    n.shortName.lowercase(Locale.ROOT).contains(q) ||
                    hex.contains(q) ||
                    n.nodeNum.toString().contains(q) ||
                    n.hardwareModel.lowercase(Locale.ROOT).contains(q)
            }
        }
        val selfNum = mid.myNum?.toLong()?.and(0xFFFF_FFFFL)
        val self = selfNum?.let { want ->
            filtered.firstOrNull { (it.nodeNum and 0xFFFF_FFFFL) == want }
        }
        val sorted = MeshNodeListSorter.sortForNodesTab(filtered, selfNum, now)
        val rows = MeshNodeListRowFormatter.buildRows(sorted, self, now)
        val dumpProgress = mid.ble &&
            !mid.ack &&
            (mid.conn == NodeConnectionState.CONNECTING ||
                mid.conn == NodeConnectionState.HANDSHAKING ||
                mid.conn == NodeConnectionState.RECONNECTING)
        return NodesTabUiState(
            nodes = sorted,
            rowUi = rows,
            selfNode = self,
            showInitialDumpProgress = dumpProgress,
            nowEpochSec = now,
        )
    }
}
