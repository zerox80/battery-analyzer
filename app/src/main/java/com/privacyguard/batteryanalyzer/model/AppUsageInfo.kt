package com.privacyguard.batteryanalyzer.model

import com.privacyguard.batteryanalyzer.data.local.AppUsageStatus

data class AppUsageInfo(
    val packageName: String,
    val appLabel: String,
    val lastUsedAt: Long?,
    val status: AppUsageStatus,
    val isDisabled: Boolean,
    val scheduledDisableAt: Long?,
    val notifiedAt: Long?
)
