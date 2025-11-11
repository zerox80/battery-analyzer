package com.privacyguard.batteryanalyzer.util

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import com.privacyguard.batteryanalyzer.R
import java.util.Date

object UsageTextFormatter {

    fun formatLastUsed(context: Context, lastUsedAt: Long?): String {
        if (lastUsedAt == null) {
            return context.getString(R.string.last_used_never)
        }
        val relative = DateUtils.getRelativeTimeSpanString(
            lastUsedAt,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        )
        return context.getString(R.string.last_used_format, relative)
    }

    fun formatScheduledDisable(context: Context, timestamp: Long?): String? {
        if (timestamp == null) return null
        val date = Date(timestamp)
        val dateFormat = DateFormat.getMediumDateFormat(context)
        val timeFormat = DateFormat.getTimeFormat(context)
        val formatted = "${dateFormat.format(date)} ${timeFormat.format(date)}"
        return context.getString(R.string.scheduled_disable_format, formatted)
    }
}
