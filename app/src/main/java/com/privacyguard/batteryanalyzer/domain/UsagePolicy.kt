package com.privacyguard.batteryanalyzer.domain

/**
 * Encapsulates rules that decide whether a package should be tracked or receive disable/archive
 * recommendations. This keeps critical apps (for example banking or authentication) out of the
 * automation pipeline so the UI can present more trustworthy suggestions.
 */
import java.util.concurrent.TimeUnit

class UsagePolicy(
    recentThresholdMillis: Long = DEFAULT_RECENT_THRESHOLD,
    warningThresholdMillis: Long = DEFAULT_WARNING_THRESHOLD,
    disableThresholdMillis: Long = DEFAULT_DISABLE_THRESHOLD
) {

    @Volatile
    var recentThresholdMillis: Long = recentThresholdMillis
        private set

    @Volatile
    var warningThresholdMillis: Long = warningThresholdMillis
        private set

    @Volatile
    var disableThresholdMillis: Long = disableThresholdMillis
        private set

    /**
     * Returns true when the package should be ignored by the automation pipeline. The default
     * implementation keeps everything enabled.
     */
    fun shouldSkip(@Suppress("UNUSED_PARAMETER") packageName: String): Boolean = false

    /**
     * Aligns internal thresholds with the configured firewall allow duration. Returns true when
     * the values actually changed, allowing callers to decide whether they need to recompute
     * usage classifications.
     */
    fun updateThresholds(allowDurationMillis: Long): Boolean {
        val sanitized = allowDurationMillis.coerceAtLeast(MIN_THRESHOLD)
        val newDisable = sanitized
        val newWarning = (sanitized * 3 / 4).coerceAtLeast(MIN_THRESHOLD)
        val newRecent = sanitized.coerceAtMost(DEFAULT_RECENT_THRESHOLD)

        var changed = false
        synchronized(this) {
            if (newDisable != disableThresholdMillis || newWarning != warningThresholdMillis || newRecent != recentThresholdMillis) {
                disableThresholdMillis = newDisable
                warningThresholdMillis = newWarning
                recentThresholdMillis = newRecent
                changed = true
            }
        }
        return changed
    }

    companion object {
        private val DEFAULT_RECENT_THRESHOLD = TimeUnit.DAYS.toMillis(2)
        private val DEFAULT_WARNING_THRESHOLD = TimeUnit.DAYS.toMillis(3)
        private val DEFAULT_DISABLE_THRESHOLD = TimeUnit.DAYS.toMillis(4)
        private val MIN_THRESHOLD = TimeUnit.MINUTES.toMillis(1)
    }
}
