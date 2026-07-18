package com.tired.app.domain.analyzer

import com.tired.app.domain.model.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalRuleEngine @Inject constructor() : FatigueAnalyzer {

    override suspend fun analyze(data: DailyFatigueData): FatigueAnalysisResult {
        val fatigueScore = calculateFatigueScore(data)
        val fatigueType = determineFatigueType(data)
        val summary = generateSummary(data, fatigueType, fatigueScore)
        val suggestions = generateSuggestions(fatigueType, data)

        return FatigueAnalysisResult(
            fatigueScore = fatigueScore,
            fatigueType = fatigueType,
            summary = summary,
            suggestions = suggestions,
            detailedAnalysis = generateDetailedAnalysis(data, fatigueType),
            isFromAI = false
        )
    }

    private fun calculateFatigueScore(data: DailyFatigueData): Int {
        var score = 0

        // 总屏幕时间评分 (0-30分)
        score += when {
            data.totalScreenTimeMinutes > 480 -> 30
            data.totalScreenTimeMinutes > 360 -> 25
            data.totalScreenTimeMinutes > 240 -> 18
            data.totalScreenTimeMinutes > 120 -> 10
            else -> 5
        }

        // 深夜使用评分 (0-25分)
        score += when {
            data.lateNightMinutes > 120 -> 25
            data.lateNightMinutes > 60 -> 20
            data.lateNightMinutes > 30 -> 12
            data.lateNightMinutes > 10 -> 6
            else -> 0
        }

        // 社交+短视频占比评分 (0-25分)
        val socialVideoTime = (data.categoryBreakdown[AppCategory.SOCIAL] ?: 0) +
                (data.categoryBreakdown[AppCategory.VIDEO] ?: 0)
        val socialVideoRatio = if (data.totalScreenTimeMinutes > 0)
            socialVideoTime.toFloat() / data.totalScreenTimeMinutes else 0f
        score += when {
            socialVideoRatio > 0.7 -> 25
            socialVideoRatio > 0.5 -> 18
            socialVideoRatio > 0.3 -> 10
            else -> 3
        }

        // 启动频次评分 (0-20分) - 碎片化程度
        score += when {
            data.totalLaunchCount > 200 -> 20
            data.totalLaunchCount > 120 -> 15
            data.totalLaunchCount > 60 -> 10
            data.totalLaunchCount > 30 -> 5
            else -> 2
        }

        return score.coerceIn(0, 100)
    }

    private fun determineFatigueType(data: DailyFatigueData): FatigueType {
        val socialVideoTime = (data.categoryBreakdown[AppCategory.SOCIAL] ?: 0) +
                (data.categoryBreakdown[AppCategory.VIDEO] ?: 0)
        val newsTime = data.categoryBreakdown[AppCategory.NEWS] ?: 0
        val totalTime = data.totalScreenTimeMinutes.coerceAtLeast(1)

        // 判断规则优先级
        // 1. 深夜使用严重 → 睡眠拖延
        if (data.lateNightMinutes > 60 && data.peakHour >= 22) {
            return FatigueType.SLEEP_PROCRASTINATION
        }

        // 2. 社交+视频占比 > 60% → 多巴胺过载
        if (socialVideoTime.toFloat() / totalTime > 0.6) {
            return FatigueType.DOPAMINE_OVERLOAD
        }

        // 3. 启动次数极高 → 碎片化焦虑
        if (data.totalLaunchCount > 100 && data.appUsageList.size > 10) {
            return FatigueType.FRAGMENTED_ANXIETY
        }

        // 4. 新闻资讯占比高 → 信息过载
        if (newsTime.toFloat() / totalTime > 0.3) {
            return FatigueType.INFO_OVERLOAD
        }

        // 5. 总使用时间低 → 健康
        if (data.totalScreenTimeMinutes < 120) {
            return FatigueType.HEALTHY
        }

        // 默认根据最高占比分类判断
        val maxCategory = data.categoryBreakdown.maxByOrNull { it.value }?.key
        return when (maxCategory) {
            AppCategory.SOCIAL, AppCategory.VIDEO -> FatigueType.DOPAMINE_OVERLOAD
            AppCategory.NEWS -> FatigueType.INFO_OVERLOAD
            AppCategory.GAME -> FatigueType.DOPAMINE_OVERLOAD
            else -> FatigueType.FRAGMENTED_ANXIETY
        }
    }

    private fun generateSummary(
        data: DailyFatigueData,
        type: FatigueType,
        score: Int
    ): String {
        val hours = data.totalScreenTimeMinutes / 60
        val mins = data.totalScreenTimeMinutes % 60
        val timeStr = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"

        val scoreLevel = when {
            score >= 80 -> "severe fatigue"
            score >= 60 -> "moderate fatigue"
            score >= 40 -> "mild fatigue"
            score >= 20 -> "slightly elevated"
            else -> "well balanced"
        }

        return buildString {
            append("You spent $timeStr on your phone today, with a fatigue score of $score ($scoreLevel). ")
            when (type) {
                FatigueType.DOPAMINE_OVERLOAD -> append("Social feeds and short videos took up most of your attention, so dialing back passive scrolling would help.")
                FatigueType.FRAGMENTED_ANXIETY -> append("You bounced across apps ${data.totalLaunchCount} times today, which likely made focus feel more scattered.")
                FatigueType.SLEEP_PROCRASTINATION -> append("You spent ${data.lateNightMinutes} minutes on your phone late at night, so an earlier wind-down would help.")
                FatigueType.INFO_OVERLOAD -> append("You consumed a lot of news and information today, which may be leaving your mind overloaded.")
                FatigueType.HEALTHY -> append("Your phone habits looked balanced today, so this is a good baseline to keep.")
            }
        }
    }

    private fun generateSuggestions(type: FatigueType, data: DailyFatigueData): List<String> {
        val common = mutableListOf<String>()

        when (type) {
            FatigueType.DOPAMINE_OVERLOAD -> {
                common.add("🎯 Set a daily cap for social apps, ideally under 1 hour.")
                common.add("🚶 After every 30 minutes of screen time, stand up and move for 5 minutes.")
                common.add("📖 Replace one scrolling block with reading or another slower activity.")
                common.add("🔕 Turn off non-essential notifications that pull you back in.")
            }
            FatigueType.FRAGMENTED_ANXIETY -> {
                common.add("📱 Group similar apps together to reduce switching friction.")
                common.add("⏰ Batch your messages into set check-in windows.")
                common.add("🧘 Try a 10-minute mindfulness reset to recover focus.")
                common.add("📝 Use a short task list instead of repeatedly checking your phone.")
            }
            FatigueType.SLEEP_PROCRASTINATION -> {
                common.add("🌙 Set a wind-down reminder for ${data.peakHour - 1}:00.")
                common.add("📵 Turn on Do Not Disturb one hour before bed.")
                common.add("🛁 Replace bedtime scrolling with a warm shower or another calming routine.")
                common.add("📱 Charge your phone outside the bedroom if possible.")
            }
            FatigueType.INFO_OVERLOAD -> {
                common.add("📰 Limit news browsing to fixed windows, such as 15 minutes in the morning and evening.")
                common.add("🔇 Disable push notifications from news apps.")
                common.add("🌿 For every 30 minutes of reading, rest your eyes for 2 minutes.")
                common.add("✍️ Save longer reads instead of reopening the same content again and again.")
            }
            FatigueType.HEALTHY -> {
                common.add("👏 Your usage pattern looked healthy today. Keep it going.")
                common.add("💡 Try tracking the times of day when you feel most focused.")
                common.add("🎯 Set one small digital wellness goal for tomorrow.")
            }
        }

        if (data.totalScreenTimeMinutes > 300 && type != FatigueType.HEALTHY) {
            common.add("⚡ You were on your phone for more than 5 hours today. Aim to cut ${(data.totalScreenTimeMinutes * 0.2).toInt()} minutes tomorrow.")
        }

        return common
    }

    private fun generateDetailedAnalysis(data: DailyFatigueData, type: FatigueType): String {
        return buildString {
            appendLine("## Detailed Review")
            appendLine()
            appendLine("### Usage Overview")
            appendLine("- Total screen time: ${data.totalScreenTimeMinutes / 60}h ${data.totalScreenTimeMinutes % 60}m")
            appendLine("- App launches: ${data.totalLaunchCount}")
            appendLine("- Peak hour: ${data.peakHour}:00")
            appendLine("- Late-night usage: ${data.lateNightMinutes} minutes")
            appendLine()
            appendLine("### Fatigue Pattern: ${type.displayName}")
            appendLine(type.description)
            appendLine()
            appendLine("### Category Breakdown")
            data.categoryBreakdown.entries
                .sortedByDescending { it.value }
                .forEach { (category, minutes) ->
                    val percent = if (data.totalScreenTimeMinutes > 0)
                        (minutes * 100 / data.totalScreenTimeMinutes) else 0
                    appendLine("- ${category.emoji} ${category.displayName}: ${minutes} minutes ($percent%)")
                }
        }
    }
}
