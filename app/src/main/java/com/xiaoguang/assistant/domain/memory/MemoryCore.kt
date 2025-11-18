package com.xiaoguang.assistant.domain.memory

import com.xiaoguang.assistant.data.local.database.entity.MemoryFactEntity
import com.xiaoguang.assistant.domain.memory.config.MemoryStrengthConfig
import com.xiaoguang.assistant.domain.memory.models.*
import com.xiaoguang.assistant.domain.repository.EmbeddingRepository
import com.xiaoguang.assistant.domain.knowledge.vector.ChromaVectorStore
import com.xiaoguang.assistant.domain.knowledge.vector.VectorSearchOptimizer
import com.xiaoguang.assistant.domain.knowledge.graph.Neo4jClient
import com.xiaoguang.assistant.domain.knowledge.vector.VectorEmbeddingService
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min

/**
 * 记忆核心管理系统
 *
 * 职责：
 * - 管理事件记忆、知识事实
 * - 提供多维检索能力
 * - 实现认知遗忘机制
 *
 * 底层使用：
 * - Chroma向量数据库（语义搜索）
 * - Neo4j图数据库（实体关系）
 * - LSH优化索引（快速检索）
 */
@Singleton
class MemoryCore @Inject constructor(
    private val embeddingRepository: EmbeddingRepository,
    private val chromaStore: ChromaVectorStore,
    private val vectorOptimizer: VectorSearchOptimizer,
    private val neo4jClient: Neo4jClient,
    private val multiDimensionalRetriever: MultiDimensionalRetriever,
    private val vectorEmbeddingService: VectorEmbeddingService,
    private val memoryLlmService: MemoryLlmService  // ⭐ LLM服务（情感评估、意图分类等）
) {
    // ⭐ 配置：记忆强度计算参数
    private val strengthConfig = MemoryStrengthConfig.DEFAULT

    /**
     * 保存记忆
     */
    suspend fun saveMemory(memory: Memory): Result<Unit> {
        return try {
            // 1. 转换为MemoryFactEntity并保存到Room
            val entity = memory.toMemoryFactEntity()
            embeddingRepository.saveMemoryFact(entity)

            // 2. Chroma向量存储由EmbeddingRepository管理，这里不需要单独保存
            // 向量数据已经通过embeddingRepository.saveMemoryFact保存

            // 3. 如果有实体，保存关系到Neo4j
            if (memory.relatedEntities.isNotEmpty()) {
                saveEntitiesToNeo4j(memory)
            }

            Timber.d("[MemoryCore] 保存记忆: ${memory.content.take(50)}, category=${memory.category}")
            Result.success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "[MemoryCore] 保存记忆失败")
            Result.failure(e)
        }
    }

    /**
     * 查询记忆（多维检索）
     */
    suspend fun queryMemories(query: MemoryQuery): List<RankedMemory> {
        return try {
            multiDimensionalRetriever.retrieve(query)
        } catch (e: Exception) {
            Timber.e(e, "[MemoryCore] 查询记忆失败")
            emptyList()
        }
    }

    /**
     * 根据ID获取记忆
     */
    suspend fun getMemoryById(id: String): Memory? {
        return try {
            val idLong = if (id.startsWith("migrated_")) {
                id.removePrefix("migrated_").toLongOrNull()
            } else {
                id.toLongOrNull()
            }

            if (idLong == null) return null

            val entity = embeddingRepository.getMemoryFactById(idLong)
            entity?.toMemory()
        } catch (e: Exception) {
            Timber.e(e, "[MemoryCore] 获取记忆失败: $id")
            null
        }
    }

    /**
     * 强化记忆（访问时自动调用）
     */
    suspend fun reinforceMemory(id: String): Result<Unit> {
        return try {
            val idLong = id.toLongOrNull() ?: return Result.failure(
                IllegalArgumentException("Invalid memory ID: $id")
            )

            embeddingRepository.reinforceMemoryFact(
                id = idLong,
                reinforcedAt = System.currentTimeMillis()
            )

            Timber.d("[MemoryCore] 强化记忆: $id")
            Result.success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "[MemoryCore] 强化记忆失败")
            Result.failure(e)
        }
    }

    /**
     * 标记为遗忘
     */
    suspend fun forgetMemory(id: String): Result<Unit> {
        return try {
            val idLong = id.toLongOrNull() ?: return Result.failure(
                IllegalArgumentException("Invalid memory ID: $id")
            )

            embeddingRepository.markMemoryFactAsForgotten(
                id = idLong,
                forgottenAt = System.currentTimeMillis()
            )

            Timber.d("[MemoryCore] 遗忘记忆: $id")
            Result.success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "[MemoryCore] 标记遗忘失败")
            Result.failure(e)
        }
    }

    /**
     * 计算记忆强度（增强版Ebbinghaus）
     *
     * ⭐ 使用配置化的权重系数
     */
    fun calculateMemoryStrength(memory: Memory, now: Long = System.currentTimeMillis()): Float {
        val daysPassed = (now - memory.lastAccessedAt) / strengthConfig.millisecondsPerDay.toFloat()

        // ⭐ 基础强度（基于强化次数） - 使用配置
        val baseStrength = ln(1f + memory.reinforcementCount.toFloat()) * strengthConfig.baseStrengthMultiplier

        // ⭐ 情感强化因子（高情感事件更持久） - 使用配置
        val emotionBonus = memory.emotionIntensity * strengthConfig.emotionBonusMultiplier

        // ⭐ 重要性加权 - 使用配置
        val importanceBonus = memory.importance * strengthConfig.importanceBonusMultiplier

        // ⭐ 回忆难度惩罚（难回忆的更易忘） - 使用配置
        val difficultyPenalty = memory.recallDifficulty * strengthConfig.difficultyPenaltyMultiplier

        // ⭐ 上下文相关性加成 - 使用配置
        val contextBonus = memory.contextRelevance * strengthConfig.contextBonusMultiplier

        // 综合强度
        val effectiveStrength = baseStrength + emotionBonus + importanceBonus +
                contextBonus - difficultyPenalty

        // Ebbinghaus遗忘曲线: R = e^(-t/S)
        val retention = exp(-daysPassed / effectiveStrength.coerceAtLeast(strengthConfig.minEffectiveStrength)).toFloat()

        // ⭐ 考虑置信度 - 使用配置
        val adjustedRetention = retention * (strengthConfig.confidenceBaseWeight + memory.confidence * strengthConfig.confidenceMultiplier)

        return adjustedRetention.coerceIn(0f, 1f)
    }

    /**
     * 估算回忆难度
     */
    fun estimateRecallDifficulty(content: String): Float {
        // 内容长度因子（越长越难回忆）
        val lengthFactor = min(content.length / 500f, 1f)

        // 数字密度（数字多的内容更难记忆）
        val digitRatio = if (content.isNotEmpty()) {
            content.count { it.isDigit() } / content.length.toFloat()
        } else 0f

        // 复杂词汇密度（估算：长单词占比）
        val complexWordRatio = if (content.isNotEmpty()) {
            content.split("\\s+".toRegex())
                .count { it.length > 8 } / content.split("\\s+".toRegex()).size.toFloat()
        } else 0f

        return (lengthFactor * 0.5f + digitRatio * 0.3f + complexWordRatio * 0.2f)
            .coerceIn(0f, 1f)
    }

    /**
     * 获取统计信息
     */
    suspend fun getStatistics(): MemoryStatistics {
        return try {
            val all = embeddingRepository.getAllActiveMemoryFacts()
            val now = System.currentTimeMillis()

            val byCategory = all.groupBy {
                mapCategoryFromString(it.category)
            }.mapValues { it.value.size }

            val avgImportance = if (all.isNotEmpty()) {
                all.map { it.importance }.average().toFloat()
            } else 0f

            val avgReinforcement = if (all.isNotEmpty()) {
                all.map { it.reinforcementCount }.average().toFloat()
            } else 0f

            val forgottenCount = embeddingRepository.getAllForgottenMemoryFacts().size

            // ⭐ 使用配置的阈值
            val strongMemories = all.count { entity ->
                val memory = entity.toMemory()
                calculateMemoryStrength(memory, now) > strengthConfig.strongMemoryThreshold
            }

            val weakMemories = all.count { entity ->
                val memory = entity.toMemory()
                calculateMemoryStrength(memory, now) < strengthConfig.weakMemoryThreshold
            }

            val recentlyAccessed = all.count { entity ->
                val daysSinceAccess = (now - entity.lastAccessedAt) / 86400000
                daysSinceAccess < 7
            }

            MemoryStatistics(
                totalMemories = all.size,
                byCategory = byCategory,
                avgImportance = avgImportance,
                avgReinforcement = avgReinforcement,
                forgottenCount = forgottenCount,
                strongMemories = strongMemories,
                weakMemories = weakMemories,
                recentlyAccessed = recentlyAccessed
            )

        } catch (e: Exception) {
            Timber.e(e, "[MemoryCore] 获取统计失败")
            MemoryStatistics(0, emptyMap(), 0f, 0f, 0, 0, 0, 0)
        }
    }

    /**
     * 获取记忆总数
     */
    suspend fun getMemoryCount(): Int {
        return try {
            embeddingRepository.getAllActiveMemoryFacts().size
        } catch (e: Exception) {
            Timber.e(e, "[MemoryCore] 获取记忆总数失败")
            0
        }
    }

    /**
     * 通过内容搜索记忆（用于迁移验证）
     *
     * @param content 搜索内容
     * @param limit 返回数量
     * @param minSimilarity 最小相似度
     * @return 匹配的记忆列表
     */
    suspend fun searchByContent(
        content: String,
        limit: Int = 10,
        minSimilarity: Float = 0.8f
    ): Result<List<RankedMemory>> {
        return try {
            // 生成查询向量
            val queryEmbedding = vectorEmbeddingService.generateEmbedding(content)
                .getOrNull() ?: return Result.success(emptyList())

            // 向量搜索（使用语义搜索）
            val candidates = embeddingRepository.searchSimilarMemoryFacts(
                queryVector = queryEmbedding.toList(),
                topK = limit,
                dimension = queryEmbedding.size
            )

            // 过滤相似度
            val results = candidates
                .filter { (similarity, _) -> similarity >= minSimilarity }
                .map { (similarity, entity) ->
                    val memory = entity.toMemory()
                    val scoreBreakdown = ScoreBreakdown(
                        semanticScore = similarity,
                        recencyScore = 0f,
                        importance = memory.importance,
                        emotionScore = memory.emotionIntensity,
                        entityScore = 0f,
                        intentScore = 0f
                    )

                    RankedMemory(
                        memory = memory,
                        score = similarity,
                        scoreBreakdown = scoreBreakdown
                    )
                }

            Result.success(results)

        } catch (e: Exception) {
            Timber.e(e, "[MemoryCore] 内容搜索失败")
            Result.failure(e)
        }
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 保存实体关系到Neo4j
     */
    private suspend fun saveEntitiesToNeo4j(memory: Memory) {
        try {
            // 为每个实体创建节点
            memory.relatedEntities.forEach { entity ->
                val createNodeCypher = """
                    MERGE (e:Entity {name: ${'$'}entityName})
                    SET e.type = 'mentioned',
                        e.updatedAt = timestamp()
                """.trimIndent()

                neo4jClient.executeQuery(
                    cypher = createNodeCypher,
                    parameters = mapOf("entityName" to entity)
                )
            }

            // 创建记忆节点并建立关系
            val createMemoryCypher = """
                MERGE (m:Memory {id: ${'$'}memoryId})
                SET m.content = ${'$'}content,
                    m.category = ${'$'}category,
                    m.importance = ${'$'}importance,
                    m.timestamp = ${'$'}timestamp
                WITH m
                UNWIND ${'$'}entities AS entityName
                MATCH (e:Entity {name: entityName})
                MERGE (m)-[r:MENTIONS]->(e)
                SET r.createdAt = timestamp()
            """.trimIndent()

            neo4jClient.executeQuery(
                cypher = createMemoryCypher,
                parameters = mapOf(
                    "memoryId" to memory.id,
                    "content" to memory.content.take(200), // 限制长度
                    "category" to memory.category.name,
                    "importance" to memory.importance,
                    "timestamp" to memory.timestamp,
                    "entities" to memory.relatedEntities
                )
            )

            Timber.d("[MemoryCore] 保存实体关系到Neo4j: ${memory.relatedEntities.size}个实体")

        } catch (e: Exception) {
            Timber.w(e, "[MemoryCore] 保存实体到Neo4j失败（非致命错误）")
        }
    }

    /**
     * 从情感标签计算情感效价（LLM驱动，带fallback）
     *
     * ⭐ 优先使用LLM评估情感效价
     * ⚠️ LLM失败时自动降级到规则
     */
    suspend fun evaluateEmotionValenceWithLlm(emotionTag: String, context: String? = null): Float {
        // ⭐ 使用LLM评估
        val llmResult = memoryLlmService.evaluateEmotionValence(emotionTag, context)
        if (llmResult.isSuccess) {
            return llmResult.getOrNull() ?: calculateValenceFallback(emotionTag)
        }

        // ⚠️ Fallback
        return calculateValenceFallback(emotionTag)
    }

    /**
     * Fallback：简单规则计算情感效价
     */
    private fun calculateValenceFallback(emotionTag: String): Float {
        return when (emotionTag.lowercase()) {
            "happy", "joy", "excited", "proud", "love" -> 0.8f
            "calm", "peaceful", "content" -> 0.5f
            "neutral" -> 0f
            "sad", "disappointed", "worried" -> -0.5f
            "angry", "frustrated", "fearful", "disgusted" -> -0.8f
            else -> 0f
        }
    }

    // ========== v2.4 记忆重构系统 Facade ==========

    /**
     * 获取记忆（供MemoryReconstructionService使用）
     */
    suspend fun getMemory(id: String): Memory? {
        return getMemoryById(id)
    }

    /**
     * 保存记忆（供MemoryReconstructionService使用）
     */
    suspend fun storeMemory(memory: Memory) {
        saveMemory(memory)
    }

    /**
     * 获取所有记忆（供MemoryReconstructionService使用）
     */
    suspend fun getAllMemories(): List<Memory> {
        return try {
            embeddingRepository.getAllActiveMemoryFacts().map { it.toMemory() }
        } catch (e: Exception) {
            Timber.e(e, "[MemoryCore] 获取所有记忆失败")
            emptyList()
        }
    }

    /**
     * 更新记忆（供MemoryReconstructionService使用）
     */
    suspend fun updateMemory(memory: Memory): Result<Unit> {
        return saveMemory(memory)
    }

    /**
     * 删除记忆（供MemoryReconstructionService使用）
     */
    suspend fun deleteMemory(id: String): Boolean {
        return try {
            val entityId = id.toLongOrNull() ?: return false
            embeddingRepository.deleteMemoryFact(entityId)
            true
        } catch (e: Exception) {
            Timber.e(e, "[MemoryCore] 删除记忆失败: $id")
            false
        }
    }
}

