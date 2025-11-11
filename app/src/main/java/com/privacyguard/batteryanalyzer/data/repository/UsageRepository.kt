package com.privacyguard.batteryanalyzer.data.repository

import com.privacyguard.batteryanalyzer.data.local.AppUsageStatus
import com.privacyguard.batteryanalyzer.data.local.TrackedAppDao
import com.privacyguard.batteryanalyzer.data.local.TrackedAppEntity
import com.privacyguard.batteryanalyzer.domain.UsageEvaluation
import com.privacyguard.batteryanalyzer.model.AppUsageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class UsageRepository(private val trackedAppDao: TrackedAppDao) {

    fun observeStatus(status: AppUsageStatus): Flow<List<AppUsageInfo>> {
        return trackedAppDao.observeByStatus(status).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun applyEvaluation(evaluation: UsageEvaluation) {
        withContext(Dispatchers.IO) {
            if (evaluation.updates.isNotEmpty()) {
                trackedAppDao.upsertAll(evaluation.updates)
            }
            if (evaluation.packagesToRemove.isNotEmpty()) {
                trackedAppDao.deleteAll(evaluation.packagesToRemove)
            }
        }
    }

    private fun TrackedAppEntity.toDomain(): AppUsageInfo {
        return AppUsageInfo(
            packageName = packageName,
            appLabel = appLabel,
            lastUsedAt = lastUsedAt.takeIf { it > 0 },
            status = status,
            isDisabled = isDisabled,
            scheduledDisableAt = scheduledDisableAt,
            notifiedAt = notifiedAt
        )
    }
}
