package com.privacyguard.batteryanalyzer.domain

import com.privacyguard.batteryanalyzer.data.local.TrackedAppEntity

data class UsageEvaluation(
    val updates: List<TrackedAppEntity>,
    val packagesToRemove: List<String>,
    val appsToNotify: List<TrackedAppEntity>,
    val appsForDisableRecommendation: List<TrackedAppEntity>
)
