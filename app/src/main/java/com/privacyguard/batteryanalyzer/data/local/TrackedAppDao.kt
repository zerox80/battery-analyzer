package com.privacyguard.batteryanalyzer.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackedAppDao {

    @Query("SELECT * FROM tracked_apps ORDER BY last_used_at DESC")
    fun observeAll(): Flow<List<TrackedAppEntity>>

    @Query("SELECT * FROM tracked_apps WHERE status = :status ORDER BY last_used_at DESC")
    fun observeByStatus(status: AppUsageStatus): Flow<List<TrackedAppEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(apps: List<TrackedAppEntity>)

    @Update
    suspend fun updateApp(app: TrackedAppEntity)

    @Query("SELECT * FROM tracked_apps")
    suspend fun getAll(): List<TrackedAppEntity>

    @Query("UPDATE tracked_apps SET status = :status, is_disabled = :disabled, scheduled_disable_at = :scheduledAt, notified_at = :notifiedAt WHERE package_name = :packageName")
    suspend fun updateStatus(
        packageName: String,
        status: AppUsageStatus,
        disabled: Boolean,
        scheduledAt: Long?,
        notifiedAt: Long?
    )

    @Query("SELECT * FROM tracked_apps WHERE package_name = :packageName")
    suspend fun get(packageName: String): TrackedAppEntity?

    @Query("DELETE FROM tracked_apps WHERE package_name = :packageName")
    suspend fun delete(packageName: String)

    @Query("DELETE FROM tracked_apps WHERE package_name IN (:packageNames)")
    suspend fun deleteAll(packageNames: List<String>)
}
