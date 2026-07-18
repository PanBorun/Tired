package com.tired.app.presentation.screens.assistant

import com.tired.app.MainDispatcherRule
import com.tired.app.domain.model.AppCategory
import com.tired.app.domain.model.AppUsageInfo
import com.tired.app.domain.model.ChatConversationSummary
import com.tired.app.domain.model.ChatSender
import com.tired.app.domain.model.ChatUiMessage
import com.tired.app.domain.model.DailyFatigueData
import com.tired.app.domain.model.FatigueType
import com.tired.app.domain.repository.ChatRepository
import com.tired.app.domain.repository.UsageDataOrigin
import com.tired.app.domain.repository.UsageDataResult
import com.tired.app.domain.repository.UsageRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AIAssistantViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sampleData = DailyFatigueData(
        date = "2026-04-19",
        fatigueScore = 48,
        fatigueType = FatigueType.INFO_OVERLOAD,
        totalScreenTimeMinutes = 255,
        appUsageList = listOf(
            AppUsageInfo(
                packageName = "com.chrome",
                appName = "Chrome",
                category = AppCategory.NEWS,
                totalTimeMinutes = 80,
                launchCount = 7
            )
        ),
        categoryBreakdown = mapOf(AppCategory.NEWS to 80, AppCategory.SOCIAL to 70),
        hourlyDistribution = mapOf(21 to 22, 22 to 30),
        totalLaunchCount = 22,
        peakHour = 22,
        lateNightMinutes = 28
    )

    @Test
    fun `loads usage context and sends prompt into chat history`() = runTest {
        val chatRepository = FakeChatRepository()
        val usageRepository = FakeUsageRepository(
            UsageDataResult.Success(sampleData, UsageDataOrigin.REAL_DEVICE)
        )

        val viewModel = AIAssistantViewModel(
            chatRepository = chatRepository,
            usageRepository = usageRepository
        )

        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.context.usesUsageContext)
        assertTrue(viewModel.uiState.value.suggestedPrompts.isNotEmpty())

        viewModel.sendPrompt("根据我今天的使用情况，你觉得我最需要调整什么？")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.messages.size)
        assertEquals(ChatSender.USER, state.messages.first().sender)
        assertEquals(ChatSender.ASSISTANT, state.messages.last().sender)
        assertFalse(state.isSending)
        assertEquals(true, chatRepository.lastIncludeTodayUsage)
    }

    private class FakeChatRepository : ChatRepository {
        private val messages = MutableStateFlow<List<ChatUiMessage>>(emptyList())
        private val conversations = MutableStateFlow<List<ChatConversationSummary>>(emptyList())
        var lastIncludeTodayUsage: Boolean? = null

        override fun observeMessages(conversationId: String): Flow<List<ChatUiMessage>> = messages

        override fun observeConversations(): Flow<List<ChatConversationSummary>> = conversations

        override suspend fun sendMessage(
            conversationId: String,
            userInput: String,
            includeTodayUsage: Boolean
        ): Result<Unit> {
            lastIncludeTodayUsage = includeTodayUsage
            messages.value = messages.value + ChatUiMessage(
                id = messages.value.size.toLong() + 1,
                sender = ChatSender.USER,
                content = userInput,
                createdAt = 1L,
                conversationId = conversationId,
                usedUsageContext = includeTodayUsage
            ) + ChatUiMessage(
                id = messages.value.size.toLong() + 2,
                sender = ChatSender.ASSISTANT,
                content = "这里是 AI 回复",
                createdAt = 2L,
                conversationId = conversationId,
                usedUsageContext = includeTodayUsage
            )
            conversations.value = listOf(
                ChatConversationSummary(
                    conversationId = conversationId,
                    title = userInput,
                    sessionDate = "2026-04-19",
                    startedAt = 1L,
                    updatedAt = 2L
                )
            )
            return Result.success(Unit)
        }

        override suspend fun clearHistory() {
            messages.value = emptyList()
        }
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
