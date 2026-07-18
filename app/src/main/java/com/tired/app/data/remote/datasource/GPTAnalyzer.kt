package com.tired.app.data.remote.datasource

import com.tired.app.data.remote.api.LLMApiService
import com.tired.app.data.remote.model.ChatCompletionRequest
import com.tired.app.data.remote.model.ChatMessage
import com.tired.app.domain.analyzer.FatigueAnalyzer
import com.tired.app.domain.model.*
import kotlinx.serialization.json.Json
import javax.inject.Inject

class GPTAnalyzer @Inject constructor(
    private val apiService: LLMApiService
) : FatigueAnalyzer {

    private val json = Json { ignoreUnknownKeys = true }
    private var apiKey: String = ""

    fun setApiKey(key: String) {
        apiKey = key
    }

    override suspend fun analyze(data: DailyFatigueData): FatigueAnalysisResult {
        val prompt = buildPrompt(data)
        val request = ChatCompletionRequest(
            model = "gpt-3.5-turbo",
            messages = listOf(
                ChatMessage(
                    role = "system",
                    content = """你是一个专业的手机使用行为分析师。请根据用户的手机使用数据分析疲劳状况。
请严格按照以下JSON格式返回（不要包含其他文字）：
{
  "fatigueScore": 0-100的整数,
  "fatigueType": "DOPAMINE_OVERLOAD" 或 "FRAGMENTED_ANXIETY" 或 "SLEEP_PROCRASTINATION" 或 "INFO_OVERLOAD" 或 "HEALTHY",
  "summary": "简洁的总结分析（100字以内）",
  "suggestions": ["建议1", "建议2", "建议3", "建议4"],
  "detailedAnalysis": "详细分析报告（300字以内）"
}"""
                ),
                ChatMessage(role = "user", content = prompt)
            ),
            temperature = 0.7f,
            maxTokens = 800
        )

        return try {
            val response = apiService.chatCompletion(
                url = "https://api.openai.com/v1/chat/completions",
                authorization = "Bearer $apiKey",
                request = request
            )
            parseResponse(response.choices.firstOrNull()?.message?.content ?: "")
        } catch (e: Exception) {
            FatigueAnalysisResult(
                fatigueScore = 50,
                fatigueType = FatigueType.HEALTHY,
                summary = "AI分析暂时不可用：${e.message}",
                suggestions = listOf("请检查网络连接和API Key", "可切换到本地分析模式"),
                isFromAI = false
            )
        }
    }

    private fun buildPrompt(data: DailyFatigueData): String {
        return buildString {
            appendLine("用户今日手机使用数据：")
            appendLine("总屏幕时间：${data.totalScreenTimeMinutes}分钟")
            appendLine("应用启动次数：${data.totalLaunchCount}次")
            appendLine("使用高峰：${data.peakHour}:00")
            appendLine("深夜使用：${data.lateNightMinutes}分钟")
            appendLine("分类详情：")
            data.categoryBreakdown.forEach { (cat, min) ->
                appendLine("  ${cat.displayName}：${min}分钟")
            }
            appendLine("Top 5应用：")
            data.appUsageList.take(5).forEach { app ->
                appendLine("  ${app.appName}：${app.totalTimeMinutes}分钟")
            }
        }
    }

    private fun parseResponse(content: String): FatigueAnalysisResult {
        return try {
            val jsonStr = content.trim()
                .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

            @kotlinx.serialization.Serializable
            data class AIResponse(
                val fatigueScore: Int,
                val fatigueType: String,
                val summary: String,
                val suggestions: List<String>,
                val detailedAnalysis: String = ""
            )

            val resp = json.decodeFromString<AIResponse>(jsonStr)
            FatigueAnalysisResult(
                fatigueScore = resp.fatigueScore.coerceIn(0, 100),
                fatigueType = try { FatigueType.valueOf(resp.fatigueType) } catch (_: Exception) { FatigueType.HEALTHY },
                summary = resp.summary,
                suggestions = resp.suggestions,
                detailedAnalysis = resp.detailedAnalysis,
                isFromAI = true
            )
        } catch (e: Exception) {
            FatigueAnalysisResult(
                fatigueScore = 50,
                fatigueType = FatigueType.HEALTHY,
                summary = "AI返回解析失败",
                suggestions = listOf("建议切换到本地分析模式"),
                isFromAI = false
            )
        }
    }
}
