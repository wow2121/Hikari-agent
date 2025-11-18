package com.xiaoguang.assistant.domain.repository

import com.xiaoguang.assistant.data.local.database.entity.ConversationEmbeddingEntity
import com.xiaoguang.assistant.data.local.database.entity.MemoryFactEntity
import kotlinx.coroutines.flow.Flow

/**
 * Embedding存储仓库接口
 */
interface EmbeddingRepository {

    // ========== 对话Embedding ==========

    /**
     * 保存对话embedding
     */
    suspend fun saveConversationEmbedding(embedding: ConversationEmbeddingEntity): Long

    /**
     * 批量保存对话embeddings
     */
    suspend fun saveConversationEmbeddings(embeddings: List<ConversationEmbeddingEntity>): List<Long>

    /**
     * 根据ID获取对话embedding
     */
    suspend fun getConversationEmbeddingById(id: Long): ConversationEmbeddingEntity?

    /**
     * 根据消息ID获取对话embedding
     */
    suspend fun getConversationEmbeddingByMessageId(messageId: String): ConversationEmbeddingEntity?

    /**
     * 获取指定对话的所有embeddings
     */
    suspend fun getConversationEmbeddings(conversationId: String): List<ConversationEmbeddingEntity>

    /**
     * 获取最近的N条对话embeddings
     */
    suspend fun getRecentConversationEmbeddings(limit: Int = 100): List<ConversationEmbeddingEntity>

    /**
     * 获取时间范围内的对话embeddings
     */
    suspend fun getConversationEmbeddingsByTimeRange(
        startTime: Long,
        endTime: Long
    ): List<ConversationEmbeddingEntity>

    /**
     * 获取所有活跃的对话embeddings
     */
    suspend fun getAllConversationEmbeddings(): List<ConversationEmbeddingEntity>

    /**
     * 观察所有对话embeddings的变化
     */
    fun observeConversationEmbeddings(): Flow<List<ConversationEmbeddingEntity>>

    /**
     * 更新访问统计
     */
    suspend fun updateConversationAccessStats(id: Long, accessTime: Long = System.currentTimeMillis())

    /**
     * 批量更新访问统计
     */
    suspend fun updateConversationAccessStatsBatch(ids: List<Long>, accessTime: Long = System.currentTimeMillis())

    /**
     * 归档旧的对话embeddings
     */
    suspend fun archiveConversationEmbeddingsOlderThan(beforeTime: Long): Int

    /**
     * 删除已归档的对话embeddings
     */
    suspend fun deleteArchivedConversationEmbeddings(): Int

    /**
     * 删除指定时间之前的对话embeddings
     */
    suspend fun deleteConversationEmbeddingsOlderThan(beforeTime: Long): Int

    /**
     * 搜索相似的对话embeddings
     *
     * @param queryVector 查询向量
     * @param topK 返回的数量
     * @param dimension 向量维度
     * @return 相似度分数和embedding的配对列表,按相似度降序排列
     */
    suspend fun searchSimilarConversations(
        queryVector: List<Float>,
        topK: Int = 10,
        dimension: Int
    ): List<Pair<Float, ConversationEmbeddingEntity>>

    // ========== 记忆事实 ==========

    /**
     * 保存记忆事实
     */
    suspend fun saveMemoryFact(fact: MemoryFactEntity): Long

    /**
     * 批量保存记忆事实
     */
    suspend fun saveMemoryFacts(facts: List<MemoryFactEntity>): List<Long>

    /**
     * 更新记忆事实
     */
    suspend fun updateMemoryFact(fact: MemoryFactEntity)

    /**
     * 删除记忆事实
     */
    suspend fun deleteMemoryFact(fact: MemoryFactEntity)

    /**
     * 根据ID删除记忆事实
     */
    suspend fun deleteMemoryFact(id: Long)

    /**
     * 根据ID获取记忆事实
     */
    suspend fun getMemoryFactById(id: Long): MemoryFactEntity?

    /**
     * 根据类别获取记忆事实
     */
    suspend fun getMemoryFactsByCategory(category: String): List<MemoryFactEntity>

    /**
     * 获取所有活跃的记忆事实
     */
    suspend fun getAllActiveMemoryFacts(): List<MemoryFactEntity>

    /**
     * 获取所有记忆事实（包括活跃和遗忘的）
     */
    suspend fun getAllMemoryFacts(): List<MemoryFactEntity>

    /**
     * 观察所有活跃记忆事实的变化
     */
    fun observeActiveMemoryFacts(): Flow<List<MemoryFactEntity>>

    /**
     * 获取已遗忘的记忆事实
     */
    suspend fun getAllForgottenMemoryFacts(): List<MemoryFactEntity>

    /**
     * 搜索包含特定实体的记忆
     */
    suspend fun searchMemoryFactsByEntity(entity: String): List<MemoryFactEntity>

    /**
     * 强化记忆
     */
    suspend fun reinforceMemoryFact(id: Long, reinforcedAt: Long = System.currentTimeMillis())

    /**
     * 批量强化记忆
     */
    suspend fun reinforceMemoryFactsBatch(ids: List<Long>, reinforcedAt: Long = System.currentTimeMillis())

    /**
     * 标记为遗忘
     */
    suspend fun markMemoryFactAsForgotten(id: Long, forgottenAt: Long = System.currentTimeMillis())

    /**
     * 批量标记为遗忘
     */
    suspend fun markMemoryFactsAsForgottenBatch(ids: List<Long>, forgottenAt: Long = System.currentTimeMillis())

    /**
     * 恢复已遗忘的记忆
     */
    suspend fun restoreMemoryFact(id: Long, reinforcedAt: Long = System.currentTimeMillis())

    /**
     * 删除已遗忘的记忆
     */
    suspend fun deleteForgottenMemoryFactsBefore(beforeTime: Long): Int

    /**
     * 删除指定时间之前创建的记忆
     */
    suspend fun deleteMemoryFactsCreatedBefore(beforeTime: Long): Int

    /**
     * 获取需要清理的记忆候选
     */
    suspend fun getMemoryFactCandidatesForCleanup(
        importanceThreshold: Float,
        lastReinforcedBefore: Long
    ): List<MemoryFactEntity>

    /**
     * 搜索相似的记忆事实
     *
     * @param queryVector 查询向量
     * @param topK 返回的数量
     * @param dimension 向量维度
     * @return 相似度分数和记忆事实的配对列表,按相似度降序排列
     */
    suspend fun searchSimilarMemoryFacts(
        queryVector: List<Float>,
        topK: Int = 10,
        dimension: Int
    ): List<Pair<Float, MemoryFactEntity>>

    /**
     * 删除所有可信度低于阈值的记忆
     * @param threshold 可信度阈值
     * @return 删除的记忆数量
     */
    suspend fun deleteLowConfidenceMemories(threshold: Float): Int

    // ========== 统计信息 ==========

    /**
     * 获取对话embedding统计
     */
    suspend fun getConversationEmbeddingStats(): EmbeddingStats

    /**
     * 获取记忆事实统计
     */
    suspend fun getMemoryFactStats(): MemoryStats
}

/**
 * Embedding统计信息
 */
data class EmbeddingStats(
    val activeCount: Int,
    val archivedCount: Int,
    val averageImportance: Float
)

/**
 * 记忆统计信息
 */
data class MemoryStats(
    val activeCount: Int,
    val forgottenCount: Int,
    val averageImportance: Float,
    val averageReinforcementCount: Float,
    val categoryStats: Map<String, Int> = emptyMap()
)
