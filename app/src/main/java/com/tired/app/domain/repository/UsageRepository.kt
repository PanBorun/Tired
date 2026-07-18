package com.tired.app.domain.repository

import com.tired.app.domain.model.AppUsageInfo
import com.tired.app.domain.model.DailyFatigueData
import kotlinx.coroutines.flow.Flow

enum class UsageDataOrigin {
    REAL_DEVICE
}

sealed interface UsageDataResult<out T> {
    data class Success<T>(
        val data: T,
        val origin: UsageDataOrigin
    ) : UsageDataResult<T>

    data object PermissionRequired : UsageDataResult<Nothing>
    data object EmptyData : UsageDataResult<Nothing>
}

interface UsageRepository {
    suspend fun getTodayUsage(): List<AppUsageInfo>
    suspend fun loadDailyFatigueData(date: String): UsageDataResult<DailyFatigueData>
    suspend fun loadWeeklyFatigueData(): UsageDataResult<List<DailyFatigueData>>
    fun observeTodayUsage(): Flow<List<AppUsageInfo>>
    suspend fun hasUsagePermission(): Boolean
}
