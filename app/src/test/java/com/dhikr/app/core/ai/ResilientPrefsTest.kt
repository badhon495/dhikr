package com.dhikr.app.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import java.security.GeneralSecurityException

class ResilientPrefsTest {

    @Test
    fun returns_value_from_first_successful_open() {
        var resets = 0
        val result = openResettingOnCorruption(reset = { resets++ }, open = { "prefs" })
        assertEquals("prefs", result)
        assertEquals(0, resets)
    }

    @Test
    fun wipes_once_then_retries_when_first_open_throws_GeneralSecurityException() {
        var resets = 0
        var attempts = 0
        val result = openResettingOnCorruption(
            reset = { resets++ },
            open = {
                attempts++
                if (attempts == 1) throw GeneralSecurityException("bad tag") else "recovered"
            },
        )
        assertEquals("recovered", result)
        assertEquals(1, resets)
        assertEquals(2, attempts)
    }

    @Test
    fun wipes_once_then_retries_when_first_open_throws_IOException() {
        var resets = 0
        var attempts = 0
        val result = openResettingOnCorruption(
            reset = { resets++ },
            open = {
                attempts++
                if (attempts == 1) throw IOException("keyset unreadable") else "recovered"
            },
        )
        assertEquals("recovered", result)
        assertEquals(1, resets)
    }

    @Test
    fun rethrows_when_retry_after_wipe_also_fails() {
        var resets = 0
        val thrown = assertThrows(GeneralSecurityException::class.java) {
            openResettingOnCorruption(
                reset = { resets++ },
                open = { throw GeneralSecurityException("still broken") },
            )
        }
        assertEquals("still broken", thrown.message)
        assertEquals(1, resets)
    }

    @Test
    fun does_not_wipe_on_unrelated_runtime_exception() {
        var resets = 0
        assertThrows(IllegalStateException::class.java) {
            openResettingOnCorruption(
                reset = { resets++ },
                open = { throw IllegalStateException("programmer error") },
            )
        }
        assertEquals(0, resets)
    }
}
