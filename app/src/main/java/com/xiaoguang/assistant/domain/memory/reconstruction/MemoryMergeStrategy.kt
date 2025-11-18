package com.xiaoguang.assistant.domain.memory.reconstruction

import com.xiaoguang.assistant.domain.memory.models.Memory
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

/**
 * 记忆合并策略接口
 *
 * 定义如何合并两个相似的记忆
 */
interface MemoryMergeStrategy {
    /**
     * 合并两个记忆
     *
     * @param primary 主要记忆 (保留更多内容的记忆)
     * @param secondary 次要记忆 (将被合并的记忆)
     * @return 合并后的记忆和重构记录
     */
    suspend fun merge(primary: Memory, secondary: Memory): MergeResult

    /**
     * 计算两个记忆的相似度
     *
     * @param memory1 记忆1
     * @param memory2 记忆2
     * @return 相似度分数 (0.0-1.0)
     */
    fun calculateSimilarity(memory1: Memory, memory2: Memory): Float

    /**
     * 判断是否应该合并
     *
     * @param memory1 记忆1
     * @param memory2 记忆2
     * @return 如果相似度足够高，返回true
     */
    fun shouldMerge(memory1: Memory, memory2: Memory): Boolean
}

/**
 * 合并结果
 */
data class MergeResult(
    val mergedMemory: Memory,
    val record: ReconstructionRecord,
    val wasMerged: Boolean  // 是否真正执行了合并
)

/**
 * 智能合并策略
 *
 * 基于内容相似度、时间、重要性等多个维度进行智能合并
 */
class SmartMergeStrategy : MemoryMergeStrategy {
    private val similarityCache = ConcurrentHashMap<String, Float>()

    override suspend fun merge(primary: Memory, secondary: Memory): MergeResult {
        val similarity = calculateSimilarity(primary, secondary)

        // 如果相似度不够高，不执行合并
        if (similarity < 0.5f) {
            return MergeResult(
                mergedMemory = primary,
                record = ReconstructionRecord.create(
                    originalMemoryId = primary.id,
                    updatedMemoryId = primary.id,
                    type = ReconstructionType.UPDATE,
                    reason = "相似度不足，保持独立",
                    oldContent = primary.content,
                    newContent = primary.content,
                    similarityScore = similarity,
                    confidence = 0.0f
                ),
                wasMerged = false
            )
        }

        // 执行合并
        val mergedContent = mergeContent(primary.content, secondary.content)
        val mergedImportance = max(primary.importance, secondary.importance)
        val mergedEntities = (primary.relatedEntities + secondary.relatedEntities).distinct()
        val mergedTags = (primary.tags + secondary.tags).distinct()

        val mergedMemory = primary.copy(
            id = primary.id,  // 保持原始ID
            content = mergedContent,
            importance = mergedImportance,
            relatedEntities = mergedEntities,
            tags = mergedTags,
            lastAccessedAt = System.currentTimeMillis(),
            accessCount = max(primary.accessCount, secondary.accessCount) + 1
        )

        val record = ReconstructionRecord.create(
            originalMemoryId = primary.id,
            updatedMemoryId = mergedMemory.id,
            type = ReconstructionType.MERGE,
            reason = "相似记忆合并 (相似度: ${"%.2f".format(similarity)})",
            oldContent = "${primary.content} | ${secondary.content}",
            newContent = mergedContent,
            similarityScore = similarity,
            confidence = similarity
        )

        return MergeResult(
            mergedMemory = mergedMemory,
            record = record,
            wasMerged = true
        )
    }

    override fun calculateSimilarity(memory1: Memory, memory2: Memory): Float {
        val cacheKey = "${memory1.id}_${memory2.id}"
        similarityCache[cacheKey]?.let { return it }

        // 内容相似度 (权重 0.4)
        val contentSim = calculateContentSimilarity(memory1.content, memory2.content)

        // 实体相似度 (权重 0.3)
        val entitySim = calculateEntitySimilarity(memory1.relatedEntities, memory2.relatedEntities)

        // 标签相似度 (权重 0.2)
        val tagSim = calculateTagSimilarity(memory1.tags, memory2.tags)

        // 时间接近度 (权重 0.1)
        val timeSim = calculateTimeSimilarity(memory1.createdAt, memory2.createdAt)

        val totalScore = contentSim * 0.4f + entitySim * 0.3f + tagSim * 0.2f + timeSim * 0.1f

        similarityCache[cacheKey] = totalScore
        return totalScore
    }

