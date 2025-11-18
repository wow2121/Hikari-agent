package com.xiaoguang.assistant.domain.memory.reconstruction

import com.xiaoguang.assistant.domain.memory.models.Memory
import com.xiaoguang.assistant.domain.memory.MemoryCore
import com.xiaoguang.assistant.domain.knowledge.vector.ChromaVectorStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 语义搜索引擎
 *
 * 基于向量数据库的智能记忆搜索，支持：
 * - 语义相似度搜索
 * - 时间范围搜索
 * - 重要性筛选
 * - 多条件组合查询
 *
 * v2.4 核心功能
 */
@Singleton
class SemanticSearchEngine @Inject constructor(
    private val memoryCore: MemoryCore,
    private val vectorStore: ChromaVectorStore
) {

    private val searchCache = ConcurrentHashMap<String, SearchResult>()

    /**
     * 语义搜索
     *
     * @param query 查询文本
     * @param limit 返回结果数量限制
     * @param similarityThreshold 相似度阈值
     * @param includeMetadata 是否包含元数据
     * @return 搜索结果
     */
    suspend fun semanticSearch(
        query: String,
        limit: Int = 10,
        similarityThreshold: Float = 0.6f,
        includeMetadata: Boolean = true
    ): SearchResult = withContext(Dispatchers.IO) {
        val cacheKey = "semantic:${query}:${limit}:${similarityThreshold}"
        searchCache[cacheKey]?.let { return@withContext it }

        // 生成查询向量
        val queryVector = vectorStore.embed(query)

        // 向量相似度搜索
        val vectorResults = vectorStore.similaritySearch(
            queryVector = queryVector,
            limit = limit * 2, // 获取更多结果，后续过滤
            similarityThreshold = similarityThreshold
        )

        // 获取完整的记忆对象
        val memories = vectorResults.mapNotNull { result ->
            val memoryId = result.metadata["id"] as? String
            if (memoryId != null) {
                memoryCore.getMemory(memoryId)
            } else {
                null
            }
        }.filterNotNull()

        // 按相似度和重要性排序
        val sortedMemories = memories.sortedWith(
            compareByDescending<Memory> { memory ->
                // 找到对应的相似度分数
                vectorResults.find { (it.metadata["id"] as? String) == memory.id }?.similarity
                    ?: 0f
            }.thenByDescending { it.importance }
        ).take(limit)

        val result = SearchResult(
            query = query,
            memories = sortedMemories,
            totalFound = memories.size,
            similarityThreshold = similarityThreshold,
            searchTimeMs = System.currentTimeMillis()
        )

        searchCache[cacheKey] = result
        result
    }

    /**
     * 时间范围搜索
     *
     * @param query 查询文本（可选）
     * @param startTime 开始时间戳
     * @param endTime 结束时间戳
     * @param limit 返回结果数量限制
     * @return 搜索结果
     */
    suspend fun timeRangeSearch(
        query: String? = null,
        startTime: Long,
        endTime: Long,
        limit: Int = 20
    ): SearchResult = withContext(Dispatchers.IO) {
        val allMemories = memoryCore.getAllMemories()

        // 时间过滤
        val filtered = if (query != null) {
            // 结合文本查询和时间过滤
            val textResults = semanticSearch(query, limit = 100)
            textResults.memories.filter { memory ->
                memory.createdAt in startTime..endTime
            }
        } else {
            // 仅时间过滤
            allMemories.filter { memory ->
                memory.createdAt in startTime..endTime
            }
        }.sortedByDescending { it.createdAt }
            .take(limit)

        SearchResult(
            query = query ?: "时间范围: ${startTime} - ${endTime}",
            memories = filtered,
            totalFound = filtered.size,
            similarityThreshold = 0f,
            searchTimeMs = System.currentTimeMillis()
        )
    }

    /**
     * 重要性搜索
     *
     * @param query 查询文本
     * @param minImportance 最小重要性 (0.0-1.0)
     * @param limit 返回结果数量限制
     * @return 搜索结果
     */
    suspend fun importanceSearch(
        query: String,
        minImportance: Float = 0.5f,
        limit: Int = 10
    ): SearchResult = withContext(Dispatchers.IO) {
        val results = semanticSearch(query, limit = limit * 2)

        val filtered = results.memories
            .filter { it.importance >= minImportance }
            .sortedByDescending { it.importance }
            .take(limit)

        results.copy(memories = filtered, totalFound = filtered.size)
    }

    /**
     * 标签搜索
     *
     * @param tags 标签列表
     * @param matchAll 是否匹配所有标签（AND），否则匹配任一标签（OR）
     * @param limit 返回结果数量限制
     * @return 搜索结果
     */
    suspend fun tagSearch(
        tags: List<String>,
        matchAll: Boolean = true,
        limit: Int = 20
    ): SearchResult = withContext(Dispatchers.IO) {
        val allMemories = memoryCore.getAllMemories()

        val filtered = if (matchAll) {
            // AND 匹配：包含所有标签
            allMemories.filter { memory ->
                tags.all { tag -> memory.tags.contains(tag) }
            }
        } else {
            // OR 匹配：包含任一标签
            allMemories.filter { memory ->
                tags.any { tag -> memory.tags.contains(tag) }
            }
        }.sortedByDescending { it.importance }
            .take(limit)

        SearchResult(
            query = "标签搜索: ${tags.joinToString(", ")}",
            memories = filtered,
            totalFound = filtered.size,
            similarityThreshold = 0f,
            searchTimeMs = System.currentTimeMillis()
        )
    }

    /**
     * 多条件组合搜索
     *
     * @param criteria 搜索条件
     * @param limit 返回结果数量限制
     * @return 搜索结果
     */
    suspend fun advancedSearch(
        criteria: SearchCriteria,
        limit: Int = 20
    ): SearchResult = withContext(Dispatchers.IO) {
        val allMemories = memoryCore.getAllMemories()
        var filtered = allMemories

        // 文本搜索
        if (criteria.query != null) {
            val textResults = semanticSearch(
                query = criteria.query,
                limit = 100,
                similarityThreshold = criteria.similarityThreshold ?: 0.6f
            )
            filtered = textResults.memories.toMutableList()
        }

        // 标签过滤
        if (criteria.requiredTags.isNotEmpty()) {
            filtered = filtered.filter { memory ->
                criteria.requiredTags.all { tag -> memory.tags.contains(tag) }
            }
        }

        // 实体过滤
        if (criteria.requiredEntities.isNotEmpty()) {
            filtered = filtered.filter { memory ->
                criteria.requiredEntities.all { entity -> memory.relatedEntities.contains(entity) }
            }
        }

        // 时间范围过滤
        criteria.startTime?.let { start ->
            criteria.endTime?.let { end ->
                filtered = filtered.filter { memory ->
                    memory.createdAt in start..end
                }
            }
        }

        // 重要性过滤
        criteria.minImportance?.let { min ->
            filtered = filtered.filter { memory ->
                memory.importance >= min
            }
        }

        // 情感过滤
        criteria.minEmotionalValence?.let { min ->
            filtered = filtered.filter { memory ->
                memory.emotionalValence >= min
            }
        }

        criteria.maxEmotionalValence?.let { max ->
            filtered = filtered.filter { memory ->
                memory.emotionalValence <= max
            }
        }

        // 排序
        filtered = when (criteria.sortBy) {
            SortBy.RELEVANCE -> filtered.sortedByDescending { it.importance }
            SortBy.TIME_DESC -> filtered.sortedByDescending { it.createdAt }
            SortBy.TIME_ASC -> filtered.sortedBy { it.createdAt }
            SortBy.IMPORTANCE_DESC -> filtered.sortedByDescending { it.importance }
            SortBy.EMOTION_DESC -> filtered.sortedByDescending { it.emotionalValence }
        }.take(limit)

        SearchResult(
            query = criteria.query ?: "高级搜索",
            memories = filtered,
            totalFound = filtered.size,
            similarityThreshold = criteria.similarityThreshold ?: 0f,
            searchTimeMs = System.currentTimeMillis()
        )
    }

    /**
     * 查找相似记忆
     *
     * @param memoryId 记忆ID
     * @param limit 返回结果数量限制
     * @param excludeSelf 是否排除自身
     * @return 相似记忆列表
     */
    suspend fun findSimilarMemories(
        memoryId: String,
        limit: Int = 5,
        excludeSelf: Boolean = true
    ): List<MemoryWithSimilarity> = withContext(Dispatchers.IO) {
        val targetMemory = memoryCore.getMemory(memoryId)
            ?: throw IllegalArgumentException("Memory not found: $memoryId")

        val allMemories = memoryCore.getAllMemories()
        val candidates = if (excludeSelf) {
            allMemories.filter { it.id != memoryId }
        } else {
            allMemories
        }

        val mergeStrategy = SmartMergeStrategy()
        val similarities = candidates.mapNotNull { memory ->
            val score = mergeStrategy.calculateSimilarity(targetMemory, memory)
            if (score >= 0.5f) {
                MemoryWithSimilarity(
                    memory = memory,
                    similarity = score
                )
            } else {
                null
            }
        }.sortedByDescending { it.similarity }
            .take(limit)

        similarities
    }

    /**
     * 智能建议搜索
     *
     * 基于用户当前记忆，推荐相关记忆
     *
     * @param memory 基准记忆
     * @param limit 返回结果数量限制
     * @return 推荐记忆列表
     */
    suspend fun suggestRelatedMemories(
        memory: Memory,
        limit: Int = 3
    ): List<MemoryWithScore> = withContext(Dispatchers.IO) {
        val allMemories = memoryCore.getAllMemories()
            .filter { it.id != memory.id }

        val suggestions = allMemories.map { candidate ->
            val mergeStrategy = SmartMergeStrategy()
            val similarity = mergeStrategy.calculateSimilarity(memory, candidate)

            // 综合相似度和重要性
            val score = similarity * 0.7f + candidate.importance * 0.3f

            MemoryWithScore(
                memory = candidate,
                score = score,
                reason = generateSuggestionReason(memory, candidate, similarity)
            )
        }.filter { it.score >= 0.4f }
            .sortedByDescending { it.score }
            .take(limit)

        suggestions
    }

    /**
     * 清理搜索缓存
     */
    fun clearCache() {
        searchCache.clear()
    }

    /**
     * 获取缓存大小
     */
    fun getCacheSize(): Int {
        return searchCache.size
    }

    // ==================== 私有方法 ====================

    private fun generateSuggestionReason(
        source: Memory,
        candidate: Memory,
        similarity: Float
    ): String {
        val reasons = mutableListOf<String>()

        // 内容相似
        if (similarity > 0.7f) {
            reasons.add("内容高度相似")
        } else if (similarity > 0.5f) {
            reasons.add("内容相关")
        }

        // 共同标签
        val commonTags = source.tags.intersect(candidate.tags.toSet())
        if (commonTags.isNotEmpty()) {
            reasons.add("共同标签: ${commonTags.joinToString(", ")}")
        }

        // 共同实体
        val commonEntities = source.relatedEntities.intersect(candidate.relatedEntities.toSet())
        if (commonEntities.isNotEmpty()) {
            reasons.add("相关实体: ${commonEntities.joinToString(", ")}")
        }

        // 时间接近
        val timeDiff = kotlin.math.abs(source.createdAt - candidate.createdAt)
        if (timeDiff < 24 * 60 * 60 * 1000L) { // 24小时内
            reasons.add("时间相近")
        }

        return reasons.joinToString("，")
    }
}

