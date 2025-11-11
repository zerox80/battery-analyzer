package com.example.batteryanalyzer.ui.state

import com.example.batteryanalyzer.model.AppUsageInfo
import com.example.batteryanalyzer.settings.SettingsPreferencesDataSource

data class AppHomeState(
    val usagePermissionGranted: Boolean = false,
    val isLoading: Boolean = true,
    val recentApps: List<AppUsageInfo> = emptyList(),
    val rareApps: List<AppUsageInfo> = emptyList(),
    val disabledApps: List<AppUsageInfo> = emptyList(),
    val firewallState: FirewallUiState = FirewallUiState(),
    val firewallBlockedPackages: Set<String> = emptySet(),
    val allowDurationMillis: Long = SettingsPreferencesDataSource.DEFAULT_ALLOW_DURATION_MILLIS,
    val metricsEnabled: Boolean = false,
    val appTraffic: Map<String, Long> = emptyMap(),
    val metricsLastSampleAt: Long? = null
)
