package com.tired.app.data.local.datasource

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import com.tired.app.domain.model.AppCategory
import com.tired.app.domain.model.AppUsageInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsageStatsDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val usageStatsManager: UsageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    fun hasPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun getUsageForDay(startOfDay: Long, endOfDay: Long): List<AppUsageInfo> {
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startOfDay,
            endOfDay
        )

        return stats
            .filter { it.totalTimeInForeground > 60_000 } // 至少使用1分钟
            .map { stat ->
                val appName = getAppName(stat.packageName)
                val category = categorizeApp(stat.packageName)
                AppUsageInfo(
                    packageName = stat.packageName,
                    appName = appName,
                    category = category,
                    totalTimeMinutes = stat.totalTimeInForeground / 60_000,
                    launchCount = estimateLaunchCount(stat.totalTimeInForeground),
                    lastUsedTimestamp = stat.lastTimeUsed
                )
            }
            .sortedByDescending { it.totalTimeMinutes }
    }

    fun getHourlyDistribution(startOfDay: Long, endOfDay: Long): Map<Int, Long> {
        val events = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            startOfDay,
            endOfDay
        )

        // 简化：按总时长平均分配到活跃时段
        val distribution = mutableMapOf<Int, Long>()
        for (hour in 0..23) {
            distribution[hour] = 0L
        }

        val totalMinutes = events.sumOf { it.totalTimeInForeground / 60_000 }
        if (totalMinutes > 0) {
            // 基于使用模式估算小时分布
            val calendar = Calendar.getInstance()
            events.forEach { stat ->
                if (stat.lastTimeUsed > 0) {
                    calendar.timeInMillis = stat.lastTimeUsed
                    val hour = calendar.get(Calendar.HOUR_OF_DAY)
                    distribution[hour] = (distribution[hour] ?: 0) + stat.totalTimeInForeground / 60_000
                }
            }
        }

        return distribution
    }

    private fun getAppName(packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName.substringAfterLast(".")
        }
    }

    private fun categorizeApp(packageName: String): AppCategory {
        val pkg = packageName.lowercase()
        return when {
            // 社交媒体
            pkg.contains("wechat") || pkg.contains("weixin") ||
            pkg.contains("qq.") || pkg.contains("weibo") ||
            pkg.contains("twitter") || pkg.contains("instagram") ||
            pkg.contains("facebook") || pkg.contains("telegram") ||
            pkg.contains("whatsapp") || pkg.contains("line") ||
            pkg.contains("xiaohongshu") || pkg.contains("soul") -> AppCategory.SOCIAL

            // 视频
            pkg.contains("douyin") || pkg.contains("tiktok") ||
            pkg.contains("bilibili") || pkg.contains("youku") ||
            pkg.contains("iqiyi") || pkg.contains("youtube") ||
            pkg.contains("netflix") || pkg.contains("kuaishou") ||
            pkg.contains("video") || pkg.contains("tv.danmaku") -> AppCategory.VIDEO

            // 游戏
            pkg.contains("game") || pkg.contains("com.mihoyo") ||
            pkg.contains("com.tencent.tmgp") || pkg.contains("com.netease.game") ||
            pkg.contains("pubg") || pkg.contains("com.supercell") -> AppCategory.GAME

            // 新闻资讯
            pkg.contains("news") || pkg.contains("toutiao") ||
            pkg.contains("zhihu") || pkg.contains("163.news") ||
            pkg.contains("sina.news") || pkg.contains("bbc") -> AppCategory.NEWS

            // 购物
            pkg.contains("taobao") || pkg.contains("jd.") ||
            pkg.contains("pinduoduo") || pkg.contains("amazon") ||
            pkg.contains("shopee") || pkg.contains("tmall") -> AppCategory.SHOPPING

            // 办公学习
            pkg.contains("dingtalk") || pkg.contains("lark") ||
            pkg.contains("wps") || pkg.contains("office") ||
            pkg.contains("mail") || pkg.contains("calendar") ||
            pkg.contains("notion") || pkg.contains("evernote") ||
            pkg.contains("edu") || pkg.contains("study") -> AppCategory.WORK

            // 音乐
            pkg.contains("music") || pkg.contains("netease.cloudmusic") ||
            pkg.contains("kugou") || pkg.contains("kuwo") ||
            pkg.contains("spotify") || pkg.contains("qqmusic") -> AppCategory.MUSIC

            // 健康
            pkg.contains("health") || pkg.contains("fitness") ||
            pkg.contains("keep") || pkg.contains("sport") -> AppCategory.HEALTH

            // 系统工具
            pkg.contains("launcher") || pkg.contains("settings") ||
            pkg.contains("camera") || pkg.contains("gallery") ||
            pkg.contains("phone") || pkg.contains("dialer") ||
            pkg.contains("calculator") || pkg.contains("clock") -> AppCategory.TOOL

            else -> AppCategory.OTHER
        }
    }

    private fun estimateLaunchCount(totalTimeMs: Long): Int {
        // 估算：平均每次使用3分钟
        return (totalTimeMs / 180_000).toInt().coerceAtLeast(1)
    }
}
