package io.github.thucnguyen79.aitranslator

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import io.github.thucnguyen79.aitranslator.core.packs.PackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps pack downloads running while the app is in the background, with a progress notification.
 * Started by [AppContainer.startDownloads]; stops itself once nothing is queued or downloading.
 */
class PackDownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watcher: Job? = null

    override fun onBind(intent: Intent?) = null

    // The notification follows the app's language, not the phone's.
    override fun attachBaseContext(base: Context) = super.attachBaseContext(UiLanguage.wrap(base))

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.packs_notification_channel), NotificationManager.IMPORTANCE_LOW),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, notification(null), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        if (watcher == null) {
            val packs = (application as AiTranslatorApp).container.packs
            watcher = scope.launch {
                packs.states.collect { states ->
                    val busy = states.values.any { it is PackState.Downloading || it == PackState.Installing || it == PackState.Queued }
                    if (!busy) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        val downloading = states.values.filterIsInstance<PackState.Downloading>().firstOrNull()
                        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(downloading))
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(progress: PackState.Downloading?): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(getString(R.string.packs_notification_title))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .apply {
                if (progress != null) {
                    setContentText(getString(R.string.pack_progress, (progress.bytes / 1_000_000).toInt(), (progress.total / 1_000_000).toInt()))
                    setProgress(1000, (progress.progress * 1000).toInt(), false)
                } else {
                    setProgress(0, 0, true)
                }
            }
            .build()
    }

    private companion object {
        const val CHANNEL = "pack_downloads"
        const val NOTIFICATION_ID = 1
    }
}
