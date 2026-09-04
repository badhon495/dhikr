package com.dhikr.app.core.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SecureKeyStoreTest {

    private lateinit var store: SecureKeyStore

    @Before
    fun setUp() {
        store = SecureKeyStore(ApplicationProvider.getApplicationContext())
        runBlocking { store.setGeminiKey(null) }
    }

    @Test
    fun absent_key_reads_null() {
        assertNull(store.getGeminiKey())
        assertFalse(store.hasKey)
    }

    @Test
    fun set_then_get_round_trips() = runBlocking {
        store.setGeminiKey("AIzaTESTKEY123")
        assertEquals("AIzaTESTKEY123", store.getGeminiKey())
        assertTrue(store.hasKey)
    }

    @Test
    fun blank_value_clears_the_key() = runBlocking {
        store.setGeminiKey("something")
        store.setGeminiKey("   ")
        assertNull(store.getGeminiKey())
    }

    @Test
    fun null_value_clears_the_key() = runBlocking {
        store.setGeminiKey("something")
        store.setGeminiKey(null)
        assertNull(store.getGeminiKey())
    }

    /**
     * Simulates a backup restored onto a device whose keystore can't decrypt
     * the file: a valid-looking but undecryptable `ai_secrets.xml`. A fresh
     * [SecureKeyStore] must recover (wipe + rebuild) instead of crashing.
     */
    @Test
    fun undecryptable_backing_file_is_wiped_and_store_still_works() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val file = File(context.applicationInfo.dataDir, "shared_prefs/ai_secrets.xml")
        file.parentFile?.mkdirs()
        file.writeText(
            """<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
               <map>
                 <string name="__androidx_security_crypto_encrypted_prefs_key_keyset__">DEADBEEF</string>
                 <string name="AaBbCc">not-real-ciphertext</string>
               </map>""".trimIndent(),
        )

        val recovered = SecureKeyStore(context)
        assertNull(recovered.getGeminiKey())
        assertFalse(recovered.hasKey)

        recovered.setGeminiKey("AIzaAFTERRECOVERY")
        assertEquals("AIzaAFTERRECOVERY", recovered.getGeminiKey())
    }
}
