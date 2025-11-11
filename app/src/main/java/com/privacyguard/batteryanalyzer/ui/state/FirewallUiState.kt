package com.privacyguard.batteryanalyzer.ui.state

data class FirewallUiState(
    val isEnabled: Boolean = false,
    val isBlocking: Boolean = false,
    val reactivateAt: Long? = null,
    val blockedPackages: Set<String> = emptySet()
)
