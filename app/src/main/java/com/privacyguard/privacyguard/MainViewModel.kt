package com.privacyguard.privacyguard

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.TrafficStats
import android.os.Build
import android.os.RemoteException
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.privacyguard.privacyguard.data.local.AppUsageStatus
import com.privacyguard.privacyguard.data.repository.UsageRepository
import com.privacyguard.privacyguard.di.AppContainer
import com.privacyguard.privacyguard.domain.ApplicationManager
import com.privacyguard.privacyguard.domain.UsageAnalyzer
import com.privacyguard.privacyguard.firewall.FirewallController
import com.privacyguard.privacyguard.notifications.NotificationHelper
import com.privacyguard.privacyguard.settings.SettingsPreferencesDataSource
import com.privacyguard.privacyguard.ui.state.AppHomeState
import com.privacyguard.privacyguard.util.UsagePermissionChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.collections.buildList

class MainViewModel(
    private val appContext: Context,
    private val usageAnalyzer: UsageAnalyzer,
    private val applicationManager: ApplicationManager,
    private val usageRepository: UsageRepository,
    private val firewallController: FirewallController,
    private val settingsPreferences: SettingsPreferencesDataSource
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppHomeState())
    val uiState: StateFlow<AppHomeState> = _uiState.asStateFlow()

    private val packageManager = appContext.packageManager
    private val networkStatsManager = appContext.getSystemService(NetworkStatsManager::class.java)
    private val uidCache = mutableMapOf<String, Int>()
    private val trafficSnapshots = mutableMapOf<String, Long>()
    private val trafficHistory = mutableMapOf<String, ArrayDeque<Pair<Long, Long>>>()
    private var metricsJob: Job? = null
    private var manualUnblockSchedulerJob: Job? = null
    private val trafficWindowMillisRef = AtomicLong(TimeUnit.MINUTES.toMillis(10))
    private val trafficSampleIntervalMillis = TimeUnit.SECONDS.toMillis(30)
    private val manualUnblockCooldown = mutableMapOf<String, Long>()

    private var subscriptionsStarted = false
    private val blockThresholdMillisRef = AtomicLong(TimeUnit.DAYS.toMillis(4))
    private val firewallAllowlist = setOf(
        "com.google.android.youtube",
        // Sparkassen-Apps
        "com.starfinanz.mobile.android.pushtan",
        "de.starfinanz.smob.android.sfinanzstatus", // Sparkasse
        "de.starfinanz.smob.android.sfinanzstatus.tablet",
        "biz.first_financial.bk01_2fa" // BK Secure
    )

    init {
        observeSettings()
        subscribeToData()
        subscribeToFirewall()
        refreshUsage()
    }

    private fun subscribeToFirewall() {
        viewModelScope.launch {
            firewallController.state.collect { state ->
                _uiState.value = _uiState.value.copy(
                    firewallState = state,
                    firewallBlockedPackages = state.blockedPackages
                )
            }
        }
    }

