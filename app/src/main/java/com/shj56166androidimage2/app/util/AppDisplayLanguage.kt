package com.shj56166androidimage2.app.util

import java.util.Locale

internal enum class AppDisplayLanguage {
    EN,
    ZH_HANS,
    ZH_HANT,
}

internal fun currentAppDisplayLanguage(locale: Locale = currentPreferredLocale()): AppDisplayLanguage {
    if (locale.language != "zh") return AppDisplayLanguage.EN

    val script = locale.script.lowercase(Locale.ROOT)
    val country = locale.country.uppercase(Locale.ROOT)
    return when {
        script == "hant" -> AppDisplayLanguage.ZH_HANT
        country == "TW" || country == "HK" || country == "MO" -> AppDisplayLanguage.ZH_HANT
        else -> AppDisplayLanguage.ZH_HANS
    }
}

internal object AppLocaleState {
    @Volatile
    var preferredLocale: Locale? = null
}

private fun currentPreferredLocale(): Locale = AppLocaleState.preferredLocale ?: Locale.getDefault()
