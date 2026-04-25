@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.aura.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aura.mesh.nodedb.MeshNodeListSorter
import com.example.aura.meshwire.MeshWireNodeSummary

// ---------------------------------------------------------------------------
// Colours (matching the app's neon-dark palette)
// ---------------------------------------------------------------------------
private val BG = Color(0xFF040F1C)
private val CARD_BG = Color(0xFF0A2036)
private val BORDER = Color(0xFF42E6FF).copy(alpha = 0.22f)
private val ACCENT = Color(0xFF39E7FF)
private val TEXT_PRIMARY = Color(0xFFE7FCFF)
private val TEXT_SECONDARY = Color(0xFF8CB0BF)
private val ONLINE_DOT = Color(0xFF3DFF8F)
private val OFFLINE_DOT = Color(0xFF304A60)

/** Стрелка слева под шапкой — возврат на предыдущий экран. */
@Composable
private fun SubHeaderBackBar(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF071221)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    tint = ACCENT,
                )
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = BORDER.copy(alpha = 0.5f))
    }
}

// ---------------------------------------------------------------------------
// GroupsScreen — list of folders
// ---------------------------------------------------------------------------
@Composable
fun GroupsScreen(
    allNodes: List<MeshWireNodeSummary>,
    nowEpochSec: Long,
    onBack: () -> Unit,
    /** Контакт в открытой группе: открыть профиль на вкладке «Узлы»; второй аргумент — id папки для «назад». */
    onGroupMemberOpenProfile: (MeshWireNodeSummary, groupFolderId: Long) -> Unit = { _, _ -> },
    /** Контакт в открытой группе: открыть личный чат. */
    onGroupMemberOpenDirectMessage: (MeshWireNodeSummary) -> Unit = {},
    vm: GroupsViewModel = viewModel(),
) {
    val groups by vm.groups.collectAsState()
    val canCreateMoreGroups = groups.size < GroupsViewModel.MAX_FAV_GROUPS
    val selectedGroupId by vm.selectedGroupId.collectAsState()

    if (selectedGroupId != null) {
        val group = groups.firstOrNull { it.id == selectedGroupId }
        if (group != null) {
            FolderDetailScreen(
                group = group,
                allNodes = allNodes,
                nowEpochSec = nowEpochSec,
                onBack = { vm.selectGroup(null) },
                onMemberOpenProfile = onGroupMemberOpenProfile,
                onMemberOpenDirectMessage = onGroupMemberOpenDirectMessage,
                vm = vm,
            )
            return
        }
    }

    var showCreate by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<FavGroup?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = BG,
        topBar = {
            TopAppBar(
                title = { Text("Группы", color = TEXT_PRIMARY, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = ACCENT)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { newFolderName = ""; showCreate = true },
                        enabled = canCreateMoreGroups,
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Создать группу", tint = ACCENT)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF071221),
                    navigationIconContentColor = ACCENT,
                    titleContentColor = TEXT_PRIMARY,
                    actionIconContentColor = ACCENT,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            SubHeaderBackBar(onBack = onBack)
            if (groups.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = TEXT_SECONDARY,
                            modifier = Modifier.size(56.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("Нет групп", color = TEXT_SECONDARY, fontSize = 15.sp)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "До ${GroupsViewModel.MAX_FAV_GROUPS} групп",
                            color = TEXT_SECONDARY.copy(alpha = 0.75f),
                            fontSize = 11.sp,
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { newFolderName = ""; showCreate = true },
                            enabled = canCreateMoreGroups,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF153A52),
                                contentColor = ACCENT,
                            ),
                            border = BorderStroke(1.dp, ACCENT.copy(alpha = 0.55f)),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Создать группу", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    Text(
                        "Групп: ${groups.size}/${GroupsViewModel.MAX_FAV_GROUPS}",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        color = TEXT_SECONDARY,
                        fontSize = 12.sp,
                    )
                    if (!canCreateMoreGroups) {
                        Text(
                            "Достигнут лимит. Удалите группу, чтобы создать новую.",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                            color = TEXT_SECONDARY.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                        )
                    }
                    Button(
                        onClick = { newFolderName = ""; showCreate = true },
                        enabled = canCreateMoreGroups,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF153A52),
                            contentColor = ACCENT,
                            disabledContainerColor = Color(0xFF0E1F28),
                            disabledContentColor = TEXT_SECONDARY.copy(alpha = 0.45f),
                        ),
                        border = BorderStroke(1.dp, ACCENT.copy(alpha = if (canCreateMoreGroups) 0.55f else 0.2f)),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Добавить группу", fontWeight = FontWeight.SemiBold)
                    }
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(groups, key = { it.id }) { group ->
                            GroupFolderRow(
                                group = group,
                                onClick = { vm.selectGroup(group.id) },
                                onLongClick = { deleteTarget = group },
                            )
                        }
                    }
                }
            }
        }
    }

    // Create folder dialog
    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            containerColor = Color(0xFF0E1F33),
            title = { Text("Новая группа", color = TEXT_PRIMARY) },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Название", color = TEXT_SECONDARY) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ACCENT,
                        unfocusedBorderColor = BORDER,
                        focusedTextColor = TEXT_PRIMARY,
                        unfocusedTextColor = TEXT_PRIMARY,
                        cursorColor = ACCENT,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            vm.createGroup(newFolderName)
                            showCreate = false
                        }
                    },
                ) { Text("Создать", color = ACCENT) }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) { Text("Отмена", color = TEXT_SECONDARY) }
            },
        )
    }

    // Delete confirmation dialog
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = Color(0xFF0E1F33),
            title = { Text("Удалить папку?", color = TEXT_PRIMARY) },
            text = { Text("«${target.name}» и все её контакты будут удалены.", color = TEXT_SECONDARY) },
            confirmButton = {
                TextButton(
                    onClick = { vm.deleteGroup(target.id); deleteTarget = null },
                ) { Text("Удалить", color = Color(0xFFFF6B6B)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Отмена", color = TEXT_SECONDARY) }
            },
        )
    }
}

