package com.xiaoguang.assistant.domain.memory.reconstruction

import com.xiaoguang.assistant.domain.memory.models.Memory
import com.xiaoguang.assistant.domain.memory.MemoryCore
import com.xiaoguang.assistant.domain.knowledge.vector.ChromaVectorStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 记忆重构服务
 *
 * 提供统一的记忆重构接口，支持多种重构类型、冲突解决和版本追踪
 * v2.4 核心功能模块
 */
@Singleton
class MemoryReconstructionService @Inject constructor(
    private val memoryCore: MemoryCore,
    private val vectorStore: ChromaVectorStore,
    private val mergeStrategy: MemoryMergeStrategy = SmartMergeStrategy(),
    private val conflictResolver: MergeConflictResolver = SmartConflictResolver()
) {

    /**
     * 重构单个记忆
     *
     * @param memoryId 记忆ID
     * @param reconstructionType 重构类型
     * @param newContent 新内容
     * @param reason 重构原因
     * @return 重构结果
     */
    suspend fun reconstructMemory(
        memoryId: String,
        reconstructionType: ReconstructionType,
        newContent: String,
        reason: String
    ): ReconstructionResult = withContext(Dispatchers.IO) {
        val existingMemory = memoryCore.getMemory(memoryId)
            ?: throw IllegalArgumentException("Memory not found: $memoryId")

        when (reconstructionType) {
            ReconstructionType.APPEND -> appendMemory(existingMemory, newContent, reason)
            ReconstructionType.UPDATE -> updateMemory(existingMemory, newContent, reason)
            ReconstructionType.REPLACE -> replaceMemory(existingMemory, newContent, reason)
            ReconstructionType.CORRECTION -> correctMemory(existingMemory, newContent, reason)
            ReconstructionType.REINTERPRETATION -> reinterpretMemory(existingMemory, newContent, reason)
            ReconstructionType.MERGE -> throw IllegalArgumentException("Use mergeMemories() for MERGE operation")
        }
    }

    /**
     * 合并两个相似记忆
     *
     * @param memoryId1 记忆1 ID
     * @param memoryId2 记忆2 ID
     * @return 合并结果
     */
    suspend fun mergeMemories(memoryId1: String, memoryId2: String): MergeOperationResult = withContext(Dispatchers.IO) {
        val memory1 = memoryCore.getMemory(memoryId1)
            ?: throw IllegalArgumentException("Memory not found: $memoryId1")
        val memory2 = memoryCore.getMemory(memoryId2)
            ?: throw IllegalArgumentException("Memory not found: $memoryId2")

        // 计算相似度
        val similarity = mergeStrategy.calculateSimilarity(memory1, memory2)

        // 判断是否需要合并
        if (!mergeStrategy.shouldMerge(memory1, memory2)) {
            return@withContext MergeOperationResult(
                success = false,
                reason = "相似度不足 (${"%.2f".format(similarity)})，无需合并",
                similarity = similarity
            )
        }

        // 执行合并
        val mergeResult = mergeStrategy.merge(memory1, memory2)

        if (mergeResult.wasMerged) {
            // 保存合并后的记忆
            memoryCore.storeMemory(mergeResult.mergedMemory)

            // 更新向量数据库
            vectorStore.upsert(
                key = mergeResult.mergedMemory.id,
                vector = vectorStore.embed(mergeResult.mergedMemory.content),
                metadata = mapOf(
                    "id" to mergeResult.mergedMemory.id,
                    "content" to mergeResult.mergedMemory.content,
                    "tags" to mergeResult.mergedMemory.tags.joinToString(","),
                    "importance" to mergeResult.mergedMemory.importance.toString()
                )
            )

            MergeOperationResult(
                success = true,
                mergedMemory = mergeResult.mergedMemory,
                record = mergeResult.record,
                similarity = similarity
            )
        } else {
            MergeOperationResult(
                success = false,
                reason = mergeResult.record.reason,
                similarity = similarity
            )
        }
    }

    /**
     * 查找需要重构的记忆
     *
     * @param threshold 相似度阈值
     * @return 候选记忆对列表
     */
    suspend fun findCandidatesForReconstruction(threshold: Float = 0.5f): List<MemoryPair> = withContext(Dispatchers.IO) {
        val allMemories = memoryCore.getAllMemories()
        val candidates = mutableListOf<MemoryPair>()

        for (i in allMemories.indices) {
            for (j in (i + 1) until allMemories.size) {
                val memory1 = allMemories[i]
                val memory2 = allMemories[j]

                val similarity = mergeStrategy.calculateSimilarity(memory1, memory2)

                if (similarity >= threshold) {
                    candidates.add(
                        MemoryPair(
                            memory1 = memory1,
                            memory2 = memory2,
                            similarity = similarity
                        )
                    )
                }
            }
        }

        // 按相似度排序
        candidates.sortedByDescending { it.similarity }
    }

    /**
     * 检测记忆冲突
     *
     * @param memoryId1 记忆1 ID
     * @param memoryId2 记忆2 ID
     * @return 冲突分析结果
     */
    suspend fun detectConflict(memoryId1: String, memoryId2: String): ConflictAnalysis = withContext(Dispatchers.IO) {
        val memory1 = memoryCore.getMemory(memoryId1)
            ?: throw IllegalArgumentException("Memory not found: $memoryId1")
        val memory2 = memoryCore.getMemory(memoryId2)
            ?: throw IllegalArgumentException("Memory not found: $memoryId2")

        val conflicts = mutableListOf<ConflictType>()

        // 检测内容冲突
        val contentSim = mergeStrategy.calculateSimilarity(memory1, memory2)
        if (contentSim < 0.3f) {
            conflicts.add(ConflictType.CONTENT_CONFLICT)
        }

        // 检测时间冲突
        val timeDiff = kotlin.math.abs(memory1.createdAt - memory2.createdAt)
        if (timeDiff < 24 * 60 * 60 * 1000L) { // 24小时内的时间差异
            // 检查时间描述是否冲突
            if (hasTimeConflict(memory1.content, memory2.content)) {
                conflicts.add(ConflictType.TIME_CONFLICT)
            }
        }

        // 检测实体冲突
        val entity1 = memory1.relatedEntities.toSet()
        val entity2 = memory2.relatedEntities.toSet()
        val commonEntities = entity1.intersect(entity2)
        if (commonEntities.isNotEmpty()) {
            // 检查实体描述是否冲突
            val hasEntityConflict = checkEntityConflict(memory1.content, memory2.content, commonEntities.toList())
            if (hasEntityConflict) {
                conflicts.add(ConflictType.ENTITY_CONFLICT)
            }
        }

        // 检测重要性冲突
        if (kotlin.math.abs(memory1.importance - memory2.importance) > 0.5f) {
            conflicts.add(ConflictType.IMPORTANCE_CONFLICT)
        }

        // 检测标签冲突
        val tag1 = memory1.tags.toSet()
        val tag2 = memory2.tags.toSet()
        val commonTags = tag1.intersect(tag2)
        if (commonTags.isNotEmpty()) {
            val hasTagConflict = checkTagConflict(memory1.content, memory2.content, commonTags.toList())
            if (hasTagConflict) {
                conflicts.add(ConflictType.TAG_CONFLICT)
            }
        }

        ConflictAnalysis(
            memory1 = memory1,
            memory2 = memory2,
            conflicts = conflicts,
            similarity = contentSim
        )
    }

    /**
     * 解决记忆冲突
     *
     * @param memoryId1 记忆1 ID
     * @param memoryId2 记忆2 ID
     * @param conflictTypes 冲突类型列表
     * @return 冲突解决结果
     */
    suspend fun resolveConflict(
        memoryId1: String,
        memoryId2: String,
        conflictTypes: List<ConflictType>
    ): ConflictResolutionResult = withContext(Dispatchers.IO) {
        val memory1 = memoryCore.getMemory(memoryId1)
            ?: throw IllegalArgumentException("Memory not found: $memoryId1")
        val memory2 = memoryCore.getMemory(memoryId2)
            ?: throw IllegalArgumentException("Memory not found: $memoryId2")

        // 选择最严重的冲突类型作为主要冲突
        val primaryConflict = conflictTypes.maxByOrNull { it.severity }
            ?: throw IllegalArgumentException("No conflicts to resolve")

        // 使用冲突解决器解决冲突
        val resolution = conflictResolver.resolveConflict(memory1, memory2, primaryConflict)

        // 保存解决后的记忆
        memoryCore.storeMemory(resolution.resolvedMemory)

        ConflictResolutionResult(
            originalMemory1 = memory1,
            originalMemory2 = memory2,
            resolution = resolution
        )
    }

    /**
     * 获取记忆重构历史
     *
     * @param memoryId 记忆ID
     * @return 重构记录列表
     */
    suspend fun getReconstructionHistory(memoryId: String): List<ReconstructionRecord> = withContext(Dispatchers.IO) {
        // 这里应该从数据库查询重构历史
        // 暂时返回空列表，实际实现时需要连接数据库
        emptyList<ReconstructionRecord>()
    }

    /**
     * 清理缓存
     */
    fun clearCache() {
        if (mergeStrategy is SmartMergeStrategy) {
            mergeStrategy.clearCache()
        }
        if (conflictResolver is SmartConflictResolver) {
            conflictResolver.clearCache()
        }
    }

    // ==================== 私有方法 ====================

    private suspend fun appendMemory(
        memory: Memory,
        newContent: String,
        reason: String
    ): ReconstructionResult {
        val updatedContent = "${memory.content}\n\n补充信息: $newContent"
        val updatedMemory = memory.copy(
            content = updatedContent,
            lastAccessedAt = System.currentTimeMillis(),
            accessCount = memory.accessCount + 1
        )

        memoryCore.storeMemory(updatedMemory)

        val record = ReconstructionRecord.create(
            originalMemoryId = memory.id,
            updatedMemoryId = updatedMemory.id,
            type = ReconstructionType.APPEND,
            reason = reason,
            oldContent = memory.content,
            newContent = updatedContent,
            similarityScore = 1.0f,
            confidence = 0.9f
        )

        return ReconstructionResult(
            memory = updatedMemory,
            record = record
        )
    }

    private suspend fun updateMemory(
        memory: Memory,
        newContent: String,
        reason: String
    ): ReconstructionResult {
        val updatedMemory = memory.copy(
            content = newContent,
            lastAccessedAt = System.currentTimeMillis(),
            accessCount = memory.accessCount + 1
        )

        memoryCore.storeMemory(updatedMemory)

        val record = ReconstructionRecord.create(
            originalMemoryId = memory.id,
            updatedMemoryId = updatedMemory.id,
            type = ReconstructionType.UPDATE,
            reason = reason,
            oldContent = memory.content,
            newContent = newContent,
            similarityScore = 0.8f,
            confidence = 0.8f
        )

        return ReconstructionResult(
            memory = updatedMemory,
            record = record
        )
    }

    private suspend fun replaceMemory(
        memory: Memory,
        newContent: String,
        reason: String
    ): ReconstructionResult {
        val updatedMemory = memory.copy(
            content = newContent,
            importance = memory.importance, // 保持重要性不变
            lastAccessedAt = System.currentTimeMillis(),
            accessCount = memory.accessCount + 1
        )

        memoryCore.storeMemory(updatedMemory)

        val record = ReconstructionRecord.create(
            originalMemoryId = memory.id,
            updatedMemoryId = updatedMemory.id,
            type = ReconstructionType.REPLACE,
            reason = reason,
            oldContent = memory.content,
            newContent = newContent,
            similarityScore = 0.5f,
            confidence = 0.7f
        )

        return ReconstructionResult(
            memory = updatedMemory,
            record = record
        )
    }

    private suspend fun correctMemory(
        memory: Memory,
        newContent: String,
        reason: String
    ): ReconstructionResult {
        val updatedMemory = memory.copy(
            content = newContent,
            lastAccessedAt = System.currentTimeMillis(),
            accessCount = memory.accessCount + 1
        )

        memoryCore.storeMemory(updatedMemory)

        val record = ReconstructionRecord.create(
            originalMemoryId = memory.id,
            updatedMemoryId = updatedMemory.id,
            type = ReconstructionType.CORRECTION,
            reason = reason,
            oldContent = memory.content,
            newContent = newContent,
            similarityScore = 0.6f,
            confidence = 0.85f
        )

        return ReconstructionResult(
            memory = updatedMemory,
            record = record
        )
    }

    private suspend fun reinterpretMemory(
        memory: Memory,
        newContent: String,
        reason: String
    ): ReconstructionResult {
        val updatedContent = "${memory.content}\n\n重新理解: $newContent"
        val updatedMemory = memory.copy(
            content = updatedContent,
            lastAccessedAt = System.currentTimeMillis(),
            accessCount = memory.accessCount + 1
        )

        memoryCore.storeMemory(updatedMemory)

        val record = ReconstructionRecord.create(
            originalMemoryId = memory.id,
            updatedMemoryId = updatedMemory.id,
            type = ReconstructionType.REINTERPRETATION,
            reason = reason,
            oldContent = memory.content,
            newContent = updatedContent,
            similarityScore = 0.9f,
            confidence = 0.75f
        )

        return ReconstructionResult(
            memory = updatedMemory,
            record = record
        )
    }

    private fun hasTimeConflict(content1: String, content2: String): Boolean {
        // 简单的冲突检测：检查是否包含矛盾的时间信息
        val timeKeywords = listOf("昨天", "今天", "明天", "上周", "下周", "去年", "今年", "明年")
        var hasTime1 = false
        var hasTime2 = false

        for (keyword in timeKeywords) {
            if (content1.contains(keyword)) hasTime1 = true
            if (content2.contains(keyword)) hasTime2 = true
        }

        return hasTime1 && hasTime2
    }

    private fun checkEntityConflict(
        content1: String,
        content2: String,
        commonEntities: List<String>
    ): Boolean {
        // 检查共同实体是否在内容中被描述为不同
        // 简化实现：检查是否包含矛盾描述
        return false
    }

    private fun checkTagConflict(
        content1: String,
        content2: String,
        commonTags: List<String>
    ): Boolean {
        // 检查共同标签是否导致内容冲突
        return false
    }
}

/**
 * 重构结果
 */
data class ReconstructionResult(
    val memory: Memory,
    val record: ReconstructionRecord
)

/**
 * 合并操作结果
 */
data class MergeOperationResult(
    val success: Boolean,
    val mergedMemory: Memory? = null,
    val record: ReconstructionRecord? = null,
    val similarity: Float,
    val reason: String? = null
)

/**
 * 记忆对
 */
data class MemoryPair(
    val memory1: Memory,
    val memory2: Memory,
    val similarity: Float
)

/**
 * 冲突分析结果
 */
data class ConflictAnalysis(
    val memory1: Memory,
    val memory2: Memory,
    val conflicts: List<ConflictType>,
    val similarity: Float
)

/**
 * 冲突解决结果
 */
data class ConflictResolutionResult(
    val originalMemory1: Memory,
    val originalMemory2: Memory,
    val resolution: ConflictResolution
)