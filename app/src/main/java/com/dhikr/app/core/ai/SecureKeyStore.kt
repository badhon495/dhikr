package com.dhikr.app.core.ai

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Encrypted at-rest storage for the user's own Google Gemini API key.
 * Backed by [EncryptedSharedPreferences]; the key never leaves the device
 * except in the request to Google's Gemini API.
 */
class SecureKeyStore(context: Context) {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            "ai_secrets",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun getGeminiKey(): String? = prefs.getString(KEY_GEMINI, null)?.takeIf { it.isNotBlank() }

    val hasKey: Boolean get() = getGeminiKey() != null

    suspend fun setGeminiKey(value: String?) = withContext(Dispatchers.IO) {
        val trimmed = value?.trim()
        prefs.edit().apply {
            if (trimmed.isNullOrBlank()) remove(KEY_GEMINI) else putString(KEY_GEMINI, trimmed)
        }.apply()
    }

    private companion object {
        const val KEY_GEMINI = "gemini_api_key"
    }
}
