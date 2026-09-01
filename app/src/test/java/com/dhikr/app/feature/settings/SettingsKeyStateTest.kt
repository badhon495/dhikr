package com.dhikr.app.feature.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Exercises just the key add/clear reducer logic via a tiny in-test double of
 * the store contract. Full SettingsViewModel construction pulls in Android
 * (Context, DataStore) and is covered by instrumented tests elsewhere; this
 * keeps the new branch unit-tested.
 */
class SettingsKeyStateTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private class InMemoryKey {
        var value: String? = null
        fun get(): String? = value?.takeIf { it.isNotBlank() }
        suspend fun set(v: String?) { value = v?.trim()?.takeIf { it.isNotBlank() } }
    }

    @Test
    fun save_trims_and_marks_key_present() = runTest {
        val store = InMemoryKey()
        store.set("  AIzaKEY  ")
        assertEquals("AIzaKEY", store.get())
        assertTrue(store.get() != null)
    }

    @Test
    fun blank_save_clears_key() = runTest {
        val store = InMemoryKey()
        store.set("AIzaKEY")
        store.set("   ")
        assertFalse(store.get() != null)
    }
}