// ==================== 辅助函数 ====================

/**
 * 将字符串转换为MemoryCategory枚举
 */
private fun mapCategoryFromString(categoryStr: String): MemoryCategory {
    return try {
        MemoryCategory.valueOf(categoryStr.uppercase())
    } catch (e: Exception) {
        MemoryCategory.SEMANTIC
    }
}

// ==================== 扩展函数 ====================

/**
 * Memory → MemoryFactEntity
 */
fun Memory.toMemoryFactEntity(): MemoryFactEntity {
    return MemoryFactEntity(
        id = id.toLongOrNull() ?: 0L,
        content = content,
        category = category.name.lowercase(),
        importance = importance,
        emotionalValence = emotionalValence,
        tags = tags.joinToString(","),
        relatedEntities = relatedEntities.joinToString(","),
        relatedCharacters = relatedCharacters.joinToString(","),
        location = null, // Memory模型中没有location字段
        createdAt = createdAt,
        lastAccessedAt = lastAccessedAt,
        accessCount = accessCount,
        reinforcementCount = reinforcementCount,
        confidence = confidence,
        isActive = !isForgotten,
        isForgotten = isForgotten,
        forgottenAt = null, // Memory模型中没有forgottenAt字段
        sourceType = source ?: "unknown",
        metadata = "" // TODO: 可以将其他字段序列化到metadata
    )
}

