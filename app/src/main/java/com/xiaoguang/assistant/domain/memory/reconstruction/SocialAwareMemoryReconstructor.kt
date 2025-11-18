package com.xiaoguang.assistant.domain.memory.reconstruction

import com.xiaoguang.assistant.domain.knowledge.CharacterBook
import com.xiaoguang.assistant.domain.knowledge.models.Relationship
import com.xiaoguang.assistant.domain.knowledge.models.InteractionRecord
import com.xiaoguang.assistant.domain.model.EnhancedSocialRelation
import com.xiaoguang.assistant.domain.memory.models.Memory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 社交关系感知记忆重构器
 *
 * 整合多维度社交关系（亲密度 + 信任度）与记忆重构
 * v2.4 核心组件 - 连接 v2.3 多维度社交系统
 *
 * 主要功能：
 * 1. 基于社交关系的重要性评估
 * 2. 关系驱动的记忆合并
 * 3. 社交冲突检测与解决
 */
@Singleton
class SocialAwareMemoryReconstructor @Inject constructor(
    private val characterBook: CharacterBook
) {

    /**
     * 基于社交关系评估记忆重要性
     *
     * @param memory 记忆对象
     * @param personName 关联人员姓名
     * @return 调整后的重要性分数 (0.0-1.0)
     */
    suspend fun assessMemoryImportanceBySocialContext(
        memory: Memory,
        personName: String
    ): Float = withContext(Dispatchers.IO) {
        // 获取社交关系
        val profile = characterBook.getProfileByName(personName) ?: return@withContext memory.importance
        val relations = characterBook.getRelationshipsFrom(profile.basicInfo.characterId)
        val relation = relations.firstOrNull()

        // 基础重要性
        var adjustedImportance = memory.importance

        // 关系强度影响 (0.8 - 1.2 范围)
        val relationshipStrength = relation?.let { (it.intimacyLevel + it.trustLevel) / 2f } ?: 0.5f
        adjustedImportance *= (0.8f + relationshipStrength * 0.4f)

        // 主人关系特殊处理
        if (relation?.isMasterRelationship == true) {
            adjustedImportance = maxOf(adjustedImportance, 0.9f)
        }

        // 频繁互动的影响
        val interactionFrequencyFactor = kotlin.math.min(
            (relation?.interactionCount ?: 0) / 100f,
            1.0f
        )
        adjustedImportance += interactionFrequencyFactor * 0.1f

        // 限制在 [0.0, 1.0] 范围内
        adjustedImportance.coerceIn(0f, 1f)
    }

    /**
     * 基于社交关系判断记忆合并优先级
     *
     * @param memory1 记忆1
     * @param memory2 记忆2
     * @return 合并优先级分数 (0.0-1.0，越高越优先)
     */
    suspend fun evaluateMergePriorityBySocialContext(
        memory1: Memory,
        memory2: Memory
    ): Float = withContext(Dispatchers.IO) {
        // 获取共同实体
        val commonEntities = memory1.relatedEntities.intersect(memory2.relatedEntities.toSet())

        if (commonEntities.isEmpty()) {
            return@withContext 0.5f // 无共同实体，使用默认优先级
        }

        var priority = 0.5f

        // 检查每个实体的关系强度
        for (entity in commonEntities) {
            val profile = characterBook.getProfileByName(entity)
            if (profile != null) {
                val relations = characterBook.getRelationshipsFrom(profile.basicInfo.characterId)
                val relation = relations.firstOrNull()

                if (relation != null) {
                    val relationshipStrength = (relation.intimacyLevel + relation.trustLevel) / 2f
                    priority = maxOf(priority, relationshipStrength)

                    // 主人关系优先级最高
                    if (relation.isMasterRelationship) {
                        return@withContext 1.0f
                    }
                }
            }
        }

        // 访问频率加成
        val avgAccessCount = (memory1.accessCount + memory2.accessCount) / 2f
        val frequencyBonus = kotlin.math.min(avgAccessCount / 50f, 0.2f)
        priority += frequencyBonus

        priority.coerceIn(0f, 1f)
    }

    /**
     * 检测社交关系导致的记忆冲突
     *
     * @param memory1 记忆1
     * @param memory2 记忆2
     * @param personName 冲突涉及的人员姓名
     * @return 社交冲突分析结果
     */
    suspend fun detectSocialConflict(
        memory1: Memory,
        memory2: Memory,
        personName: String
    ): SocialConflictAnalysis? = withContext(Dispatchers.IO) {
        val profile = characterBook.getProfileByName(personName) ?: return@withContext null
        val relations = characterBook.getRelationshipsFrom(profile.basicInfo.characterId)
        val relation = relations.firstOrNull() ?: return@withContext null

        val conflicts = mutableListOf<SocialConflictType>()

        // 检测亲密关系一致性冲突
        val intimacy1 = memory1.emotionalValence
        val intimacy2 = memory2.emotionalValence
        val intimacyDiff = kotlin.math.abs(intimacy1 - intimacy2)

        if (intimacyDiff > 0.5f) {
            conflicts.add(SocialConflictType.INTIMACY_CONFLICT)
        }

        // 检测信任关系一致性冲突
        val trustContext1 = extractTrustContext(memory1.content, personName)
        val trustContext2 = extractTrustContext(memory2.content, personName)

        if (trustContext1 != null && trustContext2 != null) {
            if (trustContext1 != trustContext2) {
                conflicts.add(SocialConflictType.TRUST_CONFLICT)
            }
        }

        // 检测社交角色一致性冲突
        val role1 = extractSocialRole(memory1.content, personName)
        val role2 = extractSocialRole(memory2.content, personName)

        if (role1 != null && role2 != null && role1 != role2) {
            conflicts.add(SocialConflictType.ROLE_CONFLICT)
        }

        if (conflicts.isEmpty()) {
            return@withContext null
        }

        SocialConflictAnalysis(
            personName = personName,
            relation = relation,
            conflicts = conflicts,
            memory1 = memory1,
            memory2 = memory2
        )
    }

    /**
     * 基于社交关系解决记忆冲突
     *
     * @param conflictAnalysis 社交冲突分析
     * @return 冲突解决建议
     */
    suspend fun resolveSocialConflict(
        conflictAnalysis: SocialConflictAnalysis
    ): SocialConflictResolution = withContext(Dispatchers.IO) {
        val relation = conflictAnalysis.relation

        // 根据关系强度和类型选择解决策略
        val resolutionStrategy = when {
            // 主人关系：直接保留最新的
            relation.isMasterRelationship -> {
                SocialResolutionStrategy.KEEP_LATEST
            }

            // 高信任度关系：倾向于合并
            relation.trustLevel > 0.7f -> {
                SocialResolutionStrategy.MERGE_SMART
            }

            // 低亲密关系：保持独立性
            relation.intimacyLevel < 0.3f -> {
                SocialResolutionStrategy.KEEP_SEPARATE
            }

            // 高冲突：需要人工干预
            conflictAnalysis.conflicts.size > 1 -> {
                SocialResolutionStrategy.REQUIRES_HUMAN
            }

            // 默认：智能合并
            else -> {
                SocialResolutionStrategy.MERGE_SMART
            }
        }

        val explanation = generateResolutionExplanation(conflictAnalysis, resolutionStrategy)

        SocialConflictResolution(
            strategy = resolutionStrategy,
            explanation = explanation,
            confidence = calculateResolutionConfidence(conflictAnalysis, resolutionStrategy),
            suggestedMergePriority = evaluateMergePriorityBySocialContext(
                conflictAnalysis.memory1,
                conflictAnalysis.memory2
            )
        )
    }

    /**
     * 基于社交互动历史预测记忆重要性变化
     *
     * @param memory 记忆
     * @param personName 关联人员姓名
     * @param futureInteractions 预期的未来互动次数
     * @return 预测的重要性变化
     */
    suspend fun predictImportanceChange(
        memory: Memory,
        personName: String,
        futureInteractions: Int = 10
    ): ImportancePrediction = withContext(Dispatchers.IO) {
        val profile = characterBook.getProfileByName(personName) ?: return@withContext ImportancePrediction(
            currentImportance = memory.importance,
            predictedImportance = memory.importance,
            changeDirection = ChangeDirection.STABLE,
            confidence = 0.5f
        )

        val relations = characterBook.getRelationshipsFrom(profile.basicInfo.characterId)
        val relation = relations.firstOrNull() ?: return@withContext ImportancePrediction(
            currentImportance = memory.importance,
            predictedImportance = memory.importance,
            changeDirection = ChangeDirection.STABLE,
            confidence = 0.5f
        )

        // 基于关系趋势预测
        val currentImportance = assessMemoryImportanceBySocialContext(memory, personName)

        // 预测未来重要性
        val interactionTrendFactor = kotlin.math.min(
            futureInteractions / 50f,
            0.3f
        )

        val relationshipStrength = (relation.intimacyLevel + relation.trustLevel) / 2f
        val trendBonus = relationshipStrength * interactionTrendFactor

        val predictedImportance = (currentImportance + trendBonus).coerceIn(0f, 1f)

        val changeDirection = when {
            predictedImportance > currentImportance + 0.1f -> ChangeDirection.INCREASING
            predictedImportance < currentImportance - 0.1f -> ChangeDirection.DECREASING
            else -> ChangeDirection.STABLE
        }

        ImportancePrediction(
            currentImportance = currentImportance,
            predictedImportance = predictedImportance,
            changeDirection = changeDirection,
            confidence = relationshipStrength
        )
    }

    /**
     * 为特定社交关系推荐记忆重构
     *
     * @param personName 人员姓名
     * @param maxSuggestions 最大建议数量
     * @return 记忆重构建议列表
     */
    suspend fun suggestMemoryReconstructionForRelation(
        personName: String,
        maxSuggestions: Int = 5
    ): List<MemoryReconstructionSuggestion> = withContext(Dispatchers.IO) {
        val profile = characterBook.getProfileByName(personName) ?: return@withContext emptyList()
        val relations = characterBook.getRelationshipsFrom(profile.basicInfo.characterId)
        val relation = relations.firstOrNull() ?: return@withContext emptyList()

        val suggestions = mutableListOf<MemoryReconstructionSuggestion>()

        // 基于互动类型推荐重构
        val recentInteractions = relation.interactionHistory.take(20)

        for (interaction in recentInteractions) {
            val suggestion = when (interaction.interactionType) {
                "亲密对话" -> {
                    MemoryReconstructionSuggestion(
                        type = ReconstructionType.REINTERPRETATION,
                        reason = "基于亲密对话重新诠释记忆",
                        suggestedContent = "补充情感理解",
                        priority = Priority.HIGH
                    )
                }

                "信任建立" -> {
                    MemoryReconstructionSuggestion(
                        type = ReconstructionType.UPDATE,
                        reason = "基于信任建立更新记忆",
                        suggestedContent = "增加信任相关内容",
                        priority = Priority.HIGH
                    )
                }

                "冲突" -> {
                    MemoryReconstructionSuggestion(
                        type = ReconstructionType.CORRECTION,
                        reason = "基于冲突纠正记忆偏差",
                        suggestedContent = "客观记录冲突事件",
                        priority = Priority.CRITICAL
                    )
                }

                else -> null
            }

            suggestion?.let { suggestions.add(it) }
        }

        suggestions.distinctBy { it.type }
            .sortedBy { it.priority.ordinal }
            .take(maxSuggestions)
    }

    // ==================== 私有方法 ====================

    private fun extractTrustContext(content: String, personName: String): String? {
        // 简化实现：从内容中提取信任相关关键词
        val trustKeywords = listOf("信任", "可靠", "背叛", "诚实", "谎言")
        val found = trustKeywords.find { content.contains(it) }
        return found
    }

    private fun extractSocialRole(content: String, personName: String): String? {
        // 简化实现：从内容中提取角色信息
        val roleKeywords = mapOf(
            "朋友" to listOf("朋友", "好友"),
            "家人" to listOf("家人", "亲属", "父母", "孩子"),
            "同事" to listOf("同事", "合作", "工作伙伴"),
            "恋人" to listOf("恋人", "男朋友", "女朋友", "伴侣")
        )

        for ((role, keywords) in roleKeywords) {
            if (keywords.any { content.contains(it) }) {
                return role
            }
        }

        return null
    }

    private fun generateResolutionExplanation(
        analysis: SocialConflictAnalysis,
        strategy: SocialResolutionStrategy
    ): String {
        val personName = analysis.personName
        val relationStrength = (analysis.relation.intimacyLevel + analysis.relation.trustLevel) / 2f

        return when (strategy) {
            SocialResolutionStrategy.KEEP_LATEST -> {
                "与 $personName 的关系为最优先级，选择最新记忆"
            }

            SocialResolutionStrategy.MERGE_SMART -> {
                "与 $personName 的关系强度较高 (${"%.2f".format(relationStrength)})，智能合并冲突内容"
            }

            SocialResolutionStrategy.KEEP_SEPARATE -> {
                "与 $personName 的亲密度较低，保持记忆独立性"
            }

            SocialResolutionStrategy.REQUIRES_HUMAN -> {
                "冲突严重且多维度，涉及与 $personName 的复杂社交关系，建议人工判断"
            }
        }
    }

    private fun calculateResolutionConfidence(
        analysis: SocialConflictAnalysis,
        strategy: SocialResolutionStrategy
    ): Float {
        val relationStrength = (analysis.relation.intimacyLevel + analysis.relation.trustLevel) / 2f

        return when (strategy) {
            SocialResolutionStrategy.KEEP_LATEST -> 0.9f
            SocialResolutionStrategy.MERGE_SMART -> relationStrength
            SocialResolutionStrategy.KEEP_SEPARATE -> 0.7f
            SocialResolutionStrategy.REQUIRES_HUMAN -> 0.4f
        }
    }
}

