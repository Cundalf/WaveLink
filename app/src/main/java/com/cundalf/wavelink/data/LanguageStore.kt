package com.cundalf.wavelink.data

import android.content.Context
import android.content.res.Configuration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

enum class Language(
    val tag: String,
) {
    SYSTEM(""),
    ES("es"),
    EN("en"),
    ;

    companion object {
        fun fromTag(tag: String?): Language = entries.firstOrNull { it.tag == tag } ?: SYSTEM
    }
}

/**
 * Idioma elegido por el usuario. Vive en SharedPreferences (y no en DataStore)
 * porque debe poder leerse de forma síncrona en attachBaseContext.
 */
class LanguageStore(
    context: Context,
) {
    // En attachBaseContext (o en tests Robolectric) applicationContext puede no
    // estar disponible todavía: se usa el context directo como fallback.
    private val prefs =
        (context.applicationContext ?: context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _language = MutableStateFlow(Language.fromTag(prefs.getString(KEY_LANGUAGE, null)))
    val language: StateFlow<Language> = _language

    fun setLanguage(language: Language) {
        prefs.edit().putString(KEY_LANGUAGE, language.tag).apply()
        _language.value = language
    }

    fun currentTag(): String? = prefs.getString(KEY_LANGUAGE, null)

    companion object {
        private const val PREFS_NAME = "language_store"
        private const val KEY_LANGUAGE = "language"
    }
}

object LocaleHelper {
    /** Envuelve el contexto con el idioma configurado en la app (SYSTEM = sin cambios). */
    fun wrap(context: Context): Context {
        val language = Language.fromTag(LanguageStore(context).currentTag())
        if (language == Language.SYSTEM) return context
        val locale = Locale.forLanguageTag(language.tag)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }
}
