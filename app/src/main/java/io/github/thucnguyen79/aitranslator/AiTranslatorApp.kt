package io.github.thucnguyen79.aitranslator

import android.app.Application

class AiTranslatorApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
