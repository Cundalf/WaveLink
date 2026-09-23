package com.cundalf.wavelink

import android.app.Application
import android.content.Context
import com.cundalf.wavelink.data.LanguageStore
import com.cundalf.wavelink.data.LocaleHelper
import com.cundalf.wavelink.data.RadioRepository
import com.cundalf.wavelink.data.SettingsRepository

class SubWaveCastApplication : Application() {
    val radioRepository: RadioRepository by lazy { RadioRepository(this) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
    val languageStore: LanguageStore by lazy { LanguageStore(this) }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrap(base))
    }
}
