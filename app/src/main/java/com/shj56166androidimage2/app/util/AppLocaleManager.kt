package com.shj56166androidimage2.app.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.shj56166androidimage2.app.data.model.AppLanguage

internal fun applyAppLanguagePreference(language: AppLanguage) {
    val locales =
        when (language) {
            AppLanguage.SYSTEM -> LocaleListCompat.getEmptyLocaleList()
            AppLanguage.ENGLISH -> LocaleListCompat.forLanguageTags("en")
            AppLanguage.SIMPLIFIED_CHINESE -> LocaleListCompat.forLanguageTags("zh-Hans")
            AppLanguage.TRADITIONAL_CHINESE -> LocaleListCompat.forLanguageTags("zh-Hant")
        }
    AppLocaleState.preferredLocale = locales[0]
    AppCompatDelegate.setApplicationLocales(locales)
}

internal fun currentAppDisplayLanguage(context: Context): AppDisplayLanguage {
    val locale = AppCompatDelegate.getApplicationLocales()[0] ?: context.resources.configuration.locales[0]
    return currentAppDisplayLanguage(locale)
}
