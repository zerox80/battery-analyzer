package com.privacyguard.batteryanalyzer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.PrivacyTip
import android.text.format.DateUtils
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.privacyguard.batteryanalyzer.R
import com.privacyguard.batteryanalyzer.data.local.AppUsageStatus
import com.privacyguard.batteryanalyzer.model.AppUsageInfo
import com.privacyguard.batteryanalyzer.ui.components.AppUsageCard
import com.privacyguard.batteryanalyzer.ui.rememberDurationLabel
import com.privacyguard.batteryanalyzer.ui.state.AppHomeState
import com.privacyguard.batteryanalyzer.ui.state.FirewallUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppUsageHome(
    state: AppHomeState,
    onRequestUsagePermission: () -> Unit,
    onRefresh: () -> Unit,
    onRestoreApp: (String) -> Unit,
    onOpenAppInfo: (String) -> Unit,
    onEnableFirewall: () -> Unit,
    onDisableFirewall: () -> Unit,
    onAllowForDuration: () -> Unit,
    onBlockNow: () -> Unit,
    manualFirewallUnblock: Boolean,
    onManualUnblock: (String) -> Unit,
    onOpenNavigation: () -> Unit
) {
    val tabs = listOf(
        stringResource(id = R.string.tab_recent),
        stringResource(id = R.string.tab_rare),
        stringResource(id = R.string.tab_disabled)
    )
    val tabCounts = listOf(state.recentApps.size, state.rareApps.size, state.disabledApps.size)
    val selectedTabIndex = remember { mutableIntStateOf(0) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text(text = stringResource(id = R.string.dashboard_title)) },
                navigationIcon = {
                    IconButton(onClick = onOpenNavigation) {
                        Icon(
                            imageVector = Icons.Outlined.Menu,
                            contentDescription = stringResource(id = R.string.navigation_open)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(id = R.string.action_refresh)
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        if (!state.usagePermissionGranted) {
            PermissionRequestContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                onRequestUsagePermission = onRequestUsagePermission
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    SummaryHero(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth(),
                        permissionGranted = state.usagePermissionGranted
                    )
                }

                item { Spacer(modifier = Modifier.height(20.dp)) }

                item {
                    FirewallCard(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth(),
                        state = state.firewallState,
                        onEnable = onEnableFirewall,
                        onDisable = onDisableFirewall,
                        onAllowForDuration = onAllowForDuration,
                        onBlockNow = onBlockNow,
                        allowDurationMillis = state.allowDurationMillis,
                        manualMode = manualFirewallUnblock
                    )
                }

                item { Spacer(modifier = Modifier.height(20.dp)) }

                item {
                    UsageStatsRow(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth(),
                        recentCount = state.recentApps.size,
                        rareCount = state.rareApps.size,
                        disabledCount = state.disabledApps.size
                    )
                }

                item { Spacer(modifier = Modifier.height(20.dp)) }

                item {
                    AppListContainer(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth(),
                        tabs = tabs,
                        tabCounts = tabCounts,
                        state = state,
                        isLoading = state.isLoading,
                        selectedTabIndex = selectedTabIndex.intValue,
                        onTabSelected = { selectedTabIndex.intValue = it },
                        onRestoreApp = onRestoreApp,
                        onOpenAppInfo = onOpenAppInfo,
                        manualFirewallEnabled = manualFirewallUnblock,
                        blockedPackages = state.firewallBlockedPackages,
                        onManualUnblock = onManualUnblock
                    )
                }
            }
        }
    }
}

