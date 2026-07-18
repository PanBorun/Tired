package com.tired.app.data.remote.datasource

import com.tired.app.domain.analyzer.FatigueAnalyzer
import com.tired.app.domain.model.DailyFatigueData
import com.tired.app.domain.model.FatigueAnalysisResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QwenAnalyzer @Inject constructor(
    private val qwenChatService: QwenChatService
) : FatigueAnalyzer {
    override suspend fun analyze(data: DailyFatigueData): FatigueAnalysisResult {
        return qwenChatService.analyzeFatigue(data)
    }
}