/**
 * MemoryFactEntity → Memory
 */
fun MemoryFactEntity.toMemory(): Memory {
    return Memory(
        id = id.toString(),
        content = content,
        category = MemoryCategory.valueOf(category.uppercase()),
        importance = importance,
        confidence = confidence,
        emotionTag = null, // MemoryFactEntity中没有单独的emotionTag字段
        emotionIntensity = 0f,
        emotionalValence = emotionalValence,
        timestamp = createdAt,
        createdAt = createdAt,
        lastAccessedAt = lastAccessedAt,
        reinforcementCount = reinforcementCount,
        accessCount = accessCount,
        relatedEntities = if (relatedEntities.isNotEmpty()) {
            relatedEntities.split(",").map { it.trim() }
        } else emptyList(),
        relatedCharacters = if (relatedCharacters.isNotEmpty()) {
            relatedCharacters.split(",").map { it.trim() }
        } else emptyList(),
        recallDifficulty = 0.5f,
        contextRelevance = 0.5f,
        intent = IntentType.UNKNOWN,
        strength = null,
        similarity = 0f,
        tags = if (tags.isNotEmpty()) {
            tags.split(",").map { it.trim() }
        } else emptyList(),
        source = sourceType,
        isForgotten = isForgotten,
        expiresAt = null
    )
}
