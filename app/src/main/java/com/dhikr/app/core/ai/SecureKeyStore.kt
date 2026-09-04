package com.dhikr.app.core.ai

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.GeneralSecurityException

/**
 * Encrypted at-rest storage for the user's own Google Gemini API key.
 * Backed by [EncryptedSharedPreferences]; the key never leaves the device
 * except in the request to Google's Gemini API.
 *
 * If the backing file can't be decrypted — most commonly after Android
 * auto-backup restores it onto a new device whose hardware keystore never
 * received the master key — opening it once wipes the unreadable file and
 * starts fresh (see [openResettingOnCorruption]). The stored key is lost in
 * that case and the user re-enters it; the alternative is a crash on every
 * screen that reads the store.
 */
class SecureKeyStore(context: Context) {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences by lazy {
        openResettingOnCorruption(
            reset = { deletePrefsFile(PREFS_NAME) },
            open = { createEncryptedPrefs() },
        )
    }

    private fun createEncryptedPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            appContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /** Deletes the on-disk prefs file (data + Tink keysets) so the next
     *  [createEncryptedPrefs] rebuilds it under the current master key. */
    private fun deletePrefsFile(name: String) {
        appContext.deleteSharedPreferences(name)
        // deleteSharedPreferences only removes an already-loaded instance's
        // file lazily on some OS versions; remove the backing file directly too.
        val dir = appContext.applicationInfo.dataDir
        java.io.File("$dir/shared_prefs/$name.xml").delete()
    }

    fun getGeminiKey(): String? =
        try {
            prefs.getString(KEY_GEMINI, null)?.takeIf { it.isNotBlank() }
        } catch (e: GeneralSecurityException) {
            // A single entry failed to decrypt even though the file opened.
            // Treat as "no key" rather than crashing the caller.
            null
        } catch (e: IOException) {
            null
        }

    val hasKey: Boolean get() = getGeminiKey() != null

    suspend fun setGeminiKey(value: String?) = withContext(Dispatchers.IO) {
        val trimmed = value?.trim()
        prefs.edit().apply {
            if (trimmed.isNullOrBlank()) remove(KEY_GEMINI) else putString(KEY_GEMINI, trimmed)
        }.apply()
    }

    private companion object {
        const val PREFS_NAME = "ai_secrets"
        const val KEY_GEMINI = "gemini_api_key"
    }
}
