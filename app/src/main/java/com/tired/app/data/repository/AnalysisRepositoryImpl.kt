package com.tired.app.data.repository

import com.tired.app.data.local.dao.FatigueAnalysisDao
import com.tired.app.data.local.entity.FatigueAnalysisEntity
import com.tired.app.domain.model.FatigueAnalysisResult
import com.tired.app.domain.model.FatigueType
import com.tired.app.domain.repository.AnalysisRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalysisRepositoryImpl @Inject constructor(
    private val dao: FatigueAnalysisDao
) : AnalysisRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getCachedAnalysis(date: String): FatigueAnalysisResult? {
        val entity = dao.getByDate(date) ?: return null
        return entity.toDomain()
    }

    override suspend fun cacheAnalysis(date: String, result: FatigueAnalysisResult) {
        dao.insert(result.toEntity(date))
    }

    override suspend fun getWeeklyAnalyses(): List<Pair<String, FatigueAnalysisResult>> {
        return dao.getRecentWeek().map { it.date to it.toDomain() }
    }

    private fun FatigueAnalysisEntity.toDomain(): FatigueAnalysisResult {
        return FatigueAnalysisResult(
            fatigueScore = fatigueScore,
            fatigueType = FatigueType.valueOf(fatigueType),
            summary = summary,
            suggestions = json.decodeFromString<List<String>>(suggestions),
            detailedAnalysis = detailedAnalysis,
            isFromAI = isFromAI,
            cachedAt = cachedAt
        )
    }

    private fun FatigueAnalysisResult.toEntity(date: String): FatigueAnalysisEntity {
        return FatigueAnalysisEntity(
            date = date,
            fatigueScore = fatigueScore,
            fatigueType = fatigueType.name,
            summary = summary,
            suggestions = json.encodeToString(suggestions),
            detailedAnalysis = detailedAnalysis,
            isFromAI = isFromAI,
            cachedAt = cachedAt
        )
    }
}
