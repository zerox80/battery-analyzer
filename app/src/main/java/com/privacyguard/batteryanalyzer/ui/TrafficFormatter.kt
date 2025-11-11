package com.privacyguard.batteryanalyzer.ui

import android.content.Context
import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
fun rememberFormattedBytes(bytes: Long): String {
    val context = LocalContext.current
    return remember(bytes) { formatBytes(context, bytes) }
}

fun formatBytes(context: Context, bytes: Long): String {
    if (bytes < 0) return "â€“"
    return Formatter.formatShortFileSize(context, bytes)
}
