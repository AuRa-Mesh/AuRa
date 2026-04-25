package com.example.aura.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aura.ui.screens.MeshWireSettingsScreenColors as Mst

/** Полоска 0–100 % при загрузке секции настроек с ноды по BLE. */
@Composable
fun SettingsTabLoadProgressBar(
    percent: Int?,
    modifier: Modifier = Modifier,
) {
    val p = percent ?: return
    val clamped = p.coerceIn(0, 100)
    Column(modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { clamped / 100f },
            modifier = Modifier.fillMaxWidth(),
            color = Mst.accent,
            trackColor = Mst.dividerInCard,
        )
        Text(
            "$clamped%",
            color = Mst.muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
