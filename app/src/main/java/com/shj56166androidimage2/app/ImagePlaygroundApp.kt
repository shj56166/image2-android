package com.shj56166androidimage2.app

import android.app.Application
import androidx.work.Configuration
import com.shj56166androidimage2.app.data.repo.AppContainer
import com.shj56166androidimage2.app.util.applyAppLanguagePreference
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class ImagePlaygroundApp : Application(), Configuration.Provider {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        runBlocking {
            applyAppLanguagePreference(container.settingsRepository.settings.first().appLanguage)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
