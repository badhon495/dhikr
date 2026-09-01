package com.dhikr.app.core.ai

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
}
