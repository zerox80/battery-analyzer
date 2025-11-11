package com.privacyguard.batteryanalyzer.ui.state

import com.privacyguard.batteryanalyzer.model.AppUsageInfo
import com.privacyguard.batteryanalyzer.settings.SettingsPreferencesDataSource

data class AppHomeState(
    val usagePermissionGranted: Boolean = false,
    val isLoading: Boolean = true,
    val recentApps: List<AppUsageInfo> = emptyList(),
    val rareApps: List<AppUsageInfo> = emptyList(),
    val disabledApps: List<AppUsageInfo> = emptyList(),
    val firewallState: FirewallUiState = FirewallUiState(),
    val firewallBlockedPackages: Set<String> = emptySet(),
    val allowDurationMillis: Long = SettingsPreferencesDataSource.DEFAULT_ALLOW_DURATION_MILLIS,
    val manualFirewallUnblock: Boolean = false,
    val metricsEnabled: Boolean = false,
    val appTraffic: Map<String, Long> = emptyMap(),
    val metricsLastSampleAt: Long? = null
)
