package com.tired.app.data.remote.datasource

import com.tired.app.BuildConfig
import com.tired.app.data.remote.api.LLMApiService
import com.tired.app.data.remote.model.BackendChatRequest
import com.tired.app.data.remote.model.BackendUserProfile
import com.tired.app.data.remote.model.ChatCompletionRequest
import com.tired.app.data.remote.model.ChatMessage
import com.tired.app.domain.model.ChatRequestContext
import com.tired.app.domain.model.DailyFatigueData
import com.tired.app.domain.model.FatigueAnalysisResult
import com.tired.app.domain.model.FatigueType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QwenChatService @Inject constructor(
    private val apiService: LLMApiService
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun analyzeFatigue(data: DailyFatigueData): FatigueAnalysisResult {
        val response = complete(
            messages = listOf(
                ChatMessage(role = "system", content = FATIGUE_ANALYZER_PROMPT),
                ChatMessage(role = "user", content = buildFatiguePrompt(data))
            ),
            temperature = 0.6f,
            maxTokens = 900
        )

        return parseFatigueAnalysis(response)
    }

    suspend fun sendConversation(
        history: List<ChatMessage>,
        context: ChatRequestContext
    ): String {
        // 1. 优先尝试请求本地 Python 后端 (携带端侧核心画像)
        try {
            val backendUrl = "${BuildConfig.BACKEND_BASE_URL}/api/chat"
            val historyPool = if (context.usesUsageContext) {
                context.weeklyUsageList.mapIndexed { index, day ->
                    com.tired.app.data.remote.model.BackendDailyStats(
                        date = day.date,
                        daysAgo = index,
                        totalScreenTimeMinutes = day.totalScreenTimeMinutes,
                        lateNightMinutes = day.lateNightMinutes,
                        launchCount = day.totalLaunchCount,
                        topApps = day.appUsageList.take(5).map { app ->
                            com.tired.app.data.remote.model.BackendTopApp(
                                appName = app.appName,
                                durationMinutes = app.totalTimeMinutes,
                                category = app.category.displayName
                            )
                        }
                    )
                }
            } else {
                emptyList()
            }

            val backendReq = BackendChatRequest(
                messages = history,
                deviceHistoryPool = historyPool,
                userProfile = context.userProfile?.let {
                    BackendUserProfile(
                        dangerTimeRange = it.dangerTimeRange,
                        triggerAppHabits = it.triggerAppHabits,
                        reliefPreferences = it.reliefPreferences,
                        contextBio = it.contextBio
                    )
                }
            )
            val backendResp = apiService.backendChat(backendUrl, backendReq)
            if (backendResp.content.isNotBlank()) {
                android.util.Log.i("QwenChatService", "Chat response received from local Backend")
                return backendResp.content
            }
        } catch (e: Exception) {
            android.util.Log.w("QwenChatService", "Local backend not connected (${e.message}), gracefully falling back to direct Bailian API")
        }

        // 2. 降级方案：直接调用云端百炼 API
        val messages = buildList {
            add(ChatMessage(role = "system", content = buildChatSystemPrompt(context)))
            addAll(history)
        }

        return complete(
            messages = messages,
            temperature = 0.8f,
            maxTokens = 900
        )
    }

    private suspend fun complete(
        messages: List<ChatMessage>,
        temperature: Float,
        maxTokens: Int
    ): String {
        val response = apiService.chatCompletion(
            url = BuildConfig.BAILIAN_CHAT_URL,
            authorization = "Bearer ${BuildConfig.BAILIAN_API_KEY}",
            request = ChatCompletionRequest(
                model = BuildConfig.BAILIAN_MODEL,
                messages = messages,
                temperature = temperature,
                maxTokens = maxTokens
            )
        )

        return response.choices.firstOrNull()?.message?.content?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: error("Qwen returned an empty response")
    }

    private fun buildFatiguePrompt(data: DailyFatigueData): String {
        return buildString {
            appendLine("Analyze the user's phone activity for today and return JSON.")
            appendLine("- Date: ${data.date}")
            appendLine("- Total screen time: ${data.totalScreenTimeMinutes} minutes")
            appendLine("- App launches: ${data.totalLaunchCount}")
            appendLine("- Peak hour: ${data.peakHour}:00")
            appendLine("- Late-night usage: ${data.lateNightMinutes} minutes")
            appendLine()
            appendLine("Category breakdown:")
            data.categoryBreakdown.forEach { (category, minutes) ->
                appendLine("- ${category.displayName}: ${minutes} minutes")
            }
            appendLine()
            appendLine("Top apps:")
            data.appUsageList.take(6).forEach { app ->
                appendLine("- ${app.appName} (${app.category.displayName}): ${app.totalTimeMinutes} minutes, ${app.launchCount} launches")
            }
        }
    }

    private fun parseFatigueAnalysis(content: String): FatigueAnalysisResult {
        val jsonStr = content.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val response = json.decodeFromString<QwenFatigueResponse>(jsonStr)
        return FatigueAnalysisResult(
            fatigueScore = response.fatigueScore.coerceIn(0, 100),
            fatigueType = runCatching { FatigueType.valueOf(response.fatigueType) }
                .getOrDefault(FatigueType.HEALTHY),
            summary = response.summary,
            suggestions = response.suggestions,
            detailedAnalysis = response.detailedAnalysis,
            isFromAI = true
        )
    }

    private fun buildChatSystemPrompt(context: ChatRequestContext): String {
        return buildString {
            appendLine("You are the AI digital wellness & energy recovery companion inside the Tired? app.")
            appendLine("Your role: An empathetic, realistic, non-preachy rest guide. You help the user understand and recover from 'fake-rest' (exhausting doomscrolling/phone addiction) and cognitive burnout.")
            appendLine("Core response rules:")
            appendLine("1. Reply in the exact same language as the user (use natural, empathetic Chinese if user speaks Chinese, or English if user speaks English).")
            appendLine("2. Be warm, candid, and practical. NEVER preach, lecture, or scold the user with textbook health clichés like 'you lack discipline' or 'put down your phone immediately'.")
            appendLine("3. Keep responses concise (3-6 sentences or short punchy bullet points) so reading doesn't add more cognitive strain.")

            context.userProfile?.let { profile ->
                appendLine()
                appendLine("[Long-term User Memory & Core Profile]")
                appendLine("- High-risk fatigue window: ${profile.dangerTimeRange}")
                appendLine("- High-drain habits / Triggers: ${profile.triggerAppHabits}")
                appendLine("- Recovery preference & boundaries: ${profile.reliefPreferences}")
                appendLine("- User lifestyle context: ${profile.contextBio}")
                appendLine("INSTRUCTION: Tailor your empathy, tone, and practical recovery suggestions directly according to this profile.")
            }

            if (context.usesUsageContext && context.summary.isNotBlank()) {
                appendLine()
                appendLine("[Today's device activity context]")
                appendLine(context.summary)
            } else {
                appendLine()
                appendLine("Today's device activity is unavailable, so assist as a personalized wellness companion.")
            }
        }
    }

    @Serializable
    private data class QwenFatigueResponse(
        val fatigueScore: Int,
        val fatigueType: String,
        val summary: String,
        val suggestions: List<String>,
        val detailedAnalysis: String = ""
    )

    private companion object {
        val FATIGUE_ANALYZER_PROMPT = """
            You are an expert in phone-behavior analysis and digital wellness. Use the user's activity for today to identify the most likely fatigue pattern and recommend practical next steps.
            You must return JSON only, with no extra explanation. Use this exact schema:
            {
              "fatigueScore": 0-100 integer,
              "fatigueType": "DOPAMINE_OVERLOAD" or "FRAGMENTED_ANXIETY" or "SLEEP_PROCRASTINATION" or "INFO_OVERLOAD" or "HEALTHY",
              "summary": "English summary within 100 words",
              "suggestions": ["Suggestion 1", "Suggestion 2", "Suggestion 3", "Suggestion 4"],
              "detailedAnalysis": "English detailed analysis within 300 words"
            }
        """.trimIndent()
    }
}
