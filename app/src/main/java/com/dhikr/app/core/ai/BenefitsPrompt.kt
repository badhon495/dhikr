package com.dhikr.app.core.ai

import com.dhikr.app.core.database.entity.TasbihEntity

/**
 * Language the AI benefits write-up is generated in. Chosen once, app-wide, in
 * Settings. Only affects the Gemini prompt and its response — not the app UI,
 * which follows the system/AppCompat locale (see AppLanguage).
 */
enum class BenefitsLanguage { ENGLISH, BANGLA }

/** A hard directive appended to every prompt so a user-edited template that
 *  drops the language instruction still produces output in the right language. */
internal const val ENGLISH_DIRECTIVE = "Respond entirely in English."
internal const val BANGLA_DIRECTIVE =
    "সম্পূর্ণ উত্তরটি বাংলা ভাষায় (বাংলা লিপিতে) দাও। কুরআন ও হাদিসের আরবি উদ্ধৃতি আরবিতেই থাকবে।"

/**
 * The built-in prompt template for a language. Shown pre-filled in the
 * "Customize prompt" field; used as-is when the user hasn't overridden it.
 *
 * Placeholder tokens — `{name}`, `{arabic}`, `{pronunciation}`, `{translation}`,
 * `{source}` — are substituted at generation time. A line whose only dynamic
 * content is a token that resolves to blank (e.g. `{source}` with no source) is
 * dropped entirely.
 */
fun defaultBenefitsTemplate(language: BenefitsLanguage): String = when (language) {
    BenefitsLanguage.ENGLISH -> DEFAULT_TEMPLATE_EN
    BenefitsLanguage.BANGLA -> DEFAULT_TEMPLATE_BN
}

private val DEFAULT_TEMPLATE_EN = """
    You are an Islamic knowledge assistant. For the following dhikr, describe its
    virtues and benefits (fada'il) as reported in the Qur'an and authentic Sunnah.

    Name: {name}
    Arabic: {arabic}
    Pronunciation: {pronunciation}
    Translation: {translation}
    Source: {source}

    Rules:
    - Only cite what is established in authentic sources; name the source (surah/ayah,
      or hadith collection) where possible.
    - If a commonly-attributed benefit is weak or fabricated, say so briefly.
    - 4-8 short bullet points. No greeting, no preamble.
    - If you cannot verify benefits for this specific wording, say that plainly.
""".trimIndent()

private val DEFAULT_TEMPLATE_BN = """
    তুমি একজন ইসলামি জ্ঞান সহায়ক। নিচের যিকিরটির ফযিলত ও উপকারিতা (ফাযায়িল)
    কুরআন ও সহিহ সুন্নাহ অনুযায়ী বর্ণনা করো।

    নাম: {name}
    আরবি: {arabic}
    উচ্চারণ: {pronunciation}
    অনুবাদ: {translation}
    সূত্র: {source}

    নিয়ম:
    - কেবল সহিহ সূত্রে প্রমাণিত বিষয় উল্লেখ করো; সম্ভব হলে সূত্রের নাম দাও
      (সূরা/আয়াত অথবা হাদিস গ্রন্থ)।
    - প্রচলিত কোনো ফযিলত যদি দুর্বল বা জাল হয়, সংক্ষেপে তা উল্লেখ করো।
    - ৪-৮টি সংক্ষিপ্ত বুলেট পয়েন্ট। কোনো সম্ভাষণ বা ভূমিকা নয়।
    - এই নির্দিষ্ট শব্দগুচ্ছের ফযিলত যাচাই করতে না পারলে স্পষ্টভাবে তা বলো।
""".trimIndent()

// Note the escaped \} — Android's ICU regex engine rejects a bare '}' here,
// even though java.util.regex (used by the JVM unit tests) tolerates it.
private val TOKEN_LINE = Regex("""^\s*\S[^{}]*\{(\w+)\}\s*$""")
private val TOKEN = Regex("""\{(\w+)\}""")

/**
 * Builds the final prompt string sent to Gemini.
 *
 * @param override user's edited template, or null to use the language default.
 */
fun buildBenefitsPrompt(
    tasbih: TasbihEntity,
    language: BenefitsLanguage,
    override: String?,
): String {
    val template = override?.takeIf { it.isNotBlank() } ?: defaultBenefitsTemplate(language)
    val values = mapOf(
        "name" to tasbih.name,
        "arabic" to tasbih.arabic,
        "pronunciation" to tasbih.pronunciation,
        "translation" to tasbih.translation,
        "source" to tasbih.source.orEmpty(),
    )

    val body = template.lineSequence()
        .filterNot { line ->
            // Drop a "Label: {token}" line whose token resolves to blank.
            TOKEN_LINE.find(line)?.groupValues?.get(1)?.let { values[it]?.isBlank() } == true
        }
        .joinToString("\n") { line ->
            line.replace(TOKEN) { m -> values[m.groupValues[1]] ?: m.value }
        }
        .trimEnd()

    val directive = when (language) {
        BenefitsLanguage.ENGLISH -> ENGLISH_DIRECTIVE
        BenefitsLanguage.BANGLA -> BANGLA_DIRECTIVE
    }
    return "$body\n\n$directive"
}
