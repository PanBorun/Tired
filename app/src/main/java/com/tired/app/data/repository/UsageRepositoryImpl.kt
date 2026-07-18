package com.tired.app.data.repository

import com.tired.app.data.local.datasource.UsageStatsDataSource
import com.tired.app.domain.model.AppUsageInfo
import com.tired.app.domain.model.DailyFatigueData
import com.tired.app.domain.model.FatigueType
import com.tired.app.domain.repository.UsageDataOrigin
import com.tired.app.domain.repository.UsageDataResult
import com.tired.app.domain.repository.UsageRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsageRepositoryImpl @Inject constructor(
    private val usageStatsDataSource: UsageStatsDataSource
) : UsageRepository {

    override suspend fun getTodayUsage(): List<AppUsageInfo> {
        return when (val result = loadDailyFatigueData(todayDate())) {
            is UsageDataResult.Success -> result.data.appUsageList
            UsageDataResult.EmptyData,
            UsageDataResult.PermissionRequired -> emptyList()
        }
    }

    override suspend fun loadDailyFatigueData(date: String): UsageDataResult<DailyFatigueData> {
        if (!usageStatsDataSource.hasPermission()) {
            return UsageDataResult.PermissionRequired
        }

        val realData = buildRealDailyFatigueData(date)
        if (realData.appUsageList.isNotEmpty()) {
            return UsageDataResult.Success(realData, UsageDataOrigin.REAL_DEVICE)
        }

        return UsageDataResult.EmptyData
    }

    override suspend fun loadWeeklyFatigueData(): UsageDataResult<List<DailyFatigueData>> {
        if (!usageStatsDataSource.hasPermission()) {
            return UsageDataResult.PermissionRequired
        }

        val today = LocalDate.now()
        val dailyResults = (6 downTo 0).map { daysAgo ->
            val date = today.minusDays(daysAgo.toLong()).format(DateTimeFormatter.ISO_LOCAL_DATE)
            date to buildRealDailyFatigueData(date)
        }

        val realDays = dailyResults.mapNotNull { (_, data) ->
            data.takeIf { it.appUsageList.isNotEmpty() }
        }

        return if (realDays.isNotEmpty()) {
            UsageDataResult.Success(realDays, UsageDataOrigin.REAL_DEVICE)
        } else {
            UsageDataResult.EmptyData
        }
    }

    override fun observeTodayUsage(): Flow<List<AppUsageInfo>> = flow {
        while (true) {
            emit(getTodayUsage())
            delay(60_000)
        }
    }

    override suspend fun hasUsagePermission(): Boolean {
        return usageStatsDataSource.hasPermission()
    }

    private fun buildRealDailyFatigueData(date: String): DailyFatigueData {
        val localDate = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE)
        val (start, end) = getTimeRange(localDate)

        val usageList = usageStatsDataSource.getUsageForDay(start, end)
        val hourlyDist = usageStatsDataSource.getHourlyDistribution(start, end)

        val categoryBreakdown = usageList
            .groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.totalTimeMinutes } }

        val totalScreenTime = usageList.sumOf { it.totalTimeMinutes }
        val totalLaunches = usageList.sumOf { it.launchCount }
        val peakHour = hourlyDist.maxByOrNull { it.value }?.key ?: 12
        val lateNightMinutes = (22..23).sumOf { hourlyDist[it] ?: 0L } +
            (0..5).sumOf { hourlyDist[it] ?: 0L }

        return DailyFatigueData(
            date = date,
            fatigueScore = 0,
            fatigueType = FatigueType.HEALTHY,
            totalScreenTimeMinutes = totalScreenTime,
            appUsageList = usageList,
            categoryBreakdown = categoryBreakdown,
            hourlyDistribution = hourlyDist,
            totalLaunchCount = totalLaunches,
            peakHour = peakHour,
            lateNightMinutes = lateNightMinutes
        )
    }

    private fun todayDate(): String {
        return LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    private fun getTimeRange(date: LocalDate): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return start to end
    }
}
