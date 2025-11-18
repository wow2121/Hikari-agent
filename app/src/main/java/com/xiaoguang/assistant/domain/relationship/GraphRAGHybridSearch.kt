package com.xiaoguang.assistant.domain.relationship

import com.xiaoguang.assistant.data.local.database.entity.RelationshipNetworkEntity
import com.xiaoguang.assistant.domain.knowledge.graph.RelationshipGraphService
import com.xiaoguang.assistant.domain.knowledge.vector.ChromaVectorStore
import com.xiaoguang.assistant.domain.knowledge.vector.VectorSearchResult
import com.xiaoguang.assistant.domain.usecase.RelationshipNetworkManagementUseCase
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp

/**
 * GraphRAG 混合检索服务 (v2.2 - 纯Neo4j架构)
 * 结合向量搜索（ChromaDB）和图遍历（Neo4j）的优势
 *
 * v2.2架构变更：
 * - 使用RelationshipNetworkManagementUseCase代替DAO
 * - UseCase内部直接调用Neo4j，无需Room
 *
 * 工作流程：
 * 1. Vector Search（ChromaDB）- 语义相似度检索，找到候选关系
 * 2. Graph Traversal（Neo4j）- 图遍历扩展，找到关联关系
 * 3. Hybrid Ranking - 融合两种检索结果，综合排序
 *
 * 参考论文：GraphRAG: Graph-Retrieval-Augmented Generation
 */
