package com.xiaoguang.assistant.data.local.database.entity

/**
 * 人际网络关系数据模型（v2.2 - 纯Neo4j架构）
 * 记录人物之间的关系（不只是小光与某人，还有人与人之间）
 *
 * v2.2架构变更：
 * - 移除Room注解（@Entity, @PrimaryKey）
 * - 现为纯Kotlin数据类，用于Neo4j查询结果映射
 * - Neo4j存储使用Person节点和RELATIONSHIP关系
 *
 * 数据来源:
 * - user_mentioned: 用户明确提及
 * - ai_inferred: AI自动推断
 * - neo4j_import: 从Neo4j导入（已废弃，直接在Neo4j中）
 * - event_inferred: 从事件推断
 *
 * 双时态模型（Bi-Temporal Model）:
 * - eventTime: 关系实际发生的时间（业务时间）
 * - systemTime: 数据录入/更新的时间（系统时间）
 * - validFrom: 关系开始有效的时间
 * - validTo: 关系失效的时间（null = 当前仍有效）
 *
 * 这样可以：
 * 1. 追踪关系演变历史（如朋友→陌生人）
 * 2. 支持时间旅行查询（查询某时间点的关系网络）
 * 3. 处理数据修正（更新历史数据但不丢失原始记录）
 */
data class RelationshipNetworkEntity(
    val id: Long = 0,  // Neo4j关系ID
    val personA: String,  // 人物A（字典序小）
    val personB: String,  // 人物B（字典序大）
    val relationType: String,  // 关系类型
    val confidence: Float = 0.5f,  // 置信度 0.0-1.0
    val description: String = "",  // 关系描述
    val source: String = SOURCE_USER_MENTIONED,  // 数据来源

    // ==================== 时序模型 ====================
    val eventTime: Long? = null,  // 关系实际发生时间（业务时间，可选）
    val createdAt: Long = System.currentTimeMillis(),  // 记录创建时间（系统时间）
    val updatedAt: Long = System.currentTimeMillis(),  // 记录更新时间（系统时间）
    val lastConfirmedAt: Long = System.currentTimeMillis(),  // 最后确认时间

    // ==================== 双时态模型 ====================
    val validFrom: Long = System.currentTimeMillis(),  // 关系开始有效时间
    val validTo: Long? = null  // 关系失效时间（null = 当前仍有效）
) {
    companion object {
        // 关系类型常量
        const val RELATION_FRIEND = "friend"
        const val RELATION_FAMILY = "family"
        const val RELATION_COLLEAGUE = "colleague"
        const val RELATION_COUPLE = "couple"
        const val RELATION_CLASSMATE = "classmate"
        const val RELATION_NEIGHBOR = "neighbor"
        const val RELATION_ACQUAINTANCE = "acquaintance"
        const val RELATION_UNKNOWN = "unknown"

        // 数据来源常量
        const val SOURCE_USER_MENTIONED = "user_mentioned"  // 用户明确提及
        const val SOURCE_AI_INFERRED = "ai_inferred"        // AI自动推断
        const val SOURCE_NEO4J_IMPORT = "neo4j_import"      // 从Neo4j导入
        const val SOURCE_EVENT_INFERRED = "event_inferred"  // 从事件推断

        /**
         * 创建规范化的关系实体（双时态模型）
         * 规范化规则：personA总是字典序更小的那个（personA < personB）
         * 这样可以避免重复数据，并提升查询性能
         */
        fun create(
            personA: String,
            personB: String,
            relationType: String,
            description: String = "",
            confidence: Float = 0.5f,
            source: String = SOURCE_USER_MENTIONED,
            eventTime: Long? = null,  // 关系实际发生时间（可选）
            validFrom: Long? = null   // 关系开始有效时间（默认=现在）
        ): RelationshipNetworkEntity {
            // 字典序规范化
            val (normalizedA, normalizedB) = if (personA <= personB) {
                Pair(personA, personB)
            } else {
                Pair(personB, personA)
            }

            val now = System.currentTimeMillis()
            return RelationshipNetworkEntity(
                personA = normalizedA,
                personB = normalizedB,
                relationType = relationType,
                description = description,
                confidence = confidence,
                source = source,
                eventTime = eventTime,
                createdAt = now,
                updatedAt = now,
                lastConfirmedAt = now,
                validFrom = validFrom ?: now,
                validTo = null  // 新创建的关系默认为有效
            )
        }

        /**
         * 使关系失效（软删除）
         * 不是真正删除记录，而是设置validTo时间
         */
        fun RelationshipNetworkEntity.invalidate(invalidatedAt: Long = System.currentTimeMillis()): RelationshipNetworkEntity {
            return this.copy(
                validTo = invalidatedAt,
                updatedAt = invalidatedAt
            )
        }

        /**
         * 检查关系在指定时间点是否有效
         */
        fun RelationshipNetworkEntity.isValidAt(timestamp: Long): Boolean {
            return timestamp >= validFrom && (validTo == null || timestamp < validTo)
        }

        /**
         * 规范化两个人名的顺序（字典序）
         */
        fun normalize(personA: String, personB: String): Pair<String, String> {
            return if (personA <= personB) {
                Pair(personA, personB)
            } else {
                Pair(personB, personA)
            }
        }
    }
}
