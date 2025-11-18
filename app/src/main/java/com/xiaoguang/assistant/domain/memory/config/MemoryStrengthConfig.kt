package com.xiaoguang.assistant.domain.memory.config

/**
 * 记忆强度计算配置
 *
 * 用于配置Ebbinghaus遗忘曲线相关的权重系数
 */
data class MemoryStrengthConfig(
    /**
     * 基础强度系数（基于强化次数）
     * 计算公式：ln(1 + reinforcementCount) * BASE_STRENGTH_MULTIPLIER
     */
    val baseStrengthMultiplier: Float = 10f,

    /**
     * 情感强化因子（高情感事件更持久）
     * 计算公式：emotionIntensity * EMOTION_BONUS_MULTIPLIER
     */
    val emotionBonusMultiplier: Float = 5f,

    /**
     * 重要性加权系数
     * 计算公式：importance * IMPORTANCE_BONUS_MULTIPLIER
     */
    val importanceBonusMultiplier: Float = 3f,

    /**
     * 回忆难度惩罚系数（难回忆的更易忘）
     * 计算公式：recallDifficulty * DIFFICULTY_PENALTY_MULTIPLIER
     */
    val difficultyPenaltyMultiplier: Float = 2f,

    /**
     * 上下文相关性加成系数
     * 计算公式：contextRelevance * CONTEXT_BONUS_MULTIPLIER
     */
    val contextBonusMultiplier: Float = 2f,

    /**
     * 置信度权重（用于调整最终保留率）
     * 计算公式：retention * (CONFIDENCE_BASE_WEIGHT + confidence * CONFIDENCE_MULTIPLIER)
     */
    val confidenceBaseWeight: Float = 0.5f,
    val confidenceMultiplier: Float = 0.5f,

    /**
     * 时间单位转换系数（毫秒转天数）
     */
    val millisecondsPerDay: Long = 86400000L,

    /**
     * 最小有效强度值（避免除零错误）
     */
    val minEffectiveStrength: Float = 1f,

    /**
     * 强记忆阈值（用于统计）
     */
    val strongMemoryThreshold: Float = 0.7f,

    /**
     * 弱记忆阈值（用于统计）
     */
    val weakMemoryThreshold: Float = 0.3f
) {
    companion object {
        /**
         * 默认配置（当前系统使用的值）
         */
        val DEFAULT = MemoryStrengthConfig()

        /**
         * 保守配置（记忆更持久）
         */
        val CONSERVATIVE = MemoryStrengthConfig(
            baseStrengthMultiplier = 15f,
            emotionBonusMultiplier = 7f,
            importanceBonusMultiplier = 5f,
            difficultyPenaltyMultiplier = 1f,
            contextBonusMultiplier = 3f
        )

        /**
         * 激进配置（更快遗忘不重要记忆）
         */
        val AGGRESSIVE = MemoryStrengthConfig(
            baseStrengthMultiplier = 7f,
            emotionBonusMultiplier = 3f,
            importanceBonusMultiplier = 2f,
            difficultyPenaltyMultiplier = 3f,
            contextBonusMultiplier = 1f
        )
    }
}
