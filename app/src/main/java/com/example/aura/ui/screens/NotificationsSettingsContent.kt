package com.example.aura.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.aura.R
import com.example.aura.mesh.MessageResilientService
import com.example.aura.mesh.MeshService
import com.example.aura.notifications.MeshNotificationPreferences
import com.example.aura.ui.screens.MeshWireSettingsScreenColors as Mst

private val CardShape = RoundedCornerShape(16.dp)

/** Локальные уведомления телефона (не конфиг модуля на ноде — см. [ExternalNotificationsSettingsContent]). */
@Composable
fun NotificationsSettingsContent(
    padding: PaddingValues,
) {
    val context = LocalContext.current

    var masterEnabled by remember {
        mutableStateOf(MeshNotificationPreferences.isMasterEnabled(context.applicationContext))
    }
    var filterDm by remember {
        mutableStateOf(MeshNotificationPreferences.filterPrivateMessages(context.applicationContext))
    }
    var filterChannel by remember {
        mutableStateOf(MeshNotificationPreferences.filterChannelMessages(context.applicationContext))
    }
    var smartAlert by remember {
        mutableStateOf(MeshNotificationPreferences.smartAlert(context.applicationContext))
    }
    var showPreview by remember {
        mutableStateOf(MeshNotificationPreferences.showPreview(context.applicationContext))
    }

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && masterEnabled && Build.VERSION.SDK_INT >= 33) {
            MeshService.ensureChannel(context.applicationContext)
            try {
                ContextCompat.startForegroundService(
                    context.applicationContext,
                    Intent(context.applicationContext, MessageResilientService::class.java),
                )
            } catch (_: Exception) {
            }
        }
    }

    fun persistAppPrefs() {
        val appCtx = context.applicationContext
        MeshNotificationPreferences.setMasterEnabled(appCtx, masterEnabled)
        MeshNotificationPreferences.setFilterPrivateMessages(appCtx, filterDm)
        MeshNotificationPreferences.setFilterChannelMessages(appCtx, filterChannel)
        MeshNotificationPreferences.setSmartAlert(appCtx, smartAlert)
        MeshNotificationPreferences.setShowPreview(appCtx, showPreview)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Mst.bg)
            .padding(padding)
            .padding(horizontal = 16.dp),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.notifications_section_phone),
                    color = Mst.muted,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CardShape,
                    colors = CardDefaults.cardColors(containerColor = Mst.card),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column {
                        notifRow(
                            icon = Icons.Default.PhoneAndroid,
                            title = stringResource(R.string.notifications_enable_service),
                            subtitle = stringResource(R.string.notifications_enable_service_hint),
                            checked = masterEnabled,
                            onChecked = { v ->
                                masterEnabled = v
                                persistAppPrefs()
                                if (v && Build.VERSION.SDK_INT >= 33) {
                                    val ok = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS,
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (!ok) notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                        )
                        HorizontalDivider(color = Mst.dividerOuter.copy(alpha = 0.5f))
                        notifRow(
                            icon = Icons.Default.People,
                            title = stringResource(R.string.notifications_filter_dm),
                            checked = filterDm,
                            onChecked = { filterDm = it; persistAppPrefs() },
                            enabled = masterEnabled,
                        )
                        HorizontalDivider(color = Mst.dividerOuter.copy(alpha = 0.5f))
                        notifRow(
                            icon = Icons.Default.Notifications,
                            title = stringResource(R.string.notifications_filter_channel),
                            checked = filterChannel,
                            onChecked = { filterChannel = it; persistAppPrefs() },
                            enabled = masterEnabled,
                        )
                        HorizontalDivider(color = Mst.dividerOuter.copy(alpha = 0.5f))
                        notifRow(
                            icon = Icons.AutoMirrored.Filled.VolumeUp,
                            title = stringResource(R.string.notifications_smart_alert),
                            subtitle = stringResource(R.string.notifications_smart_alert_hint),
                            checked = smartAlert,
                            onChecked = { smartAlert = it; persistAppPrefs() },
                            enabled = masterEnabled,
                        )
                        HorizontalDivider(color = Mst.dividerOuter.copy(alpha = 0.5f))
                        notifRow(
                            icon = Icons.Default.Visibility,
                            title = stringResource(R.string.notifications_show_preview),
                            subtitle = stringResource(R.string.notifications_show_preview_hint),
                            checked = showPreview,
                            onChecked = { showPreview = it; persistAppPrefs() },
                            enabled = masterEnabled,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun notifRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Mst.accent, modifier = Modifier.padding(top = 2.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Mst.text, fontSize = 16.sp)
            subtitle?.let {
                Text(it, color = Mst.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Mst.accent,
                checkedTrackColor = Mst.accent.copy(alpha = 0.45f),
                uncheckedThumbColor = Mst.switchThumbUnchecked,
                uncheckedTrackColor = Mst.switchTrackUnchecked,
            ),
        )
    }
}