/**
 * 社交冲突类型
 */
enum class SocialConflictType {
    INTIMACY_CONFLICT,     // 亲密关系冲突
    TRUST_CONFLICT,        // 信任关系冲突
    ROLE_CONFLICT          // 社交角色冲突
}

/**
 * 社交冲突分析
 */
data class SocialConflictAnalysis(
    val personName: String,
    val relation: Relationship,
    val conflicts: List<SocialConflictType>,
    val memory1: Memory,
    val memory2: Memory
)

/**
 * 社交冲突解决策略
 */
enum class SocialResolutionStrategy {
    KEEP_LATEST,        // 保留最新的
    MERGE_SMART,        // 智能合并
    KEEP_SEPARATE,      // 保持独立
    REQUIRES_HUMAN      // 需要人工干预
}

/**
 * 社交冲突解决结果
 */
data class SocialConflictResolution(
    val strategy: SocialResolutionStrategy,
    val explanation: String,
    val confidence: Float,
    val suggestedMergePriority: Float
)

/**
 * 重要性变化预测
 */
data class ImportancePrediction(
    val currentImportance: Float,
    val predictedImportance: Float,
    val changeDirection: ChangeDirection,
    val confidence: Float
)

/**
 * 重要性变化方向
 */
enum class ChangeDirection {
    INCREASING,
    DECREASING,
    STABLE
}

/**
 * 记忆重构建议
 */
data class MemoryReconstructionSuggestion(
    val type: ReconstructionType,
    val reason: String,
    val suggestedContent: String,
    val priority: Priority
)

/**
 * 优先级
 */
enum class Priority {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
