package com.example.aura.history

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.annotation.Keep
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Пароль для экрана «История сообщений»: соль и SHA-256 в [EncryptedSharedPreferences].
 */
@Keep
object MessageHistorySecureAuth {

    private const val FILE = "message_history_secure_auth"
    private const val K_SALT = "salt_b64"
    private const val K_HASH = "hash_b64"

    private fun prefs(context: Context): SharedPreferences {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            FILE,
            masterKeyAlias,
            context.applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun hasPassword(context: Context): Boolean =
        prefs(context).contains(K_HASH)

    fun setPassword(context: Context, password: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = digest(password, salt)
        prefs(context).edit()
            .putString(K_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(K_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .apply()
    }

    fun verify(context: Context, password: String): Boolean {
        val p = prefs(context)
        val saltB64 = p.getString(K_SALT, null) ?: return false
        val hashB64 = p.getString(K_HASH, null) ?: return false
        val salt = Base64.decode(saltB64, Base64.NO_WRAP)
        val expected = Base64.decode(hashB64, Base64.NO_WRAP)
        val actual = digest(password, salt)
        return actual.contentEquals(expected)
    }

    private fun digest(password: String, salt: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        md.update(password.toByteArray(Charsets.UTF_8))
        return md.digest()
    }
}
