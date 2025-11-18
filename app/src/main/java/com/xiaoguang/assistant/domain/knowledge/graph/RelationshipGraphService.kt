package com.xiaoguang.assistant.domain.knowledge.graph

import com.xiaoguang.assistant.data.remote.api.Neo4jAPI
import com.xiaoguang.assistant.domain.knowledge.models.CharacterProfile
import com.xiaoguang.assistant.domain.knowledge.models.Relationship
import com.xiaoguang.assistant.domain.knowledge.models.RelationType
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 关系图谱服务
 * 使用Neo4j存储和查询复杂的角色关系网络
 *
 * 优化：
 * - 集成Louvain算法进行社群检测
 * - 模块度优化的社区发现
 */
@Singleton
class RelationshipGraphService @Inject constructor(
    private val neo4jClient: Neo4jClient
) {

    private val louvain = LouvainCommunityDetection()

    /**
     * 初始化图谱schema
     * 创建索引和约束
     */
    suspend fun initialize(): Result<Unit> {
        return try {
            Timber.i("[GraphService] 开始初始化图谱schema...")

            // 创建Character节点的唯一约束
            neo4jClient.createConstraint(
                label = Neo4jAPI.LABEL_CHARACTER,
                property = "characterId"
            )

            // 创建索引
            neo4jClient.createIndex(Neo4jAPI.LABEL_CHARACTER, "name")
            neo4jClient.createIndex(Neo4jAPI.LABEL_MEMORY, "memoryId")
            neo4jClient.createIndex(Neo4jAPI.LABEL_EVENT, "eventId")

            Timber.i("[GraphService] ✅ 图谱初始化完成")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "[GraphService] 图谱初始化失败")
            Result.failure(e)
        }
    }

    /**
     * 添加角色节点
     */
    suspend fun addCharacter(profile: CharacterProfile): Result<Unit> {
        val cypher = """
            MERGE (c:${Neo4jAPI.LABEL_CHARACTER} {characterId: ${'$'}characterId})
            SET c.name = ${'$'}name,
                c.nickname = ${'$'}nickname,
                c.gender = ${'$'}gender,
                c.age = ${'$'}age,
                c.updatedAt = timestamp()
            RETURN c
        """.trimIndent()

        val params: Map<String, Any> = mapOf(
            "characterId" to profile.basicInfo.characterId,
            "name" to profile.basicInfo.name,
            "nickname" to (profile.basicInfo.nickname ?: ""),
            "gender" to (profile.basicInfo.gender ?: ""),
            "age" to (profile.basicInfo.age ?: 0)
        )

        return neo4jClient.executeQuery(cypher, params).map { }
    }

    /**
     * 添加关系
     */
    suspend fun addRelationship(relationship: Relationship): Result<Unit> {
        // 将RelationType映射到Neo4j关系类型
        val relType = mapRelationType(relationship.type)

        val cypher = """
            MATCH (a:${Neo4jAPI.LABEL_CHARACTER} {characterId: ${'$'}fromId})
            MATCH (b:${Neo4jAPI.LABEL_CHARACTER} {characterId: ${'$'}toId})
            MERGE (a)-[r:$relType]->(b)
            SET r.intimacy = ${'$'}intimacy,
                r.trust = ${'$'}trust,
                r.strength = ${'$'}strength,
                r.description = ${'$'}description,
                r.updatedAt = timestamp()
            RETURN r
        """.trimIndent()

        val params: Map<String, Any> = mapOf(
            "fromId" to relationship.fromCharacterId,
            "toId" to relationship.toCharacterId,
            "intimacy" to relationship.intimacy,
            "trust" to relationship.trust,
            "strength" to relationship.getStrength(),
            "description" to (relationship.description ?: "")
        )

        return neo4jClient.executeQuery(cypher, params).map { }
    }

    /**
     * 查询角色的所有关系
     */
    suspend fun getCharacterRelationships(
        characterId: String,
        maxDepth: Int = 1
    ): Result<List<GraphRelationship>> {
        val cypher = """
            MATCH (c:${Neo4jAPI.LABEL_CHARACTER} {characterId: ${'$'}characterId})
            MATCH (c)-[r*1..$maxDepth]-(other:${Neo4jAPI.LABEL_CHARACTER})
            RETURN DISTINCT r, other
            ORDER BY r[0].strength DESC
        """.trimIndent()

        val params = mapOf("characterId" to characterId)

        return try {
            val result = neo4jClient.executeQuery(cypher, params).getOrThrow()

            val relationships = result.data.mapNotNull { data ->
                data.graph?.relationships?.firstOrNull()?.let { rel ->
                    GraphRelationship(
                        id = rel.id,
                        type = rel.type,
                        fromCharacterId = rel.startNode,
                        toCharacterId = rel.endNode,
                        properties = rel.properties
                    )
                }
            }

            Result.success(relationships)
        } catch (e: Exception) {
            Timber.e(e, "[GraphService] 查询关系失败")
            Result.failure(e)
        }
    }

    /**
     * 查找两个角色之间的最短路径
     */
    suspend fun findShortestPath(
        fromCharacterId: String,
        toCharacterId: String,
        maxDepth: Int = 5
    ): Result<List<String>> {
        val cypher = """
            MATCH (a:${Neo4jAPI.LABEL_CHARACTER} {characterId: ${'$'}fromId}),
                  (b:${Neo4jAPI.LABEL_CHARACTER} {characterId: ${'$'}toId}),
                  path = shortestPath((a)-[*..${maxDepth}]-(b))
            RETURN [node IN nodes(path) | node.name] AS path
        """.trimIndent()

        val params = mapOf(
            "fromId" to fromCharacterId,
            "toId" to toCharacterId
        )

        return try {
            val result = neo4jClient.executeQuery(cypher, params).getOrThrow()
            val pathData = result.data.firstOrNull()?.row?.firstOrNull()

            @Suppress("UNCHECKED_CAST")
            val path = (pathData as? List<String>) ?: emptyList()

            Result.success(path)
        } catch (e: Exception) {
            Timber.e(e, "[GraphService] 查找路径失败")
            Result.failure(e)
        }
    }

    /**
     * 查找相似角色（基于关系网络）
     * 使用协同过滤思想：如果两个角色有许多共同的朋友，他们可能相似
     */
    suspend fun findSimilarCharacters(
        characterId: String,
        limit: Int = 5
    ): Result<List<SimilarCharacter>> {
        val cypher = """
            MATCH (c:${Neo4jAPI.LABEL_CHARACTER} {characterId: ${'$'}characterId})-[r1]-(mutual)-[r2]-(similar:${Neo4jAPI.LABEL_CHARACTER})
            WHERE c <> similar
            WITH similar, count(DISTINCT mutual) AS commonConnections,
                 avg(r1.strength + r2.strength) AS avgConnectionStrength
            RETURN similar.characterId AS characterId,
                   similar.name AS name,
                   commonConnections,
                   avgConnectionStrength,
                   (commonConnections * avgConnectionStrength) AS similarityScore
            ORDER BY similarityScore DESC
            LIMIT ${'$'}limit
        """.trimIndent()

        val params = mapOf(
            "characterId" to characterId,
            "limit" to limit
        )

        return try {
            val result = neo4jClient.executeQuery(cypher, params).getOrThrow()

            val similarCharacters = result.data.mapNotNull { data ->
                val row = data.row ?: return@mapNotNull null

                if (row.size >= 5) {
                    SimilarCharacter(
                        characterId = row[0] as? String ?: "",
                        name = row[1] as? String ?: "",
                        commonConnections = (row[2] as? Number)?.toInt() ?: 0,
                        similarityScore = (row[4] as? Number)?.toFloat() ?: 0f
                    )
                } else {
                    null
                }
            }

            Result.success(similarCharacters)
        } catch (e: Exception) {
            Timber.e(e, "[GraphService] 查找相似角色失败")
            Result.failure(e)
        }
    }

    /**
     * 查找相关人物（GraphRAG使用）
     */
    suspend fun findRelatedPeople(
        personName: String,
        maxDepth: Int = 2
    ): Result<List<String>> {
        return try {
            val cypher = """
                MATCH (p:Person {name: ${'$'}personName})
                MATCH p-[*1..${'$'}maxDepth]-(related:Person)
                WHERE p <> related
                RETURN DISTINCT related.name AS name
                LIMIT 20
            """.trimIndent()

            val params = mapOf(
                "personName" to personName,
                "maxDepth" to maxDepth
            )

            val result = neo4jClient.executeQuery(cypher, params).getOrThrow()
            val people = result.data.mapNotNull { data ->
                (data.row?.firstOrNull() as? String)
            }

            Result.success(people)
        } catch (e: Exception) {
            Timber.e(e, "[GraphService] 查找相关人物失败")
            Result.failure(e)
        }
    }

    /**
     * 查询社群检测（社区发现）
     * 使用Louvain算法识别紧密连接的角色群体
     *
     * 优点：
     * - 自动发现社群数量
     * - 基于模块度优化
     * - O(n log n)复杂度
     */
    suspend fun detectCommunities(): Result<Map<String, List<String>>> {
        return try {
            // 1. 从Neo4j获取所有节点和边
            val nodesCypher = """
                MATCH (c:${Neo4jAPI.LABEL_CHARACTER})
                RETURN c.characterId AS id, c.name AS name
            """.trimIndent()

            val edgesCypher = """
                MATCH (a:${Neo4jAPI.LABEL_CHARACTER})-[r]-(b:${Neo4jAPI.LABEL_CHARACTER})
                WHERE a.characterId < b.characterId
                RETURN a.characterId AS from, b.characterId AS to,
                       coalesce(r.strength, 0.5) AS weight
            """.trimIndent()

            val nodesResult = neo4jClient.executeQuery(nodesCypher).getOrThrow()
            val edgesResult = neo4jClient.executeQuery(edgesCypher).getOrThrow()

            // 2. 构建Louvain输入数据
            val nodes = nodesResult.data.mapNotNull { data ->
                val row = data.row
                if (row != null && row.isNotEmpty()) {
                    LouvainCommunityDetection.Node(
                        id = row[0] as? String ?: return@mapNotNull null
                    )
                } else null
            }

            val edges = edgesResult.data.mapNotNull { data ->
                val row = data.row
                if (row != null && row.size >= 3) {
                    LouvainCommunityDetection.Edge(
                        from = row[0] as? String ?: return@mapNotNull null,
                        to = row[1] as? String ?: return@mapNotNull null,
                        weight = (row[2] as? Number)?.toDouble() ?: 0.5
                    )
                } else null
            }

            if (nodes.isEmpty()) {
                Timber.w("[GraphService] 没有节点，跳过社群检测")
                return Result.success(emptyMap())
            }

            // 3. 执行Louvain算法
            val communities = louvain.detectCommunities(nodes, edges)

            // 4. 合并小社群（成员 < 2）
            val mergedCommunities = louvain.mergeSmalCommunities(communities, edges, minSize = 2)

            // 5. 转换结果格式
            val result = mergedCommunities.mapKeys { "community_${it.key}" }

            Timber.i("[GraphService] 社群检测完成: 发现 ${result.size} 个社群")
            result.forEach { (commId, members) ->
                Timber.d("[GraphService] $commId: ${members.size} 个成员")
            }

            Result.success(result)
        } catch (e: Exception) {
            Timber.e(e, "[GraphService] Louvain社群检测失败")
            Result.failure(e)
        }
    }

    /**
     * 更新关系强度
     */
    suspend fun updateRelationshipStrength(
        fromCharacterId: String,
        toCharacterId: String,
        relationType: RelationType,
        intimacy: Float,
        trust: Float
    ): Result<Unit> {
        val relType = mapRelationType(relationType)

        val cypher = """
            MATCH (a:${Neo4jAPI.LABEL_CHARACTER} {characterId: ${'$'}fromId})
                  -[r:$relType]->
                  (b:${Neo4jAPI.LABEL_CHARACTER} {characterId: ${'$'}toId})
            SET r.intimacy = ${'$'}intimacy,
                r.trust = ${'$'}trust,
                r.strength = (${'$'}intimacy * 0.4 + ${'$'}trust * 0.3),
                r.updatedAt = timestamp()
            RETURN r
        """.trimIndent()

        val params = mapOf(
            "fromId" to fromCharacterId,
            "toId" to toCharacterId,
            "intimacy" to intimacy,
            "trust" to trust
        )

        return neo4jClient.executeQuery(cypher, params).map { }
    }

    /**
     * 删除角色节点
     */
    suspend fun deleteCharacter(characterId: String): Result<Unit> {
        val cypher = """
            MATCH (c:${Neo4jAPI.LABEL_CHARACTER} {characterId: ${'$'}characterId})
            DETACH DELETE c
        """.trimIndent()

        val params = mapOf("characterId" to characterId)

        return neo4jClient.executeQuery(cypher, params).map { }
    }

    /**
     * 检查连接状态
     */
    suspend fun checkConnection(): Boolean {
        return neo4jClient.checkConnection()
    }

    /**
     * 获取图谱统计信息
     */
    suspend fun getStats(): Result<GraphStats> {
        return try {
            val stats = neo4jClient.getStats().getOrThrow()

            Result.success(
                GraphStats(
                    totalNodes = stats["nodeCount"] as? Int ?: 0,
                    totalRelationships = stats["relationshipCount"] as? Int ?: 0,
                    totalLabels = stats["labelCount"] as? Int ?: 0
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "[GraphService] 获取统计失败")
            Result.failure(e)
        }
    }

    // ==================== Helper Methods ====================

    /**
     * 将RelationType映射到Neo4j关系类型
     */
    private fun mapRelationType(type: RelationType): String {
        return when (type) {
            RelationType.MASTER -> Neo4jAPI.REL_MASTER
            RelationType.FAMILY -> Neo4jAPI.REL_FAMILY
            RelationType.FRIEND -> Neo4jAPI.REL_FRIEND
            RelationType.COLLEAGUE -> Neo4jAPI.REL_COLLEAGUE
            RelationType.RIVAL -> Neo4jAPI.REL_RIVAL
            RelationType.LOVER -> Neo4jAPI.REL_LOVER
            else -> Neo4jAPI.REL_KNOWS
        }
    }

    /**
     * 获取某人的关系图谱
     * 用于RelationshipAggregator
     */
    suspend fun getPersonGraph(personName: String): com.xiaoguang.assistant.domain.relationship.RelationshipGraph {
        return try {
            // 查询该人物的关系网络
            val cypher = """
                MATCH (center:${Neo4jAPI.LABEL_CHARACTER} {name: ${'$'}name})
                OPTIONAL MATCH (center)-[r]-(connected:${Neo4jAPI.LABEL_CHARACTER})
                RETURN center, collect(DISTINCT {
                    from: startNode(r).name,
                    to: endNode(r).name,
                    type: type(r),
                    confidence: coalesce(r.strength, 0.5)
                }) AS relationships
            """.trimIndent()

            val result = neo4jClient.executeQuery(cypher, mapOf("name" to personName)).getOrNull()

            val nodes = mutableListOf<com.xiaoguang.assistant.domain.relationship.GraphNode>()
            val edges = mutableListOf<com.xiaoguang.assistant.domain.relationship.GraphEdge>()

            nodes.add(com.xiaoguang.assistant.domain.relationship.GraphNode(
                id = personName,
                name = personName,
                type = "center",
                depth = 0
            ))

            // 解析关系数据
            result?.data?.firstOrNull()?.let { data ->
                @Suppress("UNCHECKED_CAST")
                val relationships = data.row?.get(1) as? List<Map<String, Any>> ?: emptyList()

                for (rel in relationships) {
                    val from = rel["from"] as? String ?: continue
                    val to = rel["to"] as? String ?: continue
                    val type = rel["type"] as? String ?: "RELATED_TO"
                    val confidence = (rel["confidence"] as? Number)?.toFloat() ?: 0.5f

                    // 添加节点
                    if (from != personName && nodes.none { it.id == from }) {
                        nodes.add(com.xiaoguang.assistant.domain.relationship.GraphNode(
                            id = from,
                            name = from,
                            type = "direct",
                            depth = 1
                        ))
                    }
                    if (to != personName && nodes.none { it.id == to }) {
                        nodes.add(com.xiaoguang.assistant.domain.relationship.GraphNode(
                            id = to,
                            name = to,
                            type = "direct",
                            depth = 1
                        ))
                    }

                    // 添加边
                    edges.add(com.xiaoguang.assistant.domain.relationship.GraphEdge(
                        from = from,
                        to = to,
                        relationType = type,
                        confidence = confidence
                    ))
                }
            }

            com.xiaoguang.assistant.domain.relationship.RelationshipGraph(
                centerPerson = personName,
                nodes = nodes,
                edges = edges,
                maxDepth = 1,
                totalNodes = nodes.size,
                totalEdges = edges.size
            )

        } catch (e: Exception) {
            Timber.e(e, "[GraphService] 获取人物图谱失败: $personName")
            com.xiaoguang.assistant.domain.relationship.RelationshipGraph(
                centerPerson = personName,
                nodes = emptyList(),
                edges = emptyList(),
                maxDepth = 1,
                errorMessage = e.message
            )
        }
    }

    /**
     * 查找最短路径（返回RelationshipPath）
     * 用于RelationshipAggregator
     */
    suspend fun findShortestPathBetweenPeople(
        personA: String,
        personB: String,
        maxDepth: Int
    ): com.xiaoguang.assistant.domain.relationship.RelationshipPath? {
        return try {
            val cypher = """
                MATCH (a:${Neo4jAPI.LABEL_CHARACTER} {name: ${'$'}nameA}),
                      (b:${Neo4jAPI.LABEL_CHARACTER} {name: ${'$'}nameB}),
                      path = shortestPath((a)-[*..${maxDepth}]-(b))
                RETURN [node IN nodes(path) | node.name] AS path,
                       length(path) AS distance
            """.trimIndent()

            val result = neo4jClient.executeQuery(
                cypher,
                mapOf("nameA" to personA, "nameB" to personB)
            ).getOrNull()

            val data = result?.data?.firstOrNull()?.row
            if (data != null && data.isNotEmpty()) {
                @Suppress("UNCHECKED_CAST")
                val pathNodes = data[0] as? List<String> ?: emptyList()
                val distance = (data.getOrNull(1) as? Number)?.toInt() ?: -1

                com.xiaoguang.assistant.domain.relationship.RelationshipPath(
                    from = personA,
                    to = personB,
                    isDirect = distance == 1,
                    path = pathNodes,
                    relations = emptyList(),
                    distance = distance,
                    intermediatePersons = if (pathNodes.size > 2) {
                        pathNodes.subList(1, pathNodes.size - 1)
                    } else {
                        emptyList()
                    },
                    notFound = false,
                    errorMessage = null
                )
            } else {
                null
            }

        } catch (e: Exception) {
            Timber.e(e, "[GraphService] 查找路径失败: $personA -> $personB")
            null
        }
    }

    /**
     * 检测社区（返回社区列表）
     * 用于RelationshipAggregator
     */
    suspend fun detectCommunities(nodeIds: List<String>): List<String>? {
        return try {
            // 使用现有的detectCommunities方法
            val communitiesMap = detectCommunities().getOrNull() ?: return null

            // 查找每个节点所属的社区
            val result = mutableListOf<String>()
            for (nodeId in nodeIds) {
                val community = communitiesMap.entries.firstOrNull { (_, members) ->
                    members.contains(nodeId)
                }?.key
                if (community != null) {
                    result.add(community)
                }
            }

            result.distinct()

        } catch (e: Exception) {
            Timber.e(e, "[GraphService] 检测社区失败")
            null
        }
    }

    /**
     * 检查Neo4j服务是否可用
     */
    suspend fun isAvailable(): Boolean {
        return try {
            // 尝试执行一个简单查询来检查连接
            val result = neo4jClient.executeQuery("RETURN 1 as test")
            result.isSuccess
        } catch (e: Exception) {
            Timber.w(e, "[GraphService] Neo4j服务不可用")
            false
        }
    }
}

/**
 * 图关系（简化版，用于应用层）
 */
data class GraphRelationship(
    val id: String,
    val type: String,
    val fromCharacterId: String,
    val toCharacterId: String,
    val properties: Map<String, Any>
)

/**
 * 相似角色
 */
data class SimilarCharacter(
    val characterId: String,
    val name: String,
    val commonConnections: Int,
    val similarityScore: Float
)

/**
 * 图统计信息
 */
data class GraphStats(
    val totalNodes: Int,
    val totalRelationships: Int,
    val totalLabels: Int
)
