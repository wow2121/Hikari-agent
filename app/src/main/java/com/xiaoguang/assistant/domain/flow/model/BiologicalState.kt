package com.xiaoguang.assistant.domain.flow.model

/**
 * 生物状态
 * 模拟小光的生物钟和疲劳状态
 */
data class BiologicalState(
    /**
     * 当前时段
     */
    val timeOfDay: TimeOfDay,

    /**
     * 精力水平 (0.0 - 1.0)
     * 1.0 = 精力充沛
     * 0.0 = 完全疲惫
     */
    val energyLevel: Float,

    /**
     * 对话疲劳度 (0.0 - 1.0)
     * 0.0 = 完全不累
     * 1.0 = 非常累，不想说话了
     */
    val conversationFatigue: Float,

    /**
     * 最近对话次数（用于计算疲劳）
     */
    val recentConversationCount: Int,

    /**
     * 上次对话时间
     */
    val lastConversationTime: Long,

    /**
     * 当前时间戳
     */
    val currentTime: Long
) {
    /**
     * 综合精力（考虑时段和对话疲劳）
     */
    fun getOverallEnergy(): Float {
        return (energyLevel * (1f - conversationFatigue * 0.5f)).coerceIn(0f, 1f)
    }

    /**
     * 是否需要休息
     */
    fun needsRest(): Boolean {
        return getOverallEnergy() < 0.3f
    }

    /**
     * 是否精力充沛
     */
    fun isEnergetic(): Boolean {
        return getOverallEnergy() > 0.7f
    }

    /**
     * 是否困倦
     */
    fun isSleepy(): Boolean {
        return timeOfDay == TimeOfDay.LATE_NIGHT && energyLevel < 0.4f
    }

    /**
     * 获取状态描述
     */
    fun getStateDescription(): String {
        return when {
            isSleepy() -> "好困...想睡觉了..."
            needsRest() -> "有点累了..."
            isEnergetic() -> "精力充沛！"
            else -> "状态正常"
        }
    }
}

/**
 * 疲劳累积记录
 */
data class FatigueAccumulation(
    /**
     * 累积开始时间
     */
    val startTime: Long,

    /**
     * 对话次数
     */
    val conversationCount: Int,

    /**
     * 累积疲劳值
     */
    val accumulatedFatigue: Float
) {
    /**
     * 是否过期（超过1小时）
     */
    fun isExpired(currentTime: Long): Boolean {
        return currentTime - startTime > 60 * 60 * 1000
    }

    /**
     * 添加新对话
     */
    fun addConversation(intensity: Float = 0.1f): FatigueAccumulation {
        return copy(
            conversationCount = conversationCount + 1,
            accumulatedFatigue = (accumulatedFatigue + intensity).coerceAtMost(1f)
        )
    }

    /**
     * 自然恢复（每分钟恢复一点）
     */
    fun recover(minutes: Int): FatigueAccumulation {
        val recovery = minutes * 0.05f  // 每分钟恢复5%
        return copy(
            accumulatedFatigue = (accumulatedFatigue - recovery).coerceAtLeast(0f)
        )
    }
}
