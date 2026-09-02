package com.dhikr.app.core.ai

import com.dhikr.app.core.database.entity.TasbihEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BenefitsRepositoryTest {

    private fun tasbih(source: String? = null) = TasbihEntity(
        id = "t1", name = "Tasbih", arabic = "سُبْحَانَ اللَّه", pronunciation = "SubhanAllah",
        translation = "Glory be to Allah", source = source,
        lapTarget = 33, lapCount = 1, isBuiltIn = true, createdAt = 0L, updatedAt = 0L,
    )

    // -- fakes --

    private class FakeKeyStore(var key: String?) {
        fun getGeminiKey(): String? = key?.takeIf { it.isNotBlank() }
    }

    private class FakeGemini(val result: GeminiResult) : GeminiApi {
        var calls = 0
        var lastPrompt: String? = null
        override suspend fun generateContent(apiKey: String, prompt: String): GeminiResult {
            calls++; lastPrompt = prompt; return result
        }
    }

    private class FakeTasbihStore(private val entity: TasbihEntity?) {
        var savedId: String? = null
        var savedText: String? = null
        var savedAt: Long? = null
        suspend fun getById(id: String): TasbihEntity? = entity?.takeIf { it.id == id }
        suspend fun saveBenefits(id: String, text: String, generatedAt: Long) {
            savedId = id; savedText = text; savedAt = generatedAt
        }
    }

    // The repo takes small function/lambda seams so these fakes wire in without
    // depending on the concrete SecureKeyStore / TasbihRepository classes.
    private fun repo(
        keyStore: FakeKeyStore,
        gemini: FakeGemini,
        store: FakeTasbihStore,
        language: BenefitsLanguage = BenefitsLanguage.ENGLISH,
        override: String? = null,
    ) = BenefitsRepository(
        getKey = keyStore::getGeminiKey,
        gemini = gemini,
        getTasbih = store::getById,
        saveBenefits = store::saveBenefits,
        getLanguage = { language },
        getPromptOverride = { override },
    )

    @Test
    fun no_key_returns_NO_KEY_and_never_calls_gemini() = runTest {
        val gemini = FakeGemini(GeminiResult.Success("x"))
        val store = FakeTasbihStore(tasbih())
        val r = repo(FakeKeyStore(null), gemini, store).generate("t1")
        assertEquals(GeminiResult.Kind.NO_KEY, (r as GeminiResult.Error).kind)
        assertEquals(0, gemini.calls)
    }

    @Test
    fun missing_tasbih_returns_UNKNOWN() = runTest {
        val gemini = FakeGemini(GeminiResult.Success("x"))
        val store = FakeTasbihStore(null)
        val r = repo(FakeKeyStore("k"), gemini, store).generate("t1")
        assertEquals(GeminiResult.Kind.UNKNOWN, (r as GeminiResult.Error).kind)
        assertEquals(0, gemini.calls)
    }

    @Test
    fun success_caches_text_and_timestamp() = runTest {
        val gemini = FakeGemini(GeminiResult.Success("• virtue one"))
        val store = FakeTasbihStore(tasbih())
        val r = repo(FakeKeyStore("k"), gemini, store).generate("t1")
        assertTrue(r is GeminiResult.Success)
        assertEquals("t1", store.savedId)
        assertEquals("• virtue one", store.savedText)
        assertTrue((store.savedAt ?: 0L) > 0L)
    }

    @Test
    fun error_from_gemini_is_returned_and_not_cached() = runTest {
        val gemini = FakeGemini(GeminiResult.Error(GeminiResult.Kind.NETWORK, "down"))
        val store = FakeTasbihStore(tasbih())
        val r = repo(FakeKeyStore("k"), gemini, store).generate("t1")
        assertEquals(GeminiResult.Kind.NETWORK, (r as GeminiResult.Error).kind)
        assertEquals(null, store.savedId)
    }

    @Test
    fun prompt_includes_all_fields_and_omits_absent_source() = runTest {
        val gemini = FakeGemini(GeminiResult.Success("ok"))
        val store = FakeTasbihStore(tasbih(source = null))
        repo(FakeKeyStore("k"), gemini, store).generate("t1")
        val p = gemini.lastPrompt!!
        assertTrue(p.contains("SubhanAllah"))
        assertTrue(p.contains("Glory be to Allah"))
        assertTrue(p.contains("سُبْحَانَ اللَّه"))
        assertFalse(p.contains("Source:"))
    }

    @Test
    fun prompt_includes_source_when_present() = runTest {
        val gemini = FakeGemini(GeminiResult.Success("ok"))
        val store = FakeTasbihStore(tasbih(source = "Sahih Muslim 2691"))
        repo(FakeKeyStore("k"), gemini, store).generate("t1")
        assertTrue(gemini.lastPrompt!!.contains("Source: Sahih Muslim 2691"))
    }

    @Test
    fun bangla_language_appends_bangla_directive_to_prompt() = runTest {
        val gemini = FakeGemini(GeminiResult.Success("ok"))
        val store = FakeTasbihStore(tasbih())
        repo(FakeKeyStore("k"), gemini, store, language = BenefitsLanguage.BANGLA).generate("t1")
        assertTrue(gemini.lastPrompt!!.trimEnd().endsWith(BANGLA_DIRECTIVE))
    }

    @Test
    fun prompt_override_is_used_instead_of_default_template() = runTest {
        val gemini = FakeGemini(GeminiResult.Success("ok"))
        val store = FakeTasbihStore(tasbih())
        repo(FakeKeyStore("k"), gemini, store, override = "Just describe {name}.").generate("t1")
        assertTrue(gemini.lastPrompt!!.startsWith("Just describe Tasbih."))
    }
}
