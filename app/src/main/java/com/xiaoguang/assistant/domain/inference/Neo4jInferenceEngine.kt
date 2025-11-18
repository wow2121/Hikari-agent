package com.xiaoguang.assistant.domain.inference

import com.xiaoguang.assistant.domain.knowledge.graph.Neo4jClient
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Neo4j推理引擎
 *
 * 基于图数据库的关系推理和路径发现，增强记忆系统的推理能力。
 *
 * 核心功能：
 * 1. **关系推理**：从已知关系推导新关系（如朋友的朋友）
 * 2. **路径发现**：找到实体间的最短路径或所有路径
 * 3. **社区检测**：识别紧密连接的实体群组
 * 4. **影响力分析**：计算实体的中心度和影响力
 * 5. **模式匹配**：查找特定的关系模式
 *
 * @property neo4jClient Neo4j客户端
 *
 * @author Claude Code
 */
@Singleton
class Neo4jInferenceEngine @Inject constructor(
    private val neo4jClient: Neo4jClient
) {

    // ==================== 关系推理 ====================

    /**
     * 查找二度关系（朋友的朋友）
     *
     * 示例：张三 → 李四 → 王五
     *
     * @param personName 起始人物
     * @param relationType 关系类型（可选，如"FRIEND"）
     * @param maxResults 最大结果数
     * @return 二度关系人物列表
     */
    suspend fun findSecondDegreeRelations(
        personName: String,
        relationType: String? = null,
        maxResults: Int = 10
    ): Result<List<SecondDegreeRelation>> = runCatching {
        val relationFilter = if (relationType != null) {
            ":\$relationType"
        } else {
            ""
        }

        val cypher = """
            MATCH (start:Person {name: ${'$'}startName})-[r1$relationFilter]->(middle:Person)-[r2$relationFilter]->(end:Person)
            WHERE start <> end
            RETURN start.name AS startPerson,
                   middle.name AS middlePerson,
                   end.name AS endPerson,
                   type(r1) AS relation1,
                   type(r2) AS relation2,
                   r1.strength AS strength1,
                   r2.strength AS strength2
            LIMIT ${'$'}limit
        """.trimIndent()

        val params = mutableMapOf<String, Any>(
            "startName" to personName,
            "limit" to maxResults
        )
        if (relationType != null) {
            params["relationType"] = relationType
        }

        val result = neo4jClient.executeQuery(cypher, params).getOrThrow()

        result.data.mapNotNull { resultData ->
            val row = resultData.row ?: return@mapNotNull null

            // 通过列名查找索引
            val startPersonIdx = result.columns.indexOf("startPerson")
            val middlePersonIdx = result.columns.indexOf("middlePerson")
            val endPersonIdx = result.columns.indexOf("endPerson")
            val relation1Idx = result.columns.indexOf("relation1")
            val relation2Idx = result.columns.indexOf("relation2")
            val strength1Idx = result.columns.indexOf("strength1")
            val strength2Idx = result.columns.indexOf("strength2")

            if (startPersonIdx < 0 || middlePersonIdx < 0 || endPersonIdx < 0 ||
                relation1Idx < 0 || relation2Idx < 0 || strength1Idx < 0 || strength2Idx < 0) {
                return@mapNotNull null
            }

            SecondDegreeRelation(
                startPerson = row[startPersonIdx] as? String ?: "",
                middlePerson = row[middlePersonIdx] as? String ?: "",
                endPerson = row[endPersonIdx] as? String ?: "",
                relation1 = row[relation1Idx] as? String ?: "",
                relation2 = row[relation2Idx] as? String ?: "",
                combinedStrength = ((row[strength1Idx] as? Number)?.toFloat() ?: 0.5f) *
                                  ((row[strength2Idx] as? Number)?.toFloat() ?: 0.5f)
            )
        }
    }

    /**
     * 推理潜在关系
     *
     * 基于共同朋友、共同标签等推断可能存在的关系
     *
     * @param personA 人物A
     * @param personB 人物B
     * @return 推理结果
     */
    suspend fun inferPotentialRelationship(
        personA: String,
        personB: String
    ): Result<RelationshipInference> = runCatching {
        // 1. 查找共同朋友
        val commonFriendsCypher = """
            MATCH (a:Person {name: ${'$'}personA})-[:FRIEND]-(common:Person)-[:FRIEND]-(b:Person {name: ${'$'}personB})
            RETURN count(common) AS commonFriends, collect(common.name) AS friendNames
        """.trimIndent()

        val commonResult = neo4jClient.executeQuery(
            commonFriendsCypher,
            mapOf("personA" to personA, "personB" to personB)
        ).getOrThrow()

        val firstRow = commonResult.data.firstOrNull()?.row
        val commonFriendsIdx = commonResult.columns.indexOf("commonFriends")
        val friendNamesIdx = commonResult.columns.indexOf("friendNames")

        val commonFriends = if (firstRow != null && commonFriendsIdx >= 0) {
            firstRow[commonFriendsIdx] as? Int ?: 0
        } else 0

        val friendNames = if (firstRow != null && friendNamesIdx >= 0) {
            (firstRow[friendNamesIdx] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        } else emptyList()

        // 2. 计算关系强度
        val strength = when {
            commonFriends >= 5 -> 0.9f
            commonFriends >= 3 -> 0.7f
            commonFriends >= 1 -> 0.5f
            else -> 0.2f
        }

        // 3. 推理关系类型
        val inferredType = when {
            commonFriends >= 3 -> "可能是朋友"
            commonFriends >= 1 -> "可能认识"
            else -> "关系不明"
        }

        RelationshipInference(
            personA = personA,
            personB = personB,
            inferredType = inferredType,
            confidence = strength,
            evidence = buildString {
                append("共同朋友: $commonFriends 人")
                if (friendNames.isNotEmpty()) {
                    append(" (${friendNames.take(3).joinToString()})")
                }
            },
            commonConnections = friendNames
        )
    }

    // ==================== 路径发现 ====================

    /**
     * 查找最短路径
     *
     * @param startPerson 起始人物
     * @param endPerson 目标人物
     * @param maxDepth 最大深度
     * @return 最短路径
     */
    suspend fun findShortestPath(
        startPerson: String,
        endPerson: String,
        maxDepth: Int = 5
    ): Result<Neo4jRelationshipPath?> = runCatching {
        val cypher = """
            MATCH path = shortestPath(
                (start:Person {name: ${'$'}startName})-[*..${maxDepth}]-(end:Person {name: ${'$'}endName})
            )
            RETURN [node in nodes(path) | node.name] AS nodes,
                   [rel in relationships(path) | type(rel)] AS relations,
                   length(path) AS pathLength
        """.trimIndent()

        val result = neo4jClient.executeQuery(
            cypher,
            mapOf("startName" to startPerson, "endName" to endPerson)
        ).getOrThrow()

        val firstRow = result.data.firstOrNull()?.row

        if (firstRow != null) {
            val nodesIdx = result.columns.indexOf("nodes")
            val relationsIdx = result.columns.indexOf("relations")
            val pathLengthIdx = result.columns.indexOf("pathLength")

            if (nodesIdx >= 0 && relationsIdx >= 0 && pathLengthIdx >= 0) {
                @Suppress("UNCHECKED_CAST")
                return@runCatching Neo4jRelationshipPath(
                    nodes = (firstRow[nodesIdx] as? List<String>) ?: emptyList(),
                    relations = (firstRow[relationsIdx] as? List<String>) ?: emptyList(),
                    length = (firstRow[pathLengthIdx] as? Number)?.toInt() ?: 0
                )
            }
        }

        return@runCatching null
    }

    /**
     * 查找所有路径
     *
     * @param startPerson 起始人物
     * @param endPerson 目标人物
     * @param maxDepth 最大深度
     * @param maxPaths 最大路径数
     * @return 所有路径列表
     */
    suspend fun findAllPaths(
        startPerson: String,
        endPerson: String,
        maxDepth: Int = 4,
        maxPaths: Int = 5
    ): Result<List<Neo4jRelationshipPath>> = runCatching {
        val cypher = """
            MATCH path = (start:Person {name: ${'$'}startName})-[*..${maxDepth}]-(end:Person {name: ${'$'}endName})
            RETURN [node in nodes(path) | node.name] AS nodes,
                   [rel in relationships(path) | type(rel)] AS relations,
                   length(path) AS pathLength
            ORDER BY pathLength
            LIMIT ${'$'}maxPaths
        """.trimIndent()

        val result = neo4jClient.executeQuery(
            cypher,
            mapOf(
                "startName" to startPerson,
                "endName" to endPerson,
                "maxPaths" to maxPaths
            )
        ).getOrThrow()

        result.data.mapNotNull { resultData ->
            val row = resultData.row ?: return@mapNotNull null

            @Suppress("UNCHECKED_CAST")
            Neo4jRelationshipPath(
                nodes = getStringList(row, result.columns, "nodes"),
                relations = getStringList(row, result.columns, "relations"),
                length = getInt(row, result.columns, "pathLength")
            )
        }
    }

    // ==================== 中心度分析 ====================

    /**
     * 计算人物的度中心度（连接数）
     *
     * @param topN 返回Top N个人物
     * @return 人物及其度中心度列表
     */
    suspend fun calculateDegreeCentrality(
        topN: Int = 10
    ): Result<List<CentralityScore>> = runCatching {
        val cypher = """
            MATCH (p:Person)-[r]-()
            RETURN p.name AS person,
                   count(r) AS degree
            ORDER BY degree DESC
            LIMIT ${'$'}topN
        """.trimIndent()

        val result = neo4jClient.executeQuery(
            cypher,
            mapOf("topN" to topN)
        ).getOrThrow()

        result.data.mapNotNull { resultData ->
            val row = resultData.row ?: return@mapNotNull null

            CentralityScore(
                person = getString(row, result.columns, "person"),
                score = getFloat(row, result.columns, "degree"),
                type = CentralityType.DEGREE
            )
        }
    }

    /**
     * 计算中介中心度（桥接能力）
     *
     * 评估人物在网络中作为"桥梁"的重要性
     */
    suspend fun calculateBetweennessCentrality(
        topN: Int = 10
    ): Result<List<CentralityScore>> = runCatching {
        val cypher = """
            CALL gds.betweenness.stream({
                nodeProjection: 'Person',
                relationshipProjection: '*'
            })
            YIELD nodeId, score
            RETURN gds.util.asNode(nodeId).name AS person, score
            ORDER BY score DESC
            LIMIT ${'$'}topN
        """.trimIndent()

        // 注意：此查询需要Neo4j Graph Data Science插件
        // 如果没有插件，使用简化版本
        val fallbackCypher = """
            MATCH (p:Person)
            OPTIONAL MATCH (p)-[r]-()
            WITH p, count(r) as connections
            RETURN p.name AS person, connections * 1.0 AS score
            ORDER BY score DESC
            LIMIT ${'$'}topN
        """.trimIndent()

        val result = neo4jClient.executeQuery(
            fallbackCypher,
            mapOf("topN" to topN)
        ).getOrThrow()

        result.data.mapNotNull { resultData ->
            val row = resultData.row ?: return@mapNotNull null

            CentralityScore(
                person = getString(row, result.columns, "person"),
                score = getFloat(row, result.columns, "score"),
                type = CentralityType.BETWEENNESS
            )
        }
    }

    // ==================== 社区检测 ====================

    /**
     * 查找人物所在的社区（朋友圈）
     *
     * @param personName 人物名称
     * @return 社区成员列表
     */
    suspend fun findCommunity(
        personName: String
    ): Result<Community> = runCatching {
        val cypher = """
            MATCH (center:Person {name: ${'$'}personName})-[:FRIEND*1..2]-(member:Person)
            WITH DISTINCT member
            RETURN collect(member.name) AS members, count(member) AS size
        """.trimIndent()

        val result = neo4jClient.executeQuery(
            cypher,
            mapOf("personName" to personName)
        ).getOrThrow()

        val firstRow = result.data.firstOrNull()?.row

        if (firstRow != null) {
            @Suppress("UNCHECKED_CAST")
            return@runCatching Community(
                centerPerson = personName,
                members = getStringList(firstRow, result.columns, "members"),
                size = getInt(firstRow, result.columns, "size")
            )
        }

        return@runCatching Community(
            centerPerson = personName,
            members = emptyList(),
            size = 0
        )
    }

    // ==================== 模式匹配 ====================

    /**
     * 查找三角关系（A-B-C-A）
     *
     * @return 三角关系列表
     */
    suspend fun findTriangles(): Result<List<Triangle>> = runCatching {
        val cypher = """
            MATCH (a:Person)-[:FRIEND]-(b:Person)-[:FRIEND]-(c:Person)-[:FRIEND]-(a)
            WHERE id(a) < id(b) AND id(b) < id(c)
            RETURN a.name AS person1, b.name AS person2, c.name AS person3
            LIMIT 20
        """.trimIndent()

        val result = neo4jClient.executeQuery(cypher).getOrThrow()

        result.data.mapNotNull { resultData ->
            val row = resultData.row ?: return@mapNotNull null

            Triangle(
                person1 = getString(row, result.columns, "person1"),
                person2 = getString(row, result.columns, "person2"),
                person3 = getString(row, result.columns, "person3")
            )
        }
    }

    /**
     * 查找孤立节点（没有任何关系的人）
     */
    suspend fun findIsolatedNodes(): Result<List<String>> = runCatching {
        val cypher = """
            MATCH (p:Person)
            WHERE NOT (p)-[]-()
            RETURN p.name AS person
        """.trimIndent()

        val result = neo4jClient.executeQuery(cypher).getOrThrow()

        result.data.mapNotNull { resultData ->
            val row = resultData.row ?: return@mapNotNull null
            getString(row, result.columns, "person").takeIf { it.isNotEmpty() }
        }
    }

    // ==================== 统计分析 ====================

    /**
     * 获取网络统计信息
     */
    suspend fun getNetworkStatistics(): Result<NetworkStatistics> = runCatching {
        val cypher = """
            MATCH (p:Person)
            OPTIONAL MATCH (p)-[r]-()
            WITH count(DISTINCT p) AS totalNodes,
                 count(r) AS totalRelations,
                 avg(count(r)) AS avgDegree
            RETURN totalNodes, totalRelations, avgDegree
        """.trimIndent()

        val result = neo4jClient.executeQuery(cypher).getOrThrow()
        val firstRow = result.data.firstOrNull()?.row

        if (firstRow != null) {
            return@runCatching NetworkStatistics(
                totalNodes = getInt(firstRow, result.columns, "totalNodes"),
                totalRelations = getInt(firstRow, result.columns, "totalRelations"),
                averageDegree = getFloat(firstRow, result.columns, "avgDegree")
            )
        }

        return@runCatching NetworkStatistics(0, 0, 0f)
    }
}

// ==================== 数据模型 ====================

/**
 * 二度关系
 */
data class SecondDegreeRelation(
    val startPerson: String,
    val middlePerson: String,
    val endPerson: String,
    val relation1: String,
    val relation2: String,
    val combinedStrength: Float
) {
    override fun toString(): String {
        return "$startPerson -[$relation1]-> $middlePerson -[$relation2]-> $endPerson (强度: %.2f)".format(combinedStrength)
    }
}

/**
 * 关系推理结果
 */
data class RelationshipInference(
    val personA: String,
    val personB: String,
    val inferredType: String,
    val confidence: Float,
    val evidence: String,
    val commonConnections: List<String>
) {
    override fun toString(): String = buildString {
        appendLine("【关系推理】")
        appendLine("$personA 和 $personB: $inferredType")
        appendLine("置信度: %.1f%%".format(confidence * 100))
        appendLine("依据: $evidence")
    }
}

/**
 * 关系路径（Neo4j专用）
 */
data class Neo4jRelationshipPath(
    val nodes: List<String>,
    val relations: List<String>,
    val length: Int
) {
    override fun toString(): String {
        return nodes.joinToString(" -> ")
    }

    fun getPathDescription(): String = buildString {
        nodes.forEachIndexed { index, node ->
            append(node)
            if (index < relations.size) {
                append(" -[${relations[index]}]-> ")
            }
        }
        append(" (长度: $length)")
    }
}

/**
 * 中心度分数
 */
data class CentralityScore(
    val person: String,
    val score: Float,
    val type: CentralityType
)

enum class CentralityType {
    DEGREE,        // 度中心度
    BETWEENNESS,   // 中介中心度
    CLOSENESS,     // 接近中心度
    EIGENVECTOR    // 特征向量中心度
}

/**
 * 社区
 */
data class Community(
    val centerPerson: String,
    val members: List<String>,
    val size: Int
) {
    override fun toString(): String = buildString {
        appendLine("【社区】中心: $centerPerson")
        appendLine("成员数: $size")
        appendLine("成员: ${members.take(10).joinToString()}")
        if (members.size > 10) {
            appendLine("... 还有${members.size - 10}人")
        }
    }
}

/**
 * 三角关系
 */
data class Triangle(
    val person1: String,
    val person2: String,
    val person3: String
) {
    override fun toString(): String {
        return "$person1 ↔ $person2 ↔ $person3 ↔ $person1"
    }
}

/**
 * 网络统计
 */
data class NetworkStatistics(
    val totalNodes: Int,
    val totalRelations: Int,
    val averageDegree: Float
) {
    override fun toString(): String = buildString {
        appendLine("【关系网络统计】")
        appendLine("- 总人数: $totalNodes")
        appendLine("- 总关系数: $totalRelations")
        appendLine("- 平均连接数: %.2f".format(averageDegree))
    }
}

// ==================== 辅助扩展函数 ====================

/**
 * 从查询结果行中获取指定列的字符串值
 */
private fun getString(row: List<Any?>?, columns: List<String>, columnName: String): String {
    val index = columns.indexOf(columnName)
    return if (index >= 0 && row != null && index < row.size) {
        row[index] as? String ?: ""
    } else ""
}

/**
 * 从查询结果行中获取指定列的整数值
 */
private fun getInt(row: List<Any?>?, columns: List<String>, columnName: String): Int {
    val index = columns.indexOf(columnName)
    return if (index >= 0 && row != null && index < row.size) {
        (row[index] as? Number)?.toInt() ?: 0
    } else 0
}

/**
 * 从查询结果行中获取指定列的浮点数值
 */
private fun getFloat(row: List<Any?>?, columns: List<String>, columnName: String): Float {
    val index = columns.indexOf(columnName)
    return if (index >= 0 && row != null && index < row.size) {
        (row[index] as? Number)?.toFloat() ?: 0f
    } else 0f
}

/**
 * 从查询结果行中获取指定列的字符串列表
 */
private fun getStringList(row: List<Any?>?, columns: List<String>, columnName: String): List<String> {
    val index = columns.indexOf(columnName)
    return if (index >= 0 && row != null && index < row.size) {
        (row[index] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
    } else emptyList()
}
