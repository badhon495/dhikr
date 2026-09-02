package com.dhikr.app.feature

import com.dhikr.app.feature.home.HomeUiState
import com.dhikr.app.feature.insights.InsightsUiState
import com.dhikr.app.feature.routines.RoutineEditorUiState
import com.dhikr.app.feature.routines.RoutinesUiState
import com.dhikr.app.feature.tasbih.TasbihEditorUiState
import com.dhikr.app.feature.tasbih.TasbihLibraryUiState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every screen whose ViewModel is rebuilt on each navigation seeds its state
 * with a synthetic default before the real DataStore/Room values land a frame
 * later. Each such UiState carries a `loaded` flag that is false for that
 * synthetic default and true once real values are applied; the screen gates its
 * content on it so a non-default saved value never flashes the default first.
 *
 * These check the contract at the data-class level (full ViewModel construction
 * pulls in Android — Context, DataStore, Room — and is covered by instrumented
 * tests). Mirrors [com.dhikr.app.feature.settings.SettingsLoadedStateTest].
 */
class LoadedStateContractTest {

    @Test
    fun synthetic_defaults_are_not_loaded() {
        assertFalse("Home", HomeUiState().loaded)
        assertFalse("TasbihLibrary", TasbihLibraryUiState().loaded)
        assertFalse("Routines", RoutinesUiState().loaded)
        assertFalse("Insights", InsightsUiState().loaded)
    }

    @Test
    fun editor_default_is_not_loaded_but_populated_state_is() {
        assertFalse(TasbihEditorUiState().loaded)
        assertFalse(RoutineEditorUiState().loaded)

        assertTrue(TasbihEditorUiState(loaded = true).loaded)
        assertTrue(RoutineEditorUiState(loaded = true).loaded)
    }

    @Test
    fun editor_tracks_whether_an_existing_item_is_being_opened() {
        // The screen needs this synchronously (before the DB read) to decide
        // whether to gate: a brand-new item has nothing to wait for.
        assertFalse(TasbihEditorUiState().isEditingExisting)
        assertTrue(TasbihEditorUiState(isEditingExisting = true).isEditingExisting)

        assertFalse(RoutineEditorUiState().isEditingExisting)
        assertTrue(RoutineEditorUiState(isEditingExisting = true).isEditingExisting)
    }
}
