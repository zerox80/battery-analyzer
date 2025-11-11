package com.privacyguard.batteryanalyzer

import android.content.Context
import android.content.pm.PackageManager
import android.net.TrafficStats
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.privacyguard.batteryanalyzer.data.local.AppUsageStatus
import com.privacyguard.batteryanalyzer.data.repository.UsageRepository
import com.privacyguard.batteryanalyzer.di.AppContainer
import com.privacyguard.batteryanalyzer.domain.ApplicationManager
import com.privacyguard.batteryanalyzer.domain.UsageAnalyzer
import com.privacyguard.batteryanalyzer.firewall.FirewallController
import com.privacyguard.batteryanalyzer.notifications.NotificationHelper
import com.privacyguard.batteryanalyzer.settings.SettingsPreferencesDataSource
import com.privacyguard.batteryanalyzer.ui.state.AppHomeState
import com.privacyguard.batteryanalyzer.util.UsagePermissionChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.ArrayDeque
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
        "com.google.android.youtube"
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

    private fun launchStatusCollector(status: AppUsageStatus, onUpdate: (List<com.privacyguard.batteryanalyzer.model.AppUsageInfo>) -> Unit) {
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

        val sums = mutableMapOf<String, Long>()
        val trackedPackageSet = trackedPackages.toSet()

        for (packageName in trackedPackages) {
            val uid = resolveUid(packageName) ?: continue
            collectTrafficFromSnapshots(packageName, uid, now, windowMillis)?.let { sum ->
                sums[packageName] = sum
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
