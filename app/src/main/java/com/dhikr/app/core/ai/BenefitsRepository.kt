package com.dhikr.app.core.ai

import com.dhikr.app.core.database.TasbihRepository
import com.dhikr.app.core.database.entity.TasbihEntity

/**
 * Turns a tasbih into a cached "virtues and benefits" write-up via Gemini.
 *
 * The lambda seams (`getKey`, `getTasbih`, `saveBenefits`) keep this class
 * testable without the concrete [SecureKeyStore] / [TasbihRepository]; the
 * production [invoke]-style factory below wires the real ones.
 */
class BenefitsRepository internal constructor(
    private val getKey: () -> String?,
    private val gemini: GeminiApi,
    private val getTasbih: suspend (String) -> TasbihEntity?,
    private val saveBenefits: suspend (id: String, text: String, generatedAt: Long) -> Unit,
    private val getLanguage: suspend () -> BenefitsLanguage,
    private val getPromptOverride: suspend () -> String?,
) {

    suspend fun generate(tasbihId: String): GeminiResult {
        val key = getKey()?.takeIf { it.isNotBlank() }
            ?: return GeminiResult.Error(GeminiResult.Kind.NO_KEY, "no API key configured")
        val tasbih = getTasbih(tasbihId)
            ?: return GeminiResult.Error(GeminiResult.Kind.UNKNOWN, "tasbih not found")

        val prompt = buildBenefitsPrompt(tasbih, getLanguage(), getPromptOverride())
        return when (val result = gemini.generateContent(key, prompt)) {
            is GeminiResult.Success -> {
                saveBenefits(tasbihId, result.text, System.currentTimeMillis())
                result
            }
            is GeminiResult.Error -> result
        }
    }

    companion object {
        /** Production wiring. */
        fun create(
            keyStore: SecureKeyStore,
            gemini: GeminiApi,
            tasbihRepository: TasbihRepository,
            getLanguage: suspend () -> BenefitsLanguage,
            getPromptOverride: suspend () -> String?,
        ): BenefitsRepository = BenefitsRepository(
            getKey = keyStore::getGeminiKey,
            gemini = gemini,
            getTasbih = tasbihRepository::getById,
            saveBenefits = tasbihRepository::saveBenefits,
            getLanguage = getLanguage,
            getPromptOverride = getPromptOverride,
        )
    }
}
