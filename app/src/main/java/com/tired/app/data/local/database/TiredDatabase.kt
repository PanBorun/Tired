package com.tired.app.data.local.database

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tired.app.data.local.dao.ChatMessageDao
import com.tired.app.data.local.dao.FatigueAnalysisDao
import com.tired.app.data.local.dao.UserProfileDao
import com.tired.app.data.local.entity.ChatMessageEntity
import com.tired.app.data.local.entity.FatigueAnalysisEntity
import com.tired.app.data.local.entity.UserProfileEntity

@Database(
    entities = [FatigueAnalysisEntity::class, ChatMessageEntity::class, UserProfileEntity::class],
    version = 4,
    exportSchema = false
)
abstract class TiredDatabase : RoomDatabase() {
    abstract fun fatigueAnalysisDao(): FatigueAnalysisDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun userProfileDao(): UserProfileDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `chat_messages` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `role` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `sessionDate` TEXT NOT NULL,
                        `usedUsageContext` INTEGER NOT NULL,
                        `isError` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    ALTER TABLE `chat_messages`
                    ADD COLUMN `conversationId` TEXT NOT NULL DEFAULT ''
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    UPDATE `chat_messages`
                    SET `conversationId` = 'legacy-' || `sessionDate`
                    WHERE `conversationId` = ''
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `user_profile` (
                        `id` INTEGER PRIMARY KEY NOT NULL,
                        `dangerTimeRange` TEXT NOT NULL,
                        `triggerAppHabits` TEXT NOT NULL,
                        `reliefPreferences` TEXT NOT NULL,
                        `contextBio` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `user_profile` (id, dangerTimeRange, triggerAppHabits, reliefPreferences, contextBio, updatedAt)
                    VALUES (1, '23:00 - 01:30', '短视频、社交平台信息流', '偏好白噪音/轻音乐，讨厌大道理说教，温和同理心', '白天高强度脑力消耗，晚上下班后容易通过手机报复性放松', 1700000000000)
                    """.trimIndent()
                )
            }
        }
    }
}
