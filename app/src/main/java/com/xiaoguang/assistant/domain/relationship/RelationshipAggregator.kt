package com.xiaoguang.assistant.domain.relationship

import com.xiaoguang.assistant.data.local.database.entity.RelationshipNetworkEntity
import com.xiaoguang.assistant.domain.knowledge.CharacterBook
import com.xiaoguang.assistant.domain.knowledge.WorldBook
import com.xiaoguang.assistant.domain.knowledge.graph.RelationshipGraphService
import com.xiaoguang.assistant.domain.knowledge.models.Relationship
import com.xiaoguang.assistant.domain.usecase.RelationshipNetworkManagementUseCase
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 2: 关系聚合器 (v2.2 - 纯Neo4j架构)
 * 统一查询CharacterBook、RelationshipNetwork、Neo4j的关系数据
 * 消除数据碎片化，提供完整的关系视图
 *
 * v2.2架构说明：
 * - relationshipNetworkUseCase: 管理第三方关系（Person节点），现直接调用Neo4j
 * - relationshipGraphService: 管理角色关系（Character节点），高级图查询
 * - 两者都使用Neo4j作为后端存储，但操作不同类型的节点
 * - 聚合器保持不变，继续提供统一的查询接口
 */
@Singleton
class RelationshipAggregator @Inject constructor(
    private val characterBook: CharacterBook,
    private val worldBook: WorldBook,
    private val relationshipNetworkUseCase: RelationshipNetworkManagementUseCase,
    private val relationshipGraphService: RelationshipGraphService
) {

    /**
     * 获取完整关系数据（聚合所有来源）
     * @param personName 人物名称
     * @return 完整的关系视图，包括：
     *  - 小光与此人的关系（来自CharacterBook）
     *  - 此人与其他人的关系（来自RelationshipNetwork）
     *  - 图数据库中的社区信息（来自Neo4j）
     */
    suspend fun getCompleteRelationships(personName: String): CompleteRelationshipView {
        Timber.d("[RelationshipAggregator] 获取完整关系: $personName")

        try {
            // 1. 从CharacterBook获取角色档案和关系
            val characterProfile = characterBook.getProfileByName(personName)
            val characterRelationships = characterProfile?.let {
                characterBook.getRelationshipsFrom(it.basicInfo.characterId)
            } ?: emptyList()

            // 2. 从RelationshipNetwork获取第三方关系
            val networkRelations = relationshipNetworkUseCase.getPersonRelations(personName)

            // 3. 从Neo4j获取图数据（社区、路径等）
            val graphData = try {
                relationshipGraphService.getPersonGraph(personName)
            } catch (e: Exception) {
                Timber.w("[RelationshipAggregator] Neo4j查询失败（可能未启用）: ${e.message}")
                null
            }

            // 4. 从WorldBook查询相关背景知识
            val worldEntries = worldBook.searchEntries(personName)

            return CompleteRelationshipView(
                personName = personName,
                characterProfile = characterProfile,
                characterRelationships = characterRelationships,
                networkRelations = networkRelations,
                graphData = graphData,
                worldKnowledge = worldEntries,
                timestamp = System.currentTimeMillis()
            )

        } catch (e: Exception) {
            Timber.e(e, "[RelationshipAggregator] 获取完整关系失败: $personName")
            return CompleteRelationshipView(
                personName = personName,
                errorMessage = e.message
            )
        }
    }

    /**
     * 查找两人之间的关系路径
     * @return 关系路径（包括直接关系和间接关系）
     */
    suspend fun getRelationshipPath(
        personA: String,
        personB: String,
        maxDepth: Int = 3
    ): RelationshipPath {
        Timber.d("[RelationshipAggregator] 查找关系路径: $personA -> $personB")

        try {
            // 1. 检查直接关系
            val directRelation = relationshipNetworkUseCase.getRelationBetween(personA, personB)

            if (directRelation != null) {
                return RelationshipPath(
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
            }

            // 2. 查找共同认识的人（2度关系）
            val commonConnections = relationshipNetworkUseCase.findCommonConnections(personA, personB)

            if (commonConnections.isNotEmpty()) {
                // 选择第一个共同好友作为中间人
                val middlePerson = commonConnections.first()
                val relationAM = relationshipNetworkUseCase.getRelationBetween(personA, middlePerson)
                val relationMB = relationshipNetworkUseCase.getRelationBetween(middlePerson, personB)

                return RelationshipPath(
                    from = personA,
                    to = personB,
                    isDirect = false,
                    path = listOf(personA, middlePerson, personB),
                    relations = listOfNotNull(relationAM, relationMB),
                    distance = 2,
                    intermediatePersons = listOf(middlePerson),
                    notFound = false,
                    errorMessage = null
                )
            }

            // 3. 尝试使用Neo4j查找更深的路径
            val graphPath = try {
                relationshipGraphService.findShortestPathBetweenPeople(personA, personB, maxDepth)
            } catch (e: Exception) {
                Timber.w("[RelationshipAggregator] Neo4j路径查询失败: ${e.message}")
                null
            }

            graphPath?.let { return it }

            // 4. 未找到关系
            return RelationshipPath(
                from = personA,
                to = personB,
                isDirect = false,
                path = emptyList(),
                relations = emptyList(),
                distance = -1,
                intermediatePersons = emptyList(),
                notFound = true,
                errorMessage = null
            )

        } catch (e: Exception) {
            Timber.e(e, "[RelationshipAggregator] 查找关系路径失败")
            return RelationshipPath(
                from = personA,
                to = personB,
                isDirect = false,
                path = emptyList(),
                relations = emptyList(),
                distance = -1,
                intermediatePersons = emptyList(),
                notFound = false,
                errorMessage = e.message
            )
        }
    }

    /**
     * 获取关系图谱（某个人的社交圈）
     * @param personName 中心人物
     * @param maxDepth 最大深度（1=直接关系，2=朋友的朋友）
     * @return 关系图谱
     */
    suspend fun getRelationshipGraph(
        personName: String,
        maxDepth: Int = 2
    ): RelationshipGraph {
        Timber.d("[RelationshipAggregator] 获取关系图谱: $personName (深度: $maxDepth)")

        try {
            val nodes = mutableListOf<GraphNode>()
            val edges = mutableListOf<GraphEdge>()
            val visited = mutableSetOf<String>()

            // BFS广度优先搜索构建关系图
            val queue = mutableListOf(Pair(personName, 0))
            visited.add(personName)

            // 添加中心节点
            nodes.add(GraphNode(
                id = personName,
                name = personName,
                type = "center",
                depth = 0
            ))

            while (queue.isNotEmpty()) {
                val (currentPerson, currentDepth) = queue.removeAt(0)

                if (currentDepth >= maxDepth) {
                    continue
                }

                // 获取当前人物的所有关系
                val relations = relationshipNetworkUseCase.getPersonRelations(currentPerson)

                for (relation in relations) {
                    val otherPerson = if (relation.personA == currentPerson) {
                        relation.personB
                    } else {
                        relation.personA
                    }

                    // 添加节点
                    if (!visited.contains(otherPerson)) {
                        visited.add(otherPerson)
                        nodes.add(GraphNode(
                            id = otherPerson,
                            name = otherPerson,
                            type = if (currentDepth == 0) "direct" else "indirect",
                            depth = currentDepth + 1
                        ))

                        queue.add(Pair(otherPerson, currentDepth + 1))
                    }

                    // 添加边
                    edges.add(GraphEdge(
                        from = relation.personA,
                        to = relation.personB,
                        relationType = relation.relationType,
                        confidence = relation.confidence
                    ))
                }
            }

            // 尝试使用Neo4j进行社区检测
            val communities = try {
                relationshipGraphService.detectCommunities(nodes.map { it.id })
            } catch (e: Exception) {
                Timber.w("[RelationshipAggregator] 社区检测失败: ${e.message}")
                null
            }

            return RelationshipGraph(
                centerPerson = personName,
                nodes = nodes,
                edges = edges,
                communities = communities,
                maxDepth = maxDepth,
                totalNodes = nodes.size,
                totalEdges = edges.size
            )

        } catch (e: Exception) {
            Timber.e(e, "[RelationshipAggregator] 获取关系图谱失败")
            return RelationshipGraph(
                centerPerson = personName,
                nodes = emptyList(),
                edges = emptyList(),
                maxDepth = maxDepth,
                errorMessage = e.message
            )
        }
    }

    /**
     * 搜索相关人物
     * 聚合CharacterBook和RelationshipNetwork的搜索结果
     */
    suspend fun searchPeople(query: String): List<PersonSearchResult> {
        try {
            val results = mutableListOf<PersonSearchResult>()

            // 1. 从CharacterBook搜索
            val characterProfiles = characterBook.searchProfiles(query)
            for (profile in characterProfiles) {
                val relationCount = relationshipNetworkUseCase
                    .getPersonRelations(profile.basicInfo.name).size

                results.add(PersonSearchResult(
                    name = profile.basicInfo.name,
                    source = "CharacterBook",
                    hasProfile = true,
                    relationshipCount = relationCount,
                    bio = profile.basicInfo.bio
                ))
            }

            // 2. 从RelationshipNetwork搜索（查找名称中包含query的人）
            val allRelations = characterBook.getAllProfiles()
                .flatMap { profile ->
                    relationshipNetworkUseCase.getPersonRelations(profile.basicInfo.name)
                }

            val peopleInNetwork = allRelations
                .flatMap { listOf(it.personA, it.personB) }
                .distinct()
                .filter { it.contains(query, ignoreCase = true) }

            for (personName in peopleInNetwork) {
                // 避免重复
                if (results.none { it.name == personName }) {
                    val relationCount = relationshipNetworkUseCase
                        .getPersonRelations(personName).size

                    results.add(PersonSearchResult(
                        name = personName,
                        source = "RelationshipNetwork",
                        hasProfile = false,
                        relationshipCount = relationCount
                    ))
                }
            }

            return results.sortedByDescending { it.relationshipCount }

        } catch (e: Exception) {
            Timber.e(e, "[RelationshipAggregator] 搜索人物失败")
            return emptyList()
        }
    }

    /**
     * 生成关系摘要文本
     * 用于LLM上下文注入
     */
    suspend fun generateRelationshipSummary(personName: String): String {
        val view = getCompleteRelationships(personName)

        return buildString {
            appendLine("【$personName 的关系网络】")
            appendLine()

            // 1. 与小光的关系
            view.characterProfile?.let { profile ->
                appendLine("与小光的关系：")
                view.characterRelationships.forEach { rel ->
                    appendLine("  · ${rel.relationType}（亲密度: ${rel.intimacyLevel}）")
                }
                appendLine()
            }

            // 2. 与他人的关系
            if (view.networkRelations.isNotEmpty()) {
                appendLine("与他人的关系：")
                view.networkRelations.forEach { rel ->
                    val otherPerson = if (rel.personA == personName) rel.personB else rel.personA
                    appendLine("  · 与 $otherPerson 是 ${rel.relationType}")
                }
                appendLine()
            }

            // 3. 社区信息
            view.graphData?.let { graph ->
                appendLine("社交圈信息：")
                appendLine("  · 直接认识 ${graph.totalNodes} 人")
                appendLine("  · 关系网络包含 ${graph.totalEdges} 条连接")
                graph.communities?.let { communities ->
                    appendLine("  · 所属社区: ${communities.joinToString(", ")}")
                }
            }

            // 4. 背景知识
            if (view.worldKnowledge.isNotEmpty()) {
                appendLine()
                appendLine("相关背景：")
                view.worldKnowledge.take(3).forEach { entry ->
                    appendLine("  · ${entry.content}")
                }
            }
        }
    }
}

// ==================== 数据模型 ====================

/**
 * 完整关系视图（聚合结果）
 */
data class CompleteRelationshipView(
    val personName: String,
    val characterProfile: com.xiaoguang.assistant.domain.knowledge.models.CharacterProfile? = null,
    val characterRelationships: List<Relationship> = emptyList(),
    val networkRelations: List<RelationshipNetworkEntity> = emptyList(),
    val graphData: RelationshipGraph? = null,
    val worldKnowledge: List<com.xiaoguang.assistant.domain.knowledge.models.WorldEntry> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val errorMessage: String? = null
)

/**
 * 关系路径
 */
data class RelationshipPath(
    val from: String,
    val to: String,
    val isDirect: Boolean,
    val path: List<String>,
    val relations: List<RelationshipNetworkEntity>,
    val distance: Int,
    val intermediatePersons: List<String> = emptyList(),
    val notFound: Boolean = false,
    val errorMessage: String? = null
) {
    fun toDescription(): String {
        if (notFound) {
            return "$from 和 $to 之间暂未发现关系"
        }

        if (isDirect) {
            val relation = relations.firstOrNull()
            return "$from 和 $to 是 ${relation?.relationType ?: "未知关系"}"
        }

        if (intermediatePersons.isNotEmpty()) {
            return "$from 通过 ${intermediatePersons.joinToString("、")} 认识 $to"
        }

        return "$from 和 $to 的关系距离为 $distance 度"
    }
}

/**
 * 关系图谱
 */
data class RelationshipGraph(
    val centerPerson: String,
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>,
    val communities: List<String>? = null,
    val maxDepth: Int,
    val totalNodes: Int = 0,
    val totalEdges: Int = 0,
    val errorMessage: String? = null
)

/**
 * 图节点
 */
data class GraphNode(
    val id: String,
    val name: String,
    val type: String,  // center, direct, indirect
    val depth: Int
)

/**
 * 图边
 */
data class GraphEdge(
    val from: String,
    val to: String,
    val relationType: String,
    val confidence: Float
)

/**
 * 人物搜索结果
 */
data class PersonSearchResult(
    val name: String,
    val source: String,  // CharacterBook, RelationshipNetwork, Neo4j
    val hasProfile: Boolean,
    val relationshipCount: Int,
    val bio: String? = null
)