@Composable
private fun GroupFolderRow(
    group: FavGroup,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(18.dp),
        color = CARD_BG,
        border = BorderStroke(1.dp, BORDER),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0D2540)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.People,
                    contentDescription = null,
                    tint = ACCENT,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Text(
                group.name,
                color = TEXT_PRIMARY,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text("›", color = ACCENT, fontSize = 22.sp)
        }
    }
}

// ---------------------------------------------------------------------------
// FolderDetailScreen — nodes inside a folder
// ---------------------------------------------------------------------------
@Composable
fun FolderDetailScreen(
    group: FavGroup,
    allNodes: List<MeshWireNodeSummary>,
    nowEpochSec: Long,
    onBack: () -> Unit,
    onMemberOpenProfile: (MeshWireNodeSummary, groupFolderId: Long) -> Unit = { _, _ -> },
    onMemberOpenDirectMessage: (MeshWireNodeSummary) -> Unit = {},
    vm: GroupsViewModel = viewModel(),
) {
    val memberNums by vm.selectedGroupMembers.collectAsState()
    val memberNodes = remember(memberNums, allNodes) {
        allNodes.filter { it.nodeNum in memberNums }
    }

    var showPicker by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<MeshWireNodeSummary?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = BG,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        group.name,
                        color = TEXT_PRIMARY,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = ACCENT)
                    }
                },
                actions = {
                    IconButton(onClick = { showPicker = true }) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "Добавить контакт", tint = ACCENT)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF071221),
                    navigationIconContentColor = ACCENT,
                    titleContentColor = TEXT_PRIMARY,
                    actionIconContentColor = ACCENT,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            SubHeaderBackBar(onBack = onBack)
            if (memberNodes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.PersonAdd,
                            contentDescription = null,
                            tint = TEXT_SECONDARY,
                            modifier = Modifier.size(52.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("Нет контактов", color = TEXT_SECONDARY, fontSize = 15.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Нажмите + чтобы добавить узлы",
                            color = TEXT_SECONDARY.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(memberNodes, key = { it.nodeNum }) { node ->
                        MemberRow(
                            node = node,
                            nowEpochSec = nowEpochSec,
                            onOpenProfile = { onMemberOpenProfile(node, group.id) },
                            onOpenDirectMessage = { onMemberOpenDirectMessage(node) },
                            onRemove = { removeTarget = node },
                        )
                    }
                }
            }
        }
    }

    // Node picker bottom sheet
    if (showPicker) {
        NodePickerSheet(
            allNodes = allNodes,
            memberNums = memberNums.toSet(),
            nowEpochSec = nowEpochSec,
            onAdd = { vm.addMember(group.id, it.nodeNum) },
            onDismiss = { showPicker = false },
        )
    }

    // Remove confirmation
    removeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            containerColor = Color(0xFF0E1F33),
            title = { Text("Убрать из папки?", color = TEXT_PRIMARY) },
            text = { Text("«${target.displayLongName()}» будет удалён из этой группы.", color = TEXT_SECONDARY) },
            confirmButton = {
                TextButton(
                    onClick = { vm.removeMember(group.id, target.nodeNum); removeTarget = null },
                ) { Text("Убрать", color = Color(0xFFFF6B6B)) }
            },
            dismissButton = {
                TextButton(onClick = { removeTarget = null }) { Text("Отмена", color = TEXT_SECONDARY) }
            },
        )
    }
}

