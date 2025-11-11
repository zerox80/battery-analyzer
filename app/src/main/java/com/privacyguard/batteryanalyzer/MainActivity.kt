package com.privacyguard.batteryanalyzer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.lifecycleScope
import com.privacyguard.batteryanalyzer.ui.PrivacyGuardRoot
import com.privacyguard.batteryanalyzer.ui.theme.PrivacyGuardTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        val app = application as PrivacyGuardApp
        MainViewModel.Factory(app.container)
    }

    private var pendingFirewallAction: (() -> Unit)? = null

    private val vpnPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val permissionGranted = result.resultCode == Activity.RESULT_OK || VpnService.prepare(this) == null
        if (permissionGranted) {
            pendingFirewallAction?.invoke()
        }
        pendingFirewallAction = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PrivacyGuardTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    PrivacyGuardRoot(
                        uiStateFlow = viewModel.uiState,
                        onRequestUsagePermission = { openUsageAccessSettings() },
                        onRefresh = { viewModel.refreshUsage() },
                        onRestoreApp = { packageName ->
                            lifecycleScope.launch { viewModel.restoreApp(packageName) }
                        },
                        onOpenAppInfo = { packageName -> openAppDetails(packageName) },
                        onEnableFirewall = {
                            ensureVpnPermission {
                                lifecycleScope.launch { viewModel.enableFirewall() }
                            }
                        },
                        onDisableFirewall = {
                            lifecycleScope.launch { viewModel.disableFirewall() }
                        },
                        onAllowForDuration = {
                            ensureVpnPermission {
                                lifecycleScope.launch { viewModel.allowForConfiguredDuration() }
                            }
                        },
                        onBlockNow = {
                            ensureVpnPermission {
                                lifecycleScope.launch { viewModel.blockNow() }
                            }
                        },
                        onUpdateAllowDuration = { duration ->
                            lifecycleScope.launch { viewModel.updateAllowDuration(duration) }
                        },
                        onToggleMetrics = { enabled ->
                            lifecycleScope.launch { viewModel.setMetricsEnabled(enabled) }
                        },
                        onManualFirewallUnblockChange = { enabled ->
                            lifecycleScope.launch { viewModel.setManualFirewallUnblock(enabled) }
                        },
                        onManualFirewallUnblock = { packageName ->
                            lifecycleScope.launch { viewModel.manualUnblockPackage(packageName) }
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { viewModel.refreshUsage() }
    }

    private fun openUsageAccessSettings() {
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    private fun openAppDetails(packageName: String) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun ensureVpnPermission(onGranted: () -> Unit) {
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent == null) {
            onGranted()
        } else {
            pendingFirewallAction = onGranted
            vpnPermissionLauncher.launch(prepareIntent)
        }
    }
}
