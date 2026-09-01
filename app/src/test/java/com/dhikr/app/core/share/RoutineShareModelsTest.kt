package com.dhikr.app.core.share

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlinx.serialization.json.Json

class RoutineShareModelsTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun sample() = RoutineShareFile(
        format = SHARE_FORMAT,
        version = SHARE_VERSION,
        createdAt = 1_726_000_000_000L,
        appVersionName = "1.0",
        routines = listOf(
            ShareRoutine(
                name = "Morning Dhikr",
                steps = listOf(
                    ShareRoutineStep(tasbihId = "subhan", stepOrder = 0, targetCount = 33),
                    ShareRoutineStep(tasbihId = "custom-1", stepOrder = 1, targetCount = 10),
                ),
            ),
        ),
        tasbih = listOf(
            ShareTasbih(
                id = "custom-1", name = "My Dhikr", arabic = "x", pronunciation = "y",
                translation = "z", note = "n", source = "s", lapTarget = 10, lapCount = 1,
                dailyGoal = 100,
            ),
        ),
    )

    @Test
    fun roundTrip_isLossless() {
        val text = json.encodeToString(RoutineShareFile.serializer(), sample())
        val back = json.decodeFromString(RoutineShareFile.serializer(), text)
        assertEquals(sample(), back)
    }

    @Test
    fun unknownKey_deserializesWithoutThrowing() {
        val text = """
            {"format":"dhikr.routine","version":1,"createdAt":0,"appVersionName":"",
             "routines":[{"name":"R","steps":[]}],"tasbih":[],"somethingNew":42}
        """.trimIndent()
        val back = json.decodeFromString(RoutineShareFile.serializer(), text)
        assertEquals("R", back.routines.single().name)
    }
}
