package com.tired.app.domain.repository

import com.tired.app.data.remote.model.BackendTraitItem
import com.tired.app.domain.model.ChatConversationSummary
import com.tired.app.domain.model.ChatUiMessage
import com.tired.app.domain.model.UserDigitalProfile
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
    fun observeUserProfile(): Flow<UserDigitalProfile>
    suspend fun getUserProfile(): UserDigitalProfile
    suspend fun updateUserProfile(profile: UserDigitalProfile)

    // 阶段 2：后端自演化画像标签管理
    suspend fun getProfileTraits(): Result<List<BackendTraitItem>>
    suspend fun addProfileTrait(category: String, content: String, status: String = "ACTIVE"): Result<BackendTraitItem>
    suspend fun updateProfileTrait(traitId: Int, category: String, content: String, status: String): Result<BackendTraitItem>
    suspend fun deleteProfileTrait(traitId: Int): Result<Unit>

    // 阶段 3：防疲劳方案获取与删除
    suspend fun getAntiFatiguePlan(): Result<com.tired.app.data.remote.model.BackendAntiFatiguePlan?>
    suspend fun deleteAntiFatiguePlan(): Result<Unit>
}
