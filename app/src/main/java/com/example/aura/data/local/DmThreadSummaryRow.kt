package com.example.aura.data.local

import androidx.room.ColumnInfo

/**
 * Агрегат по личному чату с [dmPeerNodeNum]: [lastMsgAt] = макс. [createdAtMs] в БД, [lastPreview] — текст
 * **последнего** по времени ряда (и входящие, и исходящие); [unreadCount] — только невычитанные **входящие**.
 * Сортировка по свежести — в SQL [ORDER BY lastMsgAt DESC].
 */
data class DmThreadSummaryRow(
    @ColumnInfo(name = "dmPeerNodeNum") val dmPeerNodeNum: Long,
    @ColumnInfo(name = "lastMsgAt") val lastMsgAt: Long,
    @ColumnInfo(name = "lastPreview") val lastPreview: String?,
    @ColumnInfo(name = "unreadCount") val unreadCount: Int,
)
