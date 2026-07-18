package com.tired.app.domain.repository

import com.tired.app.domain.model.DailyFatigueData
import com.tired.app.domain.model.FatigueAnalysisResult

interface AnalysisRepository {
    suspend fun getCachedAnalysis(date: String): FatigueAnalysisResult?
    suspend fun cacheAnalysis(date: String, result: FatigueAnalysisResult)
    suspend fun getWeeklyAnalyses(): List<Pair<String, FatigueAnalysisResult>>
}