    override fun shouldMerge(memory1: Memory, memory2: Memory): Boolean {
        return calculateSimilarity(memory1, memory2) >= 0.5f
    }

    private fun calculateContentSimilarity(content1: String, content2: String): Float {
        // 简单实现：基于共同词汇计算相似度
        // 实际项目中可以使用更复杂的语义相似度算法
        val words1 = content1.split(Regex("\\s+")).toSet()
        val words2 = content2.split(Regex("\\s+")).toSet()

        val common = words1.intersect(words2).size
        val total = words1.union(words2).size

        return if (total == 0) 0f else common.toFloat() / total.toFloat()
    }

    private fun calculateEntitySimilarity(entities1: List<String>, entities2: List<String>): Float {
        if (entities1.isEmpty() && entities2.isEmpty()) return 1.0f
        if (entities1.isEmpty() || entities2.isEmpty()) return 0.0f

        val common = entities1.intersect(entities2.toSet()).size
        val total = entities1.union(entities2.toSet()).size

        return common.toFloat() / total.toFloat()
    }

    private fun calculateTagSimilarity(tags1: List<String>, tags2: List<String>): Float {
        if (tags1.isEmpty() && tags2.isEmpty()) return 1.0f
        if (tags1.isEmpty() || tags2.isEmpty()) return 0.0f

        val common = tags1.intersect(tags2.toSet()).size
        val total = tags1.union(tags2.toSet()).size

        return common.toFloat() / total.toFloat()
    }

    private fun calculateTimeSimilarity(time1: Long, time2: Long): Float {
        val diff = kotlin.math.abs(time1 - time2)
        val dayInMillis = 24 * 60 * 60 * 1000L

        // 1天内认为是高度相似，7天内中等相似
        return when {
            diff <= dayInMillis -> 1.0f
            diff <= 7 * dayInMillis -> 0.7f
            diff <= 30 * dayInMillis -> 0.4f
            else -> 0.1f
        }
    }

    private fun mergeContent(content1: String, content2: String): String {
        // 如果内容相似度很高，选择较长的内容
        val similarity = calculateContentSimilarity(content1, content2)
        return if (similarity > 0.8f) {
            if (content1.length >= content2.length) content1 else content2
        } else {
            // 合并内容，用分隔符连接
            "${content1.trim()} [补充: ${content2.trim()}]"
        }
    }

    /**
     * 清除缓存
     */
    fun clearCache() {
        similarityCache.clear()
    }

    /**
     * 获取缓存大小
     */
    fun getCacheSize(): Int {
        return similarityCache.size
    }
}

/**
 * 简单合并策略
 *
 * 只基于内容长度进行合并
 */
class SimpleMergeStrategy : MemoryMergeStrategy {
    override suspend fun merge(primary: Memory, secondary: Memory): MergeResult {
        val mergedContent = if (primary.content.length >= secondary.content.length) {
            primary.content
        } else {
            secondary.content
        }

        val mergedMemory = primary.copy(
            content = mergedContent,
            lastAccessedAt = System.currentTimeMillis()
        )

        return MergeResult(
            mergedMemory = mergedMemory,
            record = ReconstructionRecord.create(
                originalMemoryId = primary.id,
                updatedMemoryId = mergedMemory.id,
                type = ReconstructionType.MERGE,
                reason = "简单内容替换",
                oldContent = primary.content,
                newContent = mergedContent,
                similarityScore = 0.5f,
                confidence = 0.5f
            ),
            wasMerged = true
        )
    }

    override fun calculateSimilarity(memory1: Memory, memory2: Memory): Float {
        // 简单实现：总是返回中等相似度
        return 0.5f
    }

    override fun shouldMerge(memory1: Memory, memory2: Memory): Boolean {
        return true  // 总是合并
    }
}
