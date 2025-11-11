package com.example.batteryanalyzer.firewall

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.batteryanalyzer.ui.state.FirewallUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

private const val AUTO_BLOCK_UNIQUE_WORK = "firewall_auto_block"
private val DEFAULT_ALLOW_DURATION_MILLIS: Long = TimeUnit.DAYS.toMillis(4)

class FirewallController(
    context: Context,
    private val preferences: FirewallPreferencesDataSource
) {

    private val appContext = context.applicationContext
    private val workManager: WorkManager = WorkManager.getInstance(appContext)

    val state: Flow<FirewallUiState> = preferences.preferencesFlow.map { prefs ->
        FirewallUiState(
            isEnabled = prefs.isEnabled,
            isBlocking = prefs.isBlocking,
            reactivateAt = prefs.reactivateAt,
            blockedPackages = prefs.blockedPackages
        )
    }

    suspend fun enableFirewall(blockPackages: Set<String>? = null, allowDurationMillis: Long = DEFAULT_ALLOW_DURATION_MILLIS) {
        val packages = blockPackages ?: preferences.preferencesFlow.first().blockedPackages
        val reactivateAt = System.currentTimeMillis() + allowDurationMillis
        preferences.setState(
            isEnabled = true,
            isBlocking = false,
            reactivateAt = reactivateAt,
            blockedPackages = packages
        )
        scheduleAutoBlock(reactivateAt)
        startService(isBlocking = false, blockList = packages)
    }

    suspend fun allowForDuration(allowDurationMillis: Long, blockPackages: Set<String>? = null) {
        val packages = blockPackages ?: preferences.preferencesFlow.first().blockedPackages
        if (packages.isEmpty()) {
            setBlocking(false, System.currentTimeMillis() + allowDurationMillis, packages)
            return
        }
        preferences.setState(
            isEnabled = true,
            isBlocking = false,
            reactivateAt = System.currentTimeMillis() + allowDurationMillis,
            blockedPackages = packages
        )
        scheduleAutoBlock(System.currentTimeMillis() + allowDurationMillis)
        startService(isBlocking = false, blockList = packages)
    }

    suspend fun blockNow(blockPackages: Set<String>? = null) {
        val packages = blockPackages ?: preferences.preferencesFlow.first().blockedPackages
        preferences.setState(
            isEnabled = true,
            isBlocking = true,
            reactivateAt = null,
            blockedPackages = packages
        )
        cancelAutoBlock()
        startService(isBlocking = true, blockList = packages)
    }

    suspend fun disableFirewall() {
        preferences.setState(isEnabled = false, isBlocking = false, reactivateAt = null, blockedPackages = emptySet())
        cancelAutoBlock()
        stopService()
    }

    suspend fun setBlocking(blocking: Boolean, reactivateAt: Long?, blockPackages: Set<String>? = null) {
        val packages = blockPackages ?: preferences.preferencesFlow.first().blockedPackages
        preferences.setState(
            isEnabled = true,
            isBlocking = blocking,
            reactivateAt = reactivateAt,
            blockedPackages = packages
        )
        if (blocking) {
            cancelAutoBlock()
        } else if (reactivateAt != null) {
            scheduleAutoBlock(reactivateAt)
        }
        startService(isBlocking = blocking, blockList = packages)
    }

    suspend fun updateBlockedPackages(blockPackages: Set<String>) {
        val current = preferences.preferencesFlow.first()
        if (current.blockedPackages == blockPackages) return

        preferences.setState(
            isEnabled = current.isEnabled,
            isBlocking = current.isBlocking,
            reactivateAt = current.reactivateAt,
            blockedPackages = blockPackages
        )

        if (current.isEnabled) {
            startService(isBlocking = current.isBlocking, blockList = blockPackages)
        }
    }

    private fun startService(isBlocking: Boolean, blockList: Set<String>) {
        val intent = Intent(appContext, VpnFirewallService::class.java).apply {
            action = VpnFirewallService.ACTION_START_OR_UPDATE
            putExtra(VpnFirewallService.EXTRA_BLOCKING, isBlocking)
            putStringArrayListExtra(VpnFirewallService.EXTRA_BLOCK_LIST, ArrayList(blockList))
        }
        ContextCompat.startForegroundService(appContext, intent)
    }

    private fun stopService() {
        val intent = Intent(appContext, VpnFirewallService::class.java).apply {
            action = VpnFirewallService.ACTION_STOP
        }
        appContext.stopService(intent)
    }

    private fun scheduleAutoBlock(reactivateAt: Long) {
        val delay = reactivateAt - System.currentTimeMillis()
        if (delay <= 0) {
            workManager.enqueueUniqueWork(
                AUTO_BLOCK_UNIQUE_WORK,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<FirewallAutoBlockWorker>().build()
            )
            return
        }
        val request = OneTimeWorkRequestBuilder<FirewallAutoBlockWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniqueWork(
            AUTO_BLOCK_UNIQUE_WORK,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelAutoBlock() {
        workManager.cancelUniqueWork(AUTO_BLOCK_UNIQUE_WORK)
    }
}
