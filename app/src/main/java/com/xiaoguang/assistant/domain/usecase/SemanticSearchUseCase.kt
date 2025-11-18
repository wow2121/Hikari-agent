package com.xiaoguang.assistant.domain.usecase

import com.xiaoguang.assistant.data.local.database.entity.ConversationEmbeddingEntity
import com.xiaoguang.assistant.data.local.database.entity.MemoryFactEntity
import com.xiaoguang.assistant.domain.repository.EmbeddingRepository
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp
import kotlin.math.pow

/**
 * 语义搜索用例
 * 实现多阶段检索和智能排序
 */
@Singleton
class SemanticSearchUseCase @Inject constructor(
    private val generateEmbeddingUseCase: GenerateEmbeddingUseCase,
    private val embeddingRepository: EmbeddingRepository
) {

    /**
     * 搜索相关的对话历史
     *
     * @param query 查询文本
     * @param topK 返回的数量
     * @param minSimilarity 最小相似度阈值(0.0-1.0)
     * @param timeDecayDays 时间衰减周期(天)
     * @return 搜索结果列表,包含分数和对话实体
     */
    suspend fun searchConversations(
        query: String,
        topK: Int = 10,
        minSimilarity: Float = 0.3f,
        timeDecayDays: Int = 30
    ): Result<List<ConversationSearchResult>> {
        return try {
            // 1. 生成查询向量
            val embeddingResult = generateEmbeddingUseCase.generateEmbedding(query)
            if (embeddingResult.isFailure) {
                return Result.failure(embeddingResult.exceptionOrNull()!!)
            }

            val queryEmbedding = embeddingResult.getOrNull()!!
            Timber.d("查询向量维度: ${queryEmbedding.dimension}")

            // 2. 向量相似度搜索
            val candidates = embeddingRepository.searchSimilarConversations(
                queryVector = queryEmbedding.vector,
                topK = topK * 3, // 获取更多候选,用于重排序
                dimension = queryEmbedding.dimension
            )

            if (candidates.isEmpty()) {
                Timber.d("没有找到相似的对话")
                return Result.success(emptyList())
            }

            // 3. 多因素排序
            val now = System.currentTimeMillis()
            val scoredResults = candidates.map { (similarity, entity) ->
                // 时间衰减分数
                val timeDecayScore = calculateTimeDecay(
                    timestamp = entity.timestamp,
                    now = now,
                    decayDays = timeDecayDays
                )

                // 访问频率分数
                val accessScore = calculateAccessScore(entity.accessCount)

                // 重要性分数
                val importance = entity.importance

                // 综合分数(加权平均)
                val finalScore = (
                    similarity * 0.5f +           // 语义相似度权重50%
                    timeDecayScore * 0.2f +       // 时间新鲜度权重20%
                    importance * 0.2f +      // 重要性权重20%
                    accessScore * 0.1f            // 访问频率权重10%
                )

                ConversationSearchResult(
                    entity = entity,
                    similarityScore = similarity,
                    timeDecayScore = timeDecayScore,
                    importance = importance,
                    accessScore = accessScore,
                    finalScore = finalScore
                )
            }

            // 4. 按最终分数排序并过滤
            val results = scoredResults
                .filter { it.similarityScore >= minSimilarity }
                .sortedByDescending { it.finalScore }
                .take(topK)

            Timber.d("搜索到 ${results.size} 条相关对话, 最高分: ${results.firstOrNull()?.finalScore}")
            Result.success(results)

        } catch (e: Exception) {
            Timber.e(e, "搜索对话失败")
            Result.failure(e)
        }
    }

    /**
     * 搜索相关的记忆事实
     *
     * @param query 查询文本
     * @param topK 返回的数量
     * @param minSimilarity 最小相似度阈值
     * @param category 可选的类别过滤
     * @return 搜索结果列表
     */
    suspend fun searchMemoryFacts(
        query: String,
        topK: Int = 10,
        minSimilarity: Float = 0.4f,
        category: String? = null
    ): Result<List<MemoryFactSearchResult>> {
        return try {
            // 1. 生成查询向量
            val embeddingResult = generateEmbeddingUseCase.generateEmbedding(query)
            if (embeddingResult.isFailure) {
                return Result.failure(embeddingResult.exceptionOrNull()!!)
            }

            val queryEmbedding = embeddingResult.getOrNull()!!

            // 2. 向量相似度搜索
            val candidates = embeddingRepository.searchSimilarMemoryFacts(
                queryVector = queryEmbedding.vector,
                topK = topK * 2,
                dimension = queryEmbedding.dimension
            )

            if (candidates.isEmpty()) {
                Timber.d("没有找到相似的记忆")
                return Result.success(emptyList())
            }

            // 3. 类别过滤(如果指定)
            val filtered = if (category != null) {
                candidates.filter { it.second.category == category }
            } else {
                candidates
            }

            // 4. 计算记忆强度并排序
            val now = System.currentTimeMillis()
            val scoredResults = filtered.map { (similarity, entity) ->
                // 记忆强度分数(基于强化次数和时间)
                val memoryStrength = calculateMemoryStrength(
                    reinforcementCount = entity.reinforcementCount,
                    lastAccessedAt = entity.lastAccessedAt,
                    now = now
                )

                // 综合分数
                val finalScore = (
                    similarity * 0.6f +                  // 语义相似度权重60%
                    entity.importance * 0.2f +      // 重要性权重20%
                    memoryStrength * 0.15f +             // 记忆强度权重15%
                    entity.confidence * 0.05f            // 置信度权重5%
                )

                MemoryFactSearchResult(
                    entity = entity,
                    similarityScore = similarity,
                    importance = entity.importance,
                    memoryStrength = memoryStrength,
                    confidence = entity.confidence,
                    finalScore = finalScore
                )
            }

            // 5. 按最终分数排序并过滤
            val results = scoredResults
                .filter { it.similarityScore >= minSimilarity }
                .sortedByDescending { it.finalScore }
                .take(topK)

            Timber.d("搜索到 ${results.size} 条相关记忆, 最高分: ${results.firstOrNull()?.finalScore}")
            Result.success(results)

        } catch (e: Exception) {
            Timber.e(e, "搜索记忆失败")
            Result.failure(e)
        }
    }

    /**
     * 混合搜索:同时搜索对话历史和记忆事实
     *
     * @param query 查询文本
     * @param topKConversations 返回的对话数量
     * @param topKMemories 返回的记忆数量
     * @return 混合搜索结果
     */
    suspend fun hybridSearch(
        query: String,
        topKConversations: Int = 5,
        topKMemories: Int = 5
    ): Result<HybridSearchResult> {
        return try {
            // 并行搜索对话和记忆
            val conversationsResult = searchConversations(query, topKConversations)
            val memoriesResult = searchMemoryFacts(query, topKMemories)

            if (conversationsResult.isFailure && memoriesResult.isFailure) {
                return Result.failure(
                    Exception("对话和记忆搜索都失败")
                )
            }

            val result = HybridSearchResult(
                conversations = conversationsResult.getOrNull() ?: emptyList(),
                memories = memoriesResult.getOrNull() ?: emptyList()
            )

            Timber.d("混合搜索完成: ${result.conversations.size} 条对话, ${result.memories.size} 条记忆")
            Result.success(result)

        } catch (e: Exception) {
            Timber.e(e, "混合搜索失败")
            Result.failure(e)
        }
    }

    /**
     * 构建上下文提示词
     * 将搜索结果转换为可以注入到对话的上下文文本
     *
     * @param searchResult 搜索结果
     * @param maxLength 最大文本长度
     * @return 格式化的上下文文本
     */
    fun buildContextPrompt(
        searchResult: HybridSearchResult,
        maxLength: Int = 2000
    ): String {
        val builder = StringBuilder()

        // 添加相关记忆
        if (searchResult.memories.isNotEmpty()) {
            builder.append("【相关记忆】\n")
            searchResult.memories.take(3).forEach { result ->
                val fact = result.entity
                builder.append("- ${fact.content}")
                if (fact.category != "fact") {
                    builder.append(" [${fact.category}]")
                }
                builder.append("\n")
            }
            builder.append("\n")
        }

        // 添加相关对话
        if (searchResult.conversations.isNotEmpty()) {
            builder.append("【相关对话】\n")
            searchResult.conversations.take(3).forEach { result ->
                val conv = result.entity
                val role = if (conv.role == "user") "用户" else "助手"
                builder.append("$role: ${conv.content.take(100)}")
                if (conv.content.length > 100) builder.append("...")
                builder.append("\n")
            }
        }

        val context = builder.toString()
        return if (context.length > maxLength) {
            context.take(maxLength) + "..."
        } else {
            context
        }
    }

    // ========== 辅助计算方法 ==========

    /**
     * 计算时间衰减分数
     * 使用指数衰减: score = exp(-age / halfLife)
     */
    private fun calculateTimeDecay(
        timestamp: Long,
        now: Long,
        decayDays: Int
    ): Float {
        val ageMillis = now - timestamp
        val ageDays = ageMillis / (24 * 60 * 60 * 1000f)
        val halfLife = decayDays.toFloat()

        return exp(-ageDays / halfLife).toFloat().coerceIn(0f, 1f)
    }

    /**
     * 计算访问分数
     * 使用对数缩放避免过度奖励高访问次数
     */
    private fun calculateAccessScore(accessCount: Int): Float {
        if (accessCount <= 0) return 0f

        // 对数缩放: score = log(1 + count) / log(101)
        // 这样0次访问得0分,100次访问得1分
        val score = kotlin.math.ln(1 + accessCount.toFloat()) / kotlin.math.ln(101f)
        return score.coerceIn(0f, 1f)
    }

    /**
     * 计算记忆强度
     * 基于Ebbinghaus遗忘曲线的简化模型
     */
    private fun calculateMemoryStrength(
        reinforcementCount: Int,
        lastAccessedAt: Long,
        now: Long
    ): Float {
        // 基础强度(基于强化次数)
        val baseStrength = (1f - (1f / (1f + reinforcementCount * 0.5f)))

        // 时间衰减
        val daysSinceReinforcement = (now - lastAccessedAt) / (24 * 60 * 60 * 1000f)
        val timeDecay = exp(-daysSinceReinforcement / 30f).toFloat() // 30天半衰期

        return (baseStrength * timeDecay).coerceIn(0f, 1f)
    }
}

/**
 * 对话搜索结果
 */
data class ConversationSearchResult(
    val entity: ConversationEmbeddingEntity,
    val similarityScore: Float,
    val timeDecayScore: Float,
    val importance: Float,
    val accessScore: Float,
    val finalScore: Float
)

/**
 * 记忆事实搜索结果
 */
data class MemoryFactSearchResult(
    val entity: MemoryFactEntity,
    val similarityScore: Float,
    val importance: Float,
    val memoryStrength: Float,
    val confidence: Float,
    val finalScore: Float
)

/**
 * 混合搜索结果
 */
data class HybridSearchResult(
    val conversations: List<ConversationSearchResult>,
    val memories: List<MemoryFactSearchResult>
) {
    val isEmpty: Boolean
        get() = conversations.isEmpty() && memories.isEmpty()
}
