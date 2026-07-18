package com.tired.app.presentation.screens.home

import com.tired.app.MainDispatcherRule
import com.tired.app.data.remote.datasource.QwenAnalyzer
import com.tired.app.domain.analyzer.LocalRuleEngine
import com.tired.app.domain.model.AppCategory
import com.tired.app.domain.model.AppUsageInfo
import com.tired.app.domain.model.DailyFatigueData
import com.tired.app.domain.model.FatigueAnalysisResult
import com.tired.app.domain.model.FatigueType
import com.tired.app.domain.repository.AnalysisRepository
import com.tired.app.domain.repository.UsageDataOrigin
import com.tired.app.domain.repository.UsageDataResult
import com.tired.app.domain.repository.UsageRepository
import com.tired.app.presentation.model.UsageScreenState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sampleData = DailyFatigueData(
        date = "2026-04-19",
        fatigueScore = 62,
        fatigueType = FatigueType.DOPAMINE_OVERLOAD,
        totalScreenTimeMinutes = 310,
        appUsageList = listOf(
            AppUsageInfo(
                packageName = "com.youtube",
                appName = "YouTube",
                category = AppCategory.VIDEO,
                totalTimeMinutes = 120,
                launchCount = 9
            )
        ),
        categoryBreakdown = mapOf(
            AppCategory.VIDEO to 120,
            AppCategory.SOCIAL to 90
        ),
        hourlyDistribution = mapOf(22 to 35, 23 to 40),
        totalLaunchCount = 31,
        peakHour = 23,
        lateNightMinutes = 54
    )

    @Test
    fun `falls back to local analysis when qwen analyzer fails`() = runTest {
        val usageRepository = FakeUsageRepository(
            dailyResult = UsageDataResult.Success(sampleData, UsageDataOrigin.REAL_DEVICE)
        )
        val analysisRepository = mockk<AnalysisRepository>()
        val qwenAnalyzer = mockk<QwenAnalyzer>()

        coEvery { analysisRepository.getCachedAnalysis(any()) } returns null
        coEvery { analysisRepository.cacheAnalysis(any(), any()) } returns Unit
        coEvery { qwenAnalyzer.analyze(any()) } throws IllegalStateException("network down")

        val viewModel = HomeViewModel(
            usageRepository = usageRepository,
            analysisRepository = analysisRepository,
            localRuleEngine = LocalRuleEngine(),
            qwenAnalyzer = qwenAnalyzer
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(UsageScreenState.CONTENT, state.screenState)
        assertFalse(state.isFromAI)
        assertEquals(sampleData.totalScreenTimeMinutes, state.totalScreenTimeMinutes)
        coVerify(exactly = 0) { analysisRepository.cacheAnalysis(any(), any()) }
    }

    @Test
    fun `uses fresh cached ai analysis without calling qwen`() = runTest {
        val usageRepository = FakeUsageRepository(
            dailyResult = UsageDataResult.Success(sampleData, UsageDataOrigin.REAL_DEVICE)
        )
        val analysisRepository = mockk<AnalysisRepository>()
        val qwenAnalyzer = mockk<QwenAnalyzer>()
        val cached = FatigueAnalysisResult(
            fatigueScore = 77,
            fatigueType = FatigueType.INFO_OVERLOAD,
            summary = "Cached summary",
            suggestions = listOf("Cached suggestion"),
            isFromAI = true,
            cachedAt = System.currentTimeMillis()
        )

        coEvery { analysisRepository.getCachedAnalysis(any()) } returns cached
        coEvery { analysisRepository.cacheAnalysis(any(), any()) } returns Unit

        val viewModel = HomeViewModel(
            usageRepository = usageRepository,
            analysisRepository = analysisRepository,
            localRuleEngine = LocalRuleEngine(),
            qwenAnalyzer = qwenAnalyzer
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Cached summary", state.summary)
        assertTrue(state.isFromAI)
        assertTrue(state.analysisRefreshHint.contains("once an hour"))
        coVerify(exactly = 0) { qwenAnalyzer.analyze(any()) }
    }

    private class FakeUsageRepository(
        private val dailyResult: UsageDataResult<DailyFatigueData>
    ) : UsageRepository {
        override suspend fun getTodayUsage(): List<AppUsageInfo> = emptyList()

        override suspend fun loadDailyFatigueData(date: String): UsageDataResult<DailyFatigueData> {
            return dailyResult
        }

        override suspend fun loadWeeklyFatigueData(): UsageDataResult<List<DailyFatigueData>> {
            return UsageDataResult.EmptyData
        }

        override fun observeTodayUsage(): Flow<List<AppUsageInfo>> = emptyFlow()

        override suspend fun hasUsagePermission(): Boolean = true
    }
}
