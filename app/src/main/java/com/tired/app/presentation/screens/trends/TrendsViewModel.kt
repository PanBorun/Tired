package com.tired.app.presentation.screens.trends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tired.app.domain.analyzer.LocalRuleEngine
import com.tired.app.domain.model.DailyFatigueData
import com.tired.app.domain.repository.UsageDataOrigin
import com.tired.app.domain.repository.UsageDataResult
import com.tired.app.domain.repository.UsageRepository
import com.tired.app.presentation.model.UsageScreenState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrendsUiState(
    val screenState: UsageScreenState = UsageScreenState.LOADING,
    val dataOrigin: UsageDataOrigin? = null,
    val weeklyData: List<DailyFatigueData> = emptyList(),
    val weeklyScores: List<Pair<String, Int>> = emptyList(),
    val weeklyScreenTime: List<Pair<String, Long>> = emptyList(),
    val averageScore: Int = 0,
    val trend: String = "",
    val trendEmoji: String = "",
    val error: String? = null
)

@HiltViewModel
class TrendsViewModel @Inject constructor(
    private val usageRepository: UsageRepository,
    private val localRuleEngine: LocalRuleEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrendsUiState())
    val uiState: StateFlow<TrendsUiState> = _uiState.asStateFlow()

    init {
        loadTrends()
    }

    fun loadTrends() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                screenState = UsageScreenState.LOADING,
                error = null
            )
            try {
                when (val result = usageRepository.loadWeeklyFatigueData()) {
                    UsageDataResult.PermissionRequired -> {
                        _uiState.value = TrendsUiState(
                            screenState = UsageScreenState.PERMISSION_REQUIRED
                        )
                    }

                    UsageDataResult.EmptyData -> {
                        _uiState.value = TrendsUiState(
                            screenState = UsageScreenState.EMPTY
                        )
                    }

                    is UsageDataResult.Success -> {
                        val weeklyData = result.data
                        val scores = weeklyData.map { data ->
                            val analysis = localRuleEngine.analyze(data)
                            data.date to analysis.fatigueScore
                        }

                        val screenTimes = weeklyData.map { it.date to it.totalScreenTimeMinutes }
                        val avgScore = if (scores.isNotEmpty()) {
                            scores.map { it.second }.average().toInt()
                        } else {
                            0
                        }
                        val (trend, emoji) = calculateTrend(scores.map { it.second })

                        _uiState.value = TrendsUiState(
                            screenState = UsageScreenState.CONTENT,
                            dataOrigin = result.origin,
                            weeklyData = weeklyData,
                            weeklyScores = scores,
                            weeklyScreenTime = screenTimes,
                            averageScore = avgScore,
                            trend = trend,
                            trendEmoji = emoji
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = TrendsUiState(
                    screenState = UsageScreenState.EMPTY,
                    error = "Unable to load trend data: ${e.message}"
                )
            }
        }
    }

    private fun calculateTrend(scores: List<Int>): Pair<String, String> {
        if (scores.size < 2) return "Not enough data yet" to "📊"

        val recent = scores.takeLast(3).average()
        val earlier = scores.take(3).average()
        val diff = recent - earlier

        return when {
            diff > 10 -> "Fatigue is trending up. More recovery would help." to "📈"
            diff > 3 -> "Fatigue is rising slightly." to "↗️"
            diff < -10 -> "Fatigue has improved a lot. Keep going." to "📉"
            diff < -3 -> "Fatigue is easing." to "↘️"
            else -> "Your pattern looks fairly steady." to "➡️"
        }
    }
}
