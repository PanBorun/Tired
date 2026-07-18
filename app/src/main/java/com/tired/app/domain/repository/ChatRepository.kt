package com.tired.app.domain.repository

import com.tired.app.domain.model.ChatConversationSummary
import com.tired.app.domain.model.ChatUiMessage
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun observeMessages(conversationId: String): Flow<List<ChatUiMessage>>
    fun observeConversations(): Flow<List<ChatConversationSummary>>
    suspend fun sendMessage(
        conversationId: String,
        userInput: String,
        includeTodayUsage: Boolean
    ): Result<Unit>
    suspend fun clearHistory()
}
