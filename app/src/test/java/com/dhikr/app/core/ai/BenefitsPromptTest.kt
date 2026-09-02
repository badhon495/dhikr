package com.dhikr.app.core.ai

import com.dhikr.app.core.database.entity.TasbihEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BenefitsPromptTest {

    private fun tasbih(source: String? = null) = TasbihEntity(
        id = "t1", name = "Tasbih", arabic = "سُبْحَانَ اللَّه", pronunciation = "SubhanAllah",
        translation = "Glory be to Allah", source = source,
        lapTarget = 33, lapCount = 1, isBuiltIn = true, createdAt = 0L, updatedAt = 0L,
    )

    @Test
    fun default_english_prompt_substitutes_all_tokens() {
        val p = buildBenefitsPrompt(tasbih(source = "Sahih Muslim 2691"), BenefitsLanguage.ENGLISH, null)
        assertTrue(p.contains("Tasbih"))
        assertTrue(p.contains("سُبْحَانَ اللَّه"))
        assertTrue(p.contains("SubhanAllah"))
        assertTrue(p.contains("Glory be to Allah"))
        assertTrue(p.contains("Sahih Muslim 2691"))
        assertFalse(p.contains("{name}"))
        assertFalse(p.contains("{source}"))
    }

    @Test
    fun blank_source_drops_the_source_line_and_token() {
        val p = buildBenefitsPrompt(tasbih(source = null), BenefitsLanguage.ENGLISH, null)
        assertFalse(p.contains("{source}"))
        assertFalse(p.lineSequence().any { it.trimStart().startsWith("Source:") })
    }

    @Test
    fun english_prompt_appends_english_directive() {
        val p = buildBenefitsPrompt(tasbih(), BenefitsLanguage.ENGLISH, null)
        assertTrue(p.trimEnd().endsWith(ENGLISH_DIRECTIVE))
    }

    @Test
    fun bangla_prompt_appends_bangla_directive_and_uses_bangla_default() {
        val p = buildBenefitsPrompt(tasbih(), BenefitsLanguage.BANGLA, null)
        assertTrue(p.trimEnd().endsWith(BANGLA_DIRECTIVE))
        // Bangla default template contains Bengali script.
        assertTrue(p.any { it in 'ঀ'..'৿' })
    }

    @Test
    fun override_replaces_the_template_but_directive_still_appended() {
        val p = buildBenefitsPrompt(
            tasbih(),
            BenefitsLanguage.BANGLA,
            "Only say the word {name} and nothing else.",
        )
        assertTrue(p.contains("Only say the word Tasbih and nothing else."))
        assertFalse(p.contains("{name}"))
        assertTrue(p.trimEnd().endsWith(BANGLA_DIRECTIVE))
    }

    @Test
    fun override_without_tokens_is_used_verbatim() {
        val p = buildBenefitsPrompt(tasbih(), BenefitsLanguage.ENGLISH, "Describe this dhikr.")
        assertTrue(p.startsWith("Describe this dhikr."))
    }

    @Test
    fun defaultTemplateFor_returns_language_specific_text() {
        assertTrue(defaultBenefitsTemplate(BenefitsLanguage.ENGLISH).contains("{name}"))
        assertTrue(defaultBenefitsTemplate(BenefitsLanguage.BANGLA).contains("{name}"))
        assertTrue(defaultBenefitsTemplate(BenefitsLanguage.BANGLA).any { it in 'ঀ'..'৿' })
    }
}
