package com.tired.app.domain.usecase

import com.tired.app.domain.analyzer.FatigueAnalyzer
import com.tired.app.domain.model.DailyFatigueData
import com.tired.app.domain.model.FatigueAnalysisResult
import com.tired.app.domain.repository.AnalysisRepository
import com.tired.app.domain.repository.UsageDataResult
import com.tired.app.domain.repository.UsageRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class GetTodayFatigueUseCase @Inject constructor(
    private val usageRepository: UsageRepository,
    private val analysisRepository: AnalysisRepository
) {
    suspend fun execute(analyzer: FatigueAnalyzer): Pair<DailyFatigueData, FatigueAnalysisResult> {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val dailyData = when (val result = usageRepository.loadDailyFatigueData(today)) {
            is UsageDataResult.Success -> result.data
            UsageDataResult.PermissionRequired -> error("Usage access permission is required.")
            UsageDataResult.EmptyData -> error("No usage stats available for today.")
        }

        // 先检查缓存
        val cached = analysisRepository.getCachedAnalysis(today)
        if (cached != null) {
            return dailyData to cached
        }

        // 执行分析
        val result = analyzer.analyze(dailyData)
        analysisRepository.cacheAnalysis(today, result)
        return dailyData to result
    }
}

class GetWeeklyTrendUseCase @Inject constructor(
    private val usageRepository: UsageRepository
) {
    suspend fun execute(): List<DailyFatigueData> {
        return when (val result = usageRepository.loadWeeklyFatigueData()) {
            is UsageDataResult.Success -> result.data
            UsageDataResult.PermissionRequired -> emptyList()
            UsageDataResult.EmptyData -> emptyList()
        }
    }
}
