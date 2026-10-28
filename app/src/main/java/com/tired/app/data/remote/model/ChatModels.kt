package com.tired.app.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Float = 0.7f,
    @SerialName("max_tokens")
    val maxTokens: Int = 1024
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class ChatCompletionResponse(
    val choices: List<Choice>
)

@Serializable
data class Choice(
    val message: ChatMessage
)

@Serializable
data class BackendChatRequest(
    val messages: List<ChatMessage>,
    @SerialName("device_history_pool")
    val deviceHistoryPool: List<BackendDailyStats> = emptyList(),
    @SerialName("user_profile")
    val userProfile: BackendUserProfile? = null
)

@Serializable
data class BackendDailyStats(
    val date: String,
    @SerialName("days_ago")
    val daysAgo: Int,
    @SerialName("total_screen_time_minutes")
    val totalScreenTimeMinutes: Long,
    @SerialName("late_night_minutes")
    val lateNightMinutes: Long,
    @SerialName("launch_count")
    val launchCount: Int,
    @SerialName("top_apps")
    val topApps: List<BackendTopApp> = emptyList()
)

@Serializable
data class BackendTopApp(
    @SerialName("app_name")
    val appName: String,
    @SerialName("duration_minutes")
    val durationMinutes: Long,
    val category: String = ""
)

@Serializable
data class BackendUserProfile(
    val dangerTimeRange: String = "",
    val triggerAppHabits: String = "",
    val reliefPreferences: String = "",
    val contextBio: String = ""
)

@Serializable
data class BackendChatResponse(
    val content: String
)

@Serializable
data class BackendTraitItem(
    val id: Int,
    @SerialName("user_id")
    val userId: String = "default_user",
    val category: String,
    val content: String,
    val status: String = "ACTIVE",
    val confidence: String = "HIGH",
    @SerialName("source_date")
    val sourceDate: String = "",
    @SerialName("updated_at")
    val updatedAt: Long = 0L
)

@Serializable
data class BackendTraitListResponse(
    @SerialName("user_id")
    val userId: String = "default_user",
    val traits: List<BackendTraitItem> = emptyList()
)

@Serializable
data class BackendTraitMutationRequest(
    val category: String,
    val content: String,
    val status: String = "ACTIVE",
    val confidence: String = "HIGH"
)

@Serializable
data class BackendTraitMutationResponse(
    val status: String,
    val trait: BackendTraitItem? = null,
    @SerialName("deleted_id")
    val deletedId: Int? = null
)

@Serializable
data class BackendAntiFatiguePlan(
    @SerialName("user_id")
    val userId: String = "default_user",
    val title: String,
    @SerialName("target_fatigue_type")
    val targetFatigueType: String,
    @SerialName("target_time_window")
    val targetTimeWindow: String,
    @SerialName("trigger_apps")
    val triggerApps: List<String> = emptyList(),
    @SerialName("max_continuous_minutes")
    val maxContinuousMinutes: Int,
    @SerialName("prompt_message")
    val promptMessage: String,
    @SerialName("recovery_action_type")
    val recoveryActionType: String,
    @SerialName("action_duration_minutes")
    val actionDurationMinutes: Int = 3,
    @SerialName("micro_tips")
    val microTips: List<String> = emptyList(),
    @SerialName("updated_at")
    val updatedAt: Long = 0L
)

@Serializable
data class BackendPlanResponse(
    @SerialName("user_id")
    val userId: String = "default_user",
    val plan: BackendAntiFatiguePlan? = null
)

