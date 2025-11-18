package com.xiaoguang.assistant.domain.flow.model

/**
 * 内心想法
 * 小光的内心活动
 */
data class InnerThought(
    val type: ThoughtType,
    val content: String,
    val urgency: Float,  // 0-1，紧急程度
    val timestamp: Long = System.currentTimeMillis(),
    val purpose: String? = null,  // ✅ 想法的明确目的（如："想问XXX"，"想提醒XXX"）
    val triggerContext: String? = null  // ✅ 触发这个想法的上下文
) {
    /**
     * 是否是紧急想法
     */
    fun isUrgent(): Boolean = urgency > 0.7f

    /**
     * 是否需要立即表达
     */
    fun needsImmediateExpression(): Boolean = urgency > 0.9f

    /**
     * ✅ 是否是有明确目的的想法
     */
    fun hasPurpose(): Boolean = purpose != null && purpose.isNotBlank()
}

/**
 * 想法类型
 */
enum class ThoughtType(
    val displayName: String,
    val description: String
) {
    CURIOSITY(
        "好奇",
        "对话题或事件感到好奇"
    ),
    CARE(
        "关心",
        "关心主人或朋友的状态"
    ),
    BOREDOM(
        "无聊",
        "感到无聊，想找点事做"
    ),
    EXCITEMENT(
        "兴奋",
        "对某事感到兴奋想分享"
    ),
    WORRY(
        "担心",
        "担心主人或某事"
    ),
    MEMORY_RECALL(
        "回忆",
        "想起了过去的事情"
    ),
    RANDOM(
        "随想",
        "随机的想法"
    ),
    QUESTION(
        "疑问",
        "有疑问想要问"
    ),
    SHARE(
        "分享",
        "想分享某个想法或见闻"
    ),
    EMOTION(
        "情绪",
        "情绪相关的想法"
    ),
    GREETING(
        "问候",
        "主动问候"
    ),
    MISSING(
        "想念",
        "想念某人"
    ),
    TOOL_RESULT(
        "工具结果",
        "工具调用的结果"
    ),
    REMINDER(
        "提醒",
        "提醒主人注意待办或日程"
    )
}

/**
 * 想法集合
 */
data class Thoughts(
    val innerThoughts: List<InnerThought> = emptyList(),
    val proactivityScore: Float = 0f,  // 主动性评分 0-1
    val moodInfluence: Float = 1.0f,   // 情绪影响系数
    val shouldConsiderSpeaking: Boolean = false
) {
    /**
     * 获取最紧急的想法
     */
    fun getMostUrgent(): InnerThought? {
        return innerThoughts.maxByOrNull { it.urgency }
    }

    /**
     * 获取所有紧急想法
     */
    fun getUrgentThoughts(): List<InnerThought> {
        return innerThoughts.filter { it.isUrgent() }
    }

    /**
     * 是否有任何想法
     */
    fun hasAnyThought(): Boolean {
        return innerThoughts.isNotEmpty()
    }

    /**
     * ✅ 获取有明确目的的想法
     */
    fun getPurposefulThoughts(): List<InnerThought> {
        return innerThoughts.filter { it.hasPurpose() }
    }
}
