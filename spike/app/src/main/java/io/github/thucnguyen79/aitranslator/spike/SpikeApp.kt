package io.github.thucnguyen79.aitranslator.spike

import android.app.Application
import io.github.thucnguyen79.aitranslator.spike.core.SpikePaths
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class SpikeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        paths = SpikePaths(this).also { it.ensureDirs(this) }
    }

    companion object {
        lateinit var instance: SpikeApp
            private set
        lateinit var paths: SpikePaths
            private set

        /** Benchmarks outlive tab switches, so they run in an app-wide scope. */
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
