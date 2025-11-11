package com.privacyguard.batteryanalyzer

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.privacyguard.batteryanalyzer.firewall.VpnFirewallService
import com.privacyguard.batteryanalyzer.work.UsageSyncWorker
import com.privacyguard.batteryanalyzer.di.AppContainer
import java.util.concurrent.TimeUnit

class PrivacyGuardApp : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setMinimumLoggingLevel(android.util.Log.INFO).build()

    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        scheduleUsageSyncWork()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = getSystemService(NotificationManager::class.java) ?: return
        val usageChannel = NotificationChannel(
            UsageSyncWorker.NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.notification_channel_description)
        }
        manager.createNotificationChannel(usageChannel)

        val firewallChannel = NotificationChannel(
            VpnFirewallService.FIREWALL_CHANNEL_ID,
            getString(R.string.firewall_notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.firewall_notification_channel_description)
        }
        manager.createNotificationChannel(firewallChannel)
    }

    private fun scheduleUsageSyncWork() {
        val request = PeriodicWorkRequestBuilder<UsageSyncWorker>(6, TimeUnit.HOURS)
            .setInitialDelay(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            UsageSyncWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