/**
 * 搜索结果
 */
data class SearchResult(
    val query: String,
    val memories: List<Memory>,
    val totalFound: Int,
    val similarityThreshold: Float,
    val searchTimeMs: Long
)

/**
 * 记忆相似度包装
 */
data class MemoryWithSimilarity(
    val memory: Memory,
    val similarity: Float
)

/**
 * 记忆分数包装（用于推荐）
 */
data class MemoryWithScore(
    val memory: Memory,
    val score: Float,
    val reason: String
)

/**
 * 搜索条件
 */
data class SearchCriteria(
    val query: String? = null,
    val requiredTags: List<String> = emptyList(),
    val requiredEntities: List<String> = emptyList(),
    val startTime: Long? = null,
    val endTime: Long? = null,
    val minImportance: Float? = null,
    val minEmotionalValence: Float? = null,
    val maxEmotionalValence: Float? = null,
    val similarityThreshold: Float? = null,
    val sortBy: SortBy = SortBy.RELEVANCE
)

/**
 * 排序方式
 */
enum class SortBy {
    RELEVANCE,      // 相关性（默认按重要性）
    TIME_DESC,      // 时间降序（最新在前）
    TIME_ASC,       // 时间升序（最旧在前）
    IMPORTANCE_DESC, // 重要性降序
    EMOTION_DESC    // 情感强度降序
}
