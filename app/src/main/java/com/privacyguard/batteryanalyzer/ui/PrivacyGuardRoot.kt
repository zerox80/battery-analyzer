package com.privacyguard.batteryanalyzer.ui

import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.privacyguard.batteryanalyzer.R
import com.privacyguard.batteryanalyzer.model.AppUsageInfo
import com.privacyguard.batteryanalyzer.ui.state.AppHomeState
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

private enum class AppDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Home("home", R.string.nav_home, Icons.Outlined.Home),
    Settings("settings", R.string.nav_settings, Icons.Outlined.Settings),
    Metrics("metrics", R.string.nav_metrics, Icons.Outlined.Insights)
}

@Composable
fun PrivacyGuardRoot(
    uiStateFlow: kotlinx.coroutines.flow.StateFlow<AppHomeState>,
    onRequestUsagePermission: () -> Unit,
    onRefresh: () -> Unit,
    onRestoreApp: (String) -> Unit,
    onOpenAppInfo: (String) -> Unit,
    onEnableFirewall: () -> Unit,
    onDisableFirewall: () -> Unit,
    onAllowForDuration: () -> Unit,
    onBlockNow: () -> Unit,
    onUpdateAllowDuration: (Long) -> Unit,
    onToggleMetrics: (Boolean) -> Unit,
    onManualFirewallUnblockChange: (Boolean) -> Unit,
    onManualFirewallUnblock: (String) -> Unit
) {
    val uiState by uiStateFlow.collectAsState()
    PrivacyGuardRoot(
        uiState = uiState,
        onRequestUsagePermission = onRequestUsagePermission,
        onRefresh = onRefresh,
        onRestoreApp = onRestoreApp,
        onOpenAppInfo = onOpenAppInfo,
        onEnableFirewall = onEnableFirewall,
        onDisableFirewall = onDisableFirewall,
        onAllowForDuration = onAllowForDuration,
        onBlockNow = onBlockNow,
        onUpdateAllowDuration = onUpdateAllowDuration,
        onToggleMetrics = onToggleMetrics,
        onManualFirewallUnblockChange = onManualFirewallUnblockChange,
        onManualFirewallUnblock = onManualFirewallUnblock
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyGuardRoot(
    uiState: AppHomeState,
    onRequestUsagePermission: () -> Unit,
    onRefresh: () -> Unit,
    onRestoreApp: (String) -> Unit,
    onOpenAppInfo: (String) -> Unit,
    onEnableFirewall: () -> Unit,
    onDisableFirewall: () -> Unit,
    onAllowForDuration: () -> Unit,
    onBlockNow: () -> Unit,
    onUpdateAllowDuration: (Long) -> Unit,
    onToggleMetrics: (Boolean) -> Unit,
    onManualFirewallUnblockChange: (Boolean) -> Unit,
    onManualFirewallUnblock: (String) -> Unit
) {
    val navController = rememberNavController()
    val drawerState = androidx.compose.material3.rememberDrawerState(initialValue = androidx.compose.material3.DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val destinations = AppDestination.values().toList()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    text = stringResource(id = R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp)
                )
                Divider()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                destinations.forEach { destination ->
                    val selected = currentDestination.isRouteInHierarchy(destination.route)
                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = null
                            )
                        },
                        label = { Text(text = stringResource(id = destination.labelRes)) },
                        selected = selected,
                        onClick = {
                            scope.launch {
                                drawerState.close()
                                navController.navigate(destination.route) {
                                    launchSingleTop = true
                                    restoreState = true
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                }
                            }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }
        }
    ) {
        NavHost(
            navController = navController,
            startDestination = AppDestination.Home.route
        ) {
            composable(AppDestination.Home.route) {
                AppUsageHome(
                    state = uiState,
                    onRequestUsagePermission = onRequestUsagePermission,
                    onRefresh = onRefresh,
                    onRestoreApp = onRestoreApp,
                    onOpenAppInfo = onOpenAppInfo,
                    onEnableFirewall = onEnableFirewall,
                    onDisableFirewall = onDisableFirewall,
                    onAllowForDuration = onAllowForDuration,
                    onBlockNow = onBlockNow,
                    manualFirewallUnblock = uiState.manualFirewallUnblock,
                    onManualUnblock = onManualFirewallUnblock,
                    onOpenNavigation = {
                        scope.launch { drawerState.open() }
                    }
                )
            }
            composable(AppDestination.Settings.route) {
                SettingsScreen(
                    currentDurationMillis = uiState.allowDurationMillis,
                    manualFirewallUnblock = uiState.manualFirewallUnblock,
                    onDurationSelected = onUpdateAllowDuration,
                    onManualFirewallUnblockChange = onManualFirewallUnblockChange,
                    onOpenNavigation = {
                        scope.launch { drawerState.open() }
                    }
                )
            }
            composable(AppDestination.Metrics.route) {
                val trackedApps = (uiState.recentApps + uiState.rareApps + uiState.disabledApps)
                    .distinctBy { it.packageName }
                MetricsScreen(
                    metricsEnabled = uiState.metricsEnabled,
                    apps = trackedApps,
                    trafficByPackage = uiState.appTraffic,
                    lastSampleAt = uiState.metricsLastSampleAt,
                    onToggleMetrics = onToggleMetrics,
                    onOpenNavigation = {
                        scope.launch { drawerState.open() }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    currentDurationMillis: Long,
    manualFirewallUnblock: Boolean,
    onDurationSelected: (Long) -> Unit,
    onManualFirewallUnblockChange: (Boolean) -> Unit,
    onOpenNavigation: () -> Unit
) {
    val presetDurations = listOf(
        TimeUnit.MINUTES.toMillis(1),
        TimeUnit.MINUTES.toMillis(5),
        TimeUnit.MINUTES.toMillis(30),
        TimeUnit.HOURS.toMillis(1),
        TimeUnit.HOURS.toMillis(6),
        TimeUnit.DAYS.toMillis(1),
        TimeUnit.DAYS.toMillis(4)
    )

    val currentDurationLabel = rememberDurationLabel(currentDurationMillis)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onOpenNavigation) {
                        Icon(imageVector = Icons.Outlined.Menu, contentDescription = stringResource(id = R.string.navigation_open))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors()
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = stringResource(id = R.string.settings_description),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = stringResource(id = R.string.settings_current_duration, currentDurationLabel),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Text(
                    text = stringResource(id = R.string.settings_duration_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    ListItem(
                        headlineContent = {
                            Text(text = stringResource(id = R.string.settings_manual_firewall_title))
                        },
                        supportingContent = {
                            Text(
                                text = stringResource(id = R.string.settings_manual_firewall_description),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = manualFirewallUnblock,
                                onCheckedChange = onManualFirewallUnblockChange
                            )
                        }
                    )
                }
            }

            items(presetDurations) { duration ->
                val label = rememberDurationLabel(duration)
                val isSelected = duration == currentDurationMillis
                Card(
                    onClick = { if (!isSelected) onDurationSelected(duration) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    )
                ) {
                    ListItem(
                        headlineContent = { Text(text = label) },
                        supportingContent = {
                            if (isSelected) {
                                Text(text = stringResource(id = R.string.settings_current_duration, label))
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MetricsScreen(
    metricsEnabled: Boolean,
    apps: List<AppUsageInfo>,
    trafficByPackage: Map<String, Long>,
    lastSampleAt: Long?,
    onToggleMetrics: (Boolean) -> Unit,
    onOpenNavigation: () -> Unit
) {
    val sortedApps = apps.sortedByDescending { trafficByPackage[it.packageName] ?: 0L }
    val lastSampleText = lastSampleAt?.let {
        DateUtils.getRelativeTimeSpanString(
            it,
            System.currentTimeMillis(),
            DateUtils.SECOND_IN_MILLIS
        ).toString()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.metrics_title)) },
                navigationIcon = {
                    IconButton(onClick = onOpenNavigation) {
                        Icon(imageVector = Icons.Outlined.Menu, contentDescription = stringResource(id = R.string.navigation_open))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors()
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Text(
                    text = stringResource(id = R.string.metrics_description),
                    style = MaterialTheme.typography.bodyLarge
                )
                Row(
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = stringResource(id = R.string.metrics_toggle_label))
                    Switch(checked = metricsEnabled, onCheckedChange = onToggleMetrics)
                }
                val sampleLabel = when {
                    !metricsEnabled -> stringResource(id = R.string.metrics_disabled_hint)
                    lastSampleText != null -> stringResource(id = R.string.metrics_last_sample, lastSampleText)
                    else -> stringResource(id = R.string.metrics_no_sample)
                }
                Text(
                    text = sampleLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            if (metricsEnabled) {
                item {
                    Text(
                        text = stringResource(id = R.string.metrics_traffic_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    )
                }

                if (sortedApps.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(id = R.string.metrics_empty_list),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(sortedApps) { app ->
                        val traffic = trafficByPackage[app.packageName] ?: 0L
                        val trafficLabel = rememberFormattedBytes(traffic)
                        ListItem(
                            headlineContent = { Text(text = app.appLabel) },
                            supportingContent = { Text(text = app.packageName) },
                            trailingContent = { Text(text = trafficLabel) }
                        )
                        Divider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }
    }
}

private fun NavDestination?.isRouteInHierarchy(route: String): Boolean {
    return this?.hierarchy?.any { it.route == route } ?: false
}