@Singleton
class GraphRAGHybridSearch @Inject constructor(
    private val chromaVectorStore: ChromaVectorStore,
    private val neo4jGraphService: RelationshipGraphService,
    private val relationshipUseCase: RelationshipNetworkManagementUseCase
) {

    // 混合排序权重配置
    private val config = HybridSearchConfig(
        vectorWeight = 0.6f,        // 向量相似度权重
        graphCentralityWeight = 0.2f,  // 图中心性权重
        temporalRelevanceWeight = 0.2f  // 时间相关性权重
    )

    /**
     * 混合检索：语义搜索 + 图扩展
     *
     * @param query 查询文本
     * @param personContext 人物上下文（可选，用于过滤）
     * @param maxResults 最大返回结果数
     * @param expandHops 图扩展的最大跳数（0=不扩展，1=一跳，2=两跳）
     * @return 混合排序后的关系列表
     */
    suspend fun hybridSearch(
        query: String,
        personContext: String? = null,
        maxResults: Int = 10,
        expandHops: Int = 1
    ): Result<List<HybridSearchResult>> {
        return try {
            Timber.d("[GraphRAG] 混合检索开始: query=\"$query\", expandHops=$expandHops")

            // ==================== 阶段1: 向量检索 ====================
            val vectorResults = performVectorSearch(query, personContext, maxResults * 2)
                .getOrElse {
                    Timber.w(it, "[GraphRAG] 向量检索失败，仅使用图检索")
                    emptyList()
                }

            Timber.d("[GraphRAG] 向量检索返回${vectorResults.size}个候选")

            // ==================== 阶段2: 图扩展 ====================
            val expandedResults = if (expandHops > 0 && vectorResults.isNotEmpty()) {
                performGraphExpansion(vectorResults, expandHops)
                    .getOrElse {
                        Timber.w(it, "[GraphRAG] 图扩展失败，仅使用向量结果")
                        vectorResults
                    }
            } else {
                vectorResults
            }

            Timber.d("[GraphRAG] 图扩展后共${expandedResults.size}个候选")

            // ==================== 阶段3: 混合排序 ====================
            val rankedResults = performHybridRanking(expandedResults, query)
                .take(maxResults)

            Timber.i("[GraphRAG] 混合检索完成，返回${rankedResults.size}个结果")
            Result.success(rankedResults)

        } catch (e: Exception) {
            Timber.e(e, "[GraphRAG] 混合检索失败")
            Result.failure(e)
        }
    }

    /**
     * 查找某人的所有关系（GraphRAG增强版）
     *
     * 相比普通查询，此方法会：
     * 1. 使用向量检索找到语义相关的关系
     * 2. 通过图遍历找到间接关系（朋友的朋友等）
     * 3. 综合排序，突出最重要的关系
     */
    suspend fun findPersonRelationshipsEnhanced(
        personName: String,
        maxResults: Int = 20,
        includeIndirectRelations: Boolean = true
    ): Result<List<HybridSearchResult>> {
        return try {
            // 1. 直接关系（Neo4j数据库）
            val directRelations = relationshipUseCase.getPersonRelations(personName)
            Timber.d("[GraphRAG] ${personName}有${directRelations.size}个直接关系")

            // 2. 语义检索（ChromaDB）
            val semanticResults = chromaVectorStore.searchRelationshipsByPerson(personName, maxResults * 2)
                .getOrElse {
                    Timber.w(it, "[GraphRAG] 语义检索失败")
                    emptyList()
                }

            // 3. 图遍历扩展（Neo4j）
            val expandedPeople = if (includeIndirectRelations) {
                neo4jGraphService.findRelatedPeople(personName, maxDepth = 2)
                    .getOrElse { emptyList() }
            } else {
                emptyList()
            }

            // 4. 合并结果
            val allResults = mergeRelationshipResults(
                directRelations = directRelations,
                semanticResults = semanticResults,
                expandedPeople = expandedPeople,
                focusPerson = personName
            )

            // 5. 混合排序
            val rankedResults = performHybridRanking(allResults, "关于${personName}的所有关系")
                .take(maxResults)

            Timber.i("[GraphRAG] ${personName}的增强检索完成，返回${rankedResults.size}个结果")
            Result.success(rankedResults)

        } catch (e: Exception) {
            Timber.e(e, "[GraphRAG] 增强检索失败")
            Result.failure(e)
        }
    }

    /**
     * 查找关系路径（多跳查询）
     *
     * @param personA 起点人物
     * @param personB 终点人物
     * @param maxDepth 最大跳数
     * @return 所有可能的路径
     */
    suspend fun findRelationshipPaths(
        personA: String,
        personB: String,
        maxDepth: Int = 3
    ): Result<List<RelationshipPath>> {
        return try {
            // 1. 检查直接关系
            val directRelation = relationshipUseCase.getRelationBetween(personA, personB)
            if (directRelation != null && directRelation.validTo == null) {
                val path = RelationshipPath(
                    from = personA,
                    to = personB,
                    isDirect = true,
                    path = listOf(personA, personB),
                    relations = listOf(directRelation),
                    distance = 1,
                    intermediatePersons = emptyList(),
                    notFound = false,
                    errorMessage = null
                )
                return Result.success(listOf(path))
            }

            // 2. 使用Neo4j查找多跳路径
            val neo4jResult = neo4jGraphService.findShortestPathBetweenPeople(personA, personB, maxDepth)

            if (neo4jResult != null) {
                return Result.success(listOf(neo4jResult))
            }

            // 3. 使用Room + BFS查找路径（降级方案）
            val bfsPaths = findPathsWithBFS(personA, personB, maxDepth)

            if (bfsPaths.isNotEmpty()) {
                Timber.i("[GraphRAG] 通过BFS找到${bfsPaths.size}条路径")
                Result.success(bfsPaths)
            } else {
                Timber.w("[GraphRAG] 未找到${personA}和${personB}之间的路径")
                Result.success(emptyList())
            }

        } catch (e: Exception) {
            Timber.e(e, "[GraphRAG] 路径查找失败")
            Result.failure(e)
        }
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 执行向量检索
     */
    private suspend fun performVectorSearch(
        query: String,
        personContext: String?,
        limit: Int
    ): Result<List<HybridSearchResult>> {
        val where = if (personContext != null) {
            mapOf(
                "\$or" to listOf(
                    mapOf("personA" to personContext),
                    mapOf("personB" to personContext)
                )
            )
        } else {
            null
        }

        return chromaVectorStore.searchSimilarRelationships(query, limit, where)
            .map { vectorResults ->
                vectorResults.map { vr ->
                    HybridSearchResult(
                        relationshipId = vr.id,
                        personA = vr.metadata["personA"] as? String ?: "",
                        personB = vr.metadata["personB"] as? String ?: "",
                        relationType = vr.metadata["relationType"] as? String ?: "",
                        description = vr.content,
                        vectorScore = vr.similarity,
                        graphCentrality = 0f,  // 稍后计算
                        temporalRelevance = calculateTemporalRelevance(vr.metadata["timestamp"] as? Long),
                        finalScore = vr.similarity,  // 稍后重新计算
                        source = "vector"
                    )
                }
            }
    }

    /**
     * 执行图扩展
     */
    private suspend fun performGraphExpansion(
        seedResults: List<HybridSearchResult>,
        hops: Int
    ): Result<List<HybridSearchResult>> {
        return try {
            val expandedSet = seedResults.toMutableList()
            val processedPeople = mutableSetOf<String>()

            // 从种子结果中提取所有人物
            seedResults.forEach { result ->
                processedPeople.add(result.personA)
                processedPeople.add(result.personB)
            }

            // 迭代扩展
            repeat(hops) { hop ->
                val currentPeople = processedPeople.toList()
                val newRelations = mutableListOf<HybridSearchResult>()

                for (person in currentPeople) {
                    // 获取该人物的关系
                    val relations = relationshipUseCase.getPersonRelations(person)

                    relations.forEach { rel ->
                        val otherPerson = if (rel.personA == person) rel.personB else rel.personA

                        if (!processedPeople.contains(otherPerson)) {
                            processedPeople.add(otherPerson)

                            newRelations.add(
                                HybridSearchResult(
                                    relationshipId = rel.id.toString(),
                                    personA = rel.personA,
                                    personB = rel.personB,
                                    relationType = rel.relationType,
                                    description = rel.description,
                                    vectorScore = 0f,
                                    graphCentrality = calculateGraphCentrality(otherPerson),
                                    temporalRelevance = calculateTemporalRelevance(rel.validFrom),
                                    finalScore = 0f,
                                    source = "graph_expansion_hop_${hop + 1}"
                                )
                            )
                        }
                    }
                }

                expandedSet.addAll(newRelations)
                Timber.d("[GraphRAG] 第${hop + 1}跳扩展添加${newRelations.size}个关系")
            }

            Result.success(expandedSet)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 执行混合排序
     */
    private fun performHybridRanking(
        results: List<HybridSearchResult>,
        query: String
    ): List<HybridSearchResult> {
        return results.map { result ->
            // 计算综合得分
            val finalScore = config.vectorWeight * result.vectorScore +
                    config.graphCentralityWeight * result.graphCentrality +
                    config.temporalRelevanceWeight * result.temporalRelevance

            result.copy(finalScore = finalScore)
        }.sortedByDescending { it.finalScore }
    }

    /**
     * 合并多源关系结果
     */
    private fun mergeRelationshipResults(
        directRelations: List<RelationshipNetworkEntity>,
        semanticResults: List<VectorSearchResult>,
        expandedPeople: List<String>,
        focusPerson: String
    ): List<HybridSearchResult> {
        val merged = mutableListOf<HybridSearchResult>()
        val seenIds = mutableSetOf<String>()

        // 添加直接关系
        directRelations.forEach { rel ->
            val id = rel.id.toString()
            if (!seenIds.contains(id)) {
                seenIds.add(id)
                merged.add(
                    HybridSearchResult(
                        relationshipId = id,
                        personA = rel.personA,
                        personB = rel.personB,
                        relationType = rel.relationType,
                        description = rel.description,
                        vectorScore = 0.5f,
                        graphCentrality = 1.0f,  // 直接关系最高
                        temporalRelevance = calculateTemporalRelevance(rel.validFrom),
                        finalScore = 0f,
                        source = "direct"
                    )
                )
            }
        }

        // 添加语义结果
        semanticResults.forEach { vr ->
            if (!seenIds.contains(vr.id)) {
                seenIds.add(vr.id)
                merged.add(
                    HybridSearchResult(
                        relationshipId = vr.id,
                        personA = vr.metadata["personA"] as? String ?: "",
                        personB = vr.metadata["personB"] as? String ?: "",
                        relationType = vr.metadata["relationType"] as? String ?: "",
                        description = vr.content,
                        vectorScore = vr.similarity,
                        graphCentrality = 0f,
                        temporalRelevance = calculateTemporalRelevance(vr.metadata["timestamp"] as? Long),
                        finalScore = 0f,
                        source = "semantic"
                    )
                )
            }
        }

        return merged
    }

    /**
     * BFS查找路径（降级方案）
     */
    private suspend fun findPathsWithBFS(
        startPerson: String,
        endPerson: String,
        maxDepth: Int
    ): List<RelationshipPath> {
        val queue = mutableListOf<BFSNode>()
        val visited = mutableSetOf<String>()
        val paths = mutableListOf<RelationshipPath>()

        queue.add(BFSNode(startPerson, emptyList(), 0))
        visited.add(startPerson)

        while (queue.isNotEmpty()) {
            val current = queue.removeAt(0)

            if (current.person == endPerson) {
                // 找到路径
                paths.add(
                    RelationshipPath(
                        from = startPerson,
                        to = endPerson,
                        isDirect = false,
                        path = current.path.map { it.personA }.toMutableList().apply { add(endPerson) },
                        relations = current.path,
                        distance = current.depth,
                        intermediatePersons = current.path.map { it.personB }.dropLast(1),
                        notFound = false,
                        errorMessage = null
                    )
                )
                continue
            }

            if (current.depth >= maxDepth) {
                continue
            }

            // 扩展邻居
            val neighbors = relationshipUseCase.getPersonRelations(current.person)
            for (neighbor in neighbors) {
                val nextPerson = if (neighbor.personA == current.person) neighbor.personB else neighbor.personA

                if (!visited.contains(nextPerson)) {
                    visited.add(nextPerson)
                    queue.add(
                        BFSNode(
                            person = nextPerson,
                            path = current.path + neighbor,
                            depth = current.depth + 1
                        )
                    )
                }
            }
        }

        return paths
    }

    /**
     * 计算时间相关性
     * 越新的关系越相关，使用指数衰减
     */
    private fun calculateTemporalRelevance(timestamp: Long?): Float {
        if (timestamp == null) return 0.5f

        val now = System.currentTimeMillis()
        val ageInDays = (now - timestamp) / (1000 * 60 * 60 * 24)

        // 指数衰减：半衰期30天
        val halfLife = 30.0
        return exp(-ageInDays / halfLife).toFloat()
    }

    /**
     * 计算图中心性
     * 简化版：基于关系数量
     */
    private suspend fun calculateGraphCentrality(person: String): Float {
        val relationCount = relationshipUseCase.getPersonRelations(person).size
        // 归一化到0-1区间，假设100个关系为满分
        return (relationCount.toFloat() / 100f).coerceIn(0f, 1f)
    }

    /**
     * BFS节点（内部使用）
     */
    private data class BFSNode(
        val person: String,
        val path: List<RelationshipNetworkEntity>,
        val depth: Int
    )
}

/**
 * 混合检索配置
 */
data class HybridSearchConfig(
    val vectorWeight: Float = 0.6f,
    val graphCentralityWeight: Float = 0.2f,
    val temporalRelevanceWeight: Float = 0.2f
)

/**
 * 混合检索结果
 */
data class HybridSearchResult(
    val relationshipId: String,
    val personA: String,
    val personB: String,
    val relationType: String,
    val description: String,
    val vectorScore: Float,        // 向量相似度得分 0-1
    val graphCentrality: Float,    // 图中心性得分 0-1
    val temporalRelevance: Float,  // 时间相关性得分 0-1
    val finalScore: Float,         // 综合得分
    val source: String             // 来源标识（vector/graph_expansion/direct/semantic）
)
