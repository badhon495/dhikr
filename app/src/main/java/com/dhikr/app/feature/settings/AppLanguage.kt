package com.dhikr.app.feature.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * The three UI-language choices offered in Settings. Persistence and the actual
 * locale switch go through [AppCompatDelegate.setApplicationLocales] — with
 * `autoStoreLocales` in the manifest, AppCompat stores the choice itself and
 * re-applies it on the next launch (API 33+ delegates to the platform). So there
 * is no DataStore key for this; [current] reads the live locale list back.
 */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    ENGLISH("en"),
    BANGLA("bn");

    companion object {
        /** The active choice, derived from the application locale list. An empty
         *  list means "follow the system"; otherwise we match on the primary
         *  language subtag so region variants (e.g. "bn-BD") still map to BANGLA. */
        val current: AppLanguage
            get() {
                val locales = AppCompatDelegate.getApplicationLocales()
                if (locales.isEmpty) return SYSTEM
                val language = locales[0]?.language ?: return SYSTEM
                return entries.firstOrNull { it.tag == language } ?: SYSTEM
            }

        fun apply(language: AppLanguage) {
            val list = language.tag
                ?.let { LocaleListCompat.forLanguageTags(it) }
                ?: LocaleListCompat.getEmptyLocaleList()
            AppCompatDelegate.setApplicationLocales(list)
        }
    }
}
