package com.xiaoguang.assistant.domain.model

/**
 * 人际关系系统增强方案
 *
 * 这是未来版本的扩展方向，目前作为文档和接口定义
 */

/**
 * 关系历史事件
 * 记录与某人之间的重要事件
 */
data class RelationshipEvent(
    val timestamp: Long,
    val personName: String,
    val eventType: EventType,
    val description: String,
    val affectionImpact: Int,  // 对好感度的影响
    val importance: Int  // 重要程度 1-5
) {
    enum class EventType {
        FIRST_MEET,         // 初次见面
        HELP_RECEIVED,      // 得到帮助
        HELP_GIVEN,         // 给予帮助
        CONFLICT,           // 冲突
        RECONCILIATION,     // 和解
        CELEBRATION,        // 庆祝（生日等）
        DEEP_CONVERSATION,  // 深度对话
        PRAISE,            // 夸奖
        CRITICISM,         // 批评
        GIFT,              // 礼物
        MILESTONE          // 里程碑（成为朋友、好友等）
    }
}

/**
 * 多维关系评估
 * 不只是好感度，还有其他维度
 */
data class MultiDimensionalRelationship(
    val affection: Int,      // 好感度 0-100
    val trust: Int,          // 信任度 0-100
    val respect: Int,        // 尊重度 0-100
    val intimacy: Int,       // 亲密度 0-100
    val dependency: Int      // 依赖度 0-100（双向，对方对小光的依赖）
) {
    /**
     * 综合评分
     */
    fun overallScore(): Float {
        return (affection * 0.3f + trust * 0.25f + respect * 0.15f +
                intimacy * 0.2f + dependency * 0.1f)
    }

    companion object {
        /**
         * 从单一好感度创建默认多维关系
         */
        fun fromAffection(affection: Int): MultiDimensionalRelationship {
            return MultiDimensionalRelationship(
                affection = affection,
                trust = affection,  // 默认与好感度一致
                respect = affection.coerceAtLeast(30),  // 最低保持基本尊重
                intimacy = (affection * 0.8f).toInt(),  // 亲密度略低于好感度
                dependency = (affection * 0.6f).toInt()  // 依赖度更低
            )
        }
    }
}

/**
 * 性格特征
 * 通过互动自动推断对方性格
 */
data class PersonalityProfile(
    /** 外向 vs 内向 (-100 to 100) */
    val extraversion: Int = 0,

    /** 温柔 vs 严厉 (-100 to 100) */
    val agreeableness: Int = 0,

    /** 认真 vs 随意 (-100 to 100) */
    val conscientiousness: Int = 0,

    /** 稳定 vs 敏感 (-100 to 100) */
    val emotionalStability: Int = 0,

    /** 开放 vs 保守 (-100 to 100) */
    val openness: Int = 0,

    /** 幽默 vs 严肃 (-100 to 100) */
    val humor: Int = 0,

    /** 置信度 (0-100) */
    val confidence: Int = 0  // 样本越多，置信度越高
) {
    /**
     * 生成性格描述
     */
    fun getDescription(): String {
        return buildString {
            if (extraversion > 30) append("外向、")
            else if (extraversion < -30) append("内向、")

            if (agreeableness > 30) append("温柔、")
            else if (agreeableness < -30) append("严厉、")

            if (conscientiousness > 30) append("认真、")
            else if (conscientiousness < -30) append("随性、")

            if (humor > 30) append("幽默、")
            else if (humor < -30) append("严肃、")

            if (openness > 30) append("开放、")
            else if (openness < -30) append("保守、")

            if (isEmpty()) append("性格未知")
            else removeSuffix("、")
        }
    }

    /**
     * 获取沟通建议
     */
    fun getCommunicationAdvice(): String {
        return when {
            extraversion > 50 -> "对方很外向，可以更热情活泼"
            extraversion < -50 -> "对方比较内向，说话要温柔委婉"
            agreeableness < -50 -> "对方比较严厉，不要太随便"
            humor > 50 -> "对方很幽默，可以开玩笑"
            else -> "正常交流即可"
        }
    }
}

/**
 * 动态标签
 * 自动生成和更新的人物标签
 */
data class DynamicTag(
    val tag: String,
    val confidence: Float,  // 置信度 0-1
    val createdAt: Long,
    val source: TagSource
) {
    enum class TagSource {
        USER_MENTIONED,     // 用户明确提到
        AI_INFERRED,        // AI推断
        BEHAVIOR_OBSERVED,  // 行为观察
        TOPIC_PREFERENCE    // 话题偏好
    }

    companion object {
        /**
         * 常见标签建议
         */
        val COMMON_TAGS = listOf(
            // 性格类
            "外向", "内向", "温柔", "严厉", "幽默", "严肃",
            // 关系类
            "经常夸奖小光", "对小光很好", "喜欢开玩笑",
            // 兴趣类
            "喜欢动漫", "喜欢游戏", "喜欢音乐", "喜欢运动",
            // 状态类
            "工作很忙", "最近心情不好", "压力很大", "很开心",
            // 习惯类
            "早睡早起", "夜猫子", "话很多", "话很少"
        )
    }
}

/**
 * 人际网络节点
 * 记录人物之间的关系
 */
data class RelationshipNode(
    val personA: String,
    val personB: String,
    val relationType: String,  // 如：朋友、同事、家人、夫妻、父子等
    val description: String = "",
    val confidence: Float = 1.0f
) {
    /**
     * 常见关系类型
     */
    companion object {
        val COMMON_RELATIONS = listOf(
            "朋友", "好友", "同事", "同学",
            "父子", "父女", "母子", "母女",
            "夫妻", "兄弟", "姐妹", "兄妹",
            "恋人", "室友", "邻居", "师生"
        )
    }
}

/**
 * 关系变化通知
 * 当关系发生重要变化时触发
 */
sealed class RelationshipChangeNotification(
    val personName: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    /** 关系升级 */
    data class LevelUp(
        val person: String,
        val oldLevel: RelationshipLevel,
        val newLevel: RelationshipLevel
    ) : RelationshipChangeNotification(person)

    /** 关系降级 */
    data class LevelDown(
        val person: String,
        val oldLevel: RelationshipLevel,
        val newLevel: RelationshipLevel,
        val reason: String
    ) : RelationshipChangeNotification(person)

    /** 好感度大幅变化 */
    data class AffectionSignificantChange(
        val person: String,
        val oldAffection: Int,
        val newAffection: Int,
        val delta: Int
    ) : RelationshipChangeNotification(person)

    /** 长时间未互动 */
    data class LongTimeNoInteraction(
        val person: String,
        val daysSinceLastInteraction: Int
    ) : RelationshipChangeNotification(person)

    /** 重要纪念日 */
    data class Anniversary(
        val person: String,
        val event: String,
        val yearsPassed: Int
    ) : RelationshipChangeNotification(person)
}

/**
 * 关系维护建议
 * AI主动建议维护某些关系
 */
data class RelationshipMaintenanceSuggestion(
    val personName: String,
    val suggestionType: SuggestionType,
    val reason: String,
    val priority: Priority
) {
    enum class SuggestionType {
        CHECK_IN,           // 问候关心
        APOLOGIZE,          // 道歉
        THANK,              // 感谢
        CELEBRATE,          // 庆祝
        REMIND_MASTER       // 提醒主人
    }

    enum class Priority {
        LOW, MEDIUM, HIGH, URGENT
    }
}
