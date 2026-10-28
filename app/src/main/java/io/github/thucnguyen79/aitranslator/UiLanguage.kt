package io.github.thucnguyen79.aitranslator

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * The app's own UI language (F10.1): Vietnamese by default whatever the phone's language, or English. Each
 * half of a conversation still uses its seat's language (SeatStrings).
 */
object UiLanguage {
    const val VI = "vi"
    const val EN = "en"
    private const val KEY = "ui_lang"

    fun get(context: Context): String = prefs(context).getString(KEY, VI) ?: VI

    fun set(context: Context, code: String) = prefs(context).edit().putString(KEY, code).apply()

    /** [base] with its resources in the chosen language: for attachBaseContext of activities and services. */
    fun wrap(base: Context): Context {
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale.forLanguageTag(get(base)))
        return base.createConfigurationContext(config)
    }

    private fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
}
