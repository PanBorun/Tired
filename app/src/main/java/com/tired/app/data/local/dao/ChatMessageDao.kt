package com.tired.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tired.app.data.local.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

data class ChatConversationRow(
    val conversationId: String,
    val startedAt: Long,
    val updatedAt: Long,
    val sessionDate: String,
    val preview: String?
)

@Dao
interface ChatMessageDao {
    @Query(
        """
        SELECT * FROM chat_messages
        WHERE conversationId = :conversationId
        ORDER BY createdAt ASC, id ASC
        """
    )
    fun observeMessages(conversationId: String): Flow<List<ChatMessageEntity>>

    @Query(
        """
        SELECT * FROM chat_messages
        WHERE conversationId = :conversationId
        ORDER BY createdAt DESC, id DESC
        LIMIT :limit
        """
    )
    suspend fun getRecentMessages(conversationId: String, limit: Int): List<ChatMessageEntity>

    @Query(
        """
        SELECT
            base.conversationId AS conversationId,
            (
                SELECT createdAt
                FROM chat_messages first_msg
                WHERE first_msg.conversationId = base.conversationId
                ORDER BY createdAt ASC, id ASC
                LIMIT 1
            ) AS startedAt,
            (
                SELECT createdAt
                FROM chat_messages last_msg
                WHERE last_msg.conversationId = base.conversationId
                ORDER BY createdAt DESC, id DESC
                LIMIT 1
            ) AS updatedAt,
            (
                SELECT sessionDate
                FROM chat_messages date_msg
                WHERE date_msg.conversationId = base.conversationId
                ORDER BY createdAt ASC, id ASC
                LIMIT 1
            ) AS sessionDate,
            (
                SELECT content
                FROM chat_messages preview_msg
                WHERE preview_msg.conversationId = base.conversationId
                AND preview_msg.role = 'user'
                ORDER BY createdAt ASC, id ASC
                LIMIT 1
            ) AS preview
        FROM chat_messages base
        GROUP BY base.conversationId
        ORDER BY updatedAt DESC
        """
    )
    fun observeConversations(): Flow<List<ChatConversationRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages")
    suspend fun clearAll()
}
