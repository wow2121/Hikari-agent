package com.xiaoguang.assistant.domain.knowledge.models

import java.util.UUID

/**
 * Character Book 数据模型
 * 用于存储角色档案、记忆、关系等个人化知识
 */

/**
 * 记忆类别
 */
enum class MemoryCategory(val displayName: String, val priority: Int) {
    CORE("核心记忆", 100),           // 最重要的记忆（如身份、关键事件）
    LONG_TERM("长期记忆", 80),       // 长期保留的记忆
    SHORT_TERM("短期记忆", 50),      // 最近的交互记忆
    EPISODIC("情节记忆", 70),        // 特定事件的记忆
    SEMANTIC("语义记忆", 60),        // 概念性知识
    PREFERENCE("偏好", 75)           // 用户偏好和兴趣
}

/**
 * 角色基本信息
 */
data class BasicInfo(
    val characterId: String,                // 角色唯一ID
    val name: String,                       // 姓名
    val aliases: List<String> = emptyList(), // ✅ 别名列表（如昵称、曾用名、临时代号）
    val nickname: String? = null,           // 昵称（保留用于兼容）
    val gender: String? = null,             // 性别
    val age: Int? = null,                   // 年龄
    val platformId: String? = null,         // 平台ID（如QQ号）
    val avatarUrl: String? = null,          // 头像URL
    val bio: String? = null,                // 简介
    val isMaster: Boolean = false,          // ⭐ 是否是主人（唯一且永久锁定）
    val voiceprintId: String? = null,       // ✅ 声纹ID（关联VoiceprintProfile）
    val isStranger: Boolean = false,        // ✅ 是否是陌生人（未命名）
    val createdAt: Long = System.currentTimeMillis(),
    val lastSeenAt: Long = System.currentTimeMillis(),
    val metadata: Map<String, String> = emptyMap()  // ✅ 额外元数据
) {
    init {
        // ⚠️ 主人规则验证
        if (isMaster) {
            // 主人标识一旦设置，必须明确标记
            require(characterId.isNotEmpty()) { "主人必须有明确的characterId" }
        }
    }

    /**
     * 获取所有可能的名称（含别名）
     */
    fun getAllNames(): List<String> {
        val names = mutableListOf(name)
        names.addAll(aliases)
        nickname?.let { names.add(it) }
        return names.distinct()
    }

    /**
     * 是否匹配给定名称（支持别名）
     */
    fun matchesName(queryName: String): Boolean {
        return getAllNames().any { it.equals(queryName, ignoreCase = true) }
    }
}

/**
 * 性格特征
 */
data class Personality(
    val traits: Map<String, Float> = emptyMap(), // 性格特质 (特质名 -> 强度 0-1)
    val description: String? = null,        // 性格描述
    val communicationStyle: String? = null, // 沟通风格
    val speechStyle: List<String> = emptyList(), // 说话风格
    val coreValues: List<String> = emptyList(),  // 核心价值观
    val emotionalPattern: String? = null,   // 情绪模式
    val extractedAt: Long = System.currentTimeMillis()
) {
    /**
     * 添加或更新性格特质
     */
    fun updateTrait(trait: String, strength: Float): Personality {
        return copy(
            traits = traits + (trait to strength.coerceIn(0f, 1f)),
            extractedAt = System.currentTimeMillis()
        )
    }

    /**
     * 获取主要性格特质（前5个）
     */
    fun getTopTraits(limit: Int = 5): List<Pair<String, Float>> {
        return traits.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key to it.value }
    }
}

/**
 * 偏好设置
 */
data class Preferences(
    val interests: List<String> = emptyList(),      // 兴趣爱好
    val likes: List<String> = emptyList(),          // 喜欢的事物
    val dislikes: List<String> = emptyList(),       // 不喜欢的事物
    val habits: List<String> = emptyList(),         // 习惯
    val goals: List<String> = emptyList(),          // 目标
    val topics: Map<String, Float> = emptyMap()     // 感兴趣的话题 (话题 -> 兴趣度)
)

/**
 * 背景故事
 */
data class Background(
    val story: String? = null,                      // 背景故事
    val occupation: String? = null,                 // 职业
    val location: String? = null,                   // 所在地
    val skills: List<String> = emptyList(),         // 技能列表
    val importantEvents: List<String> = emptyList(), // 重要事件
    val milestones: Map<Long, String> = emptyMap()  // 里程碑 (时间戳 -> 事件)
)

