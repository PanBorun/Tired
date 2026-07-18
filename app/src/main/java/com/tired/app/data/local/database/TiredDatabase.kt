package com.tired.app.data.local.database

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tired.app.data.local.dao.ChatMessageDao
import com.tired.app.data.local.dao.FatigueAnalysisDao
import com.tired.app.data.local.entity.ChatMessageEntity
import com.tired.app.data.local.entity.FatigueAnalysisEntity

@Database(
    entities = [FatigueAnalysisEntity::class, ChatMessageEntity::class],
    version = 3,
    exportSchema = false
)
abstract class TiredDatabase : RoomDatabase() {
    abstract fun fatigueAnalysisDao(): FatigueAnalysisDao
    abstract fun chatMessageDao(): ChatMessageDao

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
    }
}
