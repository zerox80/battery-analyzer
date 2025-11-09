package com.example.batteryanalyzer.ui

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
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import com.example.batteryanalyzer.R
import com.example.batteryanalyzer.model.AppUsageInfo
import com.example.batteryanalyzer.ui.components.AppUsageCard
import com.example.batteryanalyzer.ui.state.AppHomeState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppUsageHome(
    state: AppHomeState,
    onRequestUsagePermission: () -> Unit,
    onRefresh: () -> Unit,
    onRestoreApp: (String) -> Unit
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
        modifier = Modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = stringResource(id = R.string.dashboard_title)) },
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                SummaryHero(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth(),
                    permissionGranted = state.usagePermissionGranted
                )

                Spacer(modifier = Modifier.height(20.dp))

                UsageStatsRow(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth(),
                    recentCount = state.recentApps.size,
                    rareCount = state.rareApps.size,
                    disabledCount = state.disabledApps.size
                )

                Spacer(modifier = Modifier.height(20.dp))

                ElevatedCard(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .weight(1f),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        androidx.compose.material3.TabRow(
                            selectedTabIndex = selectedTabIndex.intValue,
                            indicator = { tabPositions ->
                                TabRowDefaults.Indicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex.intValue]),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        ) {
                            tabs.forEachIndexed { index, title ->
                                Tab(
                                    selected = selectedTabIndex.intValue == index,
                                    onClick = {
                                        selectedTabIndex.intValue = index
                                    },
                                    text = {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = title,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = if (selectedTabIndex.intValue == index) FontWeight.SemiBold else FontWeight.Normal
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

                        Divider()

                        Box(modifier = Modifier.fillMaxSize()) {
                            if (state.isLoading) {
                                LoadingState()
                            } else {
                                when (selectedTabIndex.intValue) {
                                    0 -> AppList(
                                        apps = state.recentApps,
                                        emptyText = R.string.empty_state_recent,
                                        onRestoreApp = onRestoreApp
                                    )

                                    1 -> AppList(
                                        apps = state.rareApps,
                                        emptyText = R.string.empty_state_rare,
                                        onRestoreApp = onRestoreApp
                                    )

                                    2 -> AppList(
                                        apps = state.disabledApps,
                                        emptyText = R.string.empty_state_disabled,
                                        onRestoreApp = onRestoreApp,
                                        showRestore = true
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
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
    apps: List<AppUsageInfo>,
    emptyText: Int,
    onRestoreApp: (String) -> Unit,
    showRestore: Boolean = false
) {
    if (apps.isEmpty()) {
        EmptyState(stringResource(id = emptyText))
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(apps) { appInfo ->
                AppUsageCard(
                    app = appInfo,
                    showRestore = showRestore,
                    onRestore = { onRestoreApp(appInfo.packageName) }
                )
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
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
private fun EmptyState(text: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
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
