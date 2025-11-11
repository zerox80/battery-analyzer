package com.privacyguard.batteryanalyzer.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.privacyguard.batteryanalyzer.R
import com.privacyguard.batteryanalyzer.data.local.AppUsageStatus
import com.privacyguard.batteryanalyzer.model.AppUsageInfo
import com.privacyguard.batteryanalyzer.util.UsageTextFormatter
import com.google.accompanist.drawablepainter.rememberDrawablePainter

@Composable
fun AppUsageCard(
    app: AppUsageInfo,
    onOpenAppInfo: () -> Unit,
    showRestore: Boolean,
    onRestore: () -> Unit,
    manualFirewallEnabled: Boolean,
    isManuallyBlocked: Boolean,
    onManualUnblock: () -> Unit
) {
    val context = LocalContext.current

    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AppIcon(context = context, packageName = app.packageName, appLabel = app.appLabel)

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.appLabel,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = UsageTextFormatter.formatLastUsed(context, app.lastUsedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(app.status)
                UsageTextFormatter.formatScheduledDisable(context, app.scheduledDisableAt)?.let { text ->
                    SuggestionChip(
                        onClick = {},
                        enabled = false,
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        label = { Text(text = text) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(onClick = onOpenAppInfo) {
                    Text(text = stringResource(id = R.string.action_open_app_info))
                }

                if (manualFirewallEnabled && isManuallyBlocked) {
                    Spacer(modifier = Modifier.size(12.dp))
                    Button(
                        onClick = onManualUnblock,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        Text(text = stringResource(id = R.string.firewall_manual_unblock_button))
                    }
                }

                if (showRestore) {
                    Spacer(modifier = Modifier.size(12.dp))
                    Button(
                        onClick = onRestore,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(imageVector = Icons.Outlined.Restore, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(text = stringResource(id = R.string.action_restore_app))
                    }
                }
            }
        }
    }
}

@Composable
private fun AppIcon(context: Context, packageName: String, appLabel: String) {
    val packageManager = context.packageManager
    val drawable = remember(packageName) {
        runCatching { packageManager.getApplicationIcon(packageName) }.getOrNull()
    }
    if (drawable != null) {
        Surface(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            androidx.compose.foundation.Image(
                painter = rememberDrawablePainter(drawable = drawable),
                contentDescription = null,
                modifier = Modifier.padding(4.dp)
            )
        }
    } else {
        Surface(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = appLabel.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun StatusChip(status: AppUsageStatus) {
    val labelRes = when (status) {
        AppUsageStatus.RECENT -> R.string.status_recent
        AppUsageStatus.RARE -> R.string.status_rare
        AppUsageStatus.DISABLED -> R.string.status_disabled
    }
    val colors = when (status) {
        AppUsageStatus.RECENT -> SuggestionChipDefaults.suggestionChipColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            labelColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
        AppUsageStatus.RARE -> SuggestionChipDefaults.suggestionChipColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            labelColor = MaterialTheme.colorScheme.onTertiaryContainer
        )
        AppUsageStatus.DISABLED -> SuggestionChipDefaults.suggestionChipColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            labelColor = MaterialTheme.colorScheme.onErrorContainer
        )
    }

    SuggestionChip(
        onClick = {},
        enabled = false,
        colors = colors,
        label = { Text(text = stringResource(id = labelRes)) }
    )
}
