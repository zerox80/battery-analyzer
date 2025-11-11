package com.privacyguard.batteryanalyzer.firewall

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.privacyguard.batteryanalyzer.ui.state.FirewallUiState
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
        Log.i(TAG, "enableFirewall -> allowDuration=${allowDurationMillis} packages=${packages.size}")
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
            Log.i(TAG, "allowForDuration -> no packages, delegating to setBlocking(false)")
            setBlocking(false, System.currentTimeMillis() + allowDurationMillis, packages)
            return
        }
        Log.i(TAG, "allowForDuration -> allowDuration=${allowDurationMillis} keepBlocked=${packages.size}")
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
        Log.i(TAG, "blockNow -> packages=${packages.size}")
        preferences.setState(
            isEnabled = true,
            isBlocking = true,
            reactivateAt = null,
            blockedPackages = packages
        )
        cancelAutoBlock()
        startService(isBlocking = true, blockList = packages)
    }

    suspend fun applyManualBlockList(blockPackages: Set<String>) {
        val current = preferences.preferencesFlow.first()
        val shouldPersist = !current.isEnabled || current.isBlocking || current.reactivateAt != null || current.blockedPackages != blockPackages
        if (shouldPersist) {
            Log.i(TAG, "applyManualBlockList -> ${blockPackages.size} packages (was ${current.blockedPackages.size})")
            preferences.setState(
                isEnabled = true,
                isBlocking = false,
                reactivateAt = null,
                blockedPackages = blockPackages
            )
        } else {
            Log.d(TAG, "applyManualBlockList -> no state change required")
        }
        cancelAutoBlock()
        startService(isBlocking = false, blockList = blockPackages, includeBlockListWhenNotBlocking = true)
    }

    suspend fun disableFirewall() {
        Log.i(TAG, "disableFirewall")
        preferences.setState(isEnabled = false, isBlocking = false, reactivateAt = null, blockedPackages = emptySet())
        cancelAutoBlock()
        stopService()
    }

    suspend fun setBlocking(blocking: Boolean, reactivateAt: Long?, blockPackages: Set<String>? = null) {
        val packages = blockPackages ?: preferences.preferencesFlow.first().blockedPackages
        Log.i(TAG, "setBlocking -> blocking=$blocking reactivateAt=$reactivateAt packages=${packages.size}")
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
        val includeBlockList = !blocking && reactivateAt == null && packages.isNotEmpty()
        startService(isBlocking = blocking, blockList = packages, includeBlockListWhenNotBlocking = includeBlockList)
    }

    suspend fun updateBlockedPackages(blockPackages: Set<String>) {
        val current = preferences.preferencesFlow.first()
        if (current.blockedPackages == blockPackages) {
            Log.d(TAG, "updateBlockedPackages -> no change (${blockPackages.size})")
            return
        }
        Log.i(TAG, "updateBlockedPackages -> ${blockPackages.size} packages (was ${current.blockedPackages.size})")

        preferences.setState(
            isEnabled = current.isEnabled,
            isBlocking = current.isBlocking,
            reactivateAt = current.reactivateAt,
            blockedPackages = blockPackages
        )

        if (current.isEnabled) {
            val includeBlockList = (!current.isBlocking && current.reactivateAt == null && blockPackages.isNotEmpty())
            startService(
                isBlocking = current.isBlocking,
                blockList = blockPackages,
                includeBlockListWhenNotBlocking = includeBlockList
            )
        }
    }

    private fun startService(
        isBlocking: Boolean,
        blockList: Set<String>,
        includeBlockListWhenNotBlocking: Boolean = false
    ) {
        Log.d(TAG, "startService -> blocking=$isBlocking blockList=${blockList.size}")
        val intent = Intent(appContext, VpnFirewallService::class.java).apply {
            action = VpnFirewallService.ACTION_START_OR_UPDATE
            putExtra(VpnFirewallService.EXTRA_BLOCKING, isBlocking)
            val payload = when {
                isBlocking -> ArrayList(blockList)
                includeBlockListWhenNotBlocking -> ArrayList(blockList)
                else -> arrayListOf()
            }
            putStringArrayListExtra(VpnFirewallService.EXTRA_BLOCK_LIST, payload)
        }
        ContextCompat.startForegroundService(appContext, intent)
    }

    private fun stopService() {
        Log.d(TAG, "stopService")
        val intent = Intent(appContext, VpnFirewallService::class.java).apply {
            action = VpnFirewallService.ACTION_STOP
        }
        appContext.stopService(intent)
    }

    private fun scheduleAutoBlock(reactivateAt: Long) {
        val delay = reactivateAt - System.currentTimeMillis()
        Log.d(TAG, "scheduleAutoBlock -> reactivateAt=$reactivateAt delay=$delay")
        if (delay <= 0) {
            Log.w(TAG, "scheduleAutoBlock -> delay <= 0, enqueuing immediate block")
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
        Log.d(TAG, "cancelAutoBlock")
        workManager.cancelUniqueWork(AUTO_BLOCK_UNIQUE_WORK)
    }

    companion object {
        private const val TAG = "FirewallController"
    }
}
