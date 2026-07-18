package com.tired.app.domain.analyzer

import com.tired.app.domain.model.DailyFatigueData
import com.tired.app.domain.model.FatigueAnalysisResult

interface FatigueAnalyzer {
    suspend fun analyze(data: DailyFatigueData): FatigueAnalysisResult
}
