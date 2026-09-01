package com.dhikr.app.core.database

import com.dhikr.app.core.database.entity.TasbihEntity
import org.junit.Assert.assertNull
import org.junit.Test

class TasbihBenefitsColumnsTest {

    private fun sample() = TasbihEntity(
        id = "x", name = "n", arabic = "", pronunciation = "p", translation = "",
        lapTarget = 33, lapCount = 1, isBuiltIn = false, createdAt = 0L, updatedAt = 0L,
    )

    @Test
    fun benefits_fields_default_to_null() {
        val t = sample()
        assertNull(t.benefitsText)
        assertNull(t.benefitsGeneratedAt)
    }

    @Test
    fun benefits_fields_are_copyable() {
        val t = sample().copy(benefitsText = "• virtue", benefitsGeneratedAt = 123L)
        assert(t.benefitsText == "• virtue")
        assert(t.benefitsGeneratedAt == 123L)
    }
}
