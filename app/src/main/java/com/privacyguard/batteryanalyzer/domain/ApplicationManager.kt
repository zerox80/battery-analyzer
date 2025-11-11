package com.privacyguard.batteryanalyzer.domain

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.privacyguard.batteryanalyzer.data.local.AppUsageStatus
import com.privacyguard.batteryanalyzer.data.local.TrackedAppDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ApplicationManager(
    private val context: Context,
    private val trackedAppDao: TrackedAppDao
) {

    private val packageManager: PackageManager = context.packageManager

    suspend fun disablePackage(packageName: String) = withContext(Dispatchers.IO) {
        runCatching {
            if (packageName == context.packageName) return@withContext false
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(packageName, 0)
            }
            if ((appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0) {
                return@withContext false
            }
            packageManager.setApplicationEnabledSetting(
                packageName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
                PackageManager.DONT_KILL_APP
            )
            trackedAppDao.updateStatus(
                packageName = packageName,
                status = AppUsageStatus.DISABLED,
                disabled = true,
                scheduledAt = null,
                notifiedAt = null
            )
            true
        }.onFailure { throwable ->
            Log.w(TAG, "Failed to disable $packageName", throwable)
        }.getOrDefault(false)
    }

    suspend fun restorePackage(packageName: String) = withContext(Dispatchers.IO) {
        runCatching {
            packageManager.setApplicationEnabledSetting(
                packageName,
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                PackageManager.DONT_KILL_APP
            )
            trackedAppDao.updateStatus(
                packageName = packageName,
                status = AppUsageStatus.RECENT,
                disabled = false,
                scheduledAt = null,
                notifiedAt = null
            )
            true
        }.onFailure { throwable ->
            Log.w(TAG, "Failed to restore $packageName", throwable)
        }.getOrDefault(false)
    }

    suspend fun isPackageDisabled(packageName: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            packageManager.getApplicationEnabledSetting(packageName) in setOf(
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
            )
        }.getOrDefault(false)
    }

    companion object {
        private const val TAG = "ApplicationManager"
    }
}
