package com.privacyguard.batteryanalyzer.data.local

import androidx.room.TypeConverter

class Converters {

    @TypeConverter
    fun fromStatus(status: AppUsageStatus): String = status.name

    @TypeConverter
    fun toStatus(value: String): AppUsageStatus = AppUsageStatus.valueOf(value)
}
