package com.xiaoguang.assistant.data.repository

import com.xiaoguang.assistant.data.local.database.dao.ConversationEmbeddingDao
import com.xiaoguang.assistant.data.local.database.dao.MemoryFactDao
import com.xiaoguang.assistant.data.local.database.entity.ConversationEmbeddingEntity
import com.xiaoguang.assistant.data.local.database.entity.MemoryFactEntity
import com.xiaoguang.assistant.domain.repository.EmbeddingRepository
import com.xiaoguang.assistant.domain.repository.EmbeddingStats
import com.xiaoguang.assistant.domain.repository.MemoryStats
import com.xiaoguang.assistant.domain.util.VectorUtils
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Embedding存储仓库实现
 *
 * v2.4架构：基于Room数据库存储基础数据
 * 配合ChromaDB（向量搜索）和Neo4j（关系图谱）使用
 */
class EmbeddingRepositoryImpl @Inject constructor(
    private val conversationEmbeddingDao: ConversationEmbeddingDao,
    private val memoryFactDao: MemoryFactDao
) : EmbeddingRepository {

    // ========== 对话Embedding实现 ==========

    override suspend fun saveConversationEmbedding(embedding: ConversationEmbeddingEntity): Long {
        return conversationEmbeddingDao.insert(embedding)
    }

    override suspend fun saveConversationEmbeddings(embeddings: List<ConversationEmbeddingEntity>): List<Long> {
        return conversationEmbeddingDao.insertAll(embeddings)
    }

    override suspend fun getConversationEmbeddingById(id: Long): ConversationEmbeddingEntity? {
        return conversationEmbeddingDao.getById(id)
    }

    override suspend fun getConversationEmbeddingByMessageId(messageId: String): ConversationEmbeddingEntity? {
        return conversationEmbeddingDao.getByMessageId(messageId)
    }

    override suspend fun getConversationEmbeddings(conversationId: String): List<ConversationEmbeddingEntity> {
        return conversationEmbeddingDao.getByConversationId(conversationId)
    }

    override suspend fun getRecentConversationEmbeddings(limit: Int): List<ConversationEmbeddingEntity> {
        return conversationEmbeddingDao.getRecent(limit)
    }

    override suspend fun getConversationEmbeddingsByTimeRange(
        startTime: Long,
        endTime: Long
    ): List<ConversationEmbeddingEntity> {
        return conversationEmbeddingDao.getByTimeRange(startTime, endTime)
    }

    override suspend fun getAllConversationEmbeddings(): List<ConversationEmbeddingEntity> {
        return conversationEmbeddingDao.getAll()
    }

    override fun observeConversationEmbeddings(): Flow<List<ConversationEmbeddingEntity>> {
        return conversationEmbeddingDao.observeAll()
    }

    override suspend fun updateConversationAccessStats(id: Long, accessTime: Long) {
        conversationEmbeddingDao.updateAccessStats(id, accessTime)
    }

    override suspend fun updateConversationAccessStatsBatch(ids: List<Long>, accessTime: Long) {
        conversationEmbeddingDao.updateAccessStatsBatch(ids, accessTime)
    }

    override suspend fun archiveConversationEmbeddingsOlderThan(beforeTime: Long): Int {
        return conversationEmbeddingDao.archiveOlderThan(beforeTime)
    }

    override suspend fun deleteArchivedConversationEmbeddings(): Int {
        return conversationEmbeddingDao.deleteArchived()
    }

    override suspend fun deleteConversationEmbeddingsOlderThan(beforeTime: Long): Int {
        return conversationEmbeddingDao.deleteOlderThan(beforeTime)
    }

    override suspend fun searchSimilarConversations(
        queryVector: List<Float>,
        topK: Int,
        dimension: Int
    ): List<Pair<Float, ConversationEmbeddingEntity>> {
        Timber.w("EmbeddingRepositoryImpl: searchSimilarConversations - 使用ChromaDB进行向量搜索")
        // TODO: 集成ChromaDB进行实际向量搜索
        return emptyList()
    }

    // ========== 记忆事实实现 ==========

    override suspend fun saveMemoryFact(fact: MemoryFactEntity): Long {
        return memoryFactDao.insert(fact)
    }

    override suspend fun saveMemoryFacts(facts: List<MemoryFactEntity>): List<Long> {
        return memoryFactDao.insertAll(facts)
    }

    override suspend fun updateMemoryFact(fact: MemoryFactEntity) {
        memoryFactDao.update(fact)
    }

    override suspend fun deleteMemoryFact(fact: MemoryFactEntity) {
        memoryFactDao.delete(fact)
    }

    override suspend fun deleteMemoryFact(id: Long) {
        memoryFactDao.deleteById(id)
    }

    override suspend fun getMemoryFactById(id: Long): MemoryFactEntity? {
        return memoryFactDao.getById(id)
    }

    override suspend fun getMemoryFactsByCategory(category: String): List<MemoryFactEntity> {
        return memoryFactDao.getByCategory(category)
    }

    override suspend fun getAllActiveMemoryFacts(): List<MemoryFactEntity> {
        return memoryFactDao.getAllActive()
    }

    override suspend fun getAllMemoryFacts(): List<MemoryFactEntity> {
        return memoryFactDao.getAll()
    }

    override fun observeActiveMemoryFacts(): Flow<List<MemoryFactEntity>> {
        return memoryFactDao.observeActive()
    }

    override suspend fun getAllForgottenMemoryFacts(): List<MemoryFactEntity> {
        return memoryFactDao.getForgotten()
    }

    override suspend fun searchMemoryFactsByEntity(entity: String): List<MemoryFactEntity> {
        return memoryFactDao.searchByEntity(entity)
    }

    override suspend fun reinforceMemoryFact(id: Long, reinforcedAt: Long) {
        memoryFactDao.reinforce(id, reinforcedAt)
    }

    override suspend fun reinforceMemoryFactsBatch(ids: List<Long>, reinforcedAt: Long) {
        memoryFactDao.reinforceBatch(ids, reinforcedAt)
    }

    override suspend fun markMemoryFactAsForgotten(id: Long, forgottenAt: Long) {
        memoryFactDao.markAsForgotten(id, forgottenAt)
    }

    override suspend fun markMemoryFactsAsForgottenBatch(ids: List<Long>, forgottenAt: Long) {
        memoryFactDao.markAsForgottenBatch(ids, forgottenAt)
    }

    override suspend fun restoreMemoryFact(id: Long, reinforcedAt: Long) {
        memoryFactDao.restore(id, reinforcedAt)
    }

    override suspend fun deleteForgottenMemoryFactsBefore(beforeTime: Long): Int {
        return memoryFactDao.deleteForgottenBefore(beforeTime)
    }

    override suspend fun deleteMemoryFactsCreatedBefore(beforeTime: Long): Int {
        return memoryFactDao.deleteCreatedBefore(beforeTime)
    }

    override suspend fun getMemoryFactCandidatesForCleanup(
        importanceThreshold: Float,
        lastReinforcedBefore: Long
    ): List<MemoryFactEntity> {
        return memoryFactDao.getCleanupCandidates(importanceThreshold, lastReinforcedBefore)
    }

    override suspend fun searchSimilarMemoryFacts(
        queryVector: List<Float>,
        topK: Int,
        dimension: Int
    ): List<Pair<Float, MemoryFactEntity>> {
        Timber.w("EmbeddingRepositoryImpl: searchSimilarMemoryFacts - 使用ChromaDB进行向量搜索")
        // TODO: 集成ChromaDB进行实际向量搜索
        return emptyList()
    }

    override suspend fun deleteLowConfidenceMemories(threshold: Float): Int {
        return memoryFactDao.deleteLowConfidence(threshold)
    }

    // ========== 统计信息实现 ==========

    override suspend fun getConversationEmbeddingStats(): EmbeddingStats {
        val activeCount = conversationEmbeddingDao.getActiveCount()
        val archivedCount = conversationEmbeddingDao.getArchivedCount()
        val averageImportance = conversationEmbeddingDao.getAverageImportance() ?: 0f

        return EmbeddingStats(
            activeCount = activeCount,
            archivedCount = archivedCount,
            averageImportance = averageImportance
        )
    }

    override suspend fun getMemoryFactStats(): MemoryStats {
        val activeCount = memoryFactDao.getActiveCount()
        val forgottenCount = memoryFactDao.getForgottenCount()
        val averageImportance = memoryFactDao.getAverageImportance() ?: 0f
        val averageReinforcementCount = memoryFactDao.getAverageReinforcementCount() ?: 0f
        val categoryStatsRaw = memoryFactDao.getCategoryStatsRaw()
        val categoryStats = categoryStatsRaw.associate { it.category to it.count }

        return MemoryStats(
            activeCount = activeCount,
            forgottenCount = forgottenCount,
            averageImportance = averageImportance,
            averageReinforcementCount = averageReinforcementCount,
            categoryStats = categoryStats
        )
    }
}
