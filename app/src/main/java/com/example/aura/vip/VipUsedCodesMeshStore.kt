package com.example.aura.vip

import android.content.Context
import com.example.aura.meshwire.MeshWireAuraVipUsedCodesCodec
import java.util.concurrent.ConcurrentHashMap

/**
 * Храним «какие VIP-коды чужие узлы уже использовали» — чтобы помочь соседям восстановить
 * одноразовость после переустановки.
 *
 * Схема хранения:
 *  `nodeNum & 0xFFFFFFFF` → `LinkedHashSet<hex(hash)>` (8 байт = 16 hex-символов).
 *  LinkedHashSet сохраняет порядок вставки: при переполнении [MAX_HASHES_PER_PEER] самые старые
 *  записи вытесняются, поэтому memory-footprint ограничен.
 *
 * Данные сериализуются в [android.content.SharedPreferences] и бэкапятся Android Auto Backup
 * (peers сохраняют ваши хэши даже между своими установками).
 *
 * Источники записей:
 *  - [MeshWireAuraVipUsedCodesCodec.Kind.ANNOUNCE] — пир только что потратил код → добавляем хэши;
 *  - [MeshWireAuraVipUsedCodesCodec.Kind.RESPONSE] — пришёл ответ на наш запрос восстановления
 *    от **другого** узла (не нас), принимаем и сохраняем. Но саму подгрузку в свой локальный
 *    регистр делает [com.example.aura.security.VipExtensionUsedCodes.acceptMeshHashes].
 */
object VipUsedCodesMeshStore {

    private const val PREFS = "vip_used_codes_mesh_store"
    private const val KEY_ENTRIES = "peer_used_code_hashes_v1"

    /** Предел хэшей на одного пира — защита от переполнения памяти/диска. */
    const val MAX_HASHES_PER_PEER: Int = 64

    private val perPeerHashes = ConcurrentHashMap<Long, LinkedHashSet<String>>()

    @Volatile
    private var loaded: Boolean = false

    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val prefs = context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.getStringSet(KEY_ENTRIES, emptySet())?.forEach { raw ->
                parseEntry(raw)?.let { (node, hex) ->
                    val set = perPeerHashes.getOrPut(node) { LinkedHashSet() }
                    if (set.size < MAX_HASHES_PER_PEER) set.add(hex)
                }
            }
            loaded = true
        }
    }

    /** Запомнить, что [subjectNodeNum] использовал эти хэши. */
    fun remember(context: Context, subjectNodeNum: UInt?, hashes: List<ByteArray>) {
        if (subjectNodeNum == null || subjectNodeNum == 0u || hashes.isEmpty()) return
        ensureLoaded(context)
        val key = subjectNodeNum.toLong() and 0xFFFF_FFFFL
        val set = perPeerHashes.getOrPut(key) { LinkedHashSet() }
        var changed = false
        synchronized(set) {
            for (h in hashes) {
                if (h.size != MeshWireAuraVipUsedCodesCodec.HASH_LEN) continue
                val hex = bytesToHex(h)
                if (hex !in set) {
                    // При переполнении вытесняем самый старый — LinkedHashSet это делает
                    // при removeFirst() по iterator().next().
                    if (set.size >= MAX_HASHES_PER_PEER) {
                        val first = set.iterator().next()
                        set.remove(first)
                    }
                    set.add(hex)
                    changed = true
                }
            }
        }
        if (changed) persist(context)
    }

    /** Снимок хэшей, которые мы помним за [subjectNodeNum] (пригодится для RESPONSE). */
    fun snapshot(context: Context, subjectNodeNum: UInt?): List<ByteArray> {
        if (subjectNodeNum == null || subjectNodeNum == 0u) return emptyList()
        ensureLoaded(context)
        val key = subjectNodeNum.toLong() and 0xFFFF_FFFFL
        val set = perPeerHashes[key] ?: return emptyList()
        return synchronized(set) { set.mapNotNull { hexToBytes(it) } }
    }

    private fun persist(context: Context) {
        val snapshot = HashSet<String>()
        for ((node, set) in perPeerHashes) {
            synchronized(set) {
                for (hex in set) snapshot.add("$node:$hex")
            }
        }
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_ENTRIES, snapshot)
            .apply()
    }

    private fun parseEntry(raw: String): Pair<Long, String>? {
        val sep = raw.indexOf(':')
        if (sep <= 0) return null
        val key = raw.substring(0, sep).toLongOrNull()?.and(0xFFFF_FFFFL) ?: return null
        val hex = raw.substring(sep + 1)
        if (hex.length != MeshWireAuraVipUsedCodesCodec.HASH_LEN * 2) return null
        return key to hex
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4])
            sb.append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    private fun hexToBytes(hex: String): ByteArray? {
        if (hex.length % 2 != 0) return null
        val out = ByteArray(hex.length / 2)
        for (i in out.indices) {
            val hi = hexDigit(hex[i * 2])
            val lo = hexDigit(hex[i * 2 + 1])
            if (hi < 0 || lo < 0) return null
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    private fun hexDigit(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> -1
    }

    private val HEX = charArrayOf(
        '0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', 'a', 'b', 'c', 'd', 'e', 'f',
    )
}
