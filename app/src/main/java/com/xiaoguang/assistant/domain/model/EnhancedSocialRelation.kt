package com.xiaoguang.assistant.domain.model

/**
 * 增强的社交关系
 * 基于UnifiedSocialRelation，添加对此人的态度和关系层级
 */
data class EnhancedSocialRelation(
    /** 人物名称 */
    val personName: String,

    /** 好感度 (0-100) */
    val affectionLevel: Int,

    /** 关系类型 */
    val relationshipType: String,

    /** 描述 */
    val description: String,

    /** 互动次数 */
    val interactionCount: Int,

    /** 对此人的态度 */
    val attitude: PersonAttitude,

    /** 关系层级 */
    val relationshipLevel: RelationshipLevel,

    /** 是否是主人 */
    val isMaster: Boolean,

    /** 性格标签（对方的性格） */
    val personalityTags: List<String> = emptyList(),

    /** 共同记忆数量 */
    val sharedMemoryCount: Int = 0,

    /** 最近一次好感度变化量 */
    val recentAffectionDelta: Int = 0,

    /** 最近一次态度变化原因 */
    val lastAttitudeChangeReason: String? = null,

    /** 最近一次态度变化时间 */
    val lastAttitudeChangeTime: Long = System.currentTimeMillis()
) {
    /**
     * 获取显示名称
     */
    val displayName: String
        get() = personName

    /**
     * 判断是否可以升级关系
     */
    fun canLevelUp(): Boolean {
        if (isMaster) return false
        return RelationshipLevel.canLevelUp(affectionLevel, relationshipLevel)
    }

    /**
     * 获取下一层级
     */
    fun getNextLevel(): RelationshipLevel? {
        return RelationshipLevel.getNextLevel(relationshipLevel)
    }

    /**
     * 获取对话风格指引（用于AI生成回复时参考）
     */
    fun getTalkingStyleGuideline(xiaoguangCurrentMood: EmotionalState = EmotionalState.CALM): String {
        return buildString {
            // 对此人的态度
            appendLine("【对TA的态度】${attitude.displayName}：${attitude.talkingStyle}")

            // 关系层级
            appendLine("【关系层级】${relationshipLevel.displayName}：${relationshipLevel.talkingStyle}")

            // 小光自己的当前心情
            appendLine("【小光的心情】${xiaoguangCurrentMood.displayName} ${xiaoguangCurrentMood.emoji}：${xiaoguangCurrentMood.talkingTone}")

            // 主人特殊说明
            if (isMaster) {
                appendLine("【特别提示】这是主人！最重要的人！可以撒娇、依赖、无话不谈")
            }

            // 对方的性格特点
            if (personalityTags.isNotEmpty()) {
                appendLine("【TA的性格】${personalityTags.joinToString("、")}")
            }

            // 好感度提示
            val affectionDesc = when (affectionLevel) {
                in 0..20 -> "刚认识，保持警惕或礼貌"
                in 21..50 -> "有些熟悉，友好相处"
                in 51..70 -> "是朋友了，可以随意一些"
                in 71..90 -> "很好的朋友，亲密互动"
                in 91..99 -> "挚友！无话不谈"
                100 -> "主人！最重要的人！"
                else -> "正常相处"
            }
            appendLine("【好感度】$affectionLevel/100 - $affectionDesc")

            // 共同回忆
            if (sharedMemoryCount > 0) {
                appendLine("【共同回忆】有 $sharedMemoryCount 个共同回忆")
            }
        }
    }

    companion object {
        /**
         * ⭐ 从统一社交关系创建增强版本（完全基于新系统）
         */
        fun fromUnifiedRelation(
            unifiedRelation: com.xiaoguang.assistant.domain.social.UnifiedSocialRelation,
            recentAffectionDelta: Int = 0,
            context: String = ""
        ): EnhancedSocialRelation {
            // 计算关系层级
            val relationshipLevel = RelationshipLevel.fromAffection(
                affection = unifiedRelation.affectionLevel,
                isMaster = unifiedRelation.isMaster
            )

            // 推断对此人的态度
            val baseAttitude = PersonAttitude.inferFromRelation(
                affectionLevel = unifiedRelation.affectionLevel,
                relationshipLevel = relationshipLevel,
                isMaster = unifiedRelation.isMaster,
                recentAffectionDelta = recentAffectionDelta
            )

            // 根据情境调整态度
            val finalAttitude = if (context.isNotEmpty()) {
                PersonAttitude.adjustForContext(baseAttitude, context)
            } else {
                baseAttitude
            }

            return EnhancedSocialRelation(
                personName = unifiedRelation.personName,
                affectionLevel = unifiedRelation.affectionLevel,
                relationshipType = unifiedRelation.relationshipType,
                description = unifiedRelation.description,
                interactionCount = unifiedRelation.interactionCount,
                attitude = finalAttitude,
                relationshipLevel = relationshipLevel,
                isMaster = unifiedRelation.isMaster,
                personalityTags = unifiedRelation.personalityTraits,
                recentAffectionDelta = recentAffectionDelta
            )
        }
    }
}