@Composable
private fun AppListContainer(
    modifier: Modifier = Modifier,
    tabs: List<String>,
    tabCounts: List<Int>,
    state: AppHomeState,
    isLoading: Boolean,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    onRestoreApp: (String) -> Unit,
    onOpenAppInfo: (String) -> Unit,
    manualFirewallEnabled: Boolean,
    blockedPackages: Set<String>,
    onManualUnblock: (String) -> Unit
) {
    ElevatedCard(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            androidx.compose.material3.TabRow(
                selectedTabIndex = selectedTabIndex,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { onTabSelected(index) },
                        text = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (selectedTabIndex == index) FontWeight.SemiBold else FontWeight.Normal
                                )
                                Text(
                                    text = tabCounts[index].toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                }
            }

            HorizontalDivider()

            val listModifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp, max = 520.dp)

            Box(modifier = Modifier.fillMaxWidth()) {
                if (isLoading) {
                    LoadingState(modifier = Modifier.fillMaxWidth())
                } else {
                    when (selectedTabIndex) {
                        0 -> AppList(
                            modifier = listModifier,
                            apps = state.recentApps,
                            emptyText = R.string.empty_state_recent,
                            onRestoreApp = onRestoreApp,
                            onOpenAppInfo = onOpenAppInfo,
                            manualFirewallEnabled = manualFirewallEnabled,
                            blockedPackages = blockedPackages,
                            onManualUnblock = onManualUnblock
                        )

                        1 -> AppList(
                            modifier = listModifier,
                            apps = state.rareApps,
                            emptyText = R.string.empty_state_rare,
                            onRestoreApp = onRestoreApp,
                            onOpenAppInfo = onOpenAppInfo,
                            manualFirewallEnabled = manualFirewallEnabled,
                            blockedPackages = blockedPackages,
                            onManualUnblock = onManualUnblock
                        )

                        2 -> AppList(
                            modifier = listModifier,
                            apps = state.disabledApps,
                            emptyText = R.string.empty_state_disabled,
                            onRestoreApp = onRestoreApp,
                            onOpenAppInfo = onOpenAppInfo,
                            showRestore = true,
                            manualFirewallEnabled = manualFirewallEnabled,
                            blockedPackages = blockedPackages,
                            onManualUnblock = onManualUnblock
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FirewallCard(
    modifier: Modifier = Modifier,
    state: FirewallUiState,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onAllowForDuration: () -> Unit,
    onBlockNow: () -> Unit,
    allowDurationMillis: Long,
    manualMode: Boolean
) {
    val isEnabled = state.isEnabled
    val isBlocking = state.isBlocking

    var countdownText by remember(state.reactivateAt, state.isBlocking, state.isEnabled) {
        mutableStateOf<String?>(null)
    }

    val allowDurationLabel = rememberDurationLabel(allowDurationMillis)

    LaunchedEffect(state.reactivateAt, state.isBlocking, state.isEnabled) {
        if (state.isEnabled && !state.isBlocking && state.reactivateAt != null) {
            while (isActive) {
                val now = System.currentTimeMillis()
                val remaining = state.reactivateAt - now
                if (remaining <= 0) {
                    countdownText = null
                    break
                }
                countdownText = DateUtils.getRelativeTimeSpanString(
                    state.reactivateAt,
                    now,
                    DateUtils.MINUTE_IN_MILLIS
                ).toString()
                val delayMillis = remaining.coerceAtMost(DateUtils.MINUTE_IN_MILLIS)
                delay(delayMillis)
            }
        } else {
            countdownText = null
        }
    }

    ElevatedCard(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.firewall_card_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(id = R.string.firewall_card_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { checked ->
                            if (checked) onEnable() else onDisable()
                        }
                    )
                    Text(
                        text = if (isEnabled) {
                            stringResource(id = R.string.firewall_switch_label)
                        } else {
                            stringResource(id = R.string.firewall_switch_label_disabled)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val statusText = when {
                isEnabled && isBlocking -> stringResource(id = R.string.firewall_blocking_now)
                isEnabled && !isBlocking -> stringResource(id = R.string.firewall_allowing_now)
                else -> null
            }

            statusText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            if (manualMode) {
                Text(
                    text = stringResource(id = R.string.firewall_manual_mode_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isEnabled && !isBlocking && countdownText != null) {
                Text(
                    text = stringResource(id = R.string.firewall_countdown_label, countdownText!!),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isBlocking) {
                        Button(
                            onClick = onAllowForDuration,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = stringResource(id = R.string.firewall_allow_button_dynamic, allowDurationLabel))
                        }
                    } else {
                        Button(
                            onClick = onBlockNow,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = stringResource(id = R.string.firewall_block_now_button))
                        }
                    }
                }
                if (isBlocking) {
                    Text(
                        text = stringResource(id = R.string.firewall_allow_duration_hint, allowDurationLabel),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryHero(modifier: Modifier = Modifier, permissionGranted: Boolean) {
    val gradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer,
            lerp(
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.inversePrimary,
                0.35f
            )
        )
    )

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 6.dp,
        shadowElevation = 2.dp
    ) {
        Box(
            modifier = Modifier
                .background(gradient)
                .padding(horizontal = 24.dp, vertical = 28.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(id = R.string.summary_headline),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = stringResource(id = R.string.summary_subheadline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
                PermissionStatusChip(isGranted = permissionGranted)
            }
        }
    }
}

@Composable
private fun PermissionStatusChip(isGranted: Boolean) {
    val label = if (isGranted) {
        stringResource(id = R.string.permission_status_granted)
    } else {
        stringResource(id = R.string.permission_status_missing)
    }
    val colors = if (isGranted) {
        AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f),
            labelColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    } else {
        AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            labelColor = MaterialTheme.colorScheme.onErrorContainer
        )
    }
    val icon = if (isGranted) Icons.Outlined.CheckCircle else Icons.Outlined.Info

    AssistChip(
        onClick = {},
        enabled = false,
        colors = colors,
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null
            )
        },
        label = {
            Text(text = label)
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UsageStatsRow(
    modifier: Modifier = Modifier,
    recentCount: Int,
    rareCount: Int,
    disabledCount: Int
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        UsageStatCard(
            title = stringResource(id = R.string.stat_recent),
            value = recentCount,
            icon = Icons.Outlined.Bolt,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
        UsageStatCard(
            title = stringResource(id = R.string.stat_rare),
            value = rareCount,
            icon = Icons.Outlined.HourglassEmpty,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        )
        UsageStatCard(
            title = stringResource(id = R.string.stat_disabled),
            value = disabledCount,
            icon = Icons.Outlined.Block,
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RowScope.UsageStatCard(
    title: String,
    value: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        modifier = Modifier.weight(1f),
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.8f)
            )
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                color = contentColor
            )
        }
    }
}

@Composable
private fun AppList(
    modifier: Modifier = Modifier,
    apps: List<AppUsageInfo>,
    emptyText: Int,
    onRestoreApp: (String) -> Unit,
    onOpenAppInfo: (String) -> Unit,
    showRestore: Boolean = false,
    manualFirewallEnabled: Boolean,
    blockedPackages: Set<String>,
    onManualUnblock: (String) -> Unit
) {
    if (apps.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            EmptyState(text = stringResource(id = emptyText))
        }
    } else {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(apps) { appInfo ->
                AppUsageCard(
                    app = appInfo,
                    onOpenAppInfo = { onOpenAppInfo(appInfo.packageName) },
                    showRestore = showRestore,
                    onRestore = { onRestoreApp(appInfo.packageName) },
                    manualFirewallEnabled = manualFirewallEnabled,
                    isManuallyBlocked = appInfo.packageName in blockedPackages,
                    onManualUnblock = { onManualUnblock(appInfo.packageName) }
                )
            }
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(24.dp)
            .heightIn(min = 200.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Text(
            modifier = Modifier.padding(top = 16.dp),
            text = stringResource(id = R.string.usage_stats_loading),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier, text: String) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp)
            .heightIn(min = 200.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.Inbox,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(bottom = 12.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PermissionRequestContent(
    modifier: Modifier,
    onRequestUsagePermission: () -> Unit
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        ElevatedCard(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Icon(
                    imageVector = Icons.Outlined.PrivacyTip,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(id = R.string.permission_usage_access_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = stringResource(id = R.string.permission_usage_access_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = onRequestUsagePermission) {
                    Text(text = stringResource(id = R.string.permission_usage_access_action))
                }
            }
        }
    }
}
