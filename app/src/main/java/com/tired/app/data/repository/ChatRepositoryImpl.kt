package com.tired.app.data.repository

import com.tired.app.data.local.dao.ChatMessageDao
import com.tired.app.data.local.dao.ChatConversationRow
import com.tired.app.data.local.entity.ChatMessageEntity
import com.tired.app.data.remote.datasource.QwenChatService
import com.tired.app.data.remote.model.ChatMessage
import com.tired.app.domain.model.ChatConversationSummary
import com.tired.app.domain.model.ChatRequestContext
import com.tired.app.domain.model.ChatSender
import com.tired.app.domain.model.ChatUiMessage
import com.tired.app.domain.model.DailyFatigueData
import com.tired.app.domain.repository.ChatRepository
import com.tired.app.domain.repository.UsageDataOrigin
import com.tired.app.domain.repository.UsageDataResult
import com.tired.app.domain.repository.UsageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val chatMessageDao: ChatMessageDao,
    private val usageRepository: UsageRepository,
    private val qwenChatService: QwenChatService
) : ChatRepository {

    override fun observeMessages(conversationId: String): Flow<List<ChatUiMessage>> {
        return chatMessageDao.observeMessages(conversationId).map { messages ->
            messages.map { entity ->
                ChatUiMessage(
                    id = entity.id,
                    sender = if (entity.role == ROLE_ASSISTANT) ChatSender.ASSISTANT else ChatSender.USER,
                    content = entity.content,
                    createdAt = entity.createdAt,
                    conversationId = entity.conversationId,
                    usedUsageContext = entity.usedUsageContext,
                    isError = entity.isError
                )
            }
        }
    }

    override fun observeConversations(): Flow<List<ChatConversationSummary>> {
        return chatMessageDao.observeConversations().map { rows ->
            rows.map { row -> row.toDomain() }
        }
    }

    override suspend fun sendMessage(
        conversationId: String,
        userInput: String,
        includeTodayUsage: Boolean
    ): Result<Unit> {
        val trimmedInput = userInput.trim()
        if (trimmedInput.isEmpty()) {
            return Result.failure(IllegalArgumentException("Message cannot be empty"))
        }

        val sessionDate = today()
        val context = if (includeTodayUsage) loadTodayContext() else ChatRequestContext()

        chatMessageDao.insert(
            ChatMessageEntity(
                conversationId = conversationId,
                role = ROLE_USER,
                content = trimmedInput,
                sessionDate = sessionDate,
                usedUsageContext = context.usesUsageContext
            )
        )

        return runCatching {
            val history = chatMessageDao.getRecentMessages(conversationId, HISTORY_LIMIT)
                .asReversed()
                .map { entity ->
                    ChatMessage(
                        role = entity.role,
                        content = entity.content
                    )
                }

            val answer = qwenChatService.sendConversation(history, context)
            chatMessageDao.insert(
                ChatMessageEntity(
                    conversationId = conversationId,
                    role = ROLE_ASSISTANT,
                    content = answer,
                    sessionDate = sessionDate,
                    usedUsageContext = context.usesUsageContext
                )
            )
        }
    }

    override suspend fun clearHistory() {
        chatMessageDao.clearAll()
    }

    private suspend fun loadTodayContext(): ChatRequestContext {
        return when (val result = usageRepository.loadDailyFatigueData(today())) {
            is UsageDataResult.Success -> {
                val note = when (result.origin) {
                    UsageDataOrigin.REAL_DEVICE -> "This reply will use today's real device activity."
                }
                ChatRequestContext(
                    summary = buildUsageSummary(result.data),
                    note = note,
                    usesUsageContext = true
                )
            }

            UsageDataResult.PermissionRequired,
            UsageDataResult.EmptyData -> {
                ChatRequestContext(
                    note = "Today's device activity is unavailable, so replies will use general wellness guidance.",
                    usesUsageContext = false
                )
            }
        }
    }

    private fun buildUsageSummary(data: DailyFatigueData): String {
        val topApps = data.appUsageList.take(3).joinToString("; ") { app ->
            "${app.appName} ${app.totalTimeMinutes} min"
        }
        val categories = data.categoryBreakdown.entries
            .sortedByDescending { it.value }
            .take(3)
            .joinToString("; ") { (category, minutes) ->
                "${category.displayName} ${minutes} min"
            }

        return buildString {
            appendLine("Date: ${data.date}")
            appendLine("Screen time: ${data.totalScreenTimeMinutes} min")
            appendLine("Launches: ${data.totalLaunchCount}")
            appendLine("Late-night use: ${data.lateNightMinutes} min")
            appendLine("Peak hour: ${data.peakHour}:00")
            appendLine("Top categories: $categories")
            appendLine("Most-used apps: $topApps")
        }
    }

    private fun today(): String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

    private fun ChatConversationRow.toDomain(): ChatConversationSummary {
        return ChatConversationSummary(
            conversationId = conversationId,
            title = preview
                ?.takeIf { it.isNotBlank() }
                ?.take(42)
                ?: "Untitled chat",
            sessionDate = sessionDate,
            startedAt = startedAt,
            updatedAt = updatedAt
        )
    }

    private companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        const val HISTORY_LIMIT = 12
    }
}
