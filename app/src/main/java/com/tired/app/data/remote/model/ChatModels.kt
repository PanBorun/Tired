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
    @SerialName("device_summary")
    val deviceSummary: String = "",
    @SerialName("user_profile")
    val userProfile: BackendUserProfile? = null
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

