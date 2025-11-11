package com.privacyguard.batteryanalyzer.domain

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.ArrayMap
import android.util.Log
import com.privacyguard.batteryanalyzer.data.local.AppUsageStatus
import com.privacyguard.batteryanalyzer.data.local.TrackedAppDao
import com.privacyguard.batteryanalyzer.data.local.TrackedAppEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class UsageAnalyzer(
    private val context: Context,
    private val trackedAppDao: TrackedAppDao,
    private val usagePolicy: UsagePolicy
) {

    private val packageManager: PackageManager = context.packageManager
    private val usageStatsManager: UsageStatsManager = context.getSystemService(UsageStatsManager::class.java)
        ?: throw IllegalStateException("UsageStatsManager not available")

    fun updatePolicyThresholds(allowDurationMillis: Long): Boolean {
        return usagePolicy.updateThresholds(allowDurationMillis)
    }

    suspend fun evaluateUsage(): UsageEvaluation = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val existing = trackedAppDao.getAll().associateBy { it.packageName }
        val installedApps = queryUserInstalledApps()

        val lastUsedMap = queryLastUsedTimestamps(now)

        val updates = mutableListOf<TrackedAppEntity>()
        val packagesToRemove = existing.keys.toMutableSet()
        val appsToNotify = mutableListOf<TrackedAppEntity>()
        val appsForDisableRecommendation = mutableListOf<TrackedAppEntity>()

        for (appInfo in installedApps) {
            val packageName = appInfo.packageName

            if (usagePolicy.shouldSkip(packageName)) {
                continue
            }

            packagesToRemove.remove(packageName)

            val label = runCatching { packageManager.getApplicationLabel(appInfo).toString() }
                .getOrElse { packageName }
            val existingEntity = existing[packageName]
            val lastUsed = resolveLastUsed(packageName, lastUsedMap, existingEntity, appInfo)
            val isDisabled = isPackageDisabled(packageName)

            val notifyAt = lastUsed?.let { it + usagePolicy.warningThresholdMillis }
            val disableAt = lastUsed?.let { it + usagePolicy.disableThresholdMillis }

            var scheduledDisableAt = when {
                isDisabled -> null
                disableAt != null -> disableAt
                else -> null
            }
            var notifiedAt = if (isDisabled) null else existingEntity?.notifiedAt

            val shouldRecommendDisable = !isDisabled && disableAt != null && now >= disableAt && (notifiedAt == null || notifiedAt < disableAt)
            val resolvedStatus = when {
                isDisabled -> AppUsageStatus.DISABLED
                lastUsed != null && now - lastUsed <= usagePolicy.recentThresholdMillis -> AppUsageStatus.RECENT
                else -> AppUsageStatus.RARE
            }

            if (shouldRecommendDisable) {
                appsForDisableRecommendation += TrackedAppEntity(
                    packageName = packageName,
                    appLabel = label,
                    lastUsedAt = lastUsed ?: 0L,
                    status = AppUsageStatus.RARE,
                    isDisabled = false,
                    scheduledDisableAt = disableAt,
                    notifiedAt = now
                )
                scheduledDisableAt = disableAt
                notifiedAt = now
            } else if (!isDisabled && notifyAt != null && now >= notifyAt && (notifiedAt == null || notifiedAt < notifyAt) && (disableAt == null || now < disableAt)) {
                appsToNotify += TrackedAppEntity(
                    packageName = packageName,
                    appLabel = label,
                    lastUsedAt = lastUsed ?: 0L,
                    status = resolvedStatus,
                    isDisabled = false,
                    scheduledDisableAt = disableAt,
                    notifiedAt = now
                )
                notifiedAt = now
            } else if (disableAt == null) {
                notifiedAt = null
            }

            val resolvedIsDisabled = isDisabled
            val entity = TrackedAppEntity(
                packageName = packageName,
                appLabel = label,
                lastUsedAt = lastUsed ?: 0L,
                status = resolvedStatus,
                isDisabled = resolvedIsDisabled,
                scheduledDisableAt = scheduledDisableAt,
                notifiedAt = notifiedAt
            )
            updates += entity
        }

        UsageEvaluation(
            updates = updates,
            packagesToRemove = packagesToRemove.toList(),
            appsToNotify = appsToNotify,
            appsForDisableRecommendation = appsForDisableRecommendation
        )
    }

    private fun queryUserInstalledApps(): List<ApplicationInfo> {
        val apps = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getInstalledApplications(
                    PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstalledApplications(
                    PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_COMPONENTS
                )
            }
        }.getOrElse { throwable ->
            Log.w(TAG, "Failed to query installed applications", throwable)
            emptyList()
        }

        return apps.filter { appInfo: ApplicationInfo ->
            appInfo.packageName != context.packageName &&
                (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0
        }
    }

    private fun queryLastUsedTimestamps(now: Long): Map<String, Long> {
        val map = ArrayMap<String, Long>()
        val startTime = now - TimeUnit.DAYS.toMillis(30)
        val events = runCatching { usageStatsManager.queryEvents(startTime, now) }
            .onFailure { throwable ->
                Log.w(TAG, "Failed to query usage events", throwable)
            }
            .getOrNull() ?: return emptyMap()
        val usageEvent = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(usageEvent)
            if (usageEvent.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                usageEvent.eventType == UsageEvents.Event.ACTIVITY_RESUMED
            ) {
                val packageName = usageEvent.packageName ?: continue
                if (packageName == context.packageName || packageName.isBlank()) continue
                val previous = map[packageName] ?: 0L
                if (usageEvent.timeStamp > previous) {
                    map[packageName] = usageEvent.timeStamp
                }
            }
        }
        return map
    }

    private fun resolveLastUsed(
        packageName: String,
        lastUsedMap: Map<String, Long>,
        existing: TrackedAppEntity?,
        appInfo: ApplicationInfo
    ): Long? {
        val fromUsage = lastUsedMap[packageName]
        if (fromUsage != null) return fromUsage

        val previous = existing?.lastUsedAt?.takeIf { it > 0 }
        if (previous != null) return previous

        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            maxOf(packageInfo.firstInstallTime, packageInfo.lastUpdateTime)
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to get fallback timestamp for $packageName", t)
            null
        }
    }

    private fun isPackageDisabled(packageName: String): Boolean {
        return runCatching {
            packageManager.getApplicationEnabledSetting(packageName) in disabledStates
        }.getOrDefault(false)
    }

    companion object {
        private const val TAG = "UsageAnalyzer"
        private val disabledStates = setOf(
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED
        )
    }
}
