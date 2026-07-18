package com.tired.app.presentation.screens.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tired.app.domain.model.*
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

data class AnalysisUiState(
    val screenState: UsageScreenState = UsageScreenState.LOADING,
    val selectedDate: String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
    val dataOrigin: UsageDataOrigin? = null,
    val categoryBreakdown: Map<AppCategory, Long> = emptyMap(),
    val hourlyDistribution: Map<Int, Long> = emptyMap(),
    val totalScreenTimeMinutes: Long = 0,
    val totalLaunchCount: Int = 0,
    val peakHour: Int = 0,
    val lateNightMinutes: Long = 0,
    val appUsageList: List<AppUsageInfo> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class AnalysisViewModel @Inject constructor(
    private val usageRepository: UsageRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalysisUiState())
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()

    init {
        loadData(_uiState.value.selectedDate)
    }

    fun selectDate(date: String) {
        _uiState.value = _uiState.value.copy(selectedDate = date)
        loadData(date)
    }

    fun refresh() {
        loadData(_uiState.value.selectedDate)
    }

    private fun loadData(date: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                screenState = UsageScreenState.LOADING,
                error = null
            )
            try {
                when (val result = usageRepository.loadDailyFatigueData(date)) {
                    UsageDataResult.PermissionRequired -> {
                        _uiState.value = AnalysisUiState(
                            screenState = UsageScreenState.PERMISSION_REQUIRED,
                            selectedDate = date
                        )
                    }

                    UsageDataResult.EmptyData -> {
                        _uiState.value = AnalysisUiState(
                            screenState = UsageScreenState.EMPTY,
                            selectedDate = date
                        )
                    }

                    is UsageDataResult.Success -> {
                        val data = result.data
                        _uiState.value = AnalysisUiState(
                            screenState = UsageScreenState.CONTENT,
                            selectedDate = date,
                            dataOrigin = result.origin,
                            categoryBreakdown = data.categoryBreakdown,
                            hourlyDistribution = data.hourlyDistribution,
                            totalScreenTimeMinutes = data.totalScreenTimeMinutes,
                            totalLaunchCount = data.totalLaunchCount,
                            peakHour = data.peakHour,
                            lateNightMinutes = data.lateNightMinutes,
                            appUsageList = data.appUsageList
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = AnalysisUiState(
                    screenState = UsageScreenState.EMPTY,
                    selectedDate = date,
                    error = "Unable to load data: ${e.message}"
                )
            }
        }
    }
}
