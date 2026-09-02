package com.dhikr.app.feature.settings

import com.dhikr.app.core.datastore.HapticMode
import com.dhikr.app.core.datastore.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The settings screen must not paint a pill row until the persisted values have
 * loaded, otherwise a non-default saved choice (e.g. Theme = Dark) flashes the
 * default pill for one frame before snapping to the real selection.
 *
 * Full SettingsViewModel construction pulls in Android (Context, DataStore); as
 * with [SettingsKeyStateTest] this exercises the state contract directly.
 */
class SettingsLoadedStateTest {

    @Test
    fun default_state_is_not_loaded() {
        assertFalse(SettingsUiState().loaded)
    }

    @Test
    fun state_built_from_persisted_values_is_loaded() {
        // Mirrors the shape SettingsViewModel emits once its DataStore combine
        // produces its first value.
        val loaded = SettingsUiState(
            themeMode = ThemeMode.DARK,
            hapticMode = HapticMode.OFF,
            loaded = true,
        )
        assertTrue(loaded.loaded)
        assertEquals(ThemeMode.DARK, loaded.themeMode)
    }
}
