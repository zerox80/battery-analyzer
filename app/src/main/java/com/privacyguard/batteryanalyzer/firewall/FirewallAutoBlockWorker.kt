package com.privacyguard.batteryanalyzer.firewall

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class FirewallAutoBlockWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val controller = FirewallControllerProvider.get(applicationContext)
        controller.blockNow()
        return Result.success()
    }
}
