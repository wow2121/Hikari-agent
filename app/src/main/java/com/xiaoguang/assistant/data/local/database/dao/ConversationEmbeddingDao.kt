package com.xiaoguang.assistant.data.local.database.dao

import androidx.room.*
import com.xiaoguang.assistant.data.local.database.entity.ConversationEmbeddingEntity
import kotlinx.coroutines.flow.Flow

/**
 * 对话Embedding DAO
 */
@Dao
interface ConversationEmbeddingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(embedding: ConversationEmbeddingEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(embeddings: List<ConversationEmbeddingEntity>): List<Long>

    @Update
    suspend fun update(embedding: ConversationEmbeddingEntity)

    @Delete
    suspend fun delete(embedding: ConversationEmbeddingEntity)

    @Query("SELECT * FROM conversation_embeddings WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ConversationEmbeddingEntity?

    @Query("SELECT * FROM conversation_embeddings WHERE messageId = :messageId LIMIT 1")
    suspend fun getByMessageId(messageId: String): ConversationEmbeddingEntity?

    @Query("SELECT * FROM conversation_embeddings WHERE conversationId = :conversationId ORDER BY timestamp DESC")
    suspend fun getByConversationId(conversationId: String): List<ConversationEmbeddingEntity>

    @Query("SELECT * FROM conversation_embeddings WHERE isArchived = 0 ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<ConversationEmbeddingEntity>

    @Query("SELECT * FROM conversation_embeddings WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    suspend fun getByTimeRange(startTime: Long, endTime: Long): List<ConversationEmbeddingEntity>

    @Query("SELECT * FROM conversation_embeddings WHERE isArchived = 0")
    suspend fun getAll(): List<ConversationEmbeddingEntity>

    @Query("SELECT * FROM conversation_embeddings WHERE isArchived = 0")
    fun observeAll(): Flow<List<ConversationEmbeddingEntity>>

    @Query("UPDATE conversation_embeddings SET accessCount = accessCount + 1, lastAccessedAt = :accessTime WHERE id = :id")
    suspend fun updateAccessStats(id: Long, accessTime: Long = System.currentTimeMillis())

    @Query("UPDATE conversation_embeddings SET accessCount = accessCount + 1, lastAccessedAt = :accessTime WHERE id IN (:ids)")
    suspend fun updateAccessStatsBatch(ids: List<Long>, accessTime: Long = System.currentTimeMillis())

    @Query("UPDATE conversation_embeddings SET isArchived = 1 WHERE timestamp < :beforeTime")
    suspend fun archiveOlderThan(beforeTime: Long): Int

    @Query("DELETE FROM conversation_embeddings WHERE isArchived = 1")
    suspend fun deleteArchived(): Int

    @Query("DELETE FROM conversation_embeddings WHERE timestamp < :beforeTime")
    suspend fun deleteOlderThan(beforeTime: Long): Int

    // 统计查询
    @Query("SELECT COUNT(*) FROM conversation_embeddings WHERE isArchived = 0")
    suspend fun getActiveCount(): Int

    @Query("SELECT COUNT(*) FROM conversation_embeddings WHERE isArchived = 1")
    suspend fun getArchivedCount(): Int

    @Query("SELECT AVG(importance) FROM conversation_embeddings WHERE isArchived = 0")
    suspend fun getAverageImportance(): Float
}
