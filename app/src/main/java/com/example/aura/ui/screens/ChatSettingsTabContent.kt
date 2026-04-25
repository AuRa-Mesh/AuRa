package com.example.aura.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.aura.R
import com.example.aura.bluetooth.MeshPhoneLocationToMeshSender
import com.example.aura.bluetooth.MeshNodeRemoteActions
import com.example.aura.ui.vip.VipMyPlanBubble
import com.example.aura.ui.screens.MeshWireSettingsScreenColors as Mst

private val CardRadius = RoundedCornerShape(16.dp)

@Composable
fun ChatSettingsTabContent(
    padding: PaddingValues,
    settingsListState: LazyListState,
    deviceAddress: String? = null,
    localNodeNum: UInt? = null,
    matrixBackdropActive: Boolean = false,
    matrixUnlocked: Boolean = false,
    onMatrixLockedClick: () -> Unit = {},
    onOpenLoRa: () -> Unit = {},
    onOpenUserSettings: () -> Unit = {},
    onOpenChannels: () -> Unit = {},
    onOpenSecurity: () -> Unit = {},
    onOpenDeviceSettings: () -> Unit = {},
    onOpenMqtt: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenExternalNotifications: () -> Unit = {},
    onOpenTelemetry: () -> Unit = {},
    nodeConnected: Boolean = false,
    onOpenFirmwareUpdate: () -> Unit = {},
    provideLocationToMesh: Boolean = false,
    /** Синхронизируется с профилем узла: при true передача координат запрещена глобально. */
    hideCoordinatesTransmission: Boolean = false,
    onProvideLocationToMeshChange: (Boolean) -> Unit = {},
    /** Открыть диалог подключения на вкладке USB (serial). */
    onOpenSerialConnection: () -> Unit = {},
    /** После успешной admin-команды на ноду (как запись конфига). */
    onNodeConfigWriteSuccess: () -> Unit = {},
    onOpenMessageHistory: () -> Unit = {},
    onOpenMatrixSettings: () -> Unit = {},
    /** Та же пошаговая инструкция, что при первом запуске приложения. */
    onOpenAppCapabilitiesTutorial: () -> Unit = {},
    /** Открыть окно ввода VIP-пароля для активации/продления тарифа. */
    onOpenVipActivate: () -> Unit = {},
    /** true — показать пункт «Активировать VIP тариф» (только если VIP уже исчерпан). */
    showVipActivate: Boolean = false,
) {
    val context = LocalContext.current
    val addr = deviceAddress?.trim()?.takeIf { it.isNotEmpty() }
    /** Радио-конфиг и админ-команды доступны при привязанном адресе и активном BLE. */
    val radioConfigurable = addr != null && nodeConnected

    var rebootDialogOpen by remember { mutableStateOf(false) }
    var shutdownDialogOpen by remember { mutableStateOf(false) }
    var factoryDialogOpen by remember { mutableStateOf(false) }
    var adminBusy by remember { mutableStateOf(false) }
    var aboutBubbleOpen by remember { mutableStateOf(false) }
    var myPlanBubbleOpen by remember { mutableStateOf(false) }
    val meshLocationPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { result ->
            val fine = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
            val coarse = result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (fine || coarse) {
                onProvideLocationToMeshChange(true)
            } else {
                onProvideLocationToMeshChange(false)
                Toast.makeText(
                    context,
                    context.getString(R.string.settings_location_permission_denied),
                    Toast.LENGTH_LONG,
                ).show()
            }
        },
    )

    fun openAppDetailsForPermissions() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(
                context,
                context.getString(R.string.settings_system_settings),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun toastNeedDevice() {
        Toast.makeText(
            context,
            context.getString(R.string.clear_node_list_toast_need_device),
            Toast.LENGTH_SHORT,
        ).show()
    }

    fun toastNeedNodeId() {
        Toast.makeText(
            context,
            context.getString(R.string.clear_node_list_toast_need_node_id),
            Toast.LENGTH_SHORT,
        ).show()
    }

    fun toastConnectRadioFirst() {
        Toast.makeText(
            context,
            context.getString(R.string.settings_connect_radio_first),
            Toast.LENGTH_SHORT,
        ).show()
    }

    fun adminReady(): Boolean {
        if (!nodeConnected || addr == null) {
            toastNeedDevice()
            return false
        }
        if (localNodeNum == null) {
            toastNeedNodeId()
            return false
        }
        return true
    }

    fun toastAdmin(ok: Boolean, err: String?) {
        Toast.makeText(
            context,
            if (ok) {
                context.getString(R.string.admin_command_sent)
            } else {
                context.getString(R.string.admin_command_failed, err?.takeIf { it.isNotBlank() } ?: "BLE")
            },
            if (ok) Toast.LENGTH_SHORT else Toast.LENGTH_LONG,
        ).show()
    }

    if (rebootDialogOpen) {
        AlertDialog(
            onDismissRequest = { if (!adminBusy) rebootDialogOpen = false },
            title = { Text(stringResource(R.string.admin_reboot_title), color = Mst.text) },
            text = { Text(stringResource(R.string.admin_reboot_body), color = Mst.muted) },
            confirmButton = {
                TextButton(
                    enabled = !adminBusy,
                    onClick = {
                        if (!adminReady()) return@TextButton
                        adminBusy = true
                        MeshNodeRemoteActions.sendAdminReboot(
                            context.applicationContext,
                            addr!!,
                            localNodeNum!!,
                            delaySeconds = 5,
                        ) { ok, err, _ ->
                            adminBusy = false
                            toastAdmin(ok, err)
                            rebootDialogOpen = false
                            if (ok) onNodeConfigWriteSuccess()
                        }
                    },
                ) {
                    Text(stringResource(R.string.admin_confirm), color = Mst.accent)
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !adminBusy,
                    onClick = { rebootDialogOpen = false },
                ) {
                    Text(stringResource(R.string.action_cancel), color = Mst.muted)
                }
            },
            containerColor = Mst.card,
            titleContentColor = Mst.text,
            textContentColor = Mst.text,
        )
    }

    if (shutdownDialogOpen) {
        AlertDialog(
            onDismissRequest = { if (!adminBusy) shutdownDialogOpen = false },
            title = { Text(stringResource(R.string.admin_shutdown_title), color = Mst.text) },
            text = { Text(stringResource(R.string.admin_shutdown_body), color = Mst.muted) },
            confirmButton = {
                TextButton(
                    enabled = !adminBusy,
                    onClick = {
                        if (!adminReady()) return@TextButton
                        adminBusy = true
                        MeshNodeRemoteActions.sendAdminShutdown(
                            context.applicationContext,
                            addr!!,
                            localNodeNum!!,
                            delaySeconds = 5,
                        ) { ok, err, _ ->
                            adminBusy = false
                            toastAdmin(ok, err)
                            shutdownDialogOpen = false
                            if (ok) onNodeConfigWriteSuccess()
                        }
                    },
                ) {
                    Text(stringResource(R.string.admin_confirm), color = Mst.accent)
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !adminBusy,
                    onClick = { shutdownDialogOpen = false },
                ) {
                    Text(stringResource(R.string.action_cancel), color = Mst.muted)
                }
            },
            containerColor = Mst.card,
            titleContentColor = Mst.text,
            textContentColor = Mst.text,
        )
    }

    if (factoryDialogOpen) {
        AlertDialog(
            onDismissRequest = { if (!adminBusy) factoryDialogOpen = false },
            title = { Text(stringResource(R.string.admin_factory_title), color = Mst.text) },
            text = {
                Column {
                    Text(stringResource(R.string.admin_factory_body), color = Mst.muted, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        enabled = !adminBusy,
                        onClick = {
                            if (!adminReady()) return@TextButton
                            adminBusy = true
                            MeshNodeRemoteActions.sendAdminFactoryResetConfig(
                                context.applicationContext,
                                addr!!,
                                localNodeNum!!,
                                arg = 0,
                            ) { ok, err, _ ->
                                adminBusy = false
                                toastAdmin(ok, err)
                                factoryDialogOpen = false
                                if (ok) onNodeConfigWriteSuccess()
                            }
                        },
                    ) {
                        Text(stringResource(R.string.admin_factory_config_only), color = Mst.accent)
                    }
                    TextButton(
                        enabled = !adminBusy,
                        onClick = {
                            if (!adminReady()) return@TextButton
                            adminBusy = true
                            MeshNodeRemoteActions.sendAdminFactoryResetDevice(
                                context.applicationContext,
                                addr!!,
                                localNodeNum!!,
                                arg = 0,
                            ) { ok, err, _ ->
                                adminBusy = false
                                toastAdmin(ok, err)
                                factoryDialogOpen = false
                                if (ok) onNodeConfigWriteSuccess()
                            }
                        },
                    ) {
                        Text(stringResource(R.string.admin_factory_full), color = Mst.accent)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !adminBusy,
                    onClick = { factoryDialogOpen = false },
                ) {
                    Text(stringResource(R.string.action_cancel), color = Mst.muted)
                }
            },
            containerColor = Mst.card,
            titleContentColor = Mst.text,
            textContentColor = Mst.text,
        )
    }

    if (myPlanBubbleOpen) {
        VipMyPlanBubble(onDismiss = { myPlanBubbleOpen = false })
    }

    if (aboutBubbleOpen) {
        Dialog(
            onDismissRequest = { aboutBubbleOpen = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { aboutBubbleOpen = false },
                        ),
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 28.dp)
                        .fillMaxWidth()
                        .widthIn(max = 380.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = Color(0xFF0A2036).copy(alpha = 0.94f),
                    tonalElevation = 0.dp,
                    shadowElevation = 12.dp,
                    border = BorderStroke(1.dp, Color(0xFF42E6FF).copy(alpha = 0.35f)),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.app_about_display_version),
                            color = Color(0xFFE7FCFF),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.app_about_author_title),
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFF8CB0BF),
                                fontSize = 15.sp,
                                lineHeight = 21.sp,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = stringResource(R.string.app_about_author_name),
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFF8CB0BF),
                                fontSize = 15.sp,
                                lineHeight = 21.sp,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = stringResource(R.string.app_about_author_handle),
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFF8CB0BF),
                                fontSize = 15.sp,
                                lineHeight = 21.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                        TextButton(onClick = { aboutBubbleOpen = false }) {
                            Text("OK", color = Mst.accent)
                        }
                    }
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize(),
        state = settingsListState,
        contentPadding = PaddingValues(top = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            SettingsSectionTitle(stringResource(R.string.settings_section_radio))
            SettingsCardBlock {
                SettingsNavigateRow(
                    icon = Icons.Default.CellTower,
                    title = stringResource(R.string.settings_lora),
                    enabled = radioConfigurable,
                    onDisabledClick = { toastConnectRadioFirst() },
                    onClick = onOpenLoRa,
                )
                HSettDivider()
                SettingsNavigateRow(
                    icon = Icons.AutoMirrored.Filled.List,
                    title = stringResource(R.string.settings_channels),
                    enabled = radioConfigurable,
                    onDisabledClick = { toastConnectRadioFirst() },
                    onClick = onOpenChannels,
                )
                HSettDivider()
                SettingsNavigateRow(
                    icon = Icons.Default.Security,
                    title = stringResource(R.string.settings_security),
                    enabled = radioConfigurable,
                    onDisabledClick = { toastConnectRadioFirst() },
                    onClick = onOpenSecurity,
                )
            }
        }

        item {
            SettingsSectionTitle(stringResource(R.string.settings_section_device))
            SettingsCardBlock {
                SettingsNavigateRow(
                    icon = Icons.Default.Person,
                    title = stringResource(R.string.settings_user),
                    enabled = radioConfigurable,
                    onDisabledClick = { toastConnectRadioFirst() },
                    onClick = onOpenUserSettings,
                )
                HSettDivider()
                SettingsNavigateRow(
                    icon = Icons.Default.Router,
                    title = stringResource(R.string.settings_device),
                    enabled = radioConfigurable,
                    onDisabledClick = { toastConnectRadioFirst() },
                    onClick = onOpenDeviceSettings,
                )
            }
        }

        item {
            SettingsSectionTitle(stringResource(R.string.settings_app_section))
            SettingsCardBlock {
                SettingsToggleRow(
                    icon = Icons.Default.LocationOn,
                    title = stringResource(R.string.settings_location_provide_network),
                    checked = provideLocationToMesh && !hideCoordinatesTransmission,
                    onCheckedChange = { want ->
                        if (want && hideCoordinatesTransmission) {
                            Toast.makeText(
                                context,
                                "Выключите «Скрыть координаты» в профиле узла (Местоположение)",
                                Toast.LENGTH_LONG,
                            ).show()
                            return@SettingsToggleRow
                        }
                        if (want) {
                            if (MeshPhoneLocationToMeshSender.hasLocationPermission(context)) {
                                onProvideLocationToMeshChange(true)
                            } else {
                                // Системный диалог с точным / приблизительным и режимом доступа
                                locationPermissionLauncher.launch(meshLocationPermissions)
                            }
                        } else {
                            onProvideLocationToMeshChange(false)
                            Toast.makeText(
                                context,
                                context.getString(R.string.settings_location_disable_open_settings),
                                Toast.LENGTH_LONG,
                            ).show()
                            openAppDetailsForPermissions()
                        }
                    },
                )
                HSettDivider()
                SettingsNavigateRow(
                    icon = Icons.Default.Settings,
                    title = stringResource(R.string.settings_system_settings),
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    },
                )
            }
        }

        item {
            SettingsSectionTitle(stringResource(R.string.settings_module_section))
            SettingsCardBlock {
                SettingsNavigateRow(
                    icon = Icons.Default.Cloud,
                    title = stringResource(R.string.settings_mqtt),
                    enabled = radioConfigurable,
                    onDisabledClick = { toastConnectRadioFirst() },
                    onClick = onOpenMqtt,
                )
                HSettDivider()
                SettingsNavigateRow(
                    icon = Icons.Default.Usb,
                    title = stringResource(R.string.settings_com_port),
                    enabled = true,
                    onClick = {
                        Toast.makeText(
                            context,
                            context.getString(R.string.settings_serial_hint),
                            Toast.LENGTH_SHORT,
                        ).show()
                        onOpenSerialConnection()
                    },
                )
                HSettDivider()
                SettingsNavigateRow(
                    icon = Icons.Default.PhoneAndroid,
                    title = stringResource(R.string.settings_phone_notifications),
                    enabled = true,
                    onClick = onOpenNotifications,
                )
                HSettDivider()
                SettingsNavigateRow(
                    icon = Icons.Default.Notifications,
                    title = stringResource(R.string.settings_external_notifications),
                    enabled = radioConfigurable,
                    onDisabledClick = { toastConnectRadioFirst() },
                    onClick = onOpenExternalNotifications,
                )
                HSettDivider()
                SettingsNavigateRow(
                    icon = Icons.Default.Sync,
                    title = stringResource(R.string.settings_telemetry),
                    enabled = radioConfigurable,
                    onDisabledClick = { toastConnectRadioFirst() },
                    onClick = onOpenTelemetry,
                )
            }
        }

        item {
            SettingsSectionTitle(stringResource(R.string.settings_admin_section))
            SettingsCardBlock {
                SettingsNavigateRow(
                    icon = Icons.Default.RestartAlt,
                    title = stringResource(R.string.settings_reboot),
                    enabled = radioConfigurable,
                    onDisabledClick = { toastConnectRadioFirst() },
                    onClick = { rebootDialogOpen = true },
                )
                HSettDivider()
                SettingsNavigateRow(
                    icon = Icons.Default.PowerSettingsNew,
                    title = stringResource(R.string.settings_shutdown),
                    enabled = radioConfigurable,
                    onDisabledClick = { toastConnectRadioFirst() },
                    onClick = { shutdownDialogOpen = true },
                )
                HSettDivider()
                SettingsNavigateRow(
                    icon = Icons.Default.History,
                    title = stringResource(R.string.settings_factory_reset),
                    enabled = radioConfigurable,
                    onDisabledClick = { toastConnectRadioFirst() },
                    onClick = { factoryDialogOpen = true },
                )
            }
        }

        if (radioConfigurable) {
            item {
                SettingsSectionTitle(stringResource(R.string.settings_advanced_section))
                SettingsCardBlock {
                    SettingsNavigateRow(
                        icon = Icons.Default.Android,
                        title = stringResource(R.string.settings_firmware_update),
                        onClick = onOpenFirmwareUpdate,
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            SettingsCardBlock {
                SettingsNavigateRow(
                    icon = Icons.AutoMirrored.Filled.Chat,
                    title = stringResource(R.string.settings_message_history),
                    enabled = true,
                    onClick = onOpenMessageHistory,
                )
                HSettDivider()
                SettingsNavigateRow(
                    icon = Icons.Outlined.ViewModule,
                    title = stringResource(R.string.settings_matrix),
                    enabled = matrixUnlocked,
                    onDisabledClick = onMatrixLockedClick,
                    onClick = onOpenMatrixSettings,
                )
            }
        }
        item {
            SettingsCardBlock {
                SettingsNavigateRow(
                    icon = Icons.AutoMirrored.Outlined.MenuBook,
                    title = stringResource(R.string.settings_app_capabilities),
                    onClick = onOpenAppCapabilitiesTutorial,
                )
            }
        }
        item {
            SettingsCardBlock {
                SettingsNavigateRow(
                    icon = Icons.Default.Info,
                    title = stringResource(R.string.settings_about),
                    onClick = { aboutBubbleOpen = true },
                )
            }
        }
        if (showVipActivate) {
            item {
                SettingsCardBlock {
                    SettingsNavigateRow(
                        icon = Icons.Default.Star,
                        title = "Активировать VIP тариф",
                        onClick = onOpenVipActivate,
                    )
                }
            }
        } else {
            // Активный VIP: показываем окно со статусом/обратным отсчётом.
            item {
                SettingsCardBlock {
                    SettingsNavigateRow(
                        icon = Icons.Default.WorkspacePremium,
                        title = "Мой тариф",
                        onClick = { myPlanBubbleOpen = true },
                    )
                }
            }
        }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(
                            color = Mst.card.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(14.dp),
                        ),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(
                            color = Mst.card.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(14.dp),
                        ),
                )
            }
        }
    }
}

@Composable
private fun SettingsHeader(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.settings_title),
            color = Mst.text,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        color = Mst.text,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp, top = 4.dp),
    )
}

@Composable
private fun SettingsCardBlock(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardRadius,
        colors = CardDefaults.cardColors(containerColor = Mst.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            content()
        }
    }
}

@Composable
private fun HSettDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 52.dp),
        color = Mst.dividerOuter,
        thickness = 1.dp,
    )
}

@Composable
private fun SettingsNavigateRow(
    icon: ImageVector,
    title: String,
    enabled: Boolean = true,
    onDisabledClick: () -> Unit = {},
    onClick: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.48f)
            .clickable(onClick = {
                if (enabled) onClick() else onDisabledClick()
            })
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Mst.text,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = title,
                color = Mst.text,
                fontSize = 15.sp,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Mst.muted,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Mst.text,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = title,
                color = Mst.text,
                fontSize = 15.sp,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Mst.accent,
                checkedTrackColor = Mst.accent.copy(alpha = 0.45f),
                uncheckedThumbColor = Mst.switchThumbUnchecked,
                uncheckedTrackColor = Mst.dividerOuter,
            ),
        )
    }
}