@Composable
private fun MemberRow(
    node: MeshWireNodeSummary,
    nowEpochSec: Long,
    onOpenProfile: () -> Unit,
    onOpenDirectMessage: () -> Unit,
    onRemove: () -> Unit,
) {
    val online = node.isOnline(nowEpochSec, MeshNodeListSorter.DEFAULT_ONLINE_WINDOW_SEC)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = CARD_BG,
        border = BorderStroke(1.dp, BORDER),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (online) ONLINE_DOT else OFFLINE_DOT),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    node.displayLongName(),
                    color = TEXT_PRIMARY,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenProfile),
                )
                Text(
                    if (online) "В сети" else "Не в сети",
                    color = if (online) ONLINE_DOT else TEXT_SECONDARY,
                    fontSize = 11.sp,
                )
            }
            IconButton(onClick = onOpenDirectMessage, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Default.Forum,
                    contentDescription = "Личное сообщение",
                    tint = ACCENT,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Убрать", tint = Color(0xFFFF6B6B), modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// NodePickerSheet — bottom sheet to add nodes to a folder
// ---------------------------------------------------------------------------
@Composable
private fun NodePickerSheet(
    allNodes: List<MeshWireNodeSummary>,
    memberNums: Set<Long>,
    nowEpochSec: Long,
    onAdd: (MeshWireNodeSummary) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val available = remember(allNodes, memberNums) { allNodes.filter { it.nodeNum !in memberNums } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0C1C2E),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(BORDER),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Text(
                "Добавить контакты",
                color = TEXT_PRIMARY,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            )
            HorizontalDivider(color = BORDER, thickness = 0.6.dp)
            if (available.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Все узлы уже добавлены", color = TEXT_SECONDARY, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(available, key = { it.nodeNum }) { node ->
                        val online = node.isOnline(nowEpochSec, MeshNodeListSorter.DEFAULT_ONLINE_WINDOW_SEC)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAdd(node); onDismiss() },
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF0E2236),
                            border = BorderStroke(1.dp, BORDER),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (online) ONLINE_DOT else OFFLINE_DOT),
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    node.displayLongName(),
                                    color = TEXT_PRIMARY,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    tint = ACCENT,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
