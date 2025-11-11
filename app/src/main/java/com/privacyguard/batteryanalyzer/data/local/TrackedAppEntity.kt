package com.privacyguard.batteryanalyzer.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracked_apps")
data class TrackedAppEntity(
    @PrimaryKey
    @ColumnInfo(name = "package_name")
    val packageName: String,
    @ColumnInfo(name = "app_label")
    val appLabel: String,
    @ColumnInfo(name = "last_used_at")
    val lastUsedAt: Long,
    @ColumnInfo(name = "status")
    val status: AppUsageStatus,
    @ColumnInfo(name = "is_disabled")
    val isDisabled: Boolean,
    @ColumnInfo(name = "scheduled_disable_at")
    val scheduledDisableAt: Long?,
    @ColumnInfo(name = "notified_at")
    val notifiedAt: Long?
)