/**
 * 角色档案
 */
data class CharacterProfile(
    val basicInfo: BasicInfo,
    val personality: Personality = Personality(),
    val preferences: Preferences = Preferences(),
    val background: Background = Background(),
    val customFields: Map<String, Any> = emptyMap(), // 自定义字段
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * 转换为Character Card格式（兼容SillyTavern）
     */
    fun toCharacterCard(): Map<String, Any> {
        return mapOf(
            "name" to basicInfo.name,
            "description" to (personality.description ?: ""),
            "personality" to personality.traits.entries.joinToString(", ") { "${it.key}: ${it.value}" },
            "scenario" to (background.story ?: ""),
            "first_mes" to "",
            "mes_example" to "",
            "extensions" to mapOf(
                "xiaoguang" to mapOf(
                    "character_id" to basicInfo.characterId,
                    "nickname" to basicInfo.nickname,
                    "platform_id" to basicInfo.platformId,
                    "preferences" to preferences,
                    "background" to background,
                    "custom_fields" to customFields
                )
            )
        )
    }

    companion object {
        /**
         * 从Character Card格式导入
         */
        fun fromCharacterCard(cardData: Map<String, Any>): CharacterProfile {
            val name = cardData["name"] as? String ?: "Unknown"
            val description = cardData["description"] as? String

            @Suppress("UNCHECKED_CAST")
            val extensions = cardData["extensions"] as? Map<String, Any>
            @Suppress("UNCHECKED_CAST")
            val xiaoguangExt = extensions?.get("xiaoguang") as? Map<String, Any>

            val characterId = xiaoguangExt?.get("character_id") as? String
                ?: "imported_${System.currentTimeMillis()}"
            val nickname = xiaoguangExt?.get("nickname") as? String
            val platformId = xiaoguangExt?.get("platform_id") as? String

            return CharacterProfile(
                basicInfo = BasicInfo(
                    characterId = characterId,
                    name = name,
                    nickname = nickname,
                    platformId = platformId,
                    createdAt = System.currentTimeMillis(),
                    lastSeenAt = System.currentTimeMillis()
                ),
                personality = Personality(
                    description = description
                ),
                customFields = xiaoguangExt?.get("custom_fields") as? Map<String, Any> ?: emptyMap(),
                updatedAt = System.currentTimeMillis()
            )
        }
    }
}

/**
 * 角色记忆
 */
data class CharacterMemory(
    val memoryId: String = UUID.randomUUID().toString(),
    val characterId: String,               // 所属角色ID
    val category: MemoryCategory,          // 记忆类别
    val content: String,                   // 记忆内容
    val importance: Float = 0.5f,          // 重要性 (0-1)
    val emotionalValence: Float = 0f,      // 情感效价 (-1 负面 ~ 0 中性 ~ +1 正面)
    val emotionTag: String? = null,        // ⭐ 情绪标签(如HAPPY, SAD等)
    val emotionIntensity: Float? = null,   // ⭐ 情绪强度(0.0-1.0)
    val tags: List<String> = emptyList(),  // 标签
    val relatedMemories: List<String> = emptyList(), // 相关记忆ID
    val accessCount: Int = 0,              // 访问次数
    val lastAccessed: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long? = null            // 过期时间（null=永久）
) {
    /**
     * 记录访问
     */
    fun recordAccess(): CharacterMemory {
        return copy(
            accessCount = accessCount + 1,
            lastAccessed = System.currentTimeMillis()
        )
    }

    /**
     * 是否已过期
     */
    fun isExpired(): Boolean {
        return expiresAt != null && System.currentTimeMillis() > expiresAt
    }

    /**
     * 计算记忆强度（综合重要性和访问频率）
     */
    fun getMemoryStrength(): Float {
        val recencyFactor = kotlin.math.exp(-0.001 * (System.currentTimeMillis() - lastAccessed) / 1000.0).toFloat()
        val accessFactor = kotlin.math.min(accessCount.toFloat() / 10f, 1f)
        return (importance * 0.5f + recencyFactor * 0.3f + accessFactor * 0.2f).coerceIn(0f, 1f)
    }
}

/**
 * 关系类型
 */
