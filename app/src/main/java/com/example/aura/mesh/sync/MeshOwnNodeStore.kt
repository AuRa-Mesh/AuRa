package com.example.aura.mesh.sync

import android.content.Context

/**
 * Краткий снимок «своей» ноды после синхронизации (MyNodeInfo / профиль пользователя).
 */
data class MeshOwnNodeSnapshot(
    val nodeNumHex: String?,
    val hardwareModel: String?,
    val regionLabel: String?,
)

/**
 * Локальное хранилище для отображения в UI (как в типичном mesh-клиенте после want_config).
 */
object MeshOwnNodeStore {
    private const val PREFS = "aura_mesh_own_node"
    private const val KEY_NUM = "node_num_hex"
    private const val KEY_HW = "hw_model"
    private const val KEY_REGION = "region"

    fun save(context: Context, snap: MeshOwnNodeSnapshot) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_NUM, snap.nodeNumHex)
            .putString(KEY_HW, snap.hardwareModel)
            .putString(KEY_REGION, snap.regionLabel)
            .apply()
    }

    fun load(context: Context): MeshOwnNodeSnapshot? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val num = p.getString(KEY_NUM, null) ?: return null
        return MeshOwnNodeSnapshot(
            nodeNumHex = num,
            hardwareModel = p.getString(KEY_HW, null),
            regionLabel = p.getString(KEY_REGION, null),
        )
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
