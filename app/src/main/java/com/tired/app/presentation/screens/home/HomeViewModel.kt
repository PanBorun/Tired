package com.tired.app.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tired.app.data.remote.datasource.QwenAnalyzer
import com.tired.app.domain.analyzer.LocalRuleEngine
import com.tired.app.domain.model.*
import com.tired.app.domain.repository.AnalysisRepository
import com.tired.app.domain.repository.UsageDataOrigin
import com.tired.app.domain.repository.UsageDataResult
import com.tired.app.domain.repository.UsageRepository
import com.tired.app.presentation.model.UsageScreenState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class HomeUiState(
    val screenState: UsageScreenState = UsageScreenState.LOADING,
    val dataOrigin: UsageDataOrigin? = null,
    val fatigueScore: Int = 0,
    val fatigueType: FatigueType = FatigueType.HEALTHY,
    val summary: String = "",
    val suggestions: List<String> = emptyList(),
    val totalScreenTimeMinutes: Long = 0,
    val totalLaunchCount: Int = 0,
    val topApps: List<AppUsageInfo> = emptyList(),
    val isFromAI: Boolean = false,
    val analysisRefreshHint: String = "",
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val usageRepository: UsageRepository,
    private val analysisRepository: AnalysisRepository,
    private val localRuleEngine: LocalRuleEngine,
    private val qwenAnalyzer: QwenAnalyzer
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                screenState = UsageScreenState.LOADING,
                error = null
            )

            try {
                val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                when (val dailyResult = usageRepository.loadDailyFatigueData(today)) {
                    UsageDataResult.PermissionRequired -> {
                        _uiState.value = HomeUiState(
                            screenState = UsageScreenState.PERMISSION_REQUIRED
                        )
                    }

                    UsageDataResult.EmptyData -> {
                        _uiState.value = HomeUiState(
                            screenState = UsageScreenState.EMPTY
                        )
                    }

                    is UsageDataResult.Success -> {
                        val dailyData = dailyResult.data
                        val result = if (dailyResult.origin == UsageDataOrigin.REAL_DEVICE) {
                            resolveAnalysis(today, dailyData)
                        } else {
                            analyzeWithFallback(dailyData)
                        }

                        _uiState.value = HomeUiState(
                            screenState = UsageScreenState.CONTENT,
                            dataOrigin = dailyResult.origin,
                            fatigueScore = result.fatigueScore,
                            fatigueType = result.fatigueType,
                            summary = result.summary,
                            suggestions = result.suggestions,
                            totalScreenTimeMinutes = dailyData.totalScreenTimeMinutes,
                            totalLaunchCount = dailyData.totalLaunchCount,
                            topApps = dailyData.appUsageList.take(5),
                            isFromAI = result.isFromAI,
                            analysisRefreshHint = buildAnalysisRefreshHint(result)
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = HomeUiState(
                    screenState = UsageScreenState.EMPTY,
                    error = "Unable to load data: ${e.message}"
                )
            }
        }
    }

    fun refresh() {
        loadData()
    }

    private suspend fun resolveAnalysis(
        date: String,
        dailyData: DailyFatigueData
    ): FatigueAnalysisResult {
        val cached = analysisRepository.getCachedAnalysis(date)
            ?.takeIf { it.isFromAI }
        if (cached != null && !isExpired(cached)) {
            return cached
        }

        val result = analyzeWithFallback(dailyData)
        if (result.isFromAI) {
            analysisRepository.cacheAnalysis(date, result)
            return result
        }
        return cached ?: result
    }

    private suspend fun analyzeWithFallback(dailyData: DailyFatigueData): FatigueAnalysisResult {
        return runCatching {
            qwenAnalyzer.analyze(dailyData)
        }.getOrElse {
            localRuleEngine.analyze(dailyData)
        }
    }

    private fun isExpired(result: FatigueAnalysisResult): Boolean {
        return System.currentTimeMillis() - result.cachedAt >= ANALYSIS_CACHE_TTL_MS
    }

    private fun buildAnalysisRefreshHint(result: FatigueAnalysisResult): String {
        return if (result.isFromAI) {
            "AI analysis refreshes about once an hour. Pull to refresh after that for a newer reading."
        } else {
            "Local analysis is shown right away. AI insights refresh about once an hour when available."
        }
    }

    private companion object {
        const val ANALYSIS_CACHE_TTL_MS = 60 * 60 * 1000L
    }
}
