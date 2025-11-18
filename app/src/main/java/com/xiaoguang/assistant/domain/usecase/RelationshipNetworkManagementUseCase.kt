package com.xiaoguang.assistant.domain.usecase

import com.xiaoguang.assistant.data.local.database.entity.RelationshipNetworkEntity
import com.xiaoguang.assistant.domain.common.RetryPolicy
import com.xiaoguang.assistant.domain.knowledge.graph.Neo4jClient
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 关系网络管理UseCase (v2.2 - 纯Neo4j架构)
 * 管理人物之间的关系网络（不只是小光与某人的关系）
 *
 * v2.2架构变更：
 * - 直接调用Neo4j，不再使用Room
 * - 使用RetryPolicy提供网络容错
 * - 保持相同的API接口，便于迁移
 */
@Singleton
class RelationshipNetworkManagementUseCase @Inject constructor(
    private val neo4jClient: Neo4jClient,
    private val retryPolicy: RetryPolicy = RetryPolicy.DEFAULT
) {

    /**
     * 记录两人之间的关系 (Neo4j实现)
     */
    suspend fun recordRelation(
        personA: String,
        personB: String,
        relationType: String,
        description: String = "",
        confidence: Float = 1.0f,
        source: String = "user_mentioned"
    ): Long {
        // 规范化人名（字典序）
        val (normalizedA, normalizedB) = RelationshipNetworkEntity.normalize(personA, personB)
        val now = System.currentTimeMillis()

        // 使用MERGE创建或更新关系
        val cypher = """
            MERGE (a:Person {name: ${'$'}personA})
            MERGE (b:Person {name: ${'$'}personB})
            MERGE (a)-[r:RELATIONSHIP]->(b)
            ON CREATE SET
                r.relationType = ${'$'}relationType,
                r.description = ${'$'}description,
                r.confidence = ${'$'}confidence,
                r.source = ${'$'}source,
                r.createdAt = ${'$'}now,
                r.updatedAt = ${'$'}now,
                r.lastConfirmedAt = ${'$'}now,
                r.validFrom = ${'$'}now
            ON MATCH SET
                r.relationType = ${'$'}relationType,
                r.description = ${'$'}description,
                r.confidence = (r.confidence + ${'$'}confidence) / 2,
                r.updatedAt = ${'$'}now,
                r.lastConfirmedAt = ${'$'}now
            RETURN id(r) as relationshipId
        """.trimIndent()

        val parameters = mapOf(
            "personA" to normalizedA,
            "personB" to normalizedB,
            "relationType" to relationType,
            "description" to description,
            "confidence" to confidence,
            "source" to source,
            "now" to now
        )

        return retryPolicy.executeResult("recordRelation") {
            neo4jClient.executeQuery(cypher, parameters)
        }.fold(
            onSuccess = { result ->
                val relationshipId = result.data.firstOrNull()?.row?.firstOrNull() as? Number
                val id = relationshipId?.toLong() ?: 0L
                Timber.d("[Neo4j] 记录关系: $normalizedA <-> $normalizedB ($relationType), ID=$id")
                id
            },
            onFailure = { e ->
                Timber.e(e, "[Neo4j] 记录关系失败: $personA <-> $personB")
                0L  // 返回默认ID
            }
        )
    }

    /**
     * 获取某人的所有关系 (Neo4j实现)
     */
    suspend fun getPersonRelations(personName: String): List<RelationshipNetworkEntity> {
        // 查询双向关系（作为起点或终点）
        val cypher = """
            MATCH (p:Person {name: ${'$'}personName})-[r:RELATIONSHIP]-(other:Person)
            WHERE r.validTo IS NULL OR r.validTo > ${'$'}now
            RETURN
                p.name as personA,
                other.name as personB,
                r.relationType as relationType,
                r.confidence as confidence,
                r.description as description,
                r.source as source,
                r.createdAt as createdAt,
                r.updatedAt as updatedAt,
                r.lastConfirmedAt as lastConfirmedAt,
                r.validFrom as validFrom,
                r.validTo as validTo,
                id(r) as relationshipId
        """.trimIndent()

        val parameters = mapOf(
            "personName" to personName,
            "now" to System.currentTimeMillis()
        )

        return retryPolicy.executeResult("getPersonRelations") {
            neo4jClient.executeQuery(cypher, parameters)
        }.fold(
            onSuccess = { result ->
                result.data.mapNotNull { parseRelationshipEntity(it.row) }
            },
            onFailure = { e ->
                Timber.w(e, "[Neo4j] 获取${personName}的关系失败，返回空列表")
                emptyList()
            }
        )
    }

    /**
     * 获取两人之间的关系 (Neo4j实现)
     */
    suspend fun getRelationBetween(personA: String, personB: String): RelationshipNetworkEntity? {
        // 规范化人名
        val (normalizedA, normalizedB) = RelationshipNetworkEntity.normalize(personA, personB)

        val cypher = """
            MATCH (a:Person {name: ${'$'}personA})-[r:RELATIONSHIP]-(b:Person {name: ${'$'}personB})
            WHERE r.validTo IS NULL OR r.validTo > ${'$'}now
            RETURN
                a.name as personA,
                b.name as personB,
                r.relationType as relationType,
                r.confidence as confidence,
                r.description as description,
                r.source as source,
                r.createdAt as createdAt,
                r.updatedAt as updatedAt,
                r.lastConfirmedAt as lastConfirmedAt,
                r.validFrom as validFrom,
                r.validTo as validTo,
                id(r) as relationshipId
            LIMIT 1
        """.trimIndent()

        val parameters = mapOf(
            "personA" to normalizedA,
            "personB" to normalizedB,
            "now" to System.currentTimeMillis()
        )

        return retryPolicy.executeResult("getRelationBetween") {
            neo4jClient.executeQuery(cypher, parameters)
        }.fold(
            onSuccess = { result ->
                result.data.firstOrNull()?.row?.let { parseRelationshipEntity(it) }
            },
            onFailure = { e ->
                Timber.w(e, "[Neo4j] 获取关系失败: $personA <-> $personB")
                null
            }
        )
    }

    /**
     * 生成某人的关系网络描述
     */
    suspend fun generateNetworkDescription(personName: String): String {
        val relations = getPersonRelations(personName)

        if (relations.isEmpty()) {
            return "还不了解${personName}的人际关系呢..."
        }

        return buildString {
            appendLine("【${personName}的人际关系】")
            relations.forEach { relation ->
                val otherPerson = if (relation.personA == personName) {
                    relation.personB
                } else {
                    relation.personA
                }
                appendLine("· 与${otherPerson}是${relation.relationType}")
            }
        }
    }

    /**
     * 查找共同认识的人 (Neo4j实现 - 使用图算法优化)
     */
    suspend fun findCommonConnections(personA: String, personB: String): List<String> {
        // 使用Neo4j的图遍历能力直接查询共同连接
        val cypher = """
            MATCH (a:Person {name: ${'$'}personA})-[:RELATIONSHIP]-(common:Person)-[:RELATIONSHIP]-(b:Person {name: ${'$'}personB})
            WHERE a <> b AND common <> a AND common <> b
            RETURN DISTINCT common.name as commonPerson
        """.trimIndent()

        val parameters = mapOf(
            "personA" to personA,
            "personB" to personB
        )

        return retryPolicy.executeResult("findCommonConnections") {
            neo4jClient.executeQuery(cypher, parameters)
        }.fold(
            onSuccess = { result ->
                result.data.mapNotNull { it.row?.firstOrNull() as? String }
            },
            onFailure = { e ->
                Timber.w(e, "[Neo4j] 查找共同连接失败，回退到逐个查询")
                // 回退到原有逻辑
                val relationsA = getPersonRelations(personA)
                val relationsB = getPersonRelations(personB)

                val connectionsA = relationsA.map {
                    if (it.personA == personA) it.personB else it.personA
                }.toSet()

                val connectionsB = relationsB.map {
                    if (it.personA == personB) it.personB else it.personA
                }.toSet()

                connectionsA.intersect(connectionsB).toList()
            }
        )
    }

    /**
     * 从对话中自动推断关系
     */
    suspend fun inferRelationFromConversation(
        conversationText: String,
        knownPeople: List<String>
    ) {
        // 常见关系词汇
        val relationKeywords = mapOf(
            "朋友" to listOf("朋友", "好朋友"),
            "同事" to listOf("同事", "同行", "搭档"),
            "家人" to listOf("家人", "亲人"),
            "夫妻" to listOf("老公", "老婆", "丈夫", "妻子"),
            "父子" to listOf("爸爸", "父亲", "儿子"),
            "母子" to listOf("妈妈", "母亲", "儿子"),
            "兄弟" to listOf("哥哥", "弟弟", "兄弟"),
            "姐妹" to listOf("姐姐", "妹妹", "姐妹")
        )

        // 尝试匹配 "A是B的XX" 或 "A和B是XX"
        for ((relationType, keywords) in relationKeywords) {
            for (keyword in keywords) {
                // 模式1: "张三是李四的朋友"
                val pattern1 = Regex("(\\S+)是(\\S+)的$keyword")
                pattern1.find(conversationText)?.let { match ->
                    val personA = match.groupValues[1]
                    val personB = match.groupValues[2]
                    if (knownPeople.contains(personA) && knownPeople.contains(personB)) {
                        recordRelation(
                            personA = personA,
                            personB = personB,
                            relationType = relationType,
                            confidence = 0.8f,
                            source = "ai_inferred"
                        )
                    }
                }

                // 模式2: "张三和李四是朋友"
                val pattern2 = Regex("(\\S+)和(\\S+)是$keyword")
                pattern2.find(conversationText)?.let { match ->
                    val personA = match.groupValues[1]
                    val personB = match.groupValues[2]
                    if (knownPeople.contains(personA) && knownPeople.contains(personB)) {
                        recordRelation(
                            personA = personA,
                            personB = personB,
                            relationType = relationType,
                            confidence = 0.8f,
                            source = "ai_inferred"
                        )
                    }
                }
            }
        }
    }

    /**
     * 记录关系事件（更新关系强度） (Neo4j实现)
     */
    suspend fun recordRelationEvent(
        personA: String,
        personB: String,
        eventType: String,
        description: String,
        emotionalImpact: Float
    ) {
        val (normalizedA, normalizedB) = RelationshipNetworkEntity.normalize(personA, personB)
        val now = System.currentTimeMillis()

        val cypher = """
            MATCH (a:Person {name: ${'$'}personA})-[r:RELATIONSHIP]-(b:Person {name: ${'$'}personB})
            WHERE r.validTo IS NULL OR r.validTo > ${'$'}now
            SET
                r.confidence = CASE
                    WHEN r.confidence IS NULL THEN 0.5 + ${'$'}emotionalImpact * 0.1
                    ELSE (r.confidence + ${'$'}emotionalImpact * 0.1)
                END,
                r.description = COALESCE(r.description, '') + ${'$'}eventText,
                r.updatedAt = ${'$'}now,
                r.lastConfirmedAt = ${'$'}now
            RETURN id(r) as relationshipId
        """.trimIndent()

        val eventText = "\n[$eventType] $description"

        val parameters = mapOf(
            "personA" to normalizedA,
            "personB" to normalizedB,
            "eventText" to eventText,
            "emotionalImpact" to emotionalImpact,
            "now" to now
        )

        retryPolicy.executeResult("recordRelationEvent") {
            neo4jClient.executeQuery(cypher, parameters)
        }.fold(
            onSuccess = { result ->
                if (result.data.isEmpty()) {
                    // 关系不存在，创建新关系
                    recordRelation(
                        personA = normalizedA,
                        personB = normalizedB,
                        relationType = "未知关系",
                        description = "[$eventType] $description",
                        confidence = (0.5f + emotionalImpact * 0.1f).coerceIn(0.1f, 1.0f),
                        source = "event_inferred"
                    )
                } else {
                    Timber.d("[Neo4j] 记录关系事件: $normalizedA <-> $normalizedB ($eventType, 影响: $emotionalImpact)")
                }
            },
            onFailure = { e ->
                Timber.e(e, "[Neo4j] 记录关系事件失败: $personA <-> $personB")
            }
        )
    }

    /**
     * 删除关系（软删除：设置validTo） (Neo4j实现)
     */
    suspend fun deleteRelation(personA: String, personB: String) {
        val (normalizedA, normalizedB) = RelationshipNetworkEntity.normalize(personA, personB)
        val now = System.currentTimeMillis()

        val cypher = """
            MATCH (a:Person {name: ${'$'}personA})-[r:RELATIONSHIP]-(b:Person {name: ${'$'}personB})
            WHERE r.validTo IS NULL OR r.validTo > ${'$'}now
            SET r.validTo = ${'$'}now, r.updatedAt = ${'$'}now
            RETURN id(r) as relationshipId
        """.trimIndent()

        val parameters = mapOf(
            "personA" to normalizedA,
            "personB" to normalizedB,
            "now" to now
        )

        retryPolicy.executeResult("deleteRelation") {
            neo4jClient.executeQuery(cypher, parameters)
        }.fold(
            onSuccess = {
                Timber.d("[Neo4j] 删除关系: $normalizedA <-> $normalizedB")
            },
            onFailure = { e ->
                Timber.e(e, "[Neo4j] 删除关系失败: $personA <-> $personB")
            }
        )
    }

    /**
     * 解析Neo4j查询结果为RelationshipNetworkEntity
     */
    private fun parseRelationshipEntity(row: List<Any?>?): RelationshipNetworkEntity? {
        if (row == null || row.size < 12) return null

        return try {
            // 规范化人名
            val personA = row[0] as? String ?: return null
            val personB = row[1] as? String ?: return null
            val (normalizedA, normalizedB) = RelationshipNetworkEntity.normalize(personA, personB)

            RelationshipNetworkEntity(
                id = (row[11] as? Number)?.toLong() ?: 0L,
                personA = normalizedA,
                personB = normalizedB,
                relationType = row[2] as? String ?: "unknown",
                confidence = (row[3] as? Number)?.toFloat() ?: 0.5f,
                description = row[4] as? String ?: "",
                source = row[5] as? String ?: "neo4j",
                createdAt = (row[6] as? Number)?.toLong() ?: System.currentTimeMillis(),
                updatedAt = (row[7] as? Number)?.toLong() ?: System.currentTimeMillis(),
                lastConfirmedAt = (row[8] as? Number)?.toLong() ?: System.currentTimeMillis(),
                validFrom = (row[9] as? Number)?.toLong() ?: System.currentTimeMillis(),
                validTo = (row[10] as? Number)?.toLong()
            )
        } catch (e: Exception) {
            Timber.e(e, "[Neo4j] 解析关系实体失败")
            null
        }
    }
}
