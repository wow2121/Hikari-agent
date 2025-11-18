package com.xiaoguang.assistant.domain.memory

import com.xiaoguang.assistant.domain.memory.models.*
import com.xiaoguang.assistant.domain.repository.EmbeddingRepository
import com.xiaoguang.assistant.domain.knowledge.vector.VectorEmbeddingService
import com.xiaoguang.assistant.domain.knowledge.vector.ChromaVectorStore
import com.xiaoguang.assistant.domain.knowledge.graph.Neo4jClient
import timber.log.Timber
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp
import kotlin.math.min
import kotlin.random.Random

/**
 * 多维记忆检索引擎
 *
 * 基于IMDMR论文的6维检索策略：
 * 1. Semantic (语义维度) - 30%
 * 2. Temporal (时间维度) - 20%
 * 3. Importance (重要性维度) - 20%
 * 4. Emotional (情感维度) - 15%
 * 5. Entity (实体维度) - 10%
 * 6. Intent (意图维度) - 5%
 */
@Singleton
class MultiDimensionalRetriever @Inject constructor(
    private val embeddingRepository: EmbeddingRepository,
    private val chromaStore: ChromaVectorStore,
    private val vectorEmbeddingService: VectorEmbeddingService,
    private val neo4jClient: Neo4jClient
) {

    // 权重配置
    private val weights = mapOf(
        "semantic" to 0.30f,
        "temporal" to 0.20f,
        "importance" to 0.20f,
        "emotional" to 0.15f,
        "entity" to 0.10f,
        "intent" to 0.05f
    )

    /**
     * 多维检索主入口
     */
    suspend fun retrieve(query: MemoryQuery): List<RankedMemory> {
        try {
            Timber.d("[MultiDimensionalRetriever] 开始检索: semantic=${query.semantic}, " +
                    "categories=${query.categories}, temporal=${query.temporal}")

            // 第一步：获取候选集
            val candidates = getCandidates(query)
            if (candidates.isEmpty()) {
                Timber.d("[MultiDimensionalRetriever] 没有找到候选记忆")
                return emptyList()
            }

            Timber.d("[MultiDimensionalRetriever] 候选集大小: ${candidates.size}")

            // 第二步：多维打分
            val ranked = candidates.map { memory ->
                val breakdown = scoreMemory(memory, query)
                val totalScore = calculateTotalScore(breakdown)

                RankedMemory(
                    memory = memory,
                    score = totalScore,
                    scoreBreakdown = breakdown
                )
            }

            // 第三步：排序和过滤
            var results = ranked
                .sortedByDescending { it.score }
                .take(query.limit)

            // 第四步：多样化采样（如果需要）
            if (query.diversified && results.size > 5) {
                results = diversifySampling(results, query.limit)
            }

            Timber.d("[MultiDimensionalRetriever] 返回 ${results.size} 条记忆")

            return results

        } catch (e: Exception) {
            Timber.e(e, "[MultiDimensionalRetriever] 检索失败")
            return emptyList()
        }
    }

    // ==================== 候选集获取 ====================

    /**
     * 获取候选集（综合多种方式）
     */
    private suspend fun getCandidates(query: MemoryQuery): List<Memory> {
        val candidates = mutableSetOf<Memory>()

        // 方式1：语义搜索（Chroma）
        if (query.semantic != null) {
            val semanticResults = searchBySemantic(query.semantic, query.semanticThreshold)
            candidates.addAll(semanticResults)
        }

        // 方式2：分类过滤
        if (query.category != null || query.categories != null) {
            val categoryResults = searchByCategory(query)
            candidates.addAll(categoryResults)
        }

        // 方式3：实体搜索（Neo4j）
        if (query.entities != null || query.characters != null) {
            val entityResults = searchByEntities(query)
            candidates.addAll(entityResults)
        }

        // 方式4：时间范围过滤
        if (query.temporal != null) {
            val temporalResults = searchByTemporal(query.temporal)
            candidates.addAll(temporalResults)
        }

        // 方式5：标签搜索
        if (query.tags != null) {
            val tagResults = searchByTags(query.tags)
            candidates.addAll(tagResults)
        }

        // 如果没有任何条件，返回所有活跃记忆
        if (candidates.isEmpty() && query.semantic == null && query.categories == null) {
            val allActive = embeddingRepository.getAllActiveMemoryFacts()
            candidates.addAll(allActive.map { it.toMemory() })
        }

        // 应用通用过滤
        return candidates.filter { memory ->
            (query.minImportance == null || memory.importance >= query.minImportance) &&
            (query.minConfidence == null || memory.confidence >= query.minConfidence) &&
            (!query.excludeForgotten || !memory.isForgotten)
        }
    }

    /**
     * 语义搜索
     */
    private suspend fun searchBySemantic(text: String, threshold: Float): List<Memory> {
        return try {
            // 生成查询向量
            val embeddingResult = vectorEmbeddingService.generateEmbedding(
                text = text
            )

            if (embeddingResult.isFailure) {
                Timber.w("[MultiDimensionalRetriever] 生成embedding失败")
                return emptyList()
            }

            val queryVector = embeddingResult.getOrNull() ?: return emptyList()

            // 向量搜索
            val similar = embeddingRepository.searchSimilarMemoryFacts(
                queryVector = queryVector.toList(),
                topK = 50,  // 扩大候选集
                dimension = queryVector.size
            )

            // 过滤低相似度
            similar
                .filter { (similarity, _) -> similarity >= threshold }
                .map { (_, entity) -> entity.toMemory() }

        } catch (e: Exception) {
            Timber.e(e, "[MultiDimensionalRetriever] 语义搜索失败")
            emptyList()
        }
    }

    /**
     * 分类搜索
     */
    private suspend fun searchByCategory(query: MemoryQuery): List<Memory> {
        return try {
            val targetCategories = when {
                query.category != null -> listOf(query.category)
                query.categories != null -> query.categories
                else -> return emptyList()
            }

            val results = mutableListOf<Memory>()
            for (category in targetCategories) {
                val entities = embeddingRepository.getMemoryFactsByCategory(
                    category.name.lowercase()
                )
                results.addAll(entities.map { it.toMemory() })
            }

            results
        } catch (e: Exception) {
            Timber.e(e, "[MultiDimensionalRetriever] 分类搜索失败")
            emptyList()
        }
    }

    /**
     * 实体搜索（Neo4j）
     */
    private suspend fun searchByEntities(query: MemoryQuery): List<Memory> {
        return try {
            val entities = (query.entities ?: emptyList()) +
                    (query.characters ?: emptyList())

            if (entities.isEmpty()) return emptyList()

            // 使用Neo4j查询提及这些实体的记忆
            val cypher = """
                MATCH (m:Memory)-[:MENTIONS]->(e:Entity)
                WHERE e.name IN ${'$'}entities
                RETURN m.id AS memoryId
                ORDER BY m.importance DESC
                LIMIT 50
            """.trimIndent()

            val result = neo4jClient.executeQuery(
                cypher = cypher,
                parameters = mapOf("entities" to entities)
            )

            if (result.isFailure) {
                Timber.w("[MultiDimensionalRetriever] Neo4j查询失败")
                return emptyList()
            }

            // 根据ID从数据库获取完整记忆
            val queryResult = result.getOrNull() ?: return emptyList()
            val memoryIds = queryResult.data.mapNotNull { resultData ->
                val row = resultData.row ?: return@mapNotNull null
                // row[0] 应该是 memoryId (根据列索引)
                val columnIndex = queryResult.columns.indexOf("memoryId")
                if (columnIndex >= 0 && columnIndex < row.size) {
                    row[columnIndex] as? String
                } else null
            }

            val results = mutableListOf<Memory>()
            for (id in memoryIds) {
                val idLong = id.toLongOrNull()
                if (idLong != null) {
                    val memory = embeddingRepository.getMemoryFactById(idLong)?.toMemory()
                    if (memory != null) {
                        results.add(memory)
                    }
                }
            }
            results

        } catch (e: Exception) {
            Timber.w(e, "[MultiDimensionalRetriever] 实体搜索失败（非致命错误）")
            emptyList()
        }
    }

    /**
     * 时间搜索
     */
    private suspend fun searchByTemporal(temporal: TemporalQuery): List<Memory> {
        return try {
            val now = System.currentTimeMillis()

            val (from, to) = when (temporal) {
                is TemporalQuery.RecentHours -> {
                    val start = now - temporal.hours * 3600000L
                    start to now
                }
                is TemporalQuery.RecentDays -> {
                    val start = now - temporal.days * 86400000L
                    start to now
                }
                is TemporalQuery.DaysAgo -> {
                    val targetDay = now - temporal.days * 86400000L
                    val start = targetDay - 86400000L / 2  // ±12小时
                    val end = targetDay + 86400000L / 2
                    start to end
                }
                is TemporalQuery.Range -> {
                    temporal.from to temporal.to
                }
                is TemporalQuery.SpecificDate -> {
                    val date = temporal.date
                    val startOfDay = date.atStartOfDay().toEpochSecond(
                        java.time.ZoneOffset.systemDefault().rules.getOffset(
                            date.atStartOfDay()
                        )
                    ) * 1000
                    val endOfDay = startOfDay + 86400000L
                    startOfDay to endOfDay
                }
                is TemporalQuery.AnniversaryMatch -> {
                    // 纪念日搜索：查找内容中包含"月-日"的记忆
                    return searchAnniversaries(temporal.monthDay)
                }
            }

            // 从数据库查询时间范围内的记忆
            embeddingRepository.getAllActiveMemoryFacts()
                .filter { it.createdAt in from..to }
                .map { it.toMemory() }

        } catch (e: Exception) {
            Timber.e(e, "[MultiDimensionalRetriever] 时间搜索失败")
            emptyList()
        }
    }

    /**
     * 纪念日搜索
     */
    private suspend fun searchAnniversaries(monthDay: String): List<Memory> {
        return try {
            val results = embeddingRepository.getMemoryFactsByCategory("anniversary")
            results.filter { it.content.contains(monthDay) || it.content.contains(monthDay.replace("-", "月") + "日") }
                .map { it.toMemory() }
        } catch (e: Exception) {
            Timber.e(e, "[MultiDimensionalRetriever] 纪念日搜索失败")
            emptyList()
        }
    }

    /**
     * 标签搜索
     */
    private suspend fun searchByTags(tags: List<String>): List<Memory> {
        // 目前MemoryFactEntity没有tags字段，返回空
        // 未来如果添加tags字段，在这里实现
        return emptyList()
    }

    // ==================== 多维打分 ====================

    /**
     * 对单个记忆进行多维打分
     */
    private suspend fun scoreMemory(memory: Memory, query: MemoryQuery): ScoreBreakdown {
        val semanticScore = scoreSemanticDimension(memory, query)
        val recencyScore = scoreTemporalDimension(memory, query)
        val importance = scoreImportanceDimension(memory)
        val emotionScore = scoreEmotionalDimension(memory, query)
        val entityScore = scoreEntityDimension(memory, query)
        val intentScore = scoreIntentDimension(memory, query)

        return ScoreBreakdown(
            semanticScore = semanticScore,
            recencyScore = recencyScore,
            importance = importance,
            emotionScore = emotionScore,
            entityScore = entityScore,
            intentScore = intentScore
        )
    }

    /**
     * 计算总分
     */
    private fun calculateTotalScore(breakdown: ScoreBreakdown): Float {
        return weights["semantic"]!! * breakdown.semanticScore +
                weights["temporal"]!! * breakdown.recencyScore +
                weights["importance"]!! * breakdown.importance +
                weights["emotional"]!! * breakdown.emotionScore +
                weights["entity"]!! * breakdown.entityScore +
                weights["intent"]!! * breakdown.intentScore
    }

    // ==================== 各维度打分函数 ====================

    /**
     * 语义维度打分
     */
    private fun scoreSemanticDimension(memory: Memory, query: MemoryQuery): Float {
        // 如果没有语义查询，返回中等分数
        if (query.semantic == null) return 0.5f

        // 简单的关键词匹配（实际已通过Chroma相似度过滤）
        val keywords = query.semantic.split("\\s+".toRegex())
        val matchCount = keywords.count { keyword ->
            memory.content.contains(keyword, ignoreCase = true)
        }

        return if (keywords.isNotEmpty()) {
            (matchCount.toFloat() / keywords.size).coerceIn(0f, 1f)
        } else 0.5f
    }

    /**
     * 时间维度打分（新近度）
     */
    private fun scoreTemporalDimension(memory: Memory, query: MemoryQuery): Float {
        val now = System.currentTimeMillis()
        val daysSinceAccess = (now - memory.lastAccessedAt) / 86400000f

        // 使用指数衰减，半衰期30天
        val recencyScore = exp(-daysSinceAccess / 30f).toFloat()

        // 如果有时间查询条件，加权
        return if (query.temporal != null) {
            val inRange = isInTemporalRange(memory, query.temporal, now)
            if (inRange) {
                min(recencyScore * 1.5f, 1f)  // 在范围内加成
            } else {
                recencyScore * 0.5f  // 不在范围内降权
            }
        } else {
            recencyScore
        }
    }

    /**
     * 重要性维度打分
     */
    private fun scoreImportanceDimension(memory: Memory): Float {
        // 综合重要性和置信度
        return (memory.importance * 0.7f + memory.confidence * 0.3f).coerceIn(0f, 1f)
    }

    /**
     * 情感维度打分
     */
    private fun scoreEmotionalDimension(memory: Memory, query: MemoryQuery): Float {
        val emotionQuery = query.emotion ?: return 0.5f

        return when (emotionQuery) {
            is EmotionQuery.AnyPositive -> {
                if (memory.emotionalValence > 0.3f) memory.emotionIntensity else 0.2f
            }
            is EmotionQuery.AnyNegative -> {
                if (memory.emotionalValence < -0.3f) memory.emotionIntensity else 0.2f
            }
            is EmotionQuery.IntenseRange -> {
                if (memory.emotionIntensity in emotionQuery.range) 1.0f else 0.3f
            }
            is EmotionQuery.SpecificEmotion -> {
                if (memory.emotionTag != null && memory.emotionTag in emotionQuery.tags) {
                    memory.emotionIntensity
                } else 0.2f
            }
            is EmotionQuery.ValenceRange -> {
                if (memory.emotionalValence in emotionQuery.range) {
                    memory.emotionIntensity
                } else 0.2f
            }
        }
    }

    /**
     * 实体维度打分
     */
    private fun scoreEntityDimension(memory: Memory, query: MemoryQuery): Float {
        val queryEntities = (query.entities ?: emptyList()) +
                (query.characters ?: emptyList())

        if (queryEntities.isEmpty()) return 0.5f

        // 计算实体匹配度
        val matchCount = queryEntities.count { entity ->
            memory.relatedEntities.contains(entity) ||
            memory.relatedCharacters.contains(entity) ||
            memory.content.contains(entity, ignoreCase = true)
        }

        return (matchCount.toFloat() / queryEntities.size).coerceIn(0f, 1f)
    }

    /**
     * 意图维度打分
     */
    private fun scoreIntentDimension(memory: Memory, query: MemoryQuery): Float {
        val queryIntent = query.intent ?: return 0.5f

        return if (memory.intent == queryIntent) 1.0f else 0.3f
    }

    // ==================== 辅助函数 ====================

    /**
     * 检查记忆是否在时间范围内
     */
    private fun isInTemporalRange(
        memory: Memory,
        temporal: TemporalQuery,
        now: Long
    ): Boolean {
        return when (temporal) {
            is TemporalQuery.RecentHours -> {
                val threshold = now - temporal.hours * 3600000L
                memory.timestamp >= threshold
            }
            is TemporalQuery.RecentDays -> {
                val threshold = now - temporal.days * 86400000L
                memory.timestamp >= threshold
            }
            is TemporalQuery.DaysAgo -> {
                val target = now - temporal.days * 86400000L
                val margin = 86400000L / 2  // ±12小时
                memory.timestamp in (target - margin)..(target + margin)
            }
            is TemporalQuery.Range -> {
                memory.timestamp in temporal.from..temporal.to
            }
            is TemporalQuery.SpecificDate -> {
                val date = temporal.date
                val startOfDay = date.atStartOfDay().toEpochSecond(
                    java.time.ZoneOffset.systemDefault().rules.getOffset(
                        date.atStartOfDay()
                    )
                ) * 1000
                val endOfDay = startOfDay + 86400000L
                memory.timestamp in startOfDay..endOfDay
            }
            is TemporalQuery.AnniversaryMatch -> {
                memory.content.contains(temporal.monthDay)
            }
        }
    }

    /**
     * 多样化采样
     */
    private fun diversifySampling(
        results: List<RankedMemory>,
        limit: Int
    ): List<RankedMemory> {
        if (results.size <= limit) return results

        val sampled = mutableListOf<RankedMemory>()
        val categorySeen = mutableSetOf<MemoryCategory>()

        // 第一轮：每个分类选一个
        for (result in results) {
            if (result.memory.category !in categorySeen) {
                sampled.add(result)
                categorySeen.add(result.memory.category)
                if (sampled.size >= limit) break
            }
        }

        // 第二轮：按分数填充剩余
        if (sampled.size < limit) {
            val remaining = results.filter { it !in sampled }
                .take(limit - sampled.size)
            sampled.addAll(remaining)
        }

        return sampled
    }
}
