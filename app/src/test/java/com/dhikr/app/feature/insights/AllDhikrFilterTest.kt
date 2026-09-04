package com.dhikr.app.feature.insights

import com.dhikr.app.core.database.TasbihHistoryGroup
import org.junit.Assert.assertEquals
import org.junit.Test

class AllDhikrFilterTest {

    private fun group(name: String) =
        TasbihHistoryGroup(tasbihId = name, tasbihName = name, lifetimeTotal = 0, dailyTotals = emptyList())

    private val all = listOf(group("Astaghfirullah"), group("SubhanAllah"), group("Durood Sharif"))

    @Test
    fun blank_query_returns_all_unchanged() {
        assertEquals(all, filterDhikrGroups(all, ""))
        assertEquals(all, filterDhikrGroups(all, "   "))
    }

    @Test
    fun matches_case_insensitive_substring_of_name() {
        assertEquals(listOf(group("SubhanAllah")), filterDhikrGroups(all, "subhan"))
    }

    @Test
    fun query_matching_multiple_names_keeps_all_matches_in_order() {
        assertEquals(
            listOf(group("Astaghfirullah"), group("Durood Sharif")),
            filterDhikrGroups(all, "r"),
        )
    }

    @Test
    fun no_match_returns_empty_list() {
        assertEquals(emptyList<TasbihHistoryGroup>(), filterDhikrGroups(all, "zzz"))
    }

    @Test
    fun query_is_trimmed_before_matching() {
        assertEquals(listOf(group("SubhanAllah")), filterDhikrGroups(all, "  subhan  "))
    }
}