// aktiviert die Firewall 
    fun enableFirewall() {
        viewModelScope.launch {
            val state = _uiState.value
            val blockList = computeBlockList(state)
            Log.i(TAG, "enableFirewall requested. manual=${state.manualFirewallUnblock}, blockCount=${blockList.size}")
            if (state.manualFirewallUnblock) {
                firewallController.applyManualBlockList(blockList)
            } else {
                firewallController.enableFirewall(blockList, state.allowDurationMillis)
            }
        }
    }

    fun disableFirewall() {
        viewModelScope.launch {
            Log.i(TAG, "disableFirewall requested")
            firewallController.disableFirewall()
        }
    }

    fun blockNow() {
        viewModelScope.launch {
            val state = _uiState.value
            val blockList = computeBlockList(state)
            Log.i(TAG, "blockNow requested. manual=${state.manualFirewallUnblock} blockCount=${blockList.size}")
            if (state.manualFirewallUnblock) {
                firewallController.applyManualBlockList(blockList)
            } else {
                firewallController.blockNow(blockList)
            }
        }
    }

    fun allowForConfiguredDuration() {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.manualFirewallUnblock) {
                Log.i(TAG, "Manual firewall mode active; skipping allowForConfiguredDuration")
                return@launch
            }
            val blockList = computeBlockList()
            Log.i(TAG, "allowForDuration requested for ${state.allowDurationMillis}ms. blockCount=${blockList.size}")
            firewallController.allowForDuration(state.allowDurationMillis, blockList)
        }
    }

    fun updateAllowDuration(durationMillis: Long) {
        viewModelScope.launch {
            settingsPreferences.setAllowDurationMillis(durationMillis)
        }
    }

    fun setMetricsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsPreferences.setMetricsEnabled(enabled)
            if (!enabled) {
                stopMetricsMonitor()
            }
        }
    }

    fun setManualFirewallUnblock(enabled: Boolean) {
        viewModelScope.launch {
            Log.i(TAG, "setManualFirewallUnblock -> $enabled")
            settingsPreferences.setManualFirewallUnblock(enabled)
            if (enabled) {
                val state = _uiState.value.copy(manualFirewallUnblock = true)
                val blockList = computeBlockList(state)
                Log.i(TAG, "Manual mode enabled. Applying manual block list with ${blockList.size} packages")
                firewallController.applyManualBlockList(blockList)
                scheduleManualUnblockSync()
            } else {
                manualUnblockSchedulerJob?.cancel()
                manualUnblockSchedulerJob = null
                manualUnblockCooldown.clear()
                syncFirewallBlockList()
            }
        }
    }

    fun refreshMetricsNow() {
        if (!_uiState.value.metricsEnabled) return
        viewModelScope.launch(Dispatchers.IO) {
            sampleAppTraffic()
        }
    }

    fun manualUnblockPackage(packageName: String) {
        viewModelScope.launch {
            Log.i(TAG, "manualUnblockPackage -> $packageName")
            val now = System.currentTimeMillis()
            val scheduledReblockAt = _uiState.value.firewallState.reactivateAt
            val allowDurationMillis = _uiState.value.allowDurationMillis
            val candidateExpiries = buildList {
                scheduledReblockAt?.takeIf { it > now }?.let { add(it) }
                if (allowDurationMillis > 0) {
                    add(now + allowDurationMillis)
                }
            }
            val cooldownUntil = candidateExpiries.minOrNull() ?: (now + blockThresholdMillisRef.get())
            Log.d(TAG, "manualUnblockPackage cooldown -> pkg=$packageName until=$cooldownUntil candidates=${candidateExpiries.size}")
            manualUnblockCooldown[packageName] = cooldownUntil
            val current = _uiState.value.firewallBlockedPackages.toMutableSet()
            if (current.remove(packageName)) {
                Log.d(TAG, "Package removed from manual block set: $packageName")
                firewallController.updateBlockedPackages(current)
            }
            scheduleManualUnblockSync()
        }
    }

    fun refreshUsage() {
        viewModelScope.launch {
            val hasPermission = UsagePermissionChecker.isUsageAccessGranted(appContext)
            _uiState.value = _uiState.value.copy(usagePermissionGranted = hasPermission, isLoading = true)

            if (!hasPermission) {
                _uiState.value = AppHomeState(usagePermissionGranted = false, isLoading = false)
                return@launch
            }

            val result = runCatching {
                withContext(Dispatchers.IO) { usageAnalyzer.evaluateUsage() }
            }

            result.onSuccess { evaluation ->
                usageRepository.applyEvaluation(evaluation)

                evaluation.appsToNotify.forEach { info ->
                    NotificationHelper.showDisableReminderNotification(
                        context = appContext,
                        appLabel = info.appLabel,
                        packageName = info.packageName,
                        isRecommendation = false
                    )
                }

                evaluation.appsForDisableRecommendation.forEach { info ->
                    NotificationHelper.showDisableReminderNotification(
                        context = appContext,
                        appLabel = info.appLabel,
                        packageName = info.packageName,
                        isRecommendation = true
                    )
                }

                syncFirewallBlockList()
            }.onFailure { throwable ->
                Log.e(TAG, "Failed to refresh usage", throwable)
            }

            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    fun restoreApp(packageName: String) {
        viewModelScope.launch {
            applicationManager.restorePackage(packageName)
            refreshUsage()
        }
    }



    private fun subscribeToData() {
        if (subscriptionsStarted) return
        subscriptionsStarted = true

        launchStatusCollector(AppUsageStatus.RECENT) { recent ->
            _uiState.value = _uiState.value.copy(recentApps = recent, isLoading = false)
        }
        launchStatusCollector(AppUsageStatus.RARE) { rare ->
            _uiState.value = _uiState.value.copy(rareApps = rare, isLoading = false)
        }
        launchStatusCollector(AppUsageStatus.DISABLED) { disabled ->
            _uiState.value = _uiState.value.copy(disabledApps = disabled, isLoading = false)
        }
    }

    private fun launchStatusCollector(status: AppUsageStatus, onUpdate: (List<com.privacyguard.privacyguard.model.AppUsageInfo>) -> Unit) {
        viewModelScope.launch {
            usageRepository.observeStatus(status).collect {
                onUpdate(it)
                syncFirewallBlockList()
            }
        }
    }

    private fun computeBlockList(state: AppHomeState = _uiState.value): Set<String> {
        val now = System.currentTimeMillis()
        val thresholdMillis = blockThresholdMillisRef.get().coerceAtLeast(TimeUnit.MINUTES.toMillis(1))
        val threshold = now - thresholdMillis

        val rarePackages = state.rareApps
            .filter { info ->
                val lastUsed = info.lastUsedAt
                lastUsed == null || lastUsed <= threshold
            }
            .map { it.packageName }

        val disabledPackages = state.disabledApps.map { it.packageName }

        val iterator = manualUnblockCooldown.entries.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().value <= now) {
                iterator.remove()
            }
        }

        val result = if (state.manualFirewallUnblock) {
            val manualSet = state.firewallBlockedPackages.toMutableSet()

            disabledPackages.forEach { pkg ->
                manualUnblockCooldown.remove(pkg)
            }

            val additions = (rarePackages + disabledPackages).filter { pkg ->
                val cooldownExpiry = manualUnblockCooldown[pkg]
                cooldownExpiry == null || cooldownExpiry <= now
            }

            manualSet += additions
            manualSet.remove(appContext.packageName)
            manualSet.removeAll(firewallAllowlist)
            manualSet
        } else {
            (rarePackages + disabledPackages).toSet()
                .filterNot { it == appContext.packageName }
                .filterNot { it in firewallAllowlist }
                .toSet()
        }
        Log.d(TAG, "computeBlockList manual=${state.manualFirewallUnblock} -> ${result.size} packages")
        return result
    }

    private fun scheduleManualUnblockSync() {
        val nextExpiry = manualUnblockCooldown.values.minOrNull()
        if (nextExpiry == null) {
            manualUnblockSchedulerJob?.cancel()
            manualUnblockSchedulerJob = null
            return
        }

        val delayMillis = (nextExpiry - System.currentTimeMillis()).coerceAtLeast(0)
        manualUnblockSchedulerJob?.cancel()
        manualUnblockSchedulerJob = viewModelScope.launch {
            Log.d(TAG, "manualUnblock scheduler waiting ${delayMillis}ms for next expiry")
            delay(delayMillis)
            Log.i(TAG, "manualUnblock scheduler expired -> resyncing firewall")
            syncFirewallBlockList()
        }
    }

    private suspend fun syncFirewallBlockList() {
        val desired = computeBlockList()
        val current = _uiState.value.firewallBlockedPackages
        if (desired != current) {
            Log.i(TAG, "syncFirewallBlockList updating firewall. desired=${desired.size}, current=${current.size}")
            firewallController.updateBlockedPackages(desired)
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsPreferences.preferencesFlow.collect { prefs ->
                val previousState = _uiState.value
                val allowDurationChanged = prefs.allowDurationMillis != previousState.allowDurationMillis
                val previousMetricsEnabled = previousState.metricsEnabled
                val manualModeChanged = prefs.manualFirewallUnblock != previousState.manualFirewallUnblock

                if (allowDurationChanged) {
                    usageAnalyzer.updatePolicyThresholds(prefs.allowDurationMillis)
                    trafficWindowMillisRef.set(prefs.allowDurationMillis.coerceAtLeast(TimeUnit.MINUTES.toMillis(1)))
                    blockThresholdMillisRef.set(prefs.allowDurationMillis.coerceAtLeast(TimeUnit.MINUTES.toMillis(1)))
                }

                _uiState.value = previousState.copy(
                    allowDurationMillis = prefs.allowDurationMillis,
                    metricsEnabled = prefs.metricsEnabled,
                    manualFirewallUnblock = prefs.manualFirewallUnblock
                )

                when {
                    prefs.metricsEnabled && !previousMetricsEnabled -> startMetricsMonitor()
                    !prefs.metricsEnabled && previousMetricsEnabled -> stopMetricsMonitor()
                }

                if (allowDurationChanged) {
                    refreshUsage()
                }

                if (manualModeChanged) {
                    syncFirewallBlockList()
                }
            }
        }
    }

    private fun startMetricsMonitor() {
        if (metricsJob != null) return
        metricsJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                sampleAppTraffic()
                delay(trafficSampleIntervalMillis)
            }
        }
    }

    private fun stopMetricsMonitor() {
        metricsJob?.cancel()
        metricsJob = null
        trafficHistory.clear()
        trafficSnapshots.clear()
        _uiState.update { state ->
            state.copy(appTraffic = emptyMap(), metricsLastSampleAt = null)
        }
    }

    private fun resolveUid(packageName: String): Int? {
        uidCache[packageName]?.let { return it }
        return try {
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(packageName, 0)
            }
            appInfo.uid.also { uidCache[packageName] = it }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun sampleAppTraffic() {
        val trackedApps = (_uiState.value.recentApps + _uiState.value.rareApps + _uiState.value.disabledApps)
            .distinctBy { it.packageName }
        val trackedPackages = trackedApps.map { it.packageName }
        val now = System.currentTimeMillis()
        val windowMillis = trafficWindowMillisRef.get()

        if (trackedPackages.isEmpty()) {
            trafficHistory.clear()
            trafficSnapshots.clear()
            _uiState.update { state ->
                state.copy(appTraffic = emptyMap(), metricsLastSampleAt = now)
            }
            return
        }

        val trackedPackageSet = trackedPackages.toSet()
        val packageUidPairs = trackedPackages.mapNotNull { packageName ->
            resolveUid(packageName)?.let { uid -> packageName to uid }
        }

        val networkStatsSums = collectTrafficViaNetworkStats(packageUidPairs, now - windowMillis, now)

        val sums: Map<String, Long>
        if (networkStatsSums != null) {
            trafficHistory.clear()
            trafficSnapshots.clear()
            sums = networkStatsSums
        } else {
            val fallbackSums = mutableMapOf<String, Long>()
            for ((packageName, uid) in packageUidPairs) {
                collectTrafficFromSnapshots(packageName, uid, now, windowMillis)?.let { sum ->
                    fallbackSums[packageName] = sum
                }
            }

            val iterator = trafficHistory.keys.iterator()
            while (iterator.hasNext()) {
                val key = iterator.next()
                if (key !in trackedPackageSet) {
                    iterator.remove()
                    trafficSnapshots.remove(key)
                }
            }

            sums = fallbackSums
        }

        _uiState.update { state ->
            state.copy(
                appTraffic = sums,
                metricsLastSampleAt = now
            )
        }
    }

    private fun collectTrafficFromSnapshots(
        packageName: String,
        uid: Int,
        now: Long,
        windowMillis: Long
    ): Long? {
        val rxBytes = TrafficStats.getUidRxBytes(uid)
        val txBytes = TrafficStats.getUidTxBytes(uid)
        if (rxBytes < 0 || txBytes < 0) return null

        val totalBytes = rxBytes + txBytes
        val previousTotal = trafficSnapshots.put(packageName, totalBytes)
        val delta = if (previousTotal == null || totalBytes < previousTotal) 0L else totalBytes - previousTotal

        val history = trafficHistory.getOrPut(packageName) { ArrayDeque() }
        if (delta > 0) {
            history.addLast(now to delta)
        }
        while (history.isNotEmpty() && now - history.first().first > windowMillis) {
            history.removeFirst()
        }
        return history.sumOf { it.second }
    }

    private fun collectTrafficViaNetworkStats(
        packageUidPairs: List<Pair<String, Int>>,
        start: Long,
        end: Long
    ): Map<String, Long>? {
        val manager = networkStatsManager ?: return null
        if (packageUidPairs.isEmpty()) return emptyMap()

        val safeStart = start.coerceAtLeast(0L)
        val uidToPackage = packageUidPairs.associate { (packageName, uid) -> uid to packageName }
        val totals = mutableMapOf<String, Long>()
        val bucket = android.app.usage.NetworkStats.Bucket()

        val networkTypes = buildList {
            add(ConnectivityManager.TYPE_WIFI)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                add(ConnectivityManager.TYPE_ETHERNET)
            }
        }

        try {
            for (networkType in networkTypes) {
                val stats = try {
                    manager.querySummary(networkType, null, safeStart, end)
                } catch (error: SecurityException) {
                    Log.w(TAG, "Network stats permission missing", error)
                    return null
                } catch (error: RemoteException) {
                    Log.w(TAG, "Unable to query network stats", error)
                } catch (error: RuntimeException) {
                    Log.v(TAG, "Skipping network type $networkType", error)
                    null
                } ?: continue

                val statsClass = stats.javaClass
                val hasNextBucketMethod = runCatching { statsClass.getMethod("hasNextBucket") }.getOrNull()
                val getNextBucketMethod = runCatching { statsClass.getMethod("getNextBucket", bucket.javaClass) }.getOrNull()
                val closeMethod = runCatching { statsClass.getMethod("close") }.getOrNull()

                if (hasNextBucketMethod == null || getNextBucketMethod == null) {
                    Log.w(TAG, "Network stats iteration unsupported for type $networkType")
                    runCatching { closeMethod?.invoke(stats) }
                    continue
                }

                try {
                    while ((hasNextBucketMethod.invoke(stats) as? Boolean) == true) {
                        getNextBucketMethod.invoke(stats, bucket)
                        val packageName = uidToPackage[bucket.uid] ?: continue
                        val bytes = bucket.rxBytes + bucket.txBytes
                        if (bytes <= 0) continue
                        totals[packageName] = (totals[packageName] ?: 0L) + bytes
                    }
                } catch (error: ReflectiveOperationException) {
                    Log.w(TAG, "Failed to iterate network stats", error)
                    runCatching { closeMethod?.invoke(stats) }
                    return null
                } catch (error: ClassCastException) {
                    Log.w(TAG, "Unexpected network stats result", error)
                    runCatching { closeMethod?.invoke(stats) }
                    return null
                }

                runCatching { closeMethod?.invoke(stats) }
            }
        } catch (error: SecurityException) {
            Log.w(TAG, "Missing permission for network stats", error)
            return null
        } catch (error: RemoteException) {
            Log.w(TAG, "Network stats service error", error)
            return null
        }

        return totals
    }

    companion object {
        private const val TAG = "MainViewModel"

        fun Factory(container: AppContainer): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return MainViewModel(
                        appContext = container.appContext,
                        usageAnalyzer = container.usageAnalyzer,
                        applicationManager = container.applicationManager,
                        usageRepository = container.usageRepository,
                        firewallController = container.firewallController,
                        settingsPreferences = container.settingsPreferences
                    ) as T
                }
            }
        }
    }
}
