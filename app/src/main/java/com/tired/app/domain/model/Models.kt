package com.tired.app.domain.model

import kotlinx.serialization.Serializable

/**
 * App使用数据
 */
data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val category: AppCategory,
    val totalTimeMinutes: Long,
    val launchCount: Int,
    val lastUsedTimestamp: Long = 0L
)

/**
 * App分类
 */
enum class AppCategory(val displayName: String, val emoji: String) {
    SOCIAL("Social", "💬"),
    VIDEO("Video", "🎬"),
    GAME("Games", "🎮"),
    NEWS("News", "📰"),
    SHOPPING("Shopping", "🛒"),
    WORK("Work", "💼"),
    TOOL("Tools", "🔧"),
    HEALTH("Health", "🏃"),
    MUSIC("Music", "🎵"),
    OTHER("Other", "📱")
}

/**
 * 疲劳类型
 */
enum class FatigueType(
    val displayName: String,
    val description: String,
    val emoji: String,
    val color: Long
) {
    DOPAMINE_OVERLOAD(
        "Dopamine Spike",
        "Heavy short-form video and social media use is overstimulating your reward system.",
        "🧠",
        0xFFE91E63
    ),
    FRAGMENTED_ANXIETY(
        "Scattered Focus",
        "Frequent app switching is scattering your focus and raising mental friction.",
        "🔀",
        0xFFFF9800
    ),
    SLEEP_PROCRASTINATION(
        "Sleep Delay",
        "Late-night phone use is pushing back sleep and disrupting your rhythm.",
        "🌙",
        0xFF673AB7
    ),
    INFO_OVERLOAD(
        "Info Overload",
        "High volumes of news and information are creating cognitive fatigue.",
        "📚",
        0xFF2196F3
    ),
    HEALTHY(
        "Balanced",
        "Your phone habits look balanced today. Keep it up.",
        "✅",
        0xFF4CAF50
    )
}

/**
 * 每日疲劳数据
 */
data class DailyFatigueData(
    val date: String, // yyyy-MM-dd
    val fatigueScore: Int, // 0-100
    val fatigueType: FatigueType,
    val totalScreenTimeMinutes: Long,
    val appUsageList: List<AppUsageInfo>,
    val categoryBreakdown: Map<AppCategory, Long>, // 分类 -> 使用时间(分钟)
    val hourlyDistribution: Map<Int, Long>, // 小时(0-23) -> 使用时间(分钟)
    val totalLaunchCount: Int,
    val peakHour: Int, // 使用高峰小时
    val lateNightMinutes: Long // 22:00-06:00使用时间
)

/**
 * 疲劳分析结果
 */
data class FatigueAnalysisResult(
    val fatigueScore: Int,
    val fatigueType: FatigueType,
    val summary: String,
    val suggestions: List<String>,
    val detailedAnalysis: String = "",
    val isFromAI: Boolean = false,
    val cachedAt: Long = System.currentTimeMillis()
)

/**
 * AI分析请求
 */
@Serializable
data class AnalysisRequest(
    val totalScreenTime: Long,
    val categoryBreakdown: Map<String, Long>,
    val hourlyDistribution: Map<String, Long>,
    val totalLaunchCount: Int,
    val topApps: List<TopAppInfo>,
    val lateNightMinutes: Long
)

@Serializable
data class TopAppInfo(
    val name: String,
    val category: String,
    val minutes: Long
)

/**
 * 成就
 */
data class Achievement(
    val id: String,
    val name: String,
    val description: String,
    val emoji: String,
    val isUnlocked: Boolean = false,
    val unlockedDate: String? = null,
    val progress: Float = 0f // 0-1
)

/**
 * 用户设置
 */
data class UserSettings(
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 22,
    val reminderMinute: Int = 0,
    val dailyScreenTimeGoalMinutes: Int = 240
)

enum class ChatSender {
    USER,
    ASSISTANT
}

data class ChatUiMessage(
    val id: Long,
    val sender: ChatSender,
    val content: String,
    val createdAt: Long,
    val conversationId: String,
    val usedUsageContext: Boolean = false,
    val isError: Boolean = false
)

data class ChatConversationSummary(
    val conversationId: String,
    val title: String,
    val sessionDate: String,
    val startedAt: Long,
    val updatedAt: Long
)

data class UserDigitalProfile(
    val dangerTimeRange: String = "23:00 - 01:30",
    val triggerAppHabits: String = "短视频、社交平台信息流",
    val reliefPreferences: String = "偏好白噪音/轻音乐，讨厌大道理说教，温和同理心",
    val contextBio: String = "白天高强度脑力消耗，晚上下班后容易通过手机报复性放松"
)

data class ChatRequestContext(
    val summary: String = "",
    val note: String = "",
    val usesUsageContext: Boolean = false,
    val userProfile: UserDigitalProfile? = null,
    val weeklyUsageList: List<DailyFatigueData> = emptyList()
)