enum class RelationType(val displayName: String) {
    MASTER("主人"),
    FAMILY("家人"),
    FRIEND("朋友"),
    CLOSE_FRIEND("好友"),
    ACQUAINTANCE("熟人"),
    COLLEAGUE("同事"),
    ROMANTIC("恋人"),
    LOVER("爱人"),  // 添加LOVER支持
    RIVAL("竞争对手"),  // 添加RIVAL支持
    STRANGER("陌生人"),
    DISLIKE("不喜欢"),
    OTHER("其他"),
    CUSTOM("自定义")
}

/**
 * 交互记录
 * ⭐ v2.3+ 支持多维度影响
 */
data class InteractionRecord(
    val timestamp: Long,
    val interactionType: String,           // 交互类型（如：message, gift, help）
    val content: String,                   // 交互内容
    val emotionalImpact: Float = 0f,       // 情感影响 (-1 ~ +1) 影响亲密度
    val trustImpact: Float = 0f,           // ⭐ 信任影响 (-1 ~ +1) 影响信任度
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * 关系
 */
data class Relationship(
    val relationshipId: String = UUID.randomUUID().toString(),
    val fromCharacterId: String,           // 源角色ID
    val toCharacterId: String,             // 目标角色ID
    val relationType: RelationType,        // 关系类型
    val intimacyLevel: Float = 0.5f,       // 亲密度 (0-1)
    val trustLevel: Float = 0.5f,          // 信任度 (0-1)
    val description: String? = null,       // 关系描述
    val interactionHistory: List<InteractionRecord> = emptyList(), // 交互历史（最近N条）
    val firstMetAt: Long = System.currentTimeMillis(),
    val lastInteractionAt: Long = System.currentTimeMillis(),
    val interactionCount: Int = 0,
    val isMasterRelationship: Boolean = false  // ⭐ 是否是与主人的关系（锁定在满值）
) {
    init {
        // ⚠️ 主人关系规则：intimacy和trust必须锁定在满值
        if (isMasterRelationship) {
            require(relationType == RelationType.MASTER) { "主人关系的类型必须是MASTER" }
        }
    }

    // 别名属性，用于兼容性
    val intimacy: Float get() = intimacyLevel
    val trust: Float get() = trustLevel
    val type: RelationType get() = relationType

    /**
     * 计算关系强度 (0-1)
     */
    fun getStrength(): Float {
        return (intimacyLevel * 0.4f + trustLevel * 0.3f + (interactionCount.coerceAtMost(100) / 100f) * 0.3f)
    }

    /**
     * 获取实际的亲密度（如果是主人，锁定为1.0）
     */
    val actualIntimacyLevel: Float
        get() = if (isMasterRelationship) 1.0f else intimacyLevel

    /**
     * 获取实际的信任度（如果是主人，锁定为1.0）
     */
    val actualTrustLevel: Float
        get() = if (isMasterRelationship) 1.0f else trustLevel

    /**
     * 记录新的交互
     * ⚠️ 如果是主人关系，intimacy和trust保持锁定，但仍记录交互历史
     */
    fun recordInteraction(record: InteractionRecord, maxHistory: Int = 50): Relationship {
        val newHistory = (interactionHistory + record).takeLast(maxHistory)

        // ⚠️ 主人关系：不更新intimacy和trust，永远锁定在1.0
        val newIntimacy = if (isMasterRelationship) {
            1.0f
        } else {
            (intimacyLevel + record.emotionalImpact * 0.01f).coerceIn(0f, 1f)
        }

        val newTrust = if (isMasterRelationship) {
            1.0f
        } else {
            (trustLevel + record.trustImpact * 0.01f).coerceIn(0f, 1f)
        }

        return copy(
            interactionHistory = newHistory,
            intimacyLevel = newIntimacy,
            trustLevel = newTrust,
            lastInteractionAt = record.timestamp,
            interactionCount = interactionCount + 1
        )
    }

    /**
     * 获取关系强度
     * ⚠️ 如果是主人，直接返回1.0（最高）
     */
    fun getRelationshipStrength(): Float {
        if (isMasterRelationship) {
            return 1.0f
        }

        val intimacyFactor = intimacyLevel * 0.4f
        val trustFactor = trustLevel * 0.3f
        val frequencyFactor = kotlin.math.min(interactionCount.toFloat() / 100f, 1f) * 0.3f
        return (intimacyFactor + trustFactor + frequencyFactor).coerceIn(0f, 1f)
    }
}

/**
 * Character Book配置
 */
data class CharacterBookConfig(
    val maxMemoriesPerCharacter: Int = 1000,
    val shortTermMemoryExpiration: Long = 7 * 24 * 60 * 60 * 1000L, // 7天
    val memoryConsolidationThreshold: Int = 10, // 访问10次后升级为长期记忆
    val enableAutoPersonalityExtraction: Boolean = true,
    val enableRelationshipTracking: Boolean = true
)

/**
 * 性格洞察分析结果
 *
 * v2.4新增：用于存储从记忆和互动中提取的性格分析
 */
data class PersonalityInsights(
    val traits: Map<String, Float> = emptyMap(),        // 性格特征及强度 (0-1)
    val interests: List<String> = emptyList(),          // 兴趣列表
    val summary: String = "",                           // 性格总结
    val confidence: Float = 0f                          // 分析置信度 (0-1)
)

// ==================== LLM主导的记忆整合支持类 ====================

/**
 * 记忆整合结果
 */
data class MemoryConsolidationResult(
    val totalEvaluated: Int,                    // 总评估数量
    val consolidated: Int,                       // 已整合数量
    val deferred: Int,                          // 暂缓数量
    val rejected: Int,                          // 拒绝数量
    val details: List<MemoryConsolidationDetail> // 详细信息
) {
    override fun toString(): String = buildString {
        appendLine("记忆整合结果:")
        appendLine("  总评估: $totalEvaluated")
        appendLine("  已整合: $consolidated")
        appendLine("  暂缓: $deferred")
        appendLine("  拒绝: $rejected")
        if (details.isNotEmpty()) {
            appendLine("  整合详情:")
            details.take(5).forEach { detail ->
                appendLine("    - ${detail.memoryId}: ${detail.decision} (${detail.reasoning})")
            }
            if (details.size > 5) {
                appendLine("    ... 还有 ${details.size - 5} 条")
            }
        }
    }
}

/**
 * 记忆整合详情
 */
data class MemoryConsolidationDetail(
    val memoryId: String,
    val content: String,
    val decision: ConsolidationDecision,
    val reasoning: String,
    val confidence: Float
)

/**
 * 记忆整合决策
 */
enum class ConsolidationDecision {
    CONSOLIDATED,    // 已整合
    DEFERRED,        // 暂缓
    REJECTED         // 拒绝
}

/**
 * LLM记忆评估结果
 */
data class MemoryEvaluationResult(
    val memory: CharacterMemory,
    val shouldConsolidate: Boolean,
    val confidence: Float,
    val reasoning: String,
    val llmScore: Float?
)

/**
 * 记忆评估上下文
 */
data class MemoryEvaluationContext(
    val characterName: String,
    val characterProfile: CharacterProfile?,
    val relatedLongTermMemories: List<CharacterMemory>,
    val relationships: List<Relationship>,
    val currentMemoryStats: MemoryStats
)

/**
 * 记忆统计信息
 */
data class MemoryStats(
    val totalMemories: Int,
    val longTermCount: Int,
    val shortTermCount: Int,
    val avgImportance: Float
)

/**
 * LLM评估响应数据模型
 */
data class LlmEvaluationResponse(
    val evaluations: List<LlmMemoryEvaluation>
)

/**
 * 单个记忆的LLM评估结果
 */
data class LlmMemoryEvaluation(
    val id: Int,
    val shouldConsolidate: Boolean,
    val confidence: Float,
    val semanticValue: Float,
    val emotionalDepth: Float,
    val associationValue: Float,
    val characterDevelopment: Float,
    val practicalValue: Float,
    val reasoning: String,
    val overallScore: Float
)

/**
 * 记忆整合决策记录（用于学习反馈）
 */
data class MemoryConsolidationDecision(
    val characterId: String,
    val memoryId: String,
    val wasConsolidated: Boolean,
    val llmScore: Float,
    val confidence: Float,
    val memoryImportance: Float,
    val memoryAccessCount: Int,
    val memoryAgeDays: Long,
    val reasoning: String,
    val timestamp: Long
)

/**
 * 记忆整合统计
 */
data class ConsolidationStatistics(
    val characterId: String,
    val totalDecisions: Int,
    val totalConsolidated: Int,
    val avgLlmScore: Float,
    val avgConfidence: Float,
    val lastUpdated: Long
)

/**
 * 决策模式分析结果
 */
data class DecisionPatternAnalysis(
    val characterId: String,
    val consolidatedRate: Float,
    val avgLlmScore: Float,
    val importanceGap: Float,
    val timestamp: Long
)

