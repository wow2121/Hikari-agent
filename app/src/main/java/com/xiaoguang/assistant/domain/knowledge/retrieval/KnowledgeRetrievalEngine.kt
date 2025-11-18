package com.xiaoguang.assistant.domain.knowledge.retrieval

import com.xiaoguang.assistant.domain.knowledge.CharacterBook
import com.xiaoguang.assistant.domain.knowledge.WorldBook
import com.xiaoguang.assistant.domain.knowledge.models.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * 知识检索引擎
 *
 * 混合检索策略：
 * 1. 关键词触发（Keyword Triggering）- Lorebook机制
 * 2. 语义搜索（Semantic Search）- 向量相似度
 * 3. 图检索（Graph Retrieval）- 关系网络
 *
 * 目标：为对话提供最相关的上下文信息
 */
@Singleton
class KnowledgeRetrievalEngine @Inject constructor(
    private val worldBook: WorldBook,
    private val characterBook: CharacterBook,
    private val chromaVectorStore: com.xiaoguang.assistant.domain.knowledge.vector.ChromaVectorStore,
    private val graphService: com.xiaoguang.assistant.domain.knowledge.graph.RelationshipGraphService,
    private val relationshipAggregator: com.xiaoguang.assistant.domain.relationship.RelationshipAggregator,  // ⭐ 注入关系聚合器
    private val jiebaSegmenter: com.xiaoguang.assistant.domain.nlp.segmentation.JiebaSegmenter,  // ⭐ 注入jieba分词器
    private val config: RetrievalConfig = RetrievalConfig()
) {

    /**
     * 检索知识上下文
     *
     * @param query 查询文本（通常是用户消息或对话历史）
     * @param characterIds 相关角色ID列表
     * @param maxTokens 最大token限制
     * @return 检索到的知识上下文
     */
    suspend fun retrieveContext(
        query: String,
        characterIds: List<String> = emptyList(),
        maxTokens: Int = config.maxTotalTokens
    ): RetrievedContext {
        try {
            Timber.d("[KnowledgeRetrieval] 开始检索: query=$query, characters=${characterIds.size}")

            // 1. 关键词触发 - World Book
            val worldContext = retrieveWorldContext(query, maxTokens / 4)

            // 2. 角色上下文检索
            val characterContexts = retrieveCharacterContexts(query, characterIds, maxTokens / 4)

            // 3. 记忆检索（多模态：关键词 + 语义）
            val relevantMemories = retrieveRelevantMemories(query, characterIds, maxTokens / 4)

            // 4. ⭐ 第三方关系网络检索（新增）
            val relationshipInfo = retrieveRelationshipNetwork(query, characterIds, maxTokens / 4)

            // 5. 组装最终上下文
            val finalContext = assembleContext(
                worldContext = worldContext,
                characterContexts = characterContexts,
                memories = relevantMemories,
                relationshipInfo = relationshipInfo,
                maxTokens = maxTokens
            )

            Timber.d("[KnowledgeRetrieval] 检索完成: " +
                    "worldEntries=${worldContext.triggeredEntries.size}, " +
                    "characters=${characterContexts.size}, " +
                    "memories=${relevantMemories.size}, " +
                    "relationships=${relationshipInfo.size}")

            return finalContext

        } catch (e: Exception) {
            Timber.e(e, "[KnowledgeRetrieval] 检索失败")
            return RetrievedContext.empty()
        }
    }

    /**
     * 检索World Book上下文
     * 使用关键词触发机制（Lorebook核心）
     */
    private suspend fun retrieveWorldContext(
        query: String,
        maxTokens: Int
    ): WorldContext {
        return try {
            worldBook.buildWorldContext(
                query = query,
                includeScene = true,
                maxTokens = maxTokens
            )
        } catch (e: Exception) {
            Timber.e(e, "[KnowledgeRetrieval] World检索失败")
            WorldContext(emptyList(), "", "", "")
        }
    }

    /**
     * 检索角色上下文
     * 为每个相关角色构建上下文
     */
    private suspend fun retrieveCharacterContexts(
        query: String,
        characterIds: List<String>,
        maxTokens: Int
    ): List<CharacterContext> {
        if (characterIds.isEmpty()) {
            return emptyList()
        }

        return try {
            val tokensPerCharacter = maxTokens / characterIds.size

            characterIds.mapNotNull { characterId ->
                characterBook.buildCharacterContext(
                    characterId = characterId,
                    query = query,
                    maxTokens = tokensPerCharacter
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "[KnowledgeRetrieval] Character检索失败")
            emptyList()
        }
    }

    /**
     * ⭐ 检索第三方关系网络（新增）
     * 提取query中提到的人物及其关系
     */
    private suspend fun retrieveRelationshipNetwork(
        query: String,
        characterIds: List<String>,
        maxTokens: Int
    ): List<RelationshipInfo> {
        if (!config.enableRelationshipRetrieval) {
            return emptyList()
        }

        return try {
            val relationshipInfoList = mutableListOf<RelationshipInfo>()

            // 1. 提取query中提到的人名（使用jieba的NER）
            val mentionedPeople = extractPersonNames(query)

            // 2. 从CharacterBook中获取人名
            val knownPeople = characterIds.mapNotNull { characterId ->
                characterBook.getProfile(characterId)?.basicInfo?.name
            }

            // 3. 合并所有人名
            val allPeople = (mentionedPeople + knownPeople).distinct()

            // 4. 为每个人物检索关系网络
            for (personName in allPeople.take(5)) {  // 限制最多5个人物，避免检索过多
                try {
                    // 使用RelationshipAggregator获取完整关系视图
                    val completeView = relationshipAggregator.getCompleteRelationships(personName)

                    if (completeView.networkRelations.isNotEmpty()) {
                        // 构建关系描述
                        val relationDesc = buildString {
                            append("$personName 的人际关系：")
                            completeView.networkRelations.take(5).forEach { relation ->
                                val otherPerson = if (relation.personA == personName) relation.personB else relation.personA
                                append("与${otherPerson}是${relation.relationType}（置信度${String.format("%.1f", relation.confidence * 100)}%）")
                                if (relation.description.isNotBlank()) {
                                    append(" - ${relation.description.take(50)}")
                                }
                                append("；")
                            }
                        }

                        relationshipInfoList.add(
                            RelationshipInfo(
                                personName = personName,
                                description = relationDesc,
                                relationCount = completeView.networkRelations.size
                            )
                        )
                    }
                } catch (e: Exception) {
                    Timber.w(e, "[KnowledgeRetrieval] 检索${personName}的关系失败")
                }
            }

            relationshipInfoList

        } catch (e: Exception) {
            Timber.e(e, "[KnowledgeRetrieval] 关系网络检索失败")
            emptyList()
        }
    }

    /**
     * 提取人名（简化版，使用jieba分词）
     */
    private fun extractPersonNames(text: String): List<String> {
        // 使用jieba提取关键词，然后过滤出可能是人名的词
        val keywords = jiebaSegmenter.extractKeywords(text, topK = 20)
        // 简化实现：假设2-4个字的词可能是人名
        return keywords.filter { it.length in 2..4 }
    }

    /**
     * 检索相关记忆
     * 混合策略：关键词搜索 + 语义相似度
     */
    private suspend fun retrieveRelevantMemories(
        query: String,
        characterIds: List<String>,
        maxTokens: Int
    ): List<RetrievedMemory> {
        if (characterIds.isEmpty()) {
            return emptyList()
        }

        return try {
            val allMemories = mutableListOf<RetrievedMemory>()

            for (characterId in characterIds) {
                // 1. 关键词搜索
                val keywordMatches = characterBook.searchMemories(characterId, query)
                    .map { memory ->
                        RetrievedMemory(
                            memory = memory,
                            relevanceScore = calculateKeywordRelevance(query, memory),
                            retrievalMethod = RetrievalMethod.KEYWORD
                        )
                    }

                allMemories.addAll(keywordMatches)

                // 2. 语义搜索（如果启用）
                if (config.enableSemanticSearch) {
                    val semanticMatches = searchSemanticMemories(characterId, query)
                    allMemories.addAll(semanticMatches)
                }
            }

            // 3. 按相关性排序并限制token
            allMemories.sortedByDescending { it.relevanceScore }
                .take(config.maxMemoriesPerRetrieval)
                .filter { estimateTokens(it.memory.content) <= maxTokens }

        } catch (e: Exception) {
            Timber.e(e, "[KnowledgeRetrieval] Memory检索失败")
            emptyList()
        }
    }

    /**
     * 组装最终上下文
     * 智能合并World、Character、Memory、Relationship信息
     */
    private fun assembleContext(
        worldContext: WorldContext,
        characterContexts: List<CharacterContext>,
        memories: List<RetrievedMemory>,
        relationshipInfo: List<RelationshipInfo>,
        maxTokens: Int
    ): RetrievedContext {
        val builder = StringBuilder()
        var currentTokens = 0

        // 1. World Book信息（优先级最高）
        if (worldContext.formattedContext.isNotEmpty()) {
            builder.appendLine("【世界设定】")
            builder.appendLine(worldContext.formattedContext)
            builder.appendLine()
            currentTokens += estimateTokens(worldContext.formattedContext)
        }

        // 2. 角色信息
        for (charContext in characterContexts) {
            if (charContext.formattedContext.isNotEmpty()) {
                val contextTokens = estimateTokens(charContext.formattedContext)
                if (currentTokens + contextTokens > maxTokens) break

                builder.appendLine(charContext.formattedContext)
                builder.appendLine()
                currentTokens += contextTokens
            }
        }

        // 3. ⭐ 第三方关系网络信息（新增）
        if (relationshipInfo.isNotEmpty()) {
            builder.appendLine("【人际关系网络】")
            for (relInfo in relationshipInfo) {
                val relTokens = estimateTokens(relInfo.description)
                if (currentTokens + relTokens > maxTokens) break

                builder.appendLine("- ${relInfo.description}")
                currentTokens += relTokens
            }
            builder.appendLine()
        }

        // 4. 相关记忆（按相关性）
        if (memories.isNotEmpty()) {
            builder.appendLine("【相关记忆】")
            for (retrievedMemory in memories) {
                val memoryTokens = estimateTokens(retrievedMemory.memory.content)
                if (currentTokens + memoryTokens > maxTokens) break

                builder.appendLine("- ${retrievedMemory.memory.content}")
                currentTokens += memoryTokens
            }
        }

        return RetrievedContext(
            worldContext = worldContext,
            characterContexts = characterContexts,
            memories = memories,
            relationshipInfo = relationshipInfo,  // ⭐ 新增字段
            formattedContext = builder.toString(),
            totalTokens = currentTokens
        )
    }

    // ==================== Helper Methods ====================

    /**
     * 计算关键词相关性分数
     * 使用jieba分词进行高质量的关键词提取
     */
    private fun calculateKeywordRelevance(query: String, memory: CharacterMemory): Float {
        // 使用jieba提取关键词（准确率81%+，远超之前的手工实现）
        val queryWords = jiebaSegmenter.extractKeywords(query, topK = 20)
        val memoryText = memory.content.lowercase()

        var matchCount = 0
        var totalWeight = 0f

        for (word in queryWords) {
            if (memoryText.contains(word.lowercase())) {
                matchCount++
                // 早期出现的词权重更高
                val position = memoryText.indexOf(word.lowercase())
                val positionWeight = 1f - (position.toFloat() / memoryText.length) * 0.3f
                totalWeight += positionWeight
            }
        }

        if (queryWords.isEmpty()) return 0f

        // 结合匹配比例、记忆重要性、记忆强度
        val matchRatio = matchCount.toFloat() / queryWords.size
        val relevance = matchRatio * 0.4f +
                memory.importance * 0.3f +
                memory.getMemoryStrength() * 0.3f

        return min(relevance * totalWeight, 1f)
    }

    /**
     * 提取关键词
     * 使用jieba分词（准确率81%+）
     */
    private fun extractKeywords(text: String): List<String> {
        return jiebaSegmenter.extractKeywords(text, topK = 20)
    }

    /**
     * 估算token数量
     * 中文约2个字符=1个token，英文约4个字符=1个token
     */
    private fun estimateTokens(text: String): Int {
        val chineseCount = text.count { it.code > 0x4E00 && it.code < 0x9FA5 }
        val otherCount = text.length - chineseCount
        return (chineseCount / 2) + (otherCount / 4)
    }

    // ==================== Advanced Retrieval (Future) ====================

    /**
     * 语义搜索（向量相似度）
     * 使用Chroma向量数据库进行语义相似度检索
     */
    private suspend fun searchSemanticMemories(
        characterId: String,
        query: String,
        topK: Int = 10
    ): List<RetrievedMemory> {
        return try {
            val result = chromaVectorStore.searchCharacterMemories(
                query = query,
                characterId = characterId,
                nResults = topK
            )

            if (result.isSuccess) {
                val searchResults = result.getOrThrow()

                // 将向量搜索结果转换为RetrievedMemory
                searchResults.mapNotNull { vectorResult ->
                    // 从数据库获取完整的记忆对象
                    val memory = characterBook.getMemories(characterId)
                        .find { it.memoryId == vectorResult.id }

                    memory?.let {
                        RetrievedMemory(
                            memory = it,
                            relevanceScore = vectorResult.similarity,
                            retrievalMethod = RetrievalMethod.SEMANTIC
                        )
                    }
                }
            } else {
                Timber.w("[KnowledgeRetrieval] 语义搜索失败: ${result.exceptionOrNull()?.message}")
                emptyList()
            }
        } catch (e: Exception) {
            Timber.e(e, "[KnowledgeRetrieval] 语义搜索异常")
            emptyList()
        }
    }

    /**
     * 图检索（关系网络）
     * 使用Neo4j查询角色关系网络
     */
    private suspend fun searchGraphRelations(
        characterIds: List<String>,
        relationTypes: List<RelationType>
    ): List<Relationship> {
        if (!config.enableGraphRetrieval || characterIds.isEmpty()) {
            return emptyList()
        }

        return try {
            val allRelationships = mutableListOf<Relationship>()

            for (characterId in characterIds) {
                val result = graphService.getCharacterRelationships(characterId, maxDepth = 2)

                if (result.isSuccess) {
                    val graphRels = result.getOrThrow()

                    // 转换GraphRelationship为Relationship
                    graphRels.forEach { graphRel ->
                        // 从图数据库属性中提取关系信息
                        val props = graphRel.properties
                        val intimacy = (props["intimacy"] as? Number)?.toFloat() ?: 0.5f
                        val trust = (props["trust"] as? Number)?.toFloat() ?: 0.5f
                        val description = props["description"] as? String ?: ""

                        // 映射关系类型
                        val relType = mapNeo4jRelationType(graphRel.type)

                        allRelationships.add(
                            Relationship(
                                relationshipId = graphRel.id,
                                fromCharacterId = graphRel.fromCharacterId,
                                toCharacterId = graphRel.toCharacterId,
                                relationType = relType,
                                intimacyLevel = intimacy,
                                trustLevel = trust,
                                description = description,
                                interactionCount = 0,
                                lastInteractionAt = System.currentTimeMillis()
                            )
                        )
                    }
                }
            }

            // 过滤指定类型的关系（如果提供了类型列表）
            if (relationTypes.isNotEmpty()) {
                allRelationships.filter { it.type in relationTypes }
            } else {
                allRelationships
            }

        } catch (e: Exception) {
            Timber.e(e, "[KnowledgeRetrieval] 图检索失败")
            emptyList()
        }
    }

    /**
     * 查找角色间的关系路径
     * 用于发现隐藏的关联
     */
    suspend fun findRelationshipPath(
        fromCharacterId: String,
        toCharacterId: String,
        maxDepth: Int = 5
    ): Result<List<String>> {
        if (!config.enableGraphRetrieval) {
            return Result.failure(Exception("图检索未启用"))
        }

        return graphService.findShortestPath(fromCharacterId, toCharacterId, maxDepth)
    }

    /**
     * 查找相似角色
     * 基于关系网络的相似度
     */
    suspend fun findSimilarCharacters(
        characterId: String,
        limit: Int = 5
    ): Result<List<com.xiaoguang.assistant.domain.knowledge.graph.SimilarCharacter>> {
        if (!config.enableGraphRetrieval) {
            return Result.failure(Exception("图检索未启用"))
        }

        return graphService.findSimilarCharacters(characterId, limit)
    }

    /**
     * 将Neo4j关系类型映射回RelationType
     */
    private fun mapNeo4jRelationType(neo4jType: String): RelationType {
        return when (neo4jType) {
            com.xiaoguang.assistant.data.remote.api.Neo4jAPI.REL_FAMILY -> RelationType.FAMILY
            com.xiaoguang.assistant.data.remote.api.Neo4jAPI.REL_FRIEND -> RelationType.FRIEND
            com.xiaoguang.assistant.data.remote.api.Neo4jAPI.REL_COLLEAGUE -> RelationType.COLLEAGUE
            com.xiaoguang.assistant.data.remote.api.Neo4jAPI.REL_RIVAL -> RelationType.RIVAL
            com.xiaoguang.assistant.data.remote.api.Neo4jAPI.REL_LOVER -> RelationType.LOVER
            else -> RelationType.ACQUAINTANCE
        }
    }

    /**
     * 时序检索（时间线查询）
     * 查询特定时间段的记忆和事件
     */
    suspend fun retrieveTimelineMemories(
        characterId: String,
        startTime: Long,
        endTime: Long,
        limit: Int = 20
    ): List<CharacterMemory> {
        return try {
            characterBook.getMemories(characterId)
                .filter { it.createdAt in startTime..endTime }
                .sortedByDescending { it.createdAt }
                .take(limit)
        } catch (e: Exception) {
            Timber.e(e, "[KnowledgeRetrieval] 时序检索失败")
            emptyList()
        }
    }

    /**
     * 情感检索
     * 查询特定情感倾向的记忆
     */
    suspend fun retrieveEmotionalMemories(
        characterId: String,
        emotionalRange: ClosedFloatingPointRange<Float>,
        limit: Int = 20
    ): List<CharacterMemory> {
        return try {
            characterBook.getMemories(characterId)
                .filter { it.emotionalValence in emotionalRange }
                .sortedByDescending { it.importance }
                .take(limit)
        } catch (e: Exception) {
            Timber.e(e, "[KnowledgeRetrieval] 情感检索失败")
            emptyList()
        }
    }

    /**
     * 标签检索
     * 查询包含特定标签的记忆
     */
    suspend fun retrieveMemoriesByTags(
        characterId: String,
        tags: List<String>,
        matchAll: Boolean = false,
        limit: Int = 20
    ): List<CharacterMemory> {
        return try {
            val memories = characterBook.getMemories(characterId)

            memories.filter { memory ->
                if (matchAll) {
                    tags.all { it in memory.tags }
                } else {
                    tags.any { it in memory.tags }
                }
            }
                .sortedByDescending { it.importance }
                .take(limit)
        } catch (e: Exception) {
            Timber.e(e, "[KnowledgeRetrieval] 标签检索失败")
            emptyList()
        }
    }
}

/**
 * 检索配置
 */
data class RetrievalConfig(
    val maxTotalTokens: Int = 3000,
    val maxMemoriesPerRetrieval: Int = 20,
    val minRelevanceScore: Float = 0.3f,
    val enableSemanticSearch: Boolean = true, // ✅ 启用Chroma向量语义搜索
    val enableGraphRetrieval: Boolean = true,  // ✅ 启用Neo4j图检索
    val enableRelationshipRetrieval: Boolean = true  // ⭐ 启用第三方关系网络检索
)

/**
 * 检索到的上下文
 */
data class RetrievedContext(
    val worldContext: WorldContext,
    val characterContexts: List<CharacterContext>,
    val memories: List<RetrievedMemory>,
    val relationshipInfo: List<RelationshipInfo> = emptyList(),  // ⭐ 新增字段
    val formattedContext: String,
    val totalTokens: Int
) {
    companion object {
        fun empty() = RetrievedContext(
            worldContext = WorldContext(emptyList(), "", "", ""),
            characterContexts = emptyList(),
            memories = emptyList(),
            relationshipInfo = emptyList(),
            formattedContext = "",
            totalTokens = 0
        )
    }
}

/**
 * ⭐ 关系信息（新增）
 */
data class RelationshipInfo(
    val personName: String,
    val description: String,
    val relationCount: Int
)

/**
 * 检索到的记忆
 */
data class RetrievedMemory(
    val memory: CharacterMemory,
    val relevanceScore: Float,
    val retrievalMethod: RetrievalMethod
)

/**
 * 检索方法
 */
enum class RetrievalMethod {
    KEYWORD,      // 关键词匹配
    SEMANTIC,     // 语义相似度
    GRAPH,        // 图关系
    TEMPORAL,     // 时序
    EMOTIONAL,    // 情感
    TAG           // 标签
}

/**
 * 世界上下文
 */
data class WorldContext(
    val triggeredEntries: List<WorldEntry>,
    val sceneDescription: String,
    val rules: String,
    val background: String,
    val formattedContext: String = ""
)

/**
 * 角色上下文
 */
data class CharacterContext(
    val characterId: String,
    val characterName: String,
    val profile: CharacterProfile,
    val relevantMemories: List<CharacterMemory>,
    val relationships: List<String>,
    val formattedContext: String = ""
)
