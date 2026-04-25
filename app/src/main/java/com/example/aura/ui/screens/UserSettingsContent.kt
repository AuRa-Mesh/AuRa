package com.example.aura.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aura.meshwire.MeshWireNodeUserProfile
import com.example.aura.ui.screens.MeshWireSettingsScreenColors as Mst

@Composable
fun UserSettingsContent(
    padding: PaddingValues,
    nodeId: String = "",
    /** MAC привязанной ноды — для GATT want_config / FromRadio. */
    deviceAddress: String? = null,
    bootstrap: MeshWireNodeUserProfile? = null,
    onBootstrapConsumed: () -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Mst.bg)
            .padding(padding)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
    ) {
        item {
            Text(
                text = "Настройки пользователя",
                color = Mst.text,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        item {
            MeshWireUserConfigBlock(
                modifier = Modifier,
                nodeId = nodeId,
                deviceAddress = deviceAddress,
                bootstrap = bootstrap,
                onBootstrapConsumed = onBootstrapConsumed,
                palette = MeshWireUserConfigPalette.Settings,
                fieldsEnabled = true,
                elevatedCard = true,
                showSettingsSyncUi = true,
            )
        }
    }
}
