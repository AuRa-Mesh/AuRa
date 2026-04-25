package com.example.aura.progression

import android.content.Context
import android.util.Log
import com.example.aura.preferences.NodeScopedStorage
import com.example.aura.security.NodeAuthStore
import java.nio.charset.StandardCharsets
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Защищённый счётчик суммарного опыта по **текущему node id**: значение + HMAC в SharedPreferences,
 * ключи разделены по [NodeScopedStorage.nodeKey] (как VIP и аптайм).
 */
internal object ExperienceSecureStore {

    private const val TAG = "ExperienceSecureStore"
    private const val PREFS = "aura_au_secure_v1"
    private const val KEY_TOTAL_BASE = "total_au_v1"
    private const val KEY_SIG_BASE = "total_au_sig_v1"
    private const val KEY_MIGRATED_BASE = "migrated_from_legacy_v1"
    private const val KEY_LEGACY_GLOBAL_AU_IMPORTED = "legacy_global_lifetime_au_imported_v1"

    private val HMAC_SECRET: ByteArray = byteArrayOf(
        0x41.toByte(), 0x75.toByte(), 0x52.toByte(), 0x41.toByte(),
        0x2D.toByte(), 0x41.toByte(), 0x55.toByte(), 0x2D.toByte(),
        0x48.toByte(), 0x4D.toByte(), 0x41.toByte(), 0x43.toByte(),
        0x76.toByte(), 0x31.toByte(), 0x7E.toByte(), 0x5C.toByte(),
    )

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun hmacIdentity(ctx: Context): String {
        val id = NodeAuthStore.loadStoredIdentity(ctx.applicationContext)?.nodeId?.trim().orEmpty()
        return if (id.isNotEmpty()) id.lowercase(Locale.ROOT) else "_unbound_"
    }

    private fun totalKey(ctx: Context) = NodeScopedStorage.scopedKey(ctx, KEY_TOTAL_BASE)
    private fun sigKey(ctx: Context) = NodeScopedStorage.scopedKey(ctx, KEY_SIG_BASE)
    private fun migratedKey(ctx: Context) = NodeScopedStorage.scopedKey(ctx, KEY_MIGRATED_BASE)

    private fun hmacHex(identity: String, total: Long): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(HMAC_SECRET, "HmacSHA256"))
        val msg = "$identity|$total".toByteArray(StandardCharsets.UTF_8)
        val raw = mac.doFinal(msg)
        return raw.joinToString("") { b -> "%02x".format(b) }
    }

    private fun verify(identity: String, total: Long, sigHex: String?): Boolean {
        if (sigHex.isNullOrEmpty()) return false
        return hmacHex(identity, total).equals(sigHex, ignoreCase = false)
    }

    fun migrateLegacyIfNeeded(ctx: Context) {
        val app = ctx.applicationContext
        val p = prefs(app)
        if (p.getBoolean(migratedKey(app), false)) return
        val nk = NodeScopedStorage.nodeKey(app)
        if (nk == NodeScopedStorage.UNBOUND) {
            return
        }
        if (!p.getBoolean(KEY_LEGACY_GLOBAL_AU_IMPORTED, false)) {
            val legacy = app.getSharedPreferences("aura_experience_v1", Context.MODE_PRIVATE)
                .getLong("lifetime_au", 0L)
                .coerceAtLeast(0L)
            if (legacy > 0L) {
                writeTotalUnchecked(app, legacy)
                legacyPrefsCleanup(app)
                p.edit().putBoolean(KEY_LEGACY_GLOBAL_AU_IMPORTED, true).apply()
            }
        }
        p.edit().putBoolean(migratedKey(app), true).apply()
    }

    private fun legacyPrefsCleanup(app: Context) {
        app.getSharedPreferences("aura_experience_v1", Context.MODE_PRIVATE)
            .edit()
            .remove("lifetime_au")
            .apply()
    }

    fun readTotalExperience(ctx: Context): Long {
        migrateLegacyIfNeeded(ctx)
        val app = ctx.applicationContext
        val p = prefs(app)
        val identity = hmacIdentity(app)
        val tk = totalKey(app)
        val sk = sigKey(app)
        val v = p.getLong(tk, 0L).coerceAtLeast(0L)
        val sig = p.getString(sk, null)
        if (v == 0L && sig == null) return 0L
        if (!verify(identity, v, sig)) {
            Log.w(TAG, "experience HMAC mismatch for node scope — reset counter")
            p.edit().remove(tk).remove(sk).apply()
            return 0L
        }
        return v
    }

    fun writeTotalExperience(ctx: Context, total: Long) {
        val v = total.coerceAtLeast(0L)
        writeTotalUnchecked(ctx.applicationContext, v)
    }

    private fun writeTotalUnchecked(app: Context, total: Long) {
        val identity = hmacIdentity(app)
        val sig = hmacHex(identity, total)
        prefs(app).edit()
            .putLong(totalKey(app), total)
            .putString(sigKey(app), sig)
            .apply()
    }
}
