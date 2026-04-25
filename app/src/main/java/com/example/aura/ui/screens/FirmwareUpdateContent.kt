package com.example.aura.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aura.R
import com.example.aura.ui.LocalNotifyNodeConfigWrite
import com.example.aura.bluetooth.MeshGattToRadioWriter
import com.example.aura.bluetooth.MeshNodeSyncMemoryStore
import com.example.aura.firmware.Esp32BleOtaClient
import com.example.aura.firmware.MeshWireDeviceHardwareInfo
import com.example.aura.firmware.MeshWireEsp32FirmwareDownloader
import com.example.aura.firmware.MeshWireFirmwareApi
import com.example.aura.firmware.MeshWireFirmwareRelease
import com.example.aura.meshwire.MeshWireLoRaToRadioEncoder
import com.example.aura.meshwire.MeshWireNodeNum
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.security.MessageDigest

private val CardBg = Color(0xFF1A2E24)
private val Accent = Color(0xFF7FD4A8)
private val Muted = Color(0xFFB8C4BC)
private val ScreenBg = Color(0xFF0D1812)

private enum class ReleaseChannel { Stable, Alpha }

@Composable
fun FirmwareUpdateContent(
    padding: PaddingValues,
    deviceAddress: String?,
    nodeId: String,
) {
    val context = LocalContext.current
    val notifyNodeConfigWrite = LocalNotifyNodeConfigWrite.current
    val scope = rememberCoroutineScope()
    val addr = deviceAddress?.trim()?.takeIf { it.isNotEmpty() }
    val nodeNum = remember(nodeId) { MeshWireNodeNum.parseToUInt(nodeId) }

    var channel by remember { mutableStateOf(ReleaseChannel.Stable) }
    var releasesStable by remember { mutableStateOf<List<MeshWireFirmwareRelease>>(emptyList()) }
    var releasesAlpha by remember { mutableStateOf<List<MeshWireFirmwareRelease>>(emptyList()) }
    var hardwareList by remember { mutableStateOf<List<MeshWireDeviceHardwareInfo>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var loadingMeta by remember { mutableStateOf(true) }
    var notesOpen by remember { mutableStateOf(false) }
    var busyMessage by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<Float?>(null) }

    val profile = remember(addr) { addr?.let { MeshNodeSyncMemoryStore.getUser(it) } }
    val hwInfo = remember(profile, hardwareList) {
        val slug = profile?.hardwareModel?.trim()?.takeIf { it.isNotEmpty() } ?: return@remember null
        hardwareList.firstOrNull { it.hwModelSlug == slug }
            ?: hardwareList.firstOrNull { it.displayName.contains(slug, ignoreCase = true) }
    }
    val displayName = hwInfo?.displayName ?: profile?.hardwareModel ?: "—"
    val targetLabel = profile?.pioEnv?.takeIf { it.isNotBlank() }
        ?: hwInfo?.platformioTarget?.takeIf { it.isNotBlank() }
        ?: "—"
    val currentVer = profile?.firmwareVersion?.takeIf { it.isNotBlank() } ?: "—"

    val selectedList = if (channel == ReleaseChannel.Stable) releasesStable else releasesAlpha
    var selectedIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(channel, selectedList.size) {
        selectedIndex = 0
    }
    val latest = selectedList.getOrNull(selectedIndex)

    LaunchedEffect(Unit) {
        loadingMeta = true
        loadError = null
        withContext(Dispatchers.IO) {
            try {
                hardwareList = MeshWireFirmwareApi.fetchDeviceHardware()
                val idx = MeshWireFirmwareApi.fetchFirmwareReleases()
                releasesStable = idx.stable
                releasesAlpha = idx.alpha
            } catch (e: Exception) {
                loadError = e.message ?: "Ошибка загрузки"
            } finally {
                loadingMeta = false
            }
        }
    }

    val pickFirmware = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null || addr == null || nodeNum == null) return@rememberLauncherForActivityResult
        val arch = hwInfo?.architecture ?: ""
        if (arch.isNotBlank() && arch != "esp32") {
            Toast.makeText(
                context,
                context.getString(R.string.firmware_ota_esp32_only),
                Toast.LENGTH_LONG,
            ).show()
            return@rememberLauncherForActivityResult
        }
        scope.launch(Dispatchers.IO) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Файл пуст")
                val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
                withContext(Dispatchers.Main) {
                    busyMessage = context.getString(R.string.firmware_ota_sending_reboot)
                    progress = null
                }
                sendOtaRebootAndRun(context.applicationContext, addr, nodeNum, hash, bytes, { s ->
                    scope.launch(Dispatchers.Main) { busyMessage = s }
                }, { p ->
                    scope.launch(Dispatchers.Main) { progress = p }
                })
                withContext(Dispatchers.Main) {
                    busyMessage = null
                    progress = null
                    Toast.makeText(
                        context,
                        context.getString(R.string.firmware_ota_done),
                        Toast.LENGTH_LONG,
                    ).show()
                    notifyNodeConfigWrite?.invoke()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    busyMessage = null
                    progress = null
                    Toast.makeText(context, e.message ?: "OTA", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
            .padding(padding)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.firmware_screen_hint),
            color = Muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Radio, null, tint = Accent)
                    Text(
                        stringResource(R.string.firmware_device_label, displayName),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    "Target: $targetLabel",
                    color = Muted,
                    fontSize = 14.sp,
                )
                Text(
                    stringResource(R.string.firmware_current_version, currentVer),
                    color = Muted,
                    fontSize = 14.sp,
                )
                Text(
                    stringResource(R.string.firmware_latest_version, latest?.title ?: "—"),
                    color = Color.White,
                    fontSize = 14.sp,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF15251C), RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ChannelSegment(
                selected = channel == ReleaseChannel.Stable,
                label = stringResource(R.string.firmware_channel_stable),
                onClick = { channel = ReleaseChannel.Stable },
                modifier = Modifier.weight(1f),
            )
            ChannelSegment(
                selected = channel == ReleaseChannel.Alpha,
                label = stringResource(R.string.firmware_channel_alpha),
                onClick = { channel = ReleaseChannel.Alpha },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { notesOpen = !notesOpen }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Release Notes", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Icon(
                if (notesOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                null,
                tint = Muted,
            )
        }
        if (notesOpen) {
            Text(
                latest?.releaseNotes?.replace("\r\n", "\n") ?: "—",
                color = Muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        HorizontalDivider(color = Color(0xFF2A3D32))

        Spacer(Modifier.height(16.dp))

        if (loadingMeta) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                androidx.compose.material3.CircularProgressIndicator(color = Accent)
            }
        }
        loadError?.let {
            Text(it, color = Color(0xFFFF8A80), fontSize = 14.sp)
        }

        busyMessage?.let {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(it, color = Accent, fontSize = 14.sp, modifier = Modifier.padding(8.dp))
                progress?.let { p ->
                    LinearProgressIndicator(
                        progress = { p },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        color = Accent,
                        trackColor = Color(0xFF2A3D32),
                    )
                }
            }
        }

        val canOta = addr != null && nodeNum != null && latest != null && hwInfo != null &&
            hwInfo.architecture == "esp32" && busyMessage == null && !loadingMeta

        Button(
            onClick = {
                if (addr == null || nodeNum == null || latest == null || hwInfo == null) return@Button
                if (hwInfo.architecture != "esp32") {
                    Toast.makeText(
                context,
                context.getString(R.string.firmware_ota_esp32_only),
                Toast.LENGTH_LONG,
            ).show()
                    return@Button
                }
                scope.launch(Dispatchers.IO) {
                    try {
                        val plat = hwInfo.platformioTarget.ifBlank {
                            profile?.pioEnv ?: error("Нет platformio target")
                        }
                        val bytes = MeshWireEsp32FirmwareDownloader.resolveEsp32FirmwareBytes(
                            releaseId = latest.id,
                            platformioTarget = plat,
                        ) { p ->
                            scope.launch(Dispatchers.Main) {
                                progress = p
                                busyMessage = context.getString(R.string.firmware_downloading)
                            }
                        }
                        val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
                        withContext(Dispatchers.Main) {
                            busyMessage = context.getString(R.string.firmware_ota_sending_reboot)
                        }
                        sendOtaRebootAndRun(
                            context.applicationContext,
                            addr,
                            nodeNum,
                            hash,
                            bytes,
                            { s -> scope.launch(Dispatchers.Main) { busyMessage = s } },
                            { p -> scope.launch(Dispatchers.Main) { progress = p } },
                        )
                        withContext(Dispatchers.Main) {
                            busyMessage = null
                            progress = null
                            Toast.makeText(
                        context,
                        context.getString(R.string.firmware_ota_done),
                        Toast.LENGTH_LONG,
                    ).show()
                            notifyNodeConfigWrite?.invoke()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            busyMessage = null
                            progress = null
                            Toast.makeText(context, e.message ?: "OTA", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            },
            enabled = canOta,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF0A120E)),
        ) {
            Icon(Icons.Default.Bluetooth, null, modifier = Modifier.padding(end = 8.dp))
            Text(stringResource(R.string.firmware_ble_ota), fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = { pickFirmware.launch("*/*") },
            enabled = addr != null && nodeNum != null && busyMessage == null,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
        ) {
            Icon(Icons.Default.Folder, null, modifier = Modifier.padding(end = 8.dp))
            Text(stringResource(R.string.firmware_pick_file))
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ChannelSegment(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) Accent else Color(0xFF15251C)
    val fg = if (selected) Color(0xFF0A120E) else Muted
    Row(
        modifier = modifier
            .background(bg, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            label,
            color = fg,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

private suspend fun sendOtaRebootAndRun(
    appCtx: android.content.Context,
    meshMac: String,
    nodeNum: UInt,
    sha256: ByteArray,
    firmware: ByteArray,
    onStatus: (String) -> Unit,
    onProgress: (Float) -> Unit,
) = withContext(Dispatchers.IO) {
    val dev = MeshWireLoRaToRadioEncoder.LoRaDeviceParams(destinationNodeNum = nodeNum)
    val pkt = MeshWireLoRaToRadioEncoder.encodeAdminOtaBleRequest(sha256, dev)
    val writer = MeshGattToRadioWriter(appCtx)
    suspendCancellableCoroutine { cont ->
        writer.writeToradio(meshMac, pkt) { ok, err ->
            if (!ok) {
                cont.resumeWithException(IllegalStateException(err ?: "ToRadio"))
            } else {
                cont.resume(Unit)
            }
        }
    }
    delay(2000)
    val hex = sha256.joinToString("") { b -> "%02x".format(b) }
    Esp32BleOtaClient(appCtx).runOtaAfterReboot(meshMac, firmware, hex, onStatus, onProgress)
}
