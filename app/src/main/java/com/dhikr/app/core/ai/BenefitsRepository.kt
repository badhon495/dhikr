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
) {

    suspend fun generate(tasbihId: String): GeminiResult {
        val key = getKey()?.takeIf { it.isNotBlank() }
            ?: return GeminiResult.Error(GeminiResult.Kind.NO_KEY, "no API key configured")
        val tasbih = getTasbih(tasbihId)
            ?: return GeminiResult.Error(GeminiResult.Kind.UNKNOWN, "tasbih not found")

        return when (val result = gemini.generateContent(key, buildBenefitsPrompt(tasbih))) {
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
        ): BenefitsRepository = BenefitsRepository(
            getKey = keyStore::getGeminiKey,
            gemini = gemini,
            getTasbih = tasbihRepository::getById,
            saveBenefits = tasbihRepository::saveBenefits,
        )
    }
}

internal fun buildBenefitsPrompt(tasbih: TasbihEntity): String = buildString {
    appendLine("You are an Islamic knowledge assistant. For the following dhikr, describe its")
    appendLine("virtues and benefits (fada'il) as reported in the Qur'an and authentic Sunnah.")
    appendLine()
    appendLine("Name: ${tasbih.name}")
    appendLine("Arabic: ${tasbih.arabic}")
    appendLine("Pronunciation: ${tasbih.pronunciation}")
    appendLine("Translation: ${tasbih.translation}")
    tasbih.source?.takeIf { it.isNotBlank() }?.let { appendLine("Source: $it") }
    appendLine()
    appendLine("Rules:")
    appendLine("- Only cite what is established in authentic sources; name the source (surah/ayah,")
    appendLine("  or hadith collection) where possible.")
    appendLine("- If a commonly-attributed benefit is weak or fabricated, say so briefly.")
    appendLine("- 4-8 short bullet points. No greeting, no preamble.")
    append("- If you cannot verify benefits for this specific wording, say that plainly.")
}
